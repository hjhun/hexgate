using System.Text;
using Hexgate.Core;
using Renci.SshNet;

internal static class ReceiveFlowChecks
{
    private const int WindowLimit=1024*1024;
    public static async Task Run(ConnectionProfile profile,string key,string fingerprint,Action<bool,string> check)
    {
        using var keyFile=new PrivateKeyFile(new MemoryStream(Encoding.UTF8.GetBytes(key)));
        using var client=new SshClient(profile.Host,profile.Port,profile.User,keyFile);
        client.HostKeyReceived+=(_,args)=>args.CanTrust=args.FingerPrintSHA256==fingerprint[7..];
        await client.ConnectAsync(CancellationToken.None);
        using var shell=client.CreateShellStream("xterm",80,24,0,0,65536);
        shell.Write("stty raw -echo; printf 'FLOW_READY\\n'\n");shell.Flush();
        var banner=new StringBuilder();var deadline=DateTime.UtcNow.AddSeconds(5);
        while(!banner.ToString().Contains("FLOW_READY\n") && DateTime.UtcNow<deadline) {
            if(shell.DataAvailable)banner.Append(shell.Read());
            await Task.Delay(10);
        }
        if(!banner.ToString().Contains("FLOW_READY\n"))throw new Exception("Flow fixture did not become ready");
        // All content is synthetic; do not read the shell while its producer fills the window.
        const int payloadLength=8*1024*1024;
        shell.Write("python3 -c \"import sys;sys.stdout.buffer.write(b'FLOW_BEGIN'+b'x'*8388608+b'FLOW_DONE');sys.stdout.flush()\"\n");shell.Flush();
        deadline=DateTime.UtcNow.AddSeconds(10);
        while(shell.Length<WindowLimit && DateTime.UtcNow<deadline)await Task.Delay(10);
        await Task.Delay(350);
        check(shell.Length<=WindowLimit,"SSH receive window bounds unread data while terminal consumption is stopped");
        using(var probe=client.CreateCommand("printf FLOW_CONTROL_ALIVE")) {
            await probe.ExecuteAsync().WaitAsync(TimeSpan.FromSeconds(5));
            check(probe.Result=="FLOW_CONTROL_ALIVE","a full terminal window does not block the shared SSH message loop");
        }
        var bytes=new byte[16384];int received=0;bool payloadCorrect=true;var suffix=new StringBuilder();var prefix=new StringBuilder();bool started=false;
        deadline=DateTime.UtcNow.AddSeconds(30);
        while(!suffix.ToString().Contains("FLOW_DONE") && DateTime.UtcNow<deadline) {
            if(!shell.DataAvailable){await Task.Delay(1);continue;}
            int length=shell.Read(bytes,0,bytes.Length);
            for(int i=0;i<length;i++) {
                if(!started) {
                    prefix.Append((char)bytes[i]);
                    if(prefix.Length>1024)throw new Exception("Flow payload marker missing");
                    started=prefix.ToString().EndsWith("FLOW_BEGIN",StringComparison.Ordinal);
                }
                else if(received<payloadLength){payloadCorrect&=bytes[i]==(byte)'x';received++;}
                else suffix.Append((char)bytes[i]);
            }
            if(shell.Length>WindowLimit)throw new Exception("Receive window exceeded while draining");
        }
        Console.WriteLine($"FLOW counts: received={received}, valid={payloadCorrect}, suffixDone={suffix.ToString().Contains("FLOW_DONE")}, unread={shell.Length}");
        check(received==payloadLength && payloadCorrect && suffix.ToString().Contains("FLOW_DONE"),
            "eight MiB SSH flood resumes without dropped or reordered terminal bytes");
        shell.Write("python3 -c \"import sys;sys.stdout.buffer.write(b'y'*8388608);sys.stdout.flush()\"\n");shell.Flush();
        deadline=DateTime.UtcNow.AddSeconds(10);
        while(shell.Length<WindowLimit && DateTime.UtcNow<deadline)await Task.Delay(10);
        check(shell.Length<=WindowLimit && shell.Length>0,"a repeated flood remains bounded before disposal");
        await Task.Run(shell.Dispose).WaitAsync(TimeSpan.FromSeconds(5));
        check(!shell.CanWrite,"a full receive window can be disposed without waiting for terminal consumption");
        using var noTerminal=client.CreateShellStreamNoTerminal(65536);
        noTerminal.Write("python3 -c \"import sys;sys.stderr.write('EXTENDED_FLOW_OK');sys.stderr.flush()\"\n");noTerminal.Flush();
        var extended=new StringBuilder();deadline=DateTime.UtcNow.AddSeconds(5);
        while(!extended.ToString().Contains("EXTENDED_FLOW_OK") && DateTime.UtcNow<deadline) {
            if(noTerminal.DataAvailable)extended.Append(noTerminal.Read());else await Task.Delay(10);
        }
        check(extended.ToString().Contains("EXTENDED_FLOW_OK"),"bounded shells consume SSH extended output through the same credit budget");
    }
}
