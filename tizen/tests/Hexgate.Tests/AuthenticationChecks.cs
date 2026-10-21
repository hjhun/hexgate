using System.Text;
using System.Text.Json.Nodes;
using Hexgate.Core;

internal static class AuthenticationChecks
{
    public static async Task Run(string directory,int port,Action<bool,string> check)
    {
        string Read(string name)=>File.ReadAllText(Path.Combine(directory,name));
        string fingerprint=JsonNode.Parse(Read("ready.json"))!["fingerprint"]!.GetValue<string>();
        var profile=new ConnectionProfile {Host="127.0.0.1",Port=port,User="fixture",Authentication=AuthenticationMethod.Password};
        async Task<bool> Attempt(ConnectionProfile target,string secret,string passphrase,Func<string,bool> trust,CancellationToken token=default)
        {
            await using var session=new SshSession();
            var banner=new TaskCompletionSource<bool>(TaskCreationOptions.RunContinuationsAsynchronously);
            var output=new StringBuilder();
            session.Output+=data=>{lock(output){output.Append(Encoding.UTF8.GetString(data));if(output.ToString().Contains("HEXGATE AUTH FIXTURE READY"))banner.TrySetResult(true);}};
            try {
                await session.Connect(target,secret,passphrase,trust,80,24,token);
                return await banner.Task.WaitAsync(TimeSpan.FromSeconds(5)) && session.Connected;
            } catch(Exception) {return false;}
        }
        bool Trust(string actual)=>actual==fingerprint;
        check(await Attempt(profile,Read("password"),"",Trust),"live password authentication reaches a real SSH PTY");
        check(!await Attempt(profile,"incorrect-fixture-password","",Trust),"incorrect password is rejected without key fallback");
        var keyProfile=profile with {Authentication=AuthenticationMethod.PrivateKey};
        check(await Attempt(keyProfile,Read("client"),"",Trust),"live private-key authentication reaches a real SSH PTY");
        check(!await Attempt(keyProfile,Read("wrong-client"),"",Trust),"incorrect private key is rejected without password fallback");
        check(await Attempt(keyProfile,Read("client-encrypted"),Read("passphrase"),Trust),"encrypted private key authenticates with its separate passphrase");
        check(!await Attempt(keyProfile,Read("client-encrypted"),"incorrect-fixture-passphrase",Trust),"incorrect encrypted-key passphrase is rejected");
        check(!await Attempt(keyProfile,Read("client-encrypted"),"",Trust),"encrypted key without its passphrase is rejected");
        int shells=JsonNode.Parse(Read("stats.json"))!["shells"]!.GetValue<int>();int verifications=0;
        check(!await Attempt(profile,Read("password"),"",actual=>{verifications++;return actual=="SHA256:changed-fixture-host";})
            && verifications>0 && JsonNode.Parse(Read("stats.json"))!["shells"]!.GetValue<int>()==shells,
            "changed-host rejection prevents authenticated PTY creation");
        using(var cancellation=new CancellationTokenSource()) {
            cancellation.Cancel();
            check(!await Attempt(profile,Read("password"),"",Trust,cancellation.Token),"cancelled connection creates no usable session");
        }
        using(var cancellation=new CancellationTokenSource()) {
            check(!await Attempt(profile,Read("password"),"",actual=>{cancellation.Cancel();return false;},cancellation.Token),
                "cancellation during host verification aborts connection safely");
        }
    }
}
