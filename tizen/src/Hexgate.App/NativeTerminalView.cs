using Hexgate.Core;
using Tizen.NUI;
using Tizen.NUI.BaseComponents;

namespace Hexgate.App;

/// <summary>Renders VT cell runs as positioned NUI actors; no escape parsing or network work.</summary>
internal sealed class NativeTerminalView : View
{
    private readonly TerminalScreen screen;
    private readonly List<View> actors=[];
    private string[] previous=[];
    private List<View>[] rowActors=[];
    private sealed record Run(int Start,int Span,string Text,int Attribute,bool Selected);
    private List<Run>[] rowRuns=[];
    private readonly View cursor;
    private bool retired;
    public TerminalInteraction Interaction {get;}
    public Func<(bool Shift,bool Control)> Modifiers {get;set;}=()=> (false,false);
    public event Action? FocusRequested;
    public event Action? PasteRequested;
    public event Action<float,float>? ContextRequested;
    public event Action? SelectionChanged;
    private float cellWidth=12,cellHeight=26;
    private int fontSize;
    private TerminalTheme theme=TerminalThemes.Resolve("hexgate");
    public float CellWidth=>cellWidth;
    public float CellHeight=>cellHeight;
    public int Columns {get;private set;}=80;
    public int Rows {get;private set;}=24;
    public NativeTerminalView(TerminalScreen screen)
    {
        this.screen=screen;Focusable=true;
        Interaction=new TerminalInteraction(screen);
        Interaction.SelectionChanged+=()=>SelectionChanged?.Invoke();
        cursor=new View { BackgroundColor=new Color("#84C7FFFF"),Sensitive=false };Add(cursor);
        TouchEvent+=HandleTouch;
        WheelEvent+=(_,args)=>{
            if(retired)return false;
            var position=args.Wheel.Point;var origin=ScreenPosition;
            Interaction.Wheel((int)((position.X-origin.X)/cellWidth),(int)((position.Y-origin.Y)/cellHeight),args.Wheel.Z);return true;
        };
    }
    private bool HandleTouch(object? sender,TouchEventArgs args)
    {
        if(retired)return false;
        var touch=args.Touch;if(touch.GetPointCount()==0)return false;
        var state=touch.GetState(0);var point=touch.GetScreenPosition(0);var origin=ScreenPosition;
        int x=(int)((point.X-origin.X)/cellWidth),y=(int)((point.Y-origin.Y)/cellHeight);
        var modifiers=Modifiers();
        if(state==PointStateType.Down) {
            FocusRequested?.Invoke();
            var button=touch.GetMouseButton(0);
            if(button==MouseButton.Secondary){ContextRequested?.Invoke(point.X,point.Y);return true;}
            if(button==MouseButton.Tertiary){PasteRequested?.Invoke();return true;}
            Interaction.Down(x,y,modifiers.Shift,modifiers.Control);
        } else if(state==PointStateType.Motion)Interaction.Move(x,y,modifiers.Shift,modifiers.Control);
        else if(state==PointStateType.Up)Interaction.Up(x,y,modifiers.Shift,modifiers.Control);
        else if(state is PointStateType.Interrupted or PointStateType.Leave)Interaction.Cancel();
        return true;
    }
    public void Configure(float width,float height,int pixels,TerminalTheme selectedTheme)
    {
        Size=new Size(width,height);fontSize=pixels;theme=selectedTheme;
        using var measure=new TextLabel { Text="MMMMMMMMMM",FontFamily="JetBrainsMono Nerd Font Mono",PixelSize=pixels };
        var natural=measure.NaturalSize;cellWidth=Math.Max(5,natural.X/10f);cellHeight=Math.Max(pixels+4,natural.Y+2);
        Columns=Math.Clamp((int)(width/cellWidth),2,240);Rows=Math.Clamp((int)(height/cellHeight),2,100);
        screen.Resize(Columns,Rows);ClearFrame();Paint();
    }
    public void ClearFrame()
    {
        foreach(var actor in actors){Remove(actor);actor.Dispose();}actors.Clear();
        previous=new string[Rows];rowActors=Enumerable.Range(0,Rows).Select(_=>new List<View>()).ToArray();
        rowRuns=Enumerable.Range(0,Rows).Select(_=>new List<Run>()).ToArray();
    }
    private Color Palette(int index,bool foreground)
    {
        if(index>=256)return new Color(foreground?theme.Foreground:theme.Background);
        if(index<16 && theme.Ansi.Length==16)return new Color(theme.Ansi[index]);
        string[] basic=["#1D2025FF","#E06C75FF","#98C379FF","#E5C07BFF","#61AFEFFF","#C678DDFF","#56B6C2FF","#ABB2BFFF",
            "#5C6370FF","#FF7A85FF","#B4E890FF","#FFD68AFF","#85C8FFFF","#E0A0FFFF","#7AE5F0FF","#FFFFFFFF"];
        if(index<16)return new Color(basic[index]);
        if(index>=232){float level=(8+(index-232)*10)/255f;return new Color(level,level,level,1);}
        index-=16;int[] cube=[0,95,135,175,215,255];return new Color(cube[index/36]/255f,cube[index/6%6]/255f,cube[index%6]/255f,1);
    }
    public void Paint()
    {
        if(retired)return;
        var frame=screen.Snapshot();
        BackgroundColor=Palette(256,false);
        for(int y=0;y<Math.Min(Rows,frame.Rows.Length);y++) {
            var cells=frame.Rows[y];var signature=string.Join('\u0001',cells.Select((c,column)=>$"{c.Attribute}:{c.Width}:{c.Text}:{Interaction.IsSelected(column,y)}"));
            if(previous[y]==signature)continue;previous[y]=signature;
            int runIndex=0;
            for(int x=0;x<cells.Length;) {
                var first=cells[x];if(first.Width==0){x++;continue;}
                bool selected=Interaction.IsSelected(x,y);
                int start=x,span=first.Width;var text=new System.Text.StringBuilder(first.Text);x+=first.Width;
                if(first.Width==1)while(x<cells.Length && cells[x].Width==1 && cells[x].Attribute==first.Attribute && Interaction.IsSelected(x,y)==selected) {
                    text.Append(cells[x].Text);span++;x++;
                }
                int fg=(first.Attribute>>9)&511,bg=first.Attribute&511;int flags=first.Attribute>>18;
                if((flags&8)!=0)(fg,bg)=(bg,fg);
                var run=new Run(start,span,(flags&16)!=0?"":text.ToString(),first.Attribute,selected);
                Run? old=runIndex<rowRuns[y].Count?rowRuns[y][runIndex]:null;
                TextLabel label;
                if(old==null) {
                    label=new TextLabel {FontFamily="JetBrainsMono Nerd Font Mono",PixelSize=fontSize,Sensitive=false};
                    Add(label);rowActors[y].Add(label);actors.Add(label);rowRuns[y].Add(run);
                } else label=(TextLabel)rowActors[y][runIndex];
                if(old?.Text!=run.Text)label.Text=run.Text;
                if(old?.Start!=start)label.Position=new Position(start*cellWidth,y*cellHeight);
                if(old?.Span!=span)label.Size=new Size(span*cellWidth+1,cellHeight);
                if(old==null || old.Attribute!=run.Attribute || old.Selected!=selected) {
                    label.TextColor=selected?new Color(theme.Background):Palette(fg,true);
                    label.BackgroundColor=selected?new Color(theme.Accent):Palette(bg,false);
                }
                rowRuns[y][runIndex++]=run;
            }
            while(rowActors[y].Count>runIndex) {
                int last=rowActors[y].Count-1;var actor=rowActors[y][last];
                Remove(actor);actors.Remove(actor);actor.Dispose();rowActors[y].RemoveAt(last);rowRuns[y].RemoveAt(last);
            }
        }
        cursor.Position=new Position(Math.Clamp(frame.CursorX,0,Columns-1)*cellWidth,Math.Clamp(frame.CursorY,0,Rows-1)*cellHeight+cellHeight-3);
        cursor.Size=new Size(cellWidth,2);if(frame.CursorHidden)cursor.Hide();else cursor.Show();cursor.RaiseToTop();
    }
    protected override void Dispose(DisposeTypes type)
    {
        if(type==DisposeTypes.Explicit && !retired){retired=true;Interaction.Dispose();ClearFrame();Remove(cursor);cursor.Dispose();}base.Dispose(type);
    }
}
