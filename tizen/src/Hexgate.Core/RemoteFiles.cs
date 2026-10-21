using Renci.SshNet;
using System.Text;

namespace Hexgate.Core;

public sealed record RemoteEntry(string Path,string Name,bool Directory);
public sealed record RemoteDocument(string Path,string Root,string Kind,string Text);

/// <summary>One serialized SFTP channel; all operations are bounded and independent of UI.</summary>
public sealed class RemoteFiles : IAsyncDisposable
{
    public const int DocumentLimit=2*1024*1024,ResourceLimit=1024*1024;
    private readonly SftpClient client;
    private readonly SemaphoreSlim gate=new(1,1);
    private readonly CancellationTokenSource lifetime=new();
    private int disposed;
    internal RemoteFiles(SftpClient client)=>this.client=client;
    private async Task<T> Run<T>(Func<CancellationToken,Task<T>> action,CancellationToken token)
    {
        ObjectDisposedException.ThrowIf(Volatile.Read(ref disposed)!=0,this);
        using var linked=CancellationTokenSource.CreateLinkedTokenSource(token,lifetime.Token);
        await gate.WaitAsync(linked.Token).ConfigureAwait(false);
        try {linked.Token.ThrowIfCancellationRequested();return await action(linked.Token).ConfigureAwait(false);}
        finally {gate.Release();}
    }
    public Task<string> Home(CancellationToken token=default)=>Run(_=>Task.FromResult(client.WorkingDirectory),token);
    public Task<IReadOnlyList<RemoteEntry>> List(string path,CancellationToken token=default)=>Run<IReadOnlyList<RemoteEntry>>(async ct=>{
        ValidatePath(path);
        var canonical=(await client.GetAsync(path,ct).ConfigureAwait(false)).FullName;
        var entries=new List<RemoteEntry>();
        await foreach(var file in client.ListDirectoryAsync(canonical,ct).ConfigureAwait(false)) {
            if(file.Name is "." or "..")continue;
            entries.Add(new(file.FullName,file.Name,file.IsDirectory));if(entries.Count>=500)break;
        }
        return entries.OrderByDescending(e=>e.Directory).ThenBy(e=>e.Name,StringComparer.Ordinal).ToArray();
    },token);
    public Task<RemoteDocument> Document(string path,CancellationToken token=default)=>Run(async ct=>{
        ValidatePath(path);
        var file=await client.GetAsync(path,ct).ConfigureAwait(false);
        string canonical=file.FullName;
        string kind=System.IO.Path.GetExtension(canonical).ToLowerInvariant() switch {
            ".md" or ".markdown"=>"markdown", ".html" or ".htm"=>"html",
            ".drawio" or ".xml"=>"drawio", ".mmd" or ".mermaid"=>"mermaid",
            _=>throw new InvalidDataException("Choose Markdown, HTML, Mermaid or draw.io")
        };
        if(!file.IsRegularFile || file.Length>DocumentLimit)throw new InvalidDataException("Document exceeds 2 MiB or is not a regular file");
        var bytes=await Read(canonical,DocumentLimit,ct).ConfigureAwait(false);
        return new RemoteDocument(canonical,Parent(canonical),kind,Encoding.UTF8.GetString(bytes));
    },token);
    public Task<byte[]> Resource(string root,string relative,CancellationToken token=default)=>Run(async ct=>{
        ValidatePath(root);ValidatePath(relative);
        if(relative.StartsWith('/') || relative.Contains('\\') || relative.Contains(':'))throw new InvalidDataException("Relative resource required");
        var file=await client.GetAsync(root.TrimEnd('/')+"/"+relative,ct).ConfigureAwait(false);
        if(!Inside(root,file.FullName))throw new InvalidDataException("Resource outside document directory");
        if(!file.IsRegularFile || file.Length>ResourceLimit)throw new InvalidDataException("Resource exceeds 1 MiB or is not a regular file");
        return await Read(file.FullName,ResourceLimit,ct).ConfigureAwait(false);
    },token);
    private async Task<byte[]> Read(string canonical,int limit,CancellationToken token)
    {
        using var stream=client.OpenRead(canonical);
        using var output=new MemoryStream();var buffer=new byte[16384];
        while(true) {
            int n=await stream.ReadAsync(buffer.AsMemory(0,Math.Min(buffer.Length,limit+1-(int)output.Length)),token).ConfigureAwait(false);
            if(n==0)break;output.Write(buffer,0,n);
            if(output.Length>limit)throw new InvalidDataException("Remote read exceeds limit");
        }
        var after=await client.GetAsync(canonical,token).ConfigureAwait(false);
        if(after.FullName!=canonical)throw new InvalidDataException("Resource path changed during read");
        return output.ToArray();
    }
    private static void ValidatePath(string path)
    {
        if(string.IsNullOrWhiteSpace(path) || path.Length>4096 || path.Any(c=>c=='\0' || char.IsControl(c)))throw new ArgumentException("Invalid remote path");
    }
    public static string Parent(string path)=>path[..Math.Max(1,path.LastIndexOf('/'))];
    public static bool Inside(string root,string path)
    {
        if(!root.StartsWith('/') || !path.StartsWith('/'))return false;
        static string Normalize(string value) {
            var parts=new List<string>();
            foreach(var part in value.Split('/')) {
                if(part is "" or ".")continue;
                if(part==".."){if(parts.Count>0)parts.RemoveAt(parts.Count-1);}else parts.Add(part);
            }
            return "/"+string.Join('/',parts);
        }
        root=Normalize(root);path=Normalize(path);
        return root=="/" || path==root || path.StartsWith(root+"/",StringComparison.Ordinal);
    }
    public async ValueTask DisposeAsync()
    {
        if(Interlocked.Exchange(ref disposed,1)!=0)return;
        lifetime.Cancel();client.Dispose();
        await gate.WaitAsync().ConfigureAwait(false);gate.Release();lifetime.Dispose();
    }
}

/// <summary>Fresh per-document accounting. Failed requests consume a slot as on Android.</summary>
public sealed class PreviewBudget
{
    private int requests,bytes;
    public void ReserveRequest(){if(Interlocked.Increment(ref requests)>64)throw new InvalidDataException("Too many preview resources");}
    public void RecordBytes(int count){if(count<0 || Interlocked.Add(ref bytes,count)>16*1024*1024)throw new InvalidDataException("Preview resources exceed 16 MiB");}
}
