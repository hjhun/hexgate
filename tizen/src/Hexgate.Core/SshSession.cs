using Renci.SshNet;
using Renci.SshNet.Common;
using System.Security.Cryptography;
using System.Threading.Channels;

namespace Hexgate.Core;

/// <summary>A view-independent SSH PTY with explicit host verification and bounded input.</summary>
public sealed partial class SshSession : IAsyncDisposable
{
    public const int InputCommandLimit=256,InputByteLimit=2*1024*1024;
    private readonly CancellationTokenSource lifetime=new();
    private sealed record WriteCommand(byte[]? Bytes);
    private readonly object lifecycleGate=new(),inputGate=new(),geometryGate=new();
    private int desiredColumns=80,desiredRows=24;
    private bool geometryQueued,refreshRequested;
    private Task? reader,writer,connectTask;
    private TaskCompletionSource? disposal;
    // One reserved slot for coalesced geometry; data credits include the active write.
    private readonly Channel<WriteCommand> input=Channel.CreateBounded<WriteCommand>(
        new BoundedChannelOptions(InputCommandLimit+1) {SingleReader=true,FullMode=BoundedChannelFullMode.Wait});
    private SshClient? client;
    private ShellStream? shell;
    private Renci.SshNet.AuthenticationMethod? authentication;
    private PrivateKeyFile? privateKey;
    private EventHandler<HostKeyEventArgs>? hostKeyHandler;
    private int queuedBytes,queuedCommands,disposed,closed;
    private string? trustedFingerprint;
    private readonly SemaphoreSlim filesGate=new(1,1);
    private RemoteFiles? files;
    public event Action? Closed;
    public event Action<byte[]>? Output;
    public bool Connected
    {
        get {
            if(Volatile.Read(ref disposed)!=0)return false;
            try{return client?.IsConnected==true && shell?.CanWrite==true && !lifetime.IsCancellationRequested;}
            catch(ObjectDisposedException){return false;}
        }
    }
    public Task Connect(ConnectionProfile profile,string secret,string passphrase,Func<string,bool> verify,
        int columns,int rows,CancellationToken cancellationToken)
    {
        profile.Validate();ValidateGeometry(columns,rows);
        lock(lifecycleGate) {
            if(connectTask!=null || disposed!=0)throw new InvalidOperationException("Session already used");
            lock(geometryGate) {
                if(!geometryQueued){desiredColumns=columns;desiredRows=rows;}
            }
            return connectTask=ConnectCore(profile,secret,passphrase,verify,columns,rows,cancellationToken);
        }
    }
    private async Task ConnectCore(ConnectionProfile profile,string secret,string passphrase,Func<string,bool> verify,
        int columns,int rows,CancellationToken cancellationToken)
    {
        using var linked=CancellationTokenSource.CreateLinkedTokenSource(lifetime.Token,cancellationToken);
        linked.Token.ThrowIfCancellationRequested();
        if(profile.Authentication==Hexgate.Core.AuthenticationMethod.Password)
            authentication=new PasswordAuthenticationMethod(profile.User,secret);
        else {
            using var keyStream=new MemoryStream(System.Text.Encoding.UTF8.GetBytes(secret));
            privateKey=new PrivateKeyFile(keyStream,passphrase);
            authentication=new PrivateKeyAuthenticationMethod(profile.User,privateKey);
        }
        // Allow the explicit 90-second identity confirmation to finish during key exchange.
        var info=new ConnectionInfo(profile.Host,profile.Port,profile.User,authentication) {Timeout=TimeSpan.FromSeconds(120)};
        var target=new SshClient(info) {KeepAliveInterval=TimeSpan.FromSeconds(15)};
        client=target;
        hostKeyHandler=(_,e)=> {
            string fingerprint="SHA256:"+Convert.ToBase64String(SHA256.HashData(e.HostKey)).TrimEnd('=');
            bool approved=Volatile.Read(ref disposed)==0 && !linked.IsCancellationRequested && verify(fingerprint);
            e.CanTrust=approved && Volatile.Read(ref disposed)==0 && !linked.IsCancellationRequested;
            if(e.CanTrust)trustedFingerprint=fingerprint;
        };
        target.HostKeyReceived+=hostKeyHandler;
        await target.ConnectAsync(linked.Token).ConfigureAwait(false);
        linked.Token.ThrowIfCancellationRequested();
        var stream=target.CreateShellStream("xterm-256color",(uint)columns,(uint)rows,0,0,64*1024);
        bool adopted=false;
        try {
            lock(lifecycleGate) {
                if(disposed==0 && !linked.IsCancellationRequested) {
                    shell=stream;adopted=true;
                    reader=Task.Run(()=>ReadOutput(stream));
                    writer=Task.Run(()=>WriteInput(stream));
                }
            }
            if(!adopted)throw new OperationCanceledException("Session retired during shell creation");
        } finally {if(!adopted)stream.Dispose();}
        Send(System.Text.Encoding.UTF8.GetBytes("printf '\\033]2;hexgate-tty:%s\\007' \"$SSH_TTY\"\n"));
        var startup=profile.HerdrSession.Length==0 ? profile.StartupCommand
            : "export PATH=\"$HOME/.local/bin:$PATH\"; herdr --session '"+profile.HerdrSession+"'";
        if(startup.Length!=0)Send(System.Text.Encoding.UTF8.GetBytes(startup+"\n"));
    }
    private async Task ReadOutput(ShellStream stream)
    {
        var buffer=new byte[16*1024];
        try {
            while(!lifetime.IsCancellationRequested) {
                int length=await stream.ReadAsync(buffer,lifetime.Token).ConfigureAwait(false);
                if(length==0)break;
                if(Volatile.Read(ref disposed)==0)Output?.Invoke(buffer.AsSpan(0,length).ToArray());
            }
        } catch(Exception) { }
        finally {NotifyClosed();}
    }
    private async Task WriteInput(ShellStream stream)
    {
        try {
            await foreach(var command in input.Reader.ReadAllAsync(lifetime.Token)) {
                if(command.Bytes is {} bytes) {
                    try {if(Volatile.Read(ref disposed)!=0)break;stream.Write(bytes,0,bytes.Length);stream.Flush();}
                    finally {ReleaseInput(bytes.Length);}
                } else {
                    if(Volatile.Read(ref disposed)!=0)break;
                    int c,r;bool refresh;
                    lock(geometryGate){c=desiredColumns;r=desiredRows;refresh=refreshRequested;refreshRequested=false;geometryQueued=false;}
                    if(refresh)stream.ChangeWindowSize((uint)(c==512?c-1:c+1),(uint)r,0,0);
                    lock(geometryGate){c=desiredColumns;r=desiredRows;}
                    stream.ChangeWindowSize((uint)c,(uint)r,0,0);
                }
            }
        } catch(OperationCanceledException) { }
        catch(Exception) {NotifyClosed();}
    }
    private void NotifyClosed()
    {
        if(Volatile.Read(ref disposed)==0 && Interlocked.Exchange(ref closed,1)==0)Closed?.Invoke();
    }
    public void Send(byte[] bytes)
    {
        lock(inputGate) {
            if(bytes.Length>1024*1024 || disposed!=0)throw new InvalidOperationException("Input unavailable or exceeds 1 MiB");
            if(bytes.Length==0)return;
            if(queuedCommands>=InputCommandLimit || bytes.Length>InputByteLimit-queuedBytes)
                throw new InvalidOperationException("Input queue full");
            var copy=bytes.ToArray();queuedBytes+=copy.Length;queuedCommands++;
            if(!input.Writer.TryWrite(new(copy))) {
                queuedBytes-=copy.Length;queuedCommands--;throw new InvalidOperationException("Input closed");
            }
        }
    }
    private void ReleaseInput(int bytes){lock(inputGate){queuedBytes-=bytes;queuedCommands--;}}
    private static void ValidateGeometry(int columns,int rows)
    {
        if(columns is <2 or >512 || rows is <2 or >256)throw new ArgumentOutOfRangeException();
    }
    public void Resize(int columns,int rows)
    {
        ValidateGeometry(columns,rows);
        lock(geometryGate) {
            if(disposed!=0 || desiredColumns==columns && desiredRows==rows)return;
            desiredColumns=columns;desiredRows=rows;QueueGeometry();
        }
    }
    public void Refresh(){lock(geometryGate){if(disposed!=0)return;refreshRequested=true;QueueGeometry();}}
    public async Task<RemoteFiles> OpenFiles(CancellationToken cancellationToken)
    {
        ObjectDisposedException.ThrowIf(Volatile.Read(ref disposed)!=0,this);
        using var linked=CancellationTokenSource.CreateLinkedTokenSource(cancellationToken,lifetime.Token);
        await filesGate.WaitAsync(linked.Token).ConfigureAwait(false);
        try {
            if(!Connected || trustedFingerprint==null)throw new InvalidOperationException("Connect before opening remote files");
            if(files!=null)return files;
            var transport=new SftpClient(client!.ConnectionInfo){OperationTimeout=TimeSpan.FromSeconds(15),KeepAliveInterval=TimeSpan.FromSeconds(15)};
            transport.HostKeyReceived+=(_,e)=>e.CanTrust="SHA256:"+Convert.ToBase64String(SHA256.HashData(e.HostKey)).TrimEnd('=')==trustedFingerprint;
            try {
                await transport.ConnectAsync(linked.Token).ConfigureAwait(false);
                linked.Token.ThrowIfCancellationRequested();
                files=new RemoteFiles(transport);return files;
            } catch {transport.Dispose();throw;}
        } finally {filesGate.Release();}
    }
    private void QueueGeometry()
    {
        if(geometryQueued || disposed!=0)return;
        geometryQueued=input.Writer.TryWrite(new(null));
    }
    public ValueTask DisposeAsync()
    {
        lock(lifecycleGate) {
            if(disposal!=null)return new(disposal.Task);
            Volatile.Write(ref disposed,1);
            disposal=new(TaskCreationOptions.RunContinuationsAsynchronously);
            // Even synchronous SSH shutdown runs off the caller's NUI thread.
            _=Task.Run(DisposeCore);return new(disposal.Task);
        }
    }
    private async Task DisposeCore()
    {
        Exception? failure=null;
        void Release(Action action){try{action();}catch(Exception error){failure??=error;}}
        try {
            Release(lifetime.Cancel);input.Writer.TryComplete();
            Task? pending;lock(lifecycleGate)pending=connectTask;
            if(client!=null && hostKeyHandler!=null)client.HostKeyReceived-=hostKeyHandler;
            // No shell/worker can be adopted after disposed is set under lifecycleGate.
            Release(()=>shell?.Dispose());Release(()=>contextProbe?.Dispose());Release(()=>client?.Dispose());
            if(pending!=null)try{await pending.ConfigureAwait(false);}catch(Exception) { }
            if(reader!=null)try{await reader.ConfigureAwait(false);}catch(Exception) { }
            if(writer!=null)try{await writer.ConfigureAwait(false);}catch(Exception) { }
            await contextGate.WaitAsync().ConfigureAwait(false);contextGate.Release();
            await filesGate.WaitAsync().ConfigureAwait(false);
            try {if(files!=null)await files.DisposeAsync().ConfigureAwait(false);}finally{filesGate.Release();}
        } catch(Exception error){failure??=error;}
        finally {
            while(input.Reader.TryRead(out var command))if(command.Bytes is {} bytes)ReleaseInput(bytes.Length);
            Release(()=>authentication?.Dispose());Release(()=>privateKey?.Dispose());
            Output=null;Closed=null;files=null;shell=null;client=null;authentication=null;privateKey=null;
            hostKeyHandler=null;trustedFingerprint=null;
            Release(contextGate.Dispose);Release(filesGate.Dispose);Release(lifetime.Dispose);
            if(failure==null)disposal!.TrySetResult();else disposal!.TrySetException(failure);
        }
    }
}
