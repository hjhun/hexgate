namespace Hexgate.Core;

// Matches the Android AppThemes presets and their ANSI palettes.
public sealed record TerminalTheme(string Id,string Label,bool Light,string Background,string Foreground,string Accent,string[] Ansi);

public static class TerminalThemes
{
    public static readonly TerminalTheme[] Presets = [
        new("hexgate","Hexgate Dark",false,"#0b1119FF","#e5ebf2FF","#e7c482FF",[]),
        new("light","Light",true,"#f5f7faFF","#17212eFF","#765518FF",[]),
        new("amber","Amber",false,"#151108FF","#ffdc83FF","#ffc94aFF",[]),
        new("dracula","Dracula",false,"#282a36FF","#f8f8f2FF","#bd93f9FF",["#21222cFF","#ff5555FF","#50fa7bFF","#f1fa8cFF","#bd93f9FF","#ff79c6FF","#8be9fdFF","#f8f8f2FF","#6272a4FF","#ff6e6eFF","#69ff94FF","#ffffa5FF","#d6acffFF","#ff92dfFF","#a4ffffFF","#ffffffFF"]),
        new("gruvbox","Gruvbox Dark",false,"#282828FF","#ebdbb2FF","#fabd2fFF",["#282828FF","#cc241dFF","#98971aFF","#d79921FF","#458588FF","#b16286FF","#689d6aFF","#a89984FF","#928374FF","#fb4934FF","#b8bb26FF","#fabd2fFF","#83a598FF","#d3869bFF","#8ec07cFF","#ebdbb2FF"]),
        new("solarized","Solarized Dark",false,"#001e27FF","#93a1a1FF","#2aa198FF",["#002831FF","#d11c24FF","#738a05FF","#a57706FF","#2176c7FF","#c61c6fFF","#259286FF","#eae3cbFF","#001e27FF","#bd3613FF","#475b62FF","#536870FF","#708284FF","#5956baFF","#819090FF","#fcf4dcFF"]),
    ];
    public static TerminalTheme Resolve(string id) => Presets.FirstOrDefault(t=>t.Id==(id=="dark"?"hexgate":id)) ?? Presets[0];
    public static bool Valid(string id) => id=="dark" || Presets.Any(t=>t.Id==id);
}
