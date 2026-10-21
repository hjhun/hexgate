namespace Hexgate.Core;

/// <summary>One pending UI status, scoped to the current connection generation.</summary>
public sealed class UiStatusDispatcher : IDisposable
{
    private readonly SynchronizationContext context;
    private readonly Func<int> generation;
    private readonly Action<string> apply;
    private readonly object gate=new();
    private (int Generation,string Text)? pending;
    private bool queued,disposed;
    public UiStatusDispatcher(SynchronizationContext context,Func<int> generation,Action<string> apply)
    {this.context=context;this.generation=generation;this.apply=apply;}
    public void Post(string text,int ticket)
    {
        lock(gate) {
            if(disposed || ticket!=generation())return;
            pending=(ticket,text);if(queued)return;queued=true;
            try {context.Post(_=>Deliver(),null);}
            catch {queued=false;pending=null;throw;}
        }
    }
    private void Deliver()
    {
        lock(gate) {
            queued=false;var next=pending;pending=null;
            if(!disposed && next is {} value && value.Generation==generation())apply(value.Text);
        }
    }
    public void Dispose(){lock(gate){disposed=true;pending=null;}}
}
