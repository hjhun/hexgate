namespace Hexgate.Core;

public enum AuthenticationMethod { PrivateKey, Password }
public sealed record ConnectionProfile
{
    public string Id { get; init; } = Guid.NewGuid().ToString();
    public string Name { get; init; } = "Development PC";
    public string Host { get; init; } = "";
    public int Port { get; init; } = 22;
    public string User { get; init; } = "";
    public AuthenticationMethod Authentication { get; init; }
    public string? CredentialAlias { get; init; }
    public string HerdrSession { get; init; } = "";
    public string StartupCommand { get; init; } = "";
    public void Validate()
    {
        if(!Enum.IsDefined(Authentication) || !Guid.TryParse(Id,out _) || string.IsNullOrWhiteSpace(Host) || string.IsNullOrWhiteSpace(User)
            || Port is <1 or >65535 || Host.Length>253 || User.Length>256) throw new ArgumentException("Invalid connection profile");
        if(HerdrSession.Length>80 || HerdrSession.Any(c=>!(char.IsAsciiLetterOrDigit(c)||c is '-' or '_')))
            throw new ArgumentException("Invalid Herdr session name");
    }
}
