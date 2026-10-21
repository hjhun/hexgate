using System.Text;
using Hexgate.Core;
using XtermSharp;

static class ParserBoundsChecks
{
    private static unsafe void Feed(EscapeSequenceParser parser,string text)
    {
        byte[] bytes=Encoding.UTF8.GetBytes(text);
        fixed(byte* p=bytes)parser.Parse(p,bytes.Length);
    }
    public static void Run(Action<bool,string> check)
    {
        using var parser=new EscapeSequenceParser();
        int calls=0,value=0,parameterCount=0;
        parser.SetCsiHandler('C',(p,c)=>{calls++;value=p[0];parameterCount=p.Length;});
        Feed(parser,"\x1b["+new string(';',100000)+"C");
        check(calls==0,"oversized CSI parameter lists are discarded without dispatch");
        Feed(parser,"\x1b[3C");check(calls==1 && value==3,"normal CSI recovers after oversized parameter list");
        Feed(parser,"\x1b["+new string('9',1000)+"C");
        check(calls==2 && value==65535,"numeric CSI parameters saturate without integer overflow");
        Feed(parser,"\x1b["+new string(';',255)+"C");
        check(calls==3 && parameterCount==256,"CSI parameter-count boundary remains supported");
        int badEsc=0,goodEsc=0;
        parser.SetEscHandler(new string(' ',40)+"A",(c,b)=>badEsc++);
        parser.SetEscHandler("D",(c,b)=>goodEsc++);
        Feed(parser,"\x1b"+new string(' ',40)+"A\u001bD");
        check(badEsc==0 && goodEsc==1,"oversized intermediate sequences discard and resynchronize");
        var probe=new ProbeDcs();parser.SetDcsHandler("$q",probe);
        Feed(parser,"\x1bP$q");
        for(int i=0;i<8;i++)Feed(parser,new string('a',1024));
        check(probe.Bytes<=4096,"streamed DCS payload is bounded before terminator arrives");
        Feed(parser,"\x1b\\");
        check(!probe.Completed,"overflowed DCS cannot execute a truncated payload");
        int hooks=probe.Hooks;Feed(parser,"\x1bP"+new string(';',300)+"$qr\x1b\\");
        check(probe.Hooks==hooks,"oversized DCS header does not activate a handler");
        Feed(parser,"\x1bP$qok\x1b\\");
        check(probe.Completed && probe.Bytes==2,"normal DCS recovers after payload and header overflow");
        Feed(parser,"\x1bP$q");for(int i=0;i<4;i++)Feed(parser,new string('a',1024));Feed(parser,"\x1b\\");
        check(probe.Completed && probe.Bytes==4096,"exact DCS payload limit remains supported");
        Feed(parser,"\x1bP$qcancel\x18");check(!probe.Completed,"canceled DCS is aborted without dispatch");
        Feed(parser,"\x1bP$qreset");int finishes=probe.Finishes;parser.Reset();
        check(!probe.Completed && probe.Finishes==finishes+1,"parser reset aborts its active DCS handler");
        int csiCalls=calls;Feed(parser,"\x1b[123\x18"+"C");
        check(calls==csiCalls,"CAN aborts an incomplete CSI rather than dispatching its suffix");
        foreach(string command in new[]{"L","M","S","T"}) {
            var bounded=new TerminalScreen(12,6);
            bounded.Feed(Encoding.ASCII.GetBytes("\x1b[1"+command));
            long before=GC.GetAllocatedBytesForCurrentThread();
            bounded.Feed(Encoding.ASCII.GetBytes("\x1b[65535"+command));
            long allocated=GC.GetAllocatedBytesForCurrentThread()-before;
            check(allocated<1024*1024,"large "+command+" line operation allocates only visible-region work");
        }
        var scrolling=new TerminalScreen(12,6);
        for(int y=0;y<6;y++)scrolling.Feed(Encoding.ASCII.GetBytes("\x1b["+(y+1)+";1H"+(char)('A'+y)));
        scrolling.Feed(Encoding.ASCII.GetBytes("\x1b[2;4r\x1b[44m\x1b[1T"));
        var lines=scrolling.Lines();
        check(lines[0].StartsWith("A") && string.IsNullOrWhiteSpace(lines[1]) && lines[2].StartsWith("B") && lines[3].StartsWith("C") && lines[4].StartsWith("E") && lines[5].StartsWith("F"),"scroll down shifts content inside its margins and preserves outside rows");
        check((scrolling.Snapshot().Rows[1][0].Attribute&511)==4,"scroll-created blanks preserve the current erase background");
        var outside=new TerminalScreen(12,6);
        for(int y=0;y<6;y++)outside.Feed(Encoding.ASCII.GetBytes("\x1b["+(y+1)+";1H"+(char)('A'+y)));
        var original=outside.Lines();outside.Feed(Encoding.ASCII.GetBytes("\x1b[2;4r\x1b[1;1H\x1b[1L\x1b[1M"));
        check(outside.Lines().SequenceEqual(original),"insert and delete line commands outside margins leave content unchanged");
        var retiring=new EscapeSequenceParser();var retiredProbe=new ProbeDcs();retiring.SetDcsHandler("$q",retiredProbe);
        Feed(retiring,"\x1bP$qactive");retiring.Dispose();
        check(retiredProbe.Finishes==1 && !retiredProbe.Completed,"parser disposal aborts the active DCS exactly once");
        var screen=new TerminalScreen();void Screen(string s)=>screen.Feed(Encoding.UTF8.GetBytes(s));
        Screen("\x1b[22;0t\x1b[23;0t");
        check(screen.Engine.Title=="" && screen.Engine.IconTitle=="","fresh title history can push and pop empty titles safely");
        var responses=new List<string>();screen.Response+=b=>responses.Add(Encoding.UTF8.GetString(b));
        Screen("\x1bP$qm"+new string('a',5000)+"\x1b\\");
        check(responses.Count==0,"oversized real status query emits no partial reply");
        Screen("\x1bP$qm\x1b\\");
        check(responses.Count==1 && responses[0].StartsWith("\x1bP1$r"),"normal SGR status query replies after overflow recovery");
        Screen("\x1b]0;small\x07");Screen("\x1b]0;"+new string('T',5000)+"\x07");
        check(screen.Engine.Title=="small" && screen.Engine.IconTitle=="small","oversized window and icon titles do not replace bounded titles");
        Screen("\x1b]2;bad\x18RECOVER");
        check(screen.Engine.Title=="small" && screen.Lines().Any(s=>s.Contains("RECOVER")),"CAN aborts OSC and restores printable output immediately");
        for(int i=0;i<40;i++)Screen("\x1b]0;T"+i+"\x07\x1b[22;0t");
        for(int i=0;i<40;i++)Screen("\x1b[23;0t");
        check(screen.Engine.Title=="T8" && screen.Engine.IconTitle=="T8","window and icon title history retain only the most recent 32 entries");
        Screen("\x1b]2;title-base\x07\x1b[22;2t\x1b]2;title-new\x07\x1b[23;2t");
        check(screen.Engine.Title=="title-base","title-only history restores the window title independently");
        Screen("\x1b]1;icon-base\x07\x1b[22;1t\x1b]1;icon-new\x07\x1b[23;1t");
        check(screen.Engine.IconTitle=="icon-base","icon-only history restores the icon title independently");
    }
    private sealed class ProbeDcs : IDcsHandler
    {
        public int Bytes,Hooks,Finishes;public bool Completed;
        public void Hook(string collect,int[] parameters,int flag){Bytes=0;Hooks++;}
        public unsafe void Put(byte* data,int start,int end){Bytes+=end-start;}
        public void Unhook(bool complete){Completed=complete;Finishes++;}
    }
}
