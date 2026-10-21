using System.Diagnostics;
using System.Reflection;
using System.Text.Json.Nodes;
using Renci.SshNet;
using Renci.SshNet.Common;

internal static class SftpFrameChecks
{
    private const int FrameLimit=1024*1024;
    public static async Task Run(string directory,int port,Action<bool,string> check)
    {
        var ready=JsonNode.Parse(File.ReadAllText(Path.Combine(directory,"ready.json")))!;
        string fingerprint=ready["fingerprint"]!.GetValue<string>();
        foreach(string mode in new[] {"valid","valid-split","maximum","consecutive","oversized",
            "oversized-split","zero","negative","over-limit","incomplete-header","incomplete-body","cancel"}) {
            File.WriteAllText(Path.Combine(directory,"mode"),mode);
            using var auth=new PasswordAuthenticationMethod("fixture",File.ReadAllText(Path.Combine(directory,"password")));
            using var client=new SftpClient(new ConnectionInfo("127.0.0.1",port,"fixture",auth)) {
                OperationTimeout=TimeSpan.FromSeconds(2)
            };
            client.HostKeyReceived+=(_,args)=>args.CanTrust=args.FingerPrintSHA256==fingerprint[7..];
            await client.ConnectAsync(CancellationToken.None);
            check(client.IsConnected,mode+": real SFTP version/realpath handshake");
            object session=typeof(SftpClient).GetField("_sftpSession",BindingFlags.Instance|BindingFlags.NonPublic)!.GetValue(client)!;
            var bufferField=session.GetType().GetField("_buffer",BindingFlags.Instance|BindingFlags.NonPublic)!;
            int bufferedPeak=0,allocationPeak=0;
            void Observe()
            {
                object buffer=bufferField.GetValue(session)!;
                bufferedPeak=Math.Max(bufferedPeak,(int)buffer.GetType().GetProperty("ActiveLength")!.GetValue(buffer)!);
                byte[] bytes=(byte[])buffer.GetType().GetField("_bytes",BindingFlags.Instance|BindingFlags.NonPublic)!.GetValue(buffer)!;
                allocationPeak=Math.Max(allocationPeak,bytes.Length);
            }
            using var cancel=new CancellationTokenSource();
            if(mode=="cancel")cancel.CancelAfter(250);
            var clock=Stopwatch.StartNew();
            int count=mode=="consecutive"?64:1;
            Task request=Task.WhenAll(Enumerable.Range(0,count).Select(i=>client.GetAttributesAsync("/probe/"+i,cancel.Token)));
            Exception? failure=null;
            try {
                while(!request.IsCompleted && clock.Elapsed<TimeSpan.FromSeconds(5)) {
                    Observe();await Task.Delay(2);
                }
                Observe();
                try {await request.WaitAsync(TimeSpan.FromSeconds(1));}catch(Exception error){failure=error;}
                check(bufferedPeak<=FrameLimit && allocationPeak<=FrameLimit,mode+": reassembly and backing allocation stay within 1 MiB");
                if(mode is "valid" or "valid-split" or "maximum" or "consecutive") {
                    check(failure==null,mode+": legitimate response completes");
                    if(mode=="maximum")check(allocationPeak==FrameLimit,"maximum: actual fragmented boundary fills 1 MiB allocation");
                } else if(mode.StartsWith("incomplete-",StringComparison.Ordinal)) {
                    check(failure is SshOperationTimeoutException,mode+": incomplete response ends with operation timeout");
                    check(clock.Elapsed<TimeSpan.FromSeconds(4),mode+": timeout completes predictably");
                } else if(mode=="cancel") {
                    check(failure is OperationCanceledException,"cancel: incomplete response observes cancellation");
                } else {
                    check(failure is SshException && failure.Message.Contains("SFTP response frame",StringComparison.Ordinal),mode+": invalid frame rejected at framing boundary");
                    check(clock.Elapsed<TimeSpan.FromSeconds(1.5),mode+": rejects before operation timeout");
                }
                Console.WriteLine($"SFTP_FRAME mode={mode} bufferedPeak={bufferedPeak} allocationPeak={allocationPeak} elapsedMs={clock.ElapsedMilliseconds}");
            } finally {
                await Task.Run(client.Dispose).WaitAsync(TimeSpan.FromSeconds(5));
                try {await request.WaitAsync(TimeSpan.FromSeconds(5));}catch(Exception) { }
            }
            check(request.IsCompleted,mode+": disposal joins the outstanding request");
        }
    }
}
