using System.Text.Json;

namespace Hexgate.Core;

public interface ISecretVault
{
    void Put(string alias, string value);
    string Get(string alias);
    void Remove(string alias);
}

public sealed class ProfileRepository
{
    private sealed class State
    {
        public List<ConnectionProfile> Profiles { get; set; } = [];
        public List<string> PendingRevocations { get; set; } = [];
        public Dictionary<string,string> Hosts { get; set; } = [];
        public string? LastProfile { get; set; }
        public bool AutoConnect { get; set; } = true;
        public string Theme { get; set; } = "hexgate";
        public int FontSize { get; set; } = 20;
    }
    private readonly string path;
    private readonly ISecretVault vault;
    private readonly object gate=new();
    private State state;
    public ProfileRepository(string directory,ISecretVault vault)
    {
        Directory.CreateDirectory(directory);path=Path.Combine(directory,"profiles.json");this.vault=vault;
        if(File.Exists(path)) {
            if(new FileInfo(path).Length>1024*1024)throw new InvalidDataException("Profile metadata too large");
            state=JsonSerializer.Deserialize<State>(File.ReadAllText(path)) ?? throw new InvalidDataException("Invalid profiles");
            foreach(var profile in state.Profiles)profile.Validate();
            ValidateJournal();
        } else state=new();
        RetryCredentialCleanup();
    }
    public IReadOnlyList<ConnectionProfile> Profiles { get {lock(gate)return state.Profiles.ToArray();} }
    public string? LastProfile { get {lock(gate)return state.LastProfile;} }
    public bool AutoConnect { get {lock(gate)return state.AutoConnect;} }
    public string Theme { get {lock(gate)return state.Theme;} }
    public int FontSize { get {lock(gate)return state.FontSize;} }
    public int PendingCredentialCleanup { get {lock(gate)return state.PendingRevocations.Count;} }
    private void ValidateJournal()
    {
        if(state.PendingRevocations.Count>256 || state.PendingRevocations.Distinct().Count()!=state.PendingRevocations.Count
            || state.PendingRevocations.Any(alias=>!ValidAlias(alias)
                || state.Profiles.Any(profile=>profile.CredentialAlias==alias)))
            throw new InvalidDataException("Invalid credential cleanup journal");
    }
    private static bool ValidAlias(string alias)=>alias.StartsWith("hexgate-",StringComparison.Ordinal)
        && alias.Length<=120 && alias.All(c=>char.IsAsciiLetterOrDigit(c)||c=='-');
    private void Persist(State next)
    {
        // Flush the complete replacement before atomic rename; never truncate the committed file.
        var temp=path+".tmp";
        using(var file=new FileStream(temp,FileMode.Create,FileAccess.Write,FileShare.None)) {
            JsonSerializer.Serialize(file,next);file.Flush(true);
        }
        File.Move(temp,path,true);state=next;
    }
    public void RetryCredentialCleanup()
    {
        lock(gate) {
            if(state.PendingRevocations.Count==0)return;
            var next=Copy();
            foreach(string alias in state.PendingRevocations) {
                try {vault.Remove(alias);next.PendingRevocations.Remove(alias);}
                catch(Exception) { /* Keep the durable intent for an explicit or startup retry. */ }
            }
            if(next.PendingRevocations.Count==state.PendingRevocations.Count)return;
            try {Persist(next);}
            catch(IOException) { /* Repeating an already completed removal is safe. */ }
            catch(UnauthorizedAccessException) { }
        }
    }
    private State Copy()=>JsonSerializer.Deserialize<State>(JsonSerializer.Serialize(state))!;
    public void Save(ConnectionProfile profile,string? replacementSecret)
    {
        profile.Validate();
        lock(gate) {
            var previous=state.Profiles.FirstOrDefault(p=>p.Id==profile.Id);
            string? alias=previous?.Authentication==profile.Authentication ? previous.CredentialAlias : null;
            if(replacementSecret==null && alias==null)throw new ArgumentException("Enter a credential for this authentication method");
            if(replacementSecret!=null && (replacementSecret.Length==0 || replacementSecret.Length>128*1024))throw new ArgumentException("Invalid credential size");
            RetryCredentialCleanup();
            if(state.PendingRevocations.Count>=255)throw new InvalidOperationException("Retry pending credential cleanup before saving");
            string? created=null;
            try {
                if(replacementSecret!=null) {
                    created=$"hexgate-{profile.Id}-{profile.Authentication}-{Guid.NewGuid():N}";
                    // Write ahead of the secure-store operation, including a partial/uncertain save.
                    var intent=Copy();intent.PendingRevocations.Add(created);Persist(intent);
                    vault.Put(created,replacementSecret);alias=created;
                }
                var next=Copy();next.Profiles.RemoveAll(p=>p.Id==profile.Id);
                next.Profiles.Add(profile with {CredentialAlias=alias});
                if(created!=null)next.PendingRevocations.Remove(created);
                if(previous?.CredentialAlias is {} old && old!=alias)next.PendingRevocations.Add(old);
                Persist(next);
            } catch {RetryCredentialCleanup();throw;}
            RetryCredentialCleanup();
        }
    }
    public string Secret(ConnectionProfile profile)
    {
        lock(gate) {
            var saved=state.Profiles.Single(p=>p.Id==profile.Id);
            return vault.Get(saved.CredentialAlias ?? throw new InvalidOperationException("Credential missing"));
        }
    }
    public void Delete(string id)
    {
        lock(gate) {
            var old=state.Profiles.Single(p=>p.Id==id);
            RetryCredentialCleanup();
            if(state.PendingRevocations.Count>=255)throw new InvalidOperationException("Retry pending credential cleanup before deleting");
            var next=Copy();next.Profiles.RemoveAll(p=>p.Id==id);
            if(next.LastProfile==id)next.LastProfile=null;
            if(old.CredentialAlias!=null)next.PendingRevocations.Add(old.CredentialAlias);
            // Commit the profile removal and revocation intent together before touching the vault.
            Persist(next);RetryCredentialCleanup();
        }
    }
    public string? Trusted(ConnectionProfile profile) {lock(gate)return state.Hosts.GetValueOrDefault(profile.Host+":"+profile.Port);}
    public void Trust(ConnectionProfile profile,string fingerprint)
    {
        if(!fingerprint.StartsWith("SHA256:",StringComparison.Ordinal) || fingerprint.Length>96)throw new ArgumentException("Invalid host fingerprint");
        lock(gate){var next=Copy();next.Hosts[profile.Host+":"+profile.Port]=fingerprint;Persist(next);}
    }
    public void Select(string id){lock(gate){var next=Copy();next.LastProfile=id;Persist(next);}}
    public void Preferences(bool autoConnect,string theme,int fontSize)
    {
        if(!TerminalThemes.Valid(theme) || fontSize is <12 or >36)throw new ArgumentException("Invalid appearance settings");
        lock(gate){var next=Copy();next.AutoConnect=autoConnect;next.Theme=theme;next.FontSize=fontSize;Persist(next);}
    }
}
