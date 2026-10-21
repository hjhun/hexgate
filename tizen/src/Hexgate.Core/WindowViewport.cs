namespace Hexgate.Core;

/// <summary>Validated drawable window plus one chrome transform; terminal cells remain physical.</summary>
public readonly record struct WindowViewport(float X,float Y,float Width,float Height,float Scale)
{
    public float LogicalWidth=>Width/Scale;
    public float LogicalHeight=>Height/Scale;
    public bool Compact=>LogicalWidth<1000;
    public float TerminalTop=>Compact?108:74;
    public float ModalWidth=>Math.Max(1,Math.Min(840,LogicalWidth-24));
    public float ModalHeight=>Math.Max(1,Math.Min(850,LogicalHeight-24));
    public static bool TryCreate(float width,float height,float start,float top,float end,float bottom,out WindowViewport viewport)
    {
        viewport=default;
        if(new[]{width,height,start,top,end,bottom}.Any(v=>!float.IsFinite(v)) || width<=0 || height<=0
            || start<0 || top<0 || end<0 || bottom<0)return false;
        float w=width-start-end,h=height-top-bottom;
        if(!float.IsFinite(w) || !float.IsFinite(h) || w<=0 || h<=0)return false;
        // Keep controls readable in small windows; reflow/scroll instead of shrinking them.
        float scale=Math.Clamp(Math.Min(w/1920,h/1080),1,2);
        viewport=new(start,top,w,h,scale);return true;
    }
    public (float X,float Y) ToLogicalScreen(float x,float y)=>((x-X)/Scale,(y-Y)/Scale);
}
