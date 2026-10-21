using Hexgate.Core;
using System.Text;

static class InteractionChecks
{
    public static void Run(Action<bool,string> check)
    {
        check(TerminalKeys.Encode("c","c",true,false,false,false)=="\x03" && TerminalKeys.Encode("v","v",true,false,false,false)=="\x16","plain Ctrl C and Ctrl V retain remote control-key semantics");
        check(TerminalKeys.Encode("Up","",true,false,false,false)=="\x1b[1;5A" && TerminalKeys.Encode("Up","",false,false,false,true)=="\x1bOA","cursor keys honor modifiers and application cursor mode");
        check(TerminalKeys.Encode("Tab","",false,false,true,false)=="\x1b[Z" && TerminalKeys.Encode("a","한",false,false,false,false)=="한","Shift Tab and Unicode hardware input are preserved");
        var screen=new TerminalScreen(30,8);long clock=100;
        void Feed(string text)=>screen.Feed(Encoding.UTF8.GetBytes(text));
        using var input=new TerminalInteraction(screen,()=>clock);var output=new List<string>();var copies=new List<string>();
        screen.Response+=b=>output.Add(Encoding.UTF8.GetString(b));input.CopyRequested+=copies.Add;
        Feed("ABCDE12345\r\nFGHIJ67890");
        input.Down(1,0,true,true);input.Move(3,1,true,true);input.Up(3,1,true,true);
        check(input.SelectedText()=="BCD\nGHI","Ctrl Shift rectangle copies only selected columns");
        input.Down(2,0,false,false);input.Up(2,1,false,false);
        check(input.SelectedText().StartsWith("CDE12345") && input.SelectedText().EndsWith("FGH"),"local linear selection spans ordered row ranges");
        Feed("\x1b[?1002h\x1b[?1006h");output.Clear();
        input.Down(1,1,false,false);input.Move(4,1,false,false);input.Up(4,1,false,false);
        check(!input.HasSelection && output.SequenceEqual(new[]{"\x1b[<0;2;2M","\x1b[<32;5;2M","\x1b[<0;5;2m"}),"tracked drag reaches remote PTY exactly once per event");
        string osc="\x1b]52;c;"+Convert.ToBase64String(Encoding.UTF8.GetBytes("한글 selection"))+"\x07";
        Feed(osc);check(copies.Count==0,"ordinary drag cannot authorize unsolicited OSC52");
        input.Down(0,0,true,false);input.Up(0,0,true,false);Feed(osc);
        check(copies.Count==0,"Shift click without drag grants no clipboard access");
        input.Down(0,0,true,false);input.Move(5,0,true,false);input.Up(5,0,true,false);
        foreach(byte b in Encoding.UTF8.GetBytes(osc))screen.Feed([b]);
        check(copies.SequenceEqual(new[]{"한글 selection"}),"Shift drag authorizes one packet-split UTF8 OSC52 copy");
        Feed(osc);check(copies.Count==1,"a second OSC52 response cannot reuse a gesture grant");
        void Grant(){input.Down(0,0,true,false);input.Move(5,0,true,false);input.Up(5,0,true,false);}
        Grant();clock+=5000;Feed(osc);check(copies.Count==1,"copy grant expires after five seconds");
        Grant();input.Cancel();Feed(osc);check(copies.Count==1,"gesture cancellation revokes pending copy grant");
        Grant();Feed("\x1b]52;c;not-base64\x07");Feed(osc);check(copies.Count==1,"malformed OSC52 consumes its one-shot grant");
        Grant();Feed("\x1b]52;c;"+new string('A',1500000)+"\x07");Feed("\r\nRECOVER");
        check(copies.Count==1 && screen.Lines().Any(s=>s.Contains("RECOVER")),"oversized OSC is bounded and parser resumes normally");
        Grant();input.Dispose();Feed(osc);check(copies.Count==1,"retired interaction cannot accept clipboard replies");
        var tty=new TerminalScreen();foreach(var b in Encoding.UTF8.GetBytes("\x1b]2;hexgate-tty:/dev/pts/17\x1b\\"))tty.Feed([b]);
        check(tty.Tty=="/dev/pts/17","OSC title preserves first byte and discovers only this PTY context");
        string? title=null;tty.Engine.SetOscHandler(2,s=>title=s);
        foreach(var b in Encoding.UTF8.GetBytes("\x1b]2;한글\x07"))tty.Feed([b]);
        check(title=="한글","OSC UTF8 remains intact across byte packet boundaries");
        var paste=new TerminalScreen();using var pasteInput=new TerminalInteraction(paste);
        paste.Feed(Encoding.UTF8.GetBytes("\x1b[?2004h"));
        check(Encoding.UTF8.GetString(pasteInput.Paste("hello\x1b[201~world"))=="\x1b[200~hello[201~world\x1b[201~","local paste uses negotiated bracketed paste and cannot escape its wrapper");
        output.Clear();using var boxInput=new TerminalInteraction(screen);
        boxInput.Down(1,0,true,true);boxInput.Up(2,1,true,true);
        check(output.Count==0 && boxInput.SelectedText()=="BC\nGH","Ctrl Shift selection overrides remote mouse tracking");
        var history=new TerminalScreen(20,4);using var historyInput=new TerminalInteraction(history);
        for(int i=0;i<20;i++)history.Feed(Encoding.UTF8.GetBytes("LINE"+i+"\r\n"));
        var before=history.Lines();historyInput.Wheel(0,0,-1);
        check(!before.SequenceEqual(history.Lines()),"mouse wheel exposes bounded engine scrollback without tracking");
    }
}
