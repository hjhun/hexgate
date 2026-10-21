using System.Text.Json.Nodes;
using Hexgate.Core;

internal static class CredentialChecks
{
    public static void Run(Action<bool,string> check)
    {
        using(var f=new Fixture()) {
            string old=f.Repository.Profiles.Single().CredentialAlias!;
            f.Vault.FailRemove=true;
            f.Repository.Save(f.Profile,"second-fixture");
            check(f.Repository.PendingCredentialCleanup==1 && f.Repository.Secret(f.Profile)=="second-fixture",
                "committed replacement reports pending cleanup without losing the new credential");
            check(!File.ReadAllText(f.Path).Contains("second-fixture"),"cleanup journal contains aliases and no secret plaintext");
            var waiting=f.Reload();
            check(waiting.PendingCredentialCleanup==1,"failed startup revocation retains durable retry intent");
            waiting.Save(f.Profile with {Name="Renamed"},null);
            check(waiting.PendingCredentialCleanup==1 && waiting.Secret(f.Profile)=="second-fixture",
                "empty edits retain both active credential and pending revocation");
            f.Vault.FailRemove=false;
            var recovered=f.Reload();
            check(recovered.Secret(f.Profile)=="second-fixture" && !f.Vault.Values.ContainsKey(old)
                && recovered.PendingCredentialCleanup==0,"restart recovers retired credential after revocation failure");
        }
        using(var f=new Fixture()) {
            int puts=f.Vault.PutCount;
            Directory.CreateDirectory(f.Path+".tmp");
            ExpectFailure(()=>f.Repository.Save(f.Profile,"uncommitted-fixture"));
            check(f.Vault.PutCount==puts && f.Repository.Secret(f.Profile)=="first-fixture",
                "failed intent write prevents secure-store mutation");
        }
        using(var f=new Fixture()) {
            f.Vault.AfterPut=()=>Directory.CreateDirectory(f.Path+".tmp");
            f.Vault.FailRemove=true;
            ExpectFailure(()=>f.Repository.Save(f.Profile,"uncommitted-fixture"));
            check(f.Repository.Secret(f.Profile)=="first-fixture" && f.Repository.PendingCredentialCleanup==1,
                "failed profile commit preserves old credential and journals new alias cleanup");
            Directory.Delete(f.Path+".tmp");f.Vault.FailRemove=false;
            var recovered=f.Reload();
            check(recovered.Secret(f.Profile)=="first-fixture" && recovered.PendingCredentialCleanup==0
                && f.Vault.Values.Count==1,"restart revokes uncommitted alias after metadata and vault failures");
        }
        using(var f=new Fixture()) {
            f.Vault.ThrowAfterPut=true;
            ExpectFailure(()=>f.Repository.Save(f.Profile,"uncertain-fixture"));
            check(f.Repository.Secret(f.Profile)=="first-fixture" && f.Vault.Values.Count==1
                && f.Repository.PendingCredentialCleanup==0,"uncertain secure-store save is revoked through its write-ahead intent");
        }
        using(var f=new Fixture()) {
            f.Repository.Select(f.Profile.Id);f.Vault.FailRemove=true;
            f.Repository.Delete(f.Profile.Id);
            check(f.Repository.Profiles.Count==0 && f.Repository.LastProfile==null
                && f.Repository.PendingCredentialCleanup==1,"profile deletion atomically records revocation and clears auto-connect selection");
            f.Vault.FailRemove=false;var recovered=f.Reload();
            check(recovered.Profiles.Count==0 && f.Vault.Values.Count==0 && recovered.PendingCredentialCleanup==0,
                "deleted credential cleanup completes after restart without restoring the profile");
        }
        using(var f=new Fixture()) {
            Directory.CreateDirectory(f.Path+".tmp");
            ExpectFailure(()=>f.Repository.Delete(f.Profile.Id));
            check(f.Repository.Profiles.Count==1 && f.Repository.Secret(f.Profile)=="first-fixture",
                "failed deletion metadata write leaves the existing profile and credential usable");
        }
        using(var f=new Fixture()) {
            f.Repository.Save(f.Profile with {Authentication=AuthenticationMethod.PrivateKey},"key-fixture");
            check(f.Vault.Values.Count==1 && f.Repository.Profiles.Single().Authentication==AuthenticationMethod.PrivateKey,
                "authentication-method switch revokes the previous method credential");
        }
        using(var f=new Fixture()) {
            string active=f.Repository.Profiles.Single().CredentialAlias!;
            var state=JsonNode.Parse(File.ReadAllText(f.Path))!;
            state["PendingRevocations"]=new JsonArray(active);File.WriteAllText(f.Path,state.ToJsonString());
            try {f.Reload();throw new Exception("Unsafe journal accepted");}
            catch(InvalidDataException) {check(f.Vault.Values.ContainsKey(active),"invalid cleanup journal cannot revoke an active profile credential");}
        }
        using(var f=new Fixture()) {
            // Simulate termination after Put and before the profile commit; no in-process rollback runs.
            string alias="hexgate-interrupted-"+Guid.NewGuid().ToString("N");
            var state=JsonNode.Parse(File.ReadAllText(f.Path))!;
            state["PendingRevocations"]=new JsonArray(alias);File.WriteAllText(f.Path,state.ToJsonString());
            f.Vault.Put(alias,"interrupted-fixture");
            var recovered=f.Reload();
            check(f.Vault.Values.Count==1 && recovered.Secret(f.Profile)=="first-fixture"
                && recovered.PendingCredentialCleanup==0,"startup replays an interrupted secure-save intent");
        }
    }
    private static void ExpectFailure(Action work)
    {
        try {work();throw new Exception("Expected injected failure");}
        catch(IOException) { }
        catch(UnauthorizedAccessException) { }
        catch(InvalidOperationException) { }
    }
    private sealed class Fixture : IDisposable
    {
        private readonly string directory=System.IO.Path.Combine(System.IO.Path.GetTempPath(),"hexgate-credential-"+Guid.NewGuid());
        public string Path=>System.IO.Path.Combine(directory,"profiles.json");
        public readonly FaultVault Vault=new();
        public readonly ConnectionProfile Profile=new() {Host="example.invalid",User="fixture",Authentication=AuthenticationMethod.Password};
        public ProfileRepository Repository {get;}
        public Fixture(){Repository=new ProfileRepository(directory,Vault);Repository.Save(Profile,"first-fixture");}
        public ProfileRepository Reload()=>new(directory,Vault);
        public void Dispose()=>Directory.Delete(directory,true);
    }
    private sealed class FaultVault : ISecretVault
    {
        public readonly Dictionary<string,string> Values=[];
        public bool FailRemove,ThrowAfterPut;
        public int PutCount;
        public Action? AfterPut;
        public void Put(string alias,string value)
        {
            PutCount++;Values.Add(alias,value);AfterPut?.Invoke();
            if(ThrowAfterPut)throw new InvalidOperationException("Fixture uncertain save");
        }
        public string Get(string alias)=>Values[alias];
        public void Remove(string alias)
        {if(FailRemove)throw new InvalidOperationException("Fixture failure");Values.Remove(alias);}
    }
}
