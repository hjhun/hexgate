using Hexgate.Core;

internal static class ViewportChecks
{
    public static void Run(Action<bool,string> check)
    {
        foreach(var size in new[]{(1920,1080),(960,540),(600,900),(480,320),(3840,2160)}) {
            check(WindowViewport.TryCreate(size.Item1,size.Item2,0,0,0,0,out var view),"drawable viewport accepts "+size);
            check(view.ModalWidth<=view.LogicalWidth && view.ModalHeight<=view.LogicalHeight,
                "modal viewport stays within the actual window "+size);
            check(view.ModalWidth-48<=size.Item1/view.Scale,"responsive input width is constrained to its modal "+size);
        }
        WindowViewport.TryCreate(3840,2160,0,0,0,0,out var large);
        check(large.Scale==2 && large.LogicalWidth==1920 && large.LogicalHeight==1080,
            "large-display chrome uses a single uniform reference transform");
        WindowViewport.TryCreate(600,900,0,0,0,0,out var narrow);
        check(narrow.Scale==1 && narrow.Compact,"small windows retain readable control sizes and reflow instead of scaling down");
        WindowViewport.TryCreate(1000,800,20,30,40,50,out var inset);
        check(inset.X==20 && inset.Y==30 && inset.Width==940 && inset.Height==720,
            "drawable geometry subtracts each inset once without substituting screen dimensions");
        var point=inset.ToLogicalScreen(120,130);
        check(point.X==100 && point.Y==100,"screen pointer coordinates map to the inset chrome origin once");
        foreach(var bad in new[]{(0f,600f,0f,0f,0f,0f),(600f,0f,0f,0f,0f,0f),
            (float.NaN,600f,0f,0f,0f,0f),(600f,600f,-1f,0f,0f,0f),
            (600f,600f,300f,0f,300f,0f),(600f,600f,0f,300f,0f,300f)})
            check(!WindowViewport.TryCreate(bad.Item1,bad.Item2,bad.Item3,bad.Item4,bad.Item5,bad.Item6,out _),
                "invalid or transient geometry cannot replace the last drawable viewport");
    }
}
