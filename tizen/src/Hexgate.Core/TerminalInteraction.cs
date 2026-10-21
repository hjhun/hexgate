using System.Text;
using XtermSharp;

namespace Hexgate.Core;

public readonly record struct CellPoint(int X,int Y);

/// <summary>Routes gestures through the VT engine and owns a single, revocable copy grant.</summary>
public sealed class TerminalInteraction : IDisposable
{
    public const int ClipboardLimit=1024*1024;
    private readonly TerminalScreen screen;
    private readonly Func<long> now;
    private bool down,remote,box,moved,armed,disposed;
    private long expires;
    private CellPoint start,end;
    private bool selected;
    public bool LocalSelectionMode {get;set;}
    public event Action<string>? CopyRequested;
    public event Action? SelectionChanged;
    public TerminalInteraction(TerminalScreen screen,Func<long>? now=null)
    {
        this.screen=screen;this.now=now??(()=>Environment.TickCount64);screen.Osc52+=ReceiveCopy;
    }
    public bool HasSelection {get{lock(screen.SyncRoot)return selected;}}
    public bool IsSelected(int x,int y)
    {
        lock(screen.SyncRoot) {
            if(disposed || !selected)return false;
            if(box)return y>=Math.Min(start.Y,end.Y) && y<=Math.Max(start.Y,end.Y) && x>=Math.Min(start.X,end.X) && x<=Math.Max(start.X,end.X);
            var (a,b)=Ordered();return y>=a.Y && y<=b.Y && (y!=a.Y || x>=a.X) && (y!=b.Y || x<=b.X);
        }
    }
    private (CellPoint A,CellPoint B) Ordered()=>start.Y<end.Y || (start.Y==end.Y && start.X<=end.X)?(start,end):(end,start);
    private CellPoint Clamp(int x,int y)
    {
        x=Math.Clamp(x,0,screen.Engine.Cols-1);y=Math.Clamp(y,0,screen.Engine.Rows-1);
        return new(x,y);
    }
    private CellPoint LocalPoint(CellPoint point)=>point.X>0 && screen.Engine.Buffer.GetChar(point.X,screen.Engine.Buffer.YDisp+point.Y).Width==0?point with{X=point.X-1}:point;
    public void Down(int x,int y,bool shift,bool control)
    {
        lock(screen.SyncRoot) {
            if(disposed)return;
            if(down)Cancel();Revoke();down=true;moved=false;armed=false;start=end=Clamp(x,y);box=shift&&control;selected=false;
            remote=screen.Engine.MouseMode!=MouseMode.Off && !box && !LocalSelectionMode;
            if(!remote)start=end=LocalPoint(start);
            if(remote)Mouse(start,false,false,shift,control);
            SelectionChanged?.Invoke();
        }
    }
    public void Move(int x,int y,bool shift,bool control)
    {
        lock(screen.SyncRoot) {
            if(disposed)return;
            var point=Clamp(x,y);
            if(down && !remote)point=LocalPoint(point);
            if(!down) {
                if(screen.Engine.MouseMode==MouseMode.AnyEvent && !LocalSelectionMode)screen.Engine.SendMouseMotion(3,point.X,point.Y);
                return;
            }
            if(point==end)return;
            end=point;moved=true;
            if(remote) {
                Arm(shift);
                if(screen.Engine.MouseMode.SendButtonTracking())Mouse(end,false,true,shift,control);
            } else {selected=true;SelectionChanged?.Invoke();}
        }
    }
    public void Up(int x,int y,bool shift,bool control)
    {
        lock(screen.SyncRoot) {
            if(!down || disposed)return;
            Move(x,y,shift,control);down=false;
            if(remote) {
                if(moved)Arm(shift);
                if(screen.Engine.MouseMode!=MouseMode.X10)Mouse(end,true,false,shift,control);
            } else {selected=moved;SelectionChanged?.Invoke();}
        }
    }
    private void Mouse(CellPoint point,bool release,bool motion,bool shift,bool control)
    {
        int flags=screen.Engine.EncodeMouseButton(0,release,shift,false,control);
        if(motion)screen.Engine.SendMouseMotion(flags,point.X,point.Y);else screen.Engine.SendEvent(flags,point.X,point.Y);
    }
    private void Arm(bool shift)
    {
        if(!shift || armed)return;armed=true;expires=now()+5000;
    }
    private void Revoke()=>expires=0;
    public void Cancel()
    {
        lock(screen.SyncRoot){if(disposed)return;Revoke();if(down && remote && screen.Engine.MouseMode is not (MouseMode.X10 or MouseMode.Off))Mouse(end,true,false,false,false);down=false;remote=false;selected=false;SelectionChanged?.Invoke();}
    }
    private void ReceiveCopy(string payload)
    {
        lock(screen.SyncRoot) {
            if(disposed || expires==0 || now()>=expires)return;
            Revoke(); // Even a malformed reply consumes this explicit gesture's grant.
            int separator=payload.IndexOf(';');if(separator<0)return;
            string target=payload[..separator],data=payload[(separator+1)..];
            if(target.Length>8 || target.Any(c=>c is not ('c' or 'p' or 's' or '0' or '1' or '2' or '3' or '4' or '5' or '6' or '7')) || data.Length>1398104 || data=="?")return;
            try {
                byte[] bytes=Convert.FromBase64String(data);if(bytes.Length==0 || bytes.Length>ClipboardLimit)return;
                string text=new UTF8Encoding(false,true).GetString(bytes);CopyRequested?.Invoke(text);
            } catch(FormatException){}catch(DecoderFallbackException){}
        }
    }
    public string SelectedText()
    {
        lock(screen.SyncRoot) {
            if(disposed || !selected)return "";
            var frame=screen.Snapshot();var (a,b)=Ordered();var result=new StringBuilder();
            int top=box?Math.Min(start.Y,end.Y):a.Y,bottom=box?Math.Max(start.Y,end.Y):b.Y;
            for(int y=top;y<=bottom;y++) {
                int left=box?Math.Min(start.X,end.X):(y==a.Y?a.X:0),right=box?Math.Max(start.X,end.X):(y==b.Y?b.X:frame.Rows[y].Length-1);
                if(y>top)result.Append('\n');
                for(int x=left;x<=right;x++){var cell=frame.Rows[y][x];if(cell.Width!=0)result.Append(cell.Text);}
            }
            string text=result.ToString();return Encoding.UTF8.GetByteCount(text)<=ClipboardLimit?text:"";
        }
    }
    public void Wheel(int x,int y,int direction)
    {
        lock(screen.SyncRoot) {
            if(disposed)return;
            Revoke();selected=false;
            if(screen.Engine.MouseMode!=MouseMode.Off && !LocalSelectionMode) {
                var point=Clamp(x,y);screen.Engine.SendEvent(screen.Engine.EncodeMouseButton(direction<0?4:5,false,false,false,false),point.X,point.Y);
            } else screen.Engine.ScrollLines(direction<0?-3:3);
            SelectionChanged?.Invoke();
        }
    }
    public byte[] Paste(string text)
    {
        if(Encoding.UTF8.GetByteCount(text)>ClipboardLimit)throw new InvalidDataException("Clipboard exceeds 1 MiB");
        // A pasted escape sequence must not close bracketed paste or inject terminal controls.
        text=text.Replace("\0","").Replace("\x1b","");
        lock(screen.SyncRoot){ObjectDisposedException.ThrowIf(disposed,this);return Encoding.UTF8.GetBytes(screen.Engine.BracketedPasteMode?"\x1b[200~"+text+"\x1b[201~":text);}
    }
    public void Dispose(){lock(screen.SyncRoot){if(disposed)return;Cancel();disposed=true;screen.Osc52-=ReceiveCopy;CopyRequested=null;SelectionChanged=null;}}
}
