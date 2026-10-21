using System.Text;
using Renci.SshNet;

namespace Hexgate.Core;

public sealed partial class SshSession
{
    private readonly SemaphoreSlim contextGate=new(1,1);
    private ShellStream? contextProbe;

    public Task<HerdrContext> QueryHerdr(string helper,string session,string tty,CancellationToken cancellationToken)
    {
        string command=HerdrContext.Command(helper,session,tty);
        // Channel creation and stream disposal can perform synchronous SSH I/O.
        return Task.Run(()=>QueryHerdrCore(command,session,cancellationToken));
    }
    private async Task<HerdrContext> QueryHerdrCore(string command,string session,CancellationToken cancellationToken)
    {
        using var linked=CancellationTokenSource.CreateLinkedTokenSource(cancellationToken,lifetime.Token);
        linked.CancelAfter(TimeSpan.FromSeconds(20));
        await contextGate.WaitAsync(linked.Token).ConfigureAwait(false);
        ShellStream? stream=null;Task? writing=null;Task<int>? reading=null;
        try {
            ObjectDisposedException.ThrowIf(Volatile.Read(ref disposed)!=0,this);
            if(!Connected)throw new InvalidOperationException("Connect before resolving Herdr context");
            // This path uses the same fixed receive buffer/consumer credit as the terminal.
            stream=client!.CreateShellStreamNoTerminal(65536);
            lock(lifecycleGate) {
                linked.Token.ThrowIfCancellationRequested();
                ObjectDisposedException.ThrowIf(disposed!=0,this);
                contextProbe=stream;
            }
            var target=stream;
            writing=Task.Run(()=>{target.Write(Encoding.UTF8.GetBytes(command));target.Flush();});
            await writing.WaitAsync(linked.Token).ConfigureAwait(false);
            using var output=new MemoryStream();var buffer=new byte[16384];
            while(true) {
                reading=stream.ReadAsync(buffer.AsMemory(0,Math.Min(buffer.Length,HerdrContext.OutputLimit+1-(int)output.Length)),linked.Token).AsTask();
                int count=await reading.WaitAsync(linked.Token).ConfigureAwait(false);
                if(count==0)break;
                output.Write(buffer,0,count);
                if(output.Length>HerdrContext.OutputLimit)throw new InvalidDataException("Herdr response exceeds limit");
            }
            linked.Token.ThrowIfCancellationRequested();
            return HerdrContext.Parse(new UTF8Encoding(false,true).GetString(output.ToArray()),session);
        } finally {
            try {
                lock(lifecycleGate)if(ReferenceEquals(contextProbe,stream))contextProbe=null;
                stream?.Dispose();
                if(writing!=null)try{await writing.ConfigureAwait(false);}catch(Exception) { }
                if(reading!=null)try{await reading.ConfigureAwait(false);}catch(Exception) { }
            } finally {contextGate.Release();}
        }
    }
}
