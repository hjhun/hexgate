using System.Text;
using Hexgate.Core;

internal static class LiveLifetimeChecks
{
    public static async Task Run(ConnectionProfile profile,string secret,string fingerprint,Action<bool,string> check)
    {
        await using(var session=new SshSession()) {
            var output=new StringBuilder();
            session.Output+=bytes=>{lock(output)output.Append(Encoding.UTF8.GetString(bytes));};
            await session.Connect(profile,secret,"",fp=>{session.Resize(113,29);session.Refresh();return fp==fingerprint;},80,24,CancellationToken.None);
            session.Send(Encoding.UTF8.GetBytes("printf 'GEOMETRY '; stty size\r"));
            bool matched=false;var end=DateTime.UtcNow.AddSeconds(5);
            while(!matched && DateTime.UtcNow<end) {
                lock(output)matched=output.ToString().Split('\n').Any(line=>line.Contains("GEOMETRY 29 113") && !line.Contains("printf"));
                await Task.Delay(20);
            }
            check(matched,"viewport changes during host verification reach the actual PTY after connect");
        }
        var blockedReader=new SshSession();
        using(var release=new ManualResetEventSlim()) {
            var entered=new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);int callbacks=0;
            blockedReader.Output+=_=>{Interlocked.Increment(ref callbacks);entered.TrySetResult();release.Wait(TimeSpan.FromSeconds(10));};
            try {
                await blockedReader.Connect(profile,secret,"",fp=>fp==fingerprint,80,24,CancellationToken.None);
                await entered.Task.WaitAsync(TimeSpan.FromSeconds(5));
                Task first=blockedReader.DisposeAsync().AsTask(),second=blockedReader.DisposeAsync().AsTask();
                check(!first.IsCompleted && !second.IsCompleted,
                    "concurrent disposal callers share completion while an output callback is still active");
                release.Set();await Task.WhenAll(first,second).WaitAsync(TimeSpan.FromSeconds(10));
                int delivered=Volatile.Read(ref callbacks);await Task.Delay(60);
                check(!blockedReader.Connected && callbacks==delivered,"completed transport disposal leaves no output callback running");
            } finally {release.Set();await blockedReader.DisposeAsync();}
        }
        var blockedConnect=new SshSession();
        using(var release=new ManualResetEventSlim()) {
            var entered=new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);int outputs=0;
            blockedConnect.Output+=_=>Interlocked.Increment(ref outputs);
            Task pending=blockedConnect.Connect(profile,secret,"",fp=>{entered.TrySetResult();release.Wait(TimeSpan.FromSeconds(10));return true;},80,24,CancellationToken.None);
            try {
                await entered.Task.WaitAsync(TimeSpan.FromSeconds(5));
                Task first=blockedConnect.DisposeAsync().AsTask(),second=blockedConnect.DisposeAsync().AsTask();
                check(!first.IsCompleted && !second.IsCompleted,"retiring an in-flight handshake has one shared cleanup operation");
                release.Set();try{await pending;}catch(Exception) { }
                await Task.WhenAll(first,second).WaitAsync(TimeSpan.FromSeconds(10));
                check(!blockedConnect.Connected && outputs==0,"handshake retirement cannot publish a shell or output afterward");
            } finally {release.Set();await blockedConnect.DisposeAsync();}
        }
        await using(var session=new SshSession()) {
            await session.Connect(profile,secret,"",fp=>fp==fingerprint,80,24,CancellationToken.None);
            Task<RemoteFiles> opening=session.OpenFiles(CancellationToken.None);
            Task closing=session.DisposeAsync().AsTask();RemoteFiles? files=null;
            try{files=await opening;}catch(Exception) { }
            await closing.WaitAsync(TimeSpan.FromSeconds(10));
            bool retired=files==null;
            if(files!=null)try{await files.Home();}catch(ObjectDisposedException){retired=true;}
            check(retired && !session.Connected,"SFTP opening racing retirement cannot outlive its owning SSH session");
        }
    }
}
