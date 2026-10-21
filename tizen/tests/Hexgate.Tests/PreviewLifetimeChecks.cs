using Hexgate.Core;

internal static class PreviewLifetimeChecks
{
    public static async Task Run(Action<bool,string> check)
    {
        var group=new AsyncWorkGroup(1);
        var entered=new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
        var release=new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
        check(group.TryRun(async()=>{entered.SetResult();await release.Task;}),"preview work is admitted below capacity");
        await entered.Task.WaitAsync(TimeSpan.FromSeconds(5));
        check(!group.TryRun(()=>Task.CompletedTask),"preview work rejects excess concurrent producers");
        var drain=group.CompleteAsync();
        check(!drain.IsCompleted,"preview shutdown waits for an active producer");
        check(ReferenceEquals(drain,group.CompleteAsync()),"preview shutdown callers join the same drain");
        check(!group.TryRun(()=>Task.CompletedTask),"retired preview owner rejects late producers");
        release.SetResult();await drain.WaitAsync(TimeSpan.FromSeconds(5));
        var faulty=new AsyncWorkGroup(1);faulty.TryRun(()=>throw new IOException("fixture"));
        try {await faulty.CompleteAsync();throw new Exception("Failure discarded");}
        catch(InvalidOperationException){check(true,"preview worker failures are observed during shutdown");}

        // Lifecycle-only server: no SFTP/resource request is made in this test.
        var document=new RemoteDocument("/fixture.md","/","markdown","# Fixture");
        var server=new PreviewServer(document,null!,Path.GetTempPath(),TerminalThemes.Resolve("hexgate"));
        Task first=server.DisposeAsync().AsTask();
        Task second=server.DisposeAsync().AsTask();
        check(ReferenceEquals(first,second),"all preview disposal callers join the same cleanup task");
        await Task.WhenAll(first,second).WaitAsync(TimeSpan.FromSeconds(5));
        await server.DisposeAsync();
        using var client=new HttpClient {Timeout=TimeSpan.FromSeconds(2)};
        bool closed=false;
        try {await client.GetAsync(server.Address);}catch(HttpRequestException){closed=true;}
        check(closed,"disposed preview listener no longer accepts requests");
    }
}
