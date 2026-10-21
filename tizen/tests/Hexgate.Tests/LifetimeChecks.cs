using System.Text;
using Hexgate.Core;

internal static class LifetimeChecks
{
    public static async Task Run(Action<bool,string> check)
    {
        await using(var queue=new SshSession()) {
            int accepted=0;
            try {for(;accepted<512;accepted++)queue.Send([65]);}
            catch(InvalidOperationException) { }
            check(accepted==256,"tiny input writes have a command-count bound independent of byte budget");
        }
        await using(var queue=new SshSession()) {
            for(int i=0;i<10000;i++)queue.Send([]);
            for(int i=0;i<256;i++)queue.Send([65]);
            check(true,"empty writes consume no pending command slots");
            try{queue.Send([65]);throw new Exception("Queue count bypassed");}
            catch(InvalidOperationException){check(true,"pending command capacity rejects the next tiny write");}
        }
        await using(var queue=new SshSession()) {
            queue.Send(new byte[1024*1024]);queue.Send(new byte[1024*1024]);
            try{queue.Send([65]);throw new Exception("Queue bytes bypassed");}
            catch(InvalidOperationException){check(true,"aggregate pending input bytes remain bounded at two MiB");}
        }
        var retired=new SshSession();await retired.DisposeAsync();await retired.DisposeAsync();
        try{retired.Send([65]);throw new Exception("Retired input accepted");}
        catch(InvalidOperationException){check(true,"retired transport rejects input after repeat disposal");}
        try{await retired.OpenFiles(CancellationToken.None);throw new Exception("Retired files accepted");}
        catch(ObjectDisposedException){check(true,"retired SFTP access fails before touching disposed synchronization resources");}
        retired.Resize(90,30);retired.Refresh();
        check(!retired.Connected,"late viewport requests cannot revive a retired transport");

        var screen=new TerminalScreen();var engine=screen.Engine;
        int callbacks=0;screen.Changed+=()=>callbacks++;screen.Response+=_=>callbacks++;
        screen.Feed(Encoding.UTF8.GetBytes("RETIRED SCREEN\x1b[?1049hALTERNATE\x1b[?1049l"));
        screen.Dispose();screen.Dispose();callbacks=0;
        screen.Feed(Encoding.UTF8.GetBytes("LATE\x1b[5n"));screen.Send([65]);
        check(callbacks==0,"retired terminal suppresses late output, response and frame callbacks");
        check(engine.Buffers.Normal.Lines.Length==0 && engine.Buffers.Alt.Lines.Length==0,
            "terminal disposal releases both normal and alternate row buffers");
        try{engine.Feed("AFTER DISPOSE");throw new Exception("Disposed engine accepted input");}
        catch(ObjectDisposedException){check(true,"mature VT parser rejects feed after disposal");}
        try{screen.Snapshot();throw new Exception("Disposed snapshot accepted");}
        catch(ObjectDisposedException){check(true,"retired native model cannot produce another frame");}

        var context=new PendingContext();int generation=1;var delivered=new List<string>();
        using var status=new UiStatusDispatcher(context,()=>generation,delivered.Add);
        for(int i=0;i<10000;i++)status.Post("Status "+i,1);
        check(context.Count==1,"status bursts occupy only one pending UI callback");
        context.Flush();check(delivered.SequenceEqual(new[]{"Status 9999"}),"status delivery preserves the latest message");
        status.Post("Old connection",1);generation=2;status.Post("Retired producer",1);
        context.Flush();check(delivered.Count==1,"queued status from a retired connection is discarded");
        status.Post("Current connection",2);status.Post("Late old producer",1);context.Flush();
        check(delivered.Last()=="Current connection","retired producer cannot replace a current pending status");
        status.Post("After shutdown",2);status.Dispose();context.Flush();status.Post("New work after shutdown",2);
        check(delivered.Count==2 && context.Count==0,"disposed UI delivery drains inert callbacks and rejects new work");
    }
    private sealed class PendingContext : SynchronizationContext
    {
        private readonly Queue<Action> pending=[];
        public int Count=>pending.Count;
        public override void Post(SendOrPostCallback callback,object? state)=>pending.Enqueue(()=>callback(state));
        public void Flush(){while(pending.TryDequeue(out var callback))callback();}
    }
}
