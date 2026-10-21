using System.Text.Json;

namespace Hexgate.Core;

public sealed record HerdrPane(string Id,string Cwd,string Name);
public sealed record HerdrContext(bool Automatic,string? Focused,IReadOnlyList<HerdrPane> Panes)
{
    public const int OutputLimit=1024*1024;
    public static void ValidateIdentity(string session,string tty)
    {
        if(session.Length is <1 or >80 || session.Any(c=>!(char.IsAsciiLetterOrDigit(c)||c is '-' or '_')))
            throw new ArgumentException("Invalid Herdr session");
        const string prefix="/dev/pts/";
        if(!tty.StartsWith(prefix,StringComparison.Ordinal) || tty.Length<=prefix.Length || tty.Length>80 || tty[prefix.Length..].Any(c=>!char.IsAsciiDigit(c)))
            throw new ArgumentException("Client SSH TTY is unavailable");
    }
    public static string Command(string helper,string session,string tty)
    {
        ValidateIdentity(session,tty);
        if(helper.Length is <1 or >65536 || helper.Contains('\0'))throw new ArgumentException("Invalid context helper");
        static string Quote(string value)=>"'"+value.Replace("'","'\"'\"'")+"'";
        return "exec python3 -c "+Quote(helper)+" "+Quote(session)+" "+Quote(tty)+"\n";
    }
    public static HerdrContext Parse(string payload,string session)
    {
        if(System.Text.Encoding.UTF8.GetByteCount(payload)>OutputLimit)throw new InvalidDataException("Herdr response exceeds limit");
        using var document=JsonDocument.Parse(payload,new JsonDocumentOptions {MaxDepth=16});
        var root=document.RootElement;
        if(root.TryGetProperty("error",out _) || root.GetProperty("session").GetString()!=session)
            throw new InvalidDataException("Herdr context unavailable");
        var rows=root.GetProperty("panes");
        if(rows.ValueKind!=JsonValueKind.Array || rows.GetArrayLength()>500)throw new InvalidDataException("Invalid pane list");
        var panes=new List<HerdrPane>();var identifiers=new HashSet<string>(StringComparer.Ordinal);
        foreach(var row in rows.EnumerateArray()) {
            string id=row.GetProperty("id").GetString()??"",cwd=row.GetProperty("cwd").GetString()??"",name=row.GetProperty("name").GetString()??id;
            if(id.Length is <1 or >80 || id.Any(char.IsControl) || !identifiers.Add(id) || !cwd.StartsWith('/') || cwd.Length>4096 || cwd.Any(char.IsControl))
                throw new InvalidDataException("Invalid pane metadata");
            name=new string(name.Where(c=>!char.IsControl(c)).Take(160).ToArray());
            panes.Add(new(id,cwd,name));
        }
        string? focused=root.TryGetProperty("focused",out var selected) && selected.ValueKind==JsonValueKind.String?selected.GetString():null;
        bool automatic=root.GetProperty("automatic").GetBoolean() && panes.Any(p=>p.Id==focused);
        return new(automatic,automatic?focused:null,panes);
    }
}
