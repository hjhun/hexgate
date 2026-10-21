using System.Text;
using System.Runtime.InteropServices;
using Hexgate.Core;
using Tizen.Security.SecureRepository;

namespace Hexgate.App;

/// <summary>Immutable random aliases allow atomic metadata replacement without deleting the old secret first.</summary>
internal sealed class SecureCredentialStore : ISecretVault
{
    private static void ValidateAlias(string alias)
    {
        if(!alias.StartsWith("hexgate-",StringComparison.Ordinal) || alias.Length>120
            || alias.Any(c=>!(char.IsAsciiLetterOrDigit(c)||c=='-')))throw new ArgumentException("Invalid credential identity");
    }
    public void Put(string alias,string value)
    {
        ValidateAlias(alias);
        DataManager.Save(alias,Encoding.UTF8.GetBytes(value),new Policy());
    }
    public string Get(string alias){ValidateAlias(alias);return Encoding.UTF8.GetString(DataManager.Get(alias,null));}
    // The managed wrapper merges missing aliases and access/storage failures into
    // InvalidOperationException. Preserve the native error distinction for idempotent revocation.
    private const int UnknownAlias=(-0x01E10000)|0x0C; // CKMC_ERROR_DB_ALIAS_UNKNOWN
    [DllImport("libkey-manager-client.so.1",EntryPoint="ckmc_remove_alias",CallingConvention=CallingConvention.Cdecl)]
    private static extern int RemoveNative(string alias);
    public void Remove(string alias)
    {
        ValidateAlias(alias);int result=RemoveNative(alias);
        if(result!=0 && result!=UnknownAlias)throw new InvalidOperationException("Secure credential revocation failed; error code "+result);
    }
    public void VerifyRoundTrip()
    {
        var secret=Guid.NewGuid().ToString();var alias="hexgate-probe-"+Guid.NewGuid().ToString("N");
        Put(alias,secret);
        try {if(Get(alias)!=secret)throw new InvalidOperationException("Secure storage round trip failed");}
        finally {Remove(alias);}
        Remove(alias); // Prove missing-alias revocation on the actual target without accessing other data.
    }
}
