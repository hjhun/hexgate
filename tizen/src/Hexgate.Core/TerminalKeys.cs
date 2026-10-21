namespace Hexgate.Core;

public static class TerminalKeys
{
    public static string Encode(string name,string text,bool control,bool alt,bool shift,bool applicationCursor)
    {
        int modifier=1+(shift?1:0)+(alt?2:0)+(control?4:0);
        string? suffix=name switch {"Up"=>"A","Down"=>"B","Right"=>"C","Left"=>"D","Home"=>"H","End"=>"F",_=>null};
        if(suffix!=null)return modifier==1?(applicationCursor?"\x1bO":"\x1b[")+suffix:"\x1b[1;"+modifier+suffix;
        string? number=name switch {"Insert"=>"2","Delete"=>"3","Prior" or "Page_Up"=>"5","Next" or "Page_Down"=>"6","F5"=>"15","F6"=>"17","F7"=>"18","F8"=>"19","F9"=>"20","F10"=>"21","F11"=>"23","F12"=>"24",_=>null};
        if(number!=null)return "\x1b["+number+(modifier==1?"":";"+modifier)+"~";
        if(name is "F1" or "F2" or "F3" or "F4")return modifier==1?"\x1bO"+(char)('P'+name[1]-'1'):"\x1b[1;"+modifier+(char)('P'+name[1]-'1');
        if(name=="Tab" && shift)return "\x1b[Z";
        text=name switch {"Return" or "KP_Enter"=>"\r","BackSpace"=>"\x7f","Tab"=>"\t","Escape"=>"\x1b",_=>text};
        if(control && text.Length==1) {
            char c=char.ToUpperInvariant(text[0]);
            if(c is >= '@' and <= '_')text=((char)(c&31)).ToString();
            else text=c switch {' ' or '2'=>"\0",'3'=>"\x1b",'4'=>"\x1c",'5'=>"\x1d",'6'=>"\x1e",'7'=>"\x1f",'8'=>"\x7f",_=>text};
        }
        return alt && text.Length>0?"\x1b"+text:text;
    }
}
