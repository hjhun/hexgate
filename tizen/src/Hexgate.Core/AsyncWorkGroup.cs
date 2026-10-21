namespace Hexgate.Core;

/// <summary>Bounded background work whose owner joins all admitted tasks at shutdown.</summary>
public sealed class AsyncWorkGroup
{
    private readonly object gate=new();
    private readonly HashSet<Task> active=[];
    private readonly int capacity;
    private bool closing;
    private Task? completion;
    private Exception? failure;

    public AsyncWorkGroup(int capacity)
    {
        if(capacity<1)throw new ArgumentOutOfRangeException(nameof(capacity));
        this.capacity=capacity;
    }
    public bool TryRun(Func<Task> work)
    {
        lock(gate) {
            if(closing || active.Count>=capacity)return false;
            Task task=Task.Run(async()=>{
                try {await work().ConfigureAwait(false);}
                catch(Exception error){lock(gate)failure??=error;}
            });
            active.Add(task);
            _=task.ContinueWith(done=>{lock(gate)active.Remove(done);},
                CancellationToken.None,TaskContinuationOptions.ExecuteSynchronously,TaskScheduler.Default);
            return true;
        }
    }
    public Task CompleteAsync()
    {
        lock(gate) {
            closing=true;
            return completion??=Finish(active.ToArray());
        }
    }
    private async Task Finish(Task[] pending)
    {
        await Task.WhenAll(pending).ConfigureAwait(false);
        lock(gate)if(failure!=null)throw new InvalidOperationException("Background work failed",failure);
    }
}
