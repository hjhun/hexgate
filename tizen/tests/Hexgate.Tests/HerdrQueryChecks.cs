using Hexgate.Core;

internal static class HerdrQueryChecks
{
    public static async Task Run(SshSession session,Action<bool,string> check)
    {
        const string helper="print('{\"session\":\"fixture\",\"automatic\":true,\"focused\":\"w1:p1\",\"panes\":[{\"id\":\"w1:p1\",\"cwd\":\"/tmp\",\"name\":\"Own fixture\"}]}')";
        var context=await session.QueryHerdr(helper,"fixture","/dev/pts/1",CancellationToken.None);
        check(context.Automatic && context.Focused=="w1:p1","actual no-PTY SSH probe returns bounded Herdr JSON");
        bool rejected=false;
        try{await session.QueryHerdr("print('x' * (1024*1024+1))","fixture","/dev/pts/1",CancellationToken.None);}catch(InvalidDataException){rejected=true;}
        check(rejected,"actual no-PTY SSH probe rejects oversized output");
        using var cancelled=new CancellationTokenSource(TimeSpan.FromMilliseconds(250));
        rejected=false;
        try{await session.QueryHerdr("import sys; sys.stdin.read()","fixture","/dev/pts/1",cancelled.Token);}catch(OperationCanceledException){rejected=true;}
        check(rejected,"actual waiting no-PTY SSH probe cancels and joins its read");
        context=await session.QueryHerdr(helper,"fixture","/dev/pts/1",CancellationToken.None);
        check(context.Automatic && session.Connected,"cancelled context probe leaves terminal transport usable");
    }
    public static async Task Retirement(ConnectionProfile profile,string secret,string fingerprint,Action<bool,string> check)
    {
        await using var retiring=new SshSession();
        await retiring.Connect(profile,secret,"",actual=>actual==fingerprint,80,24,CancellationToken.None);
        string marker=Path.Combine(Path.GetTempPath(),"hexgate-query-retire-"+Guid.NewGuid().ToString("N"));
        Task<HerdrContext>? probe=null;
        try {
            probe=retiring.QueryHerdr("from pathlib import Path; Path('"+marker+"').write_text('ready'); import sys; sys.stdin.read()","fixture","/dev/pts/1",CancellationToken.None);
            var deadline=DateTime.UtcNow.AddSeconds(10);
            while(!File.Exists(marker) && DateTime.UtcNow<deadline)await Task.Delay(25);
            check(File.Exists(marker),"actual context helper started before retirement test");
            await retiring.DisposeAsync().AsTask().WaitAsync(TimeSpan.FromSeconds(10));
            bool rejected=false;try{await probe.WaitAsync(TimeSpan.FromSeconds(2));}catch(OperationCanceledException){rejected=true;}catch(ObjectDisposedException){rejected=true;}
            check(rejected && !retiring.Connected,"session disposal closes and joins an active context probe");
        } finally {File.Delete(marker);}
    }

}
