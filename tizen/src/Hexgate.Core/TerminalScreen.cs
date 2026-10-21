using XtermSharp;

namespace Hexgate.Core;

/// <summary>Owns the reusable VT engine independently of any NUI view.</summary>
public sealed class TerminalScreen : SimpleTerminalDelegate, IDisposable
{
    private readonly object gate = new();
    private bool disposed;
    internal object SyncRoot=>gate;
    public Terminal Engine { get; }
    public event Action<byte[]>? Response;
    public event Action? Changed;
    internal event Action<string>? Osc52;
    public string? Tty {get;private set;}
    public TerminalScreen(int columns = 80, int rows = 24)
    {
        Engine = new Terminal(this, new TerminalOptions { Cols=columns, Rows=rows, ConvertEol=false, TermName="xterm-256color" });
        Engine.SetOscHandler(52,data=>Osc52?.Invoke(data));
    }
    public void Feed(byte[] data)
    {
        lock(gate) {if(disposed)return;Engine.Feed(data);Changed?.Invoke();}
    }
    public void Resize(int columns, int rows)
    {
        if(columns<2 || columns>512 || rows<2 || rows>256) throw new ArgumentOutOfRangeException();
        lock(gate) {ObjectDisposedException.ThrowIf(disposed,this);Engine.Resize(columns,rows);Changed?.Invoke();}
    }
    public string[] Lines()
    {
        lock(gate) {ObjectDisposedException.ThrowIf(disposed,this);return Enumerable.Range(0,Engine.Rows)
            .Select(row=>RowText(row)).ToArray();}
    }
    private string RowText(int row)
    {
        var text=new System.Text.StringBuilder();
        for(int column=0;column<Engine.Cols;column++) {
            var cell=Engine.Buffer.GetChar(column,Engine.Buffer.YDisp+row);
            if(cell.Code==0) { if(cell.Width!=0)text.Append(' '); }
            else { text.Append(char.ConvertFromUtf32(cell.Code));text.Append(cell.Combining); }
        }
        return text.ToString();
    }
    public TerminalFrame Snapshot()
    {
        lock(gate) {
            ObjectDisposedException.ThrowIf(disposed,this);
            var rows=new TerminalCell[Engine.Rows][];
            for(int y=0;y<Engine.Rows;y++) {
                rows[y]=new TerminalCell[Engine.Cols];
                for(int x=0;x<Engine.Cols;x++) {
                    var cell=Engine.Buffer.GetChar(x,Engine.Buffer.YDisp+y);
                    var text=cell.Code==0 ? (cell.Width==0?"":" ") : char.ConvertFromUtf32(cell.Code)+(cell.Combining??"");
                    rows[y][x]=new(text,cell.Width,cell.Attribute);
                }
            }
            return new(rows,Engine.Buffer.X,Engine.Buffer.Y,Engine.CursorHidden || Engine.Buffer.YDisp!=Engine.Buffer.YBase);
        }
    }
    public void Dispose()
    {
        lock(gate) {
            if(disposed)return;disposed=true;Response=null;Changed=null;Osc52=null;Tty=null;Engine.Dispose();
        }
    }
    public override void Send(byte[] data) {lock(gate){if(!disposed)Response?.Invoke(data);}}
    public override void SetTerminalTitle(Terminal source,string title)
    {
        lock(gate) {
            if(disposed)return;
            const string prefix="hexgate-tty:";
            if(title.StartsWith(prefix,StringComparison.Ordinal)) {
                string path=title[prefix.Length..];
                if(path.StartsWith("/dev/pts/",StringComparison.Ordinal) && path.Length>9 && path.Length<80 && path[9..].All(char.IsDigit))Tty=path;
            }
        }
    }
    public override bool IsProcessTrusted() => false;
}

public sealed record TerminalCell(string Text,int Width,int Attribute);
public sealed record TerminalFrame(TerminalCell[][] Rows,int CursorX,int CursorY,bool CursorHidden);
