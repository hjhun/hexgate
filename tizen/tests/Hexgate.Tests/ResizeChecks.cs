using System.Text;
using Hexgate.Core;

static class ResizeChecks
{
    public static void Run(Action<bool,string> check)
    {
        static void Feed(TerminalScreen screen,string text)=>screen.Feed(Encoding.UTF8.GetBytes(text));
        var alternate=new TerminalScreen(10,4);
        Feed(alternate,"\x1b[?1049hABCDEFGHIJ");
        alternate.Resize(5,4);alternate.Resize(10,4);
        check(alternate.Lines()[0]=="ABCDE     ","alternate-screen shrink discards clipped cells instead of resurrecting them on grow");

        var wide=new TerminalScreen(8,4);
        Feed(wide,"\x1b[?1049hABCD한Z");wide.Resize(5,4);
        var edge=wide.Snapshot().Rows[0][4];
        check(edge.Width==1 && !edge.Text.Contains('한'),"resizing through a wide glyph cannot leave half a glyph at the right edge");
        wide.Resize(8,4);
        check(wide.Lines()[0]=="ABCD    ","growing a clipped alternate row exposes blank cells including the old wide-glyph tail");

        var margins=new TerminalScreen(8,4);
        Feed(margins,"\x1b[?69h");margins.Resize(12,4);Feed(margins,"ABCDEFGHIJKL");
        check(margins.Lines()[0]=="ABCDEFGHIJKL","a full-width horizontal margin follows a larger viewport");
        var custom=new TerminalScreen(8,4);
        Feed(custom,"\x1b[?69h\x1b[2;6s");custom.Resize(12,4);
        check(custom.Engine.Buffer.MarginLeft==1 && custom.Engine.Buffer.MarginRight==5,"resizing preserves explicitly restricted horizontal margins");

        // The upstream engine intentionally leaves the active cursor line to the
        // remote application; exercise completed output for scrollback reflow.
        var normal=new TerminalScreen(10,4);Feed(normal,"ABCDEFGHIJ\r\n");
        normal.Resize(5,4);normal.Resize(10,4);
        check(string.Join("",normal.Lines()).Contains("ABCDEFGHIJ"),"completed normal-buffer lines retain text across shrink and grow");
        var normalWide=new TerminalScreen(8,4);Feed(normalWide,"ABCD한Z\r\n");
        normalWide.Resize(5,4);normalWide.Resize(8,4);
        check(string.Join("",normalWide.Lines()).Contains("ABCD한Z"),"completed CJK output retains whole wide glyphs during normal-buffer reflow");
    }
}
