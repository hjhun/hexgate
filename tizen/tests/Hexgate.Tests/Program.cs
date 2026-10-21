using Hexgate.Core;
using System.Text;
var screen=new TerminalScreen(80,24);
void Feed(string s)=>screen.Feed(Encoding.UTF8.GetBytes(s));
void Check(bool valid,string message){if(!valid)throw new Exception(message);Console.WriteLine("PASS "+message);}
Feed("\x1b[2J\x1b[HHELLO 한글 ┌─┐\r\nSECOND");
Check(screen.Lines()[0].Contains("HELLO 한글 ┌─┐"),"UTF8 CJK box drawing");
Feed("\x1b[1;1HREPLACED");
Check(screen.Lines()[0].StartsWith("REPLACED"),"cursor addressing replaces existing cells");
Feed("\x1b[?1049hALT");
Check(screen.Lines().Any(s=>s.Contains("ALT")),"alternate screen");
Feed("\x1b[?1049l");
Check(screen.Lines()[0].StartsWith("REPLACED"),"alternate screen restore");
screen.Resize(120,35);Check(screen.Lines().Length==35,"viewport resize");
Feed("\x1b[2J\x1b[HAe\u0301B");
Check(screen.Lines()[0].StartsWith("Ae\u0301B"),"combining character retains base and mark");
Feed("\x1b[2J\x1b[H");Check(screen.Lines().All(s=>string.IsNullOrWhiteSpace(s)),"empty VT cells render as blank spaces");

Feed("\x1b[2J\x1b[H한A");Check(screen.Engine.Buffer.X==3,"CJK consumes two terminal cells");
Feed("\x1b[2J\x1b[HAe\u0301B");Check(screen.Engine.Buffer.X==3,"combining mark consumes zero cells");
Feed("\x1b[2J\x1b[H");foreach(var b in Encoding.UTF8.GetBytes("한글"))screen.Feed([b]);
Check(screen.Lines()[0].StartsWith("한글"),"UTF8 split across packet boundaries");
Feed("\x1b[2J\x1b[H\x1b[38;2;95;135;175;48;2;8;8;8mRGB\x1b[0m");
var rgb=screen.Snapshot().Rows[0][0].Attribute;
Check(((rgb>>9)&511)==67 && (rgb&511)==232,"RGB SGR maps foreground and background to xterm palette without throwing");
foreach(string sgr in new[]{"38","48","38;2","48;5","38;2;1;2","48;2;1"})Feed("\x1b["+sgr+"m");
Feed("\x1b[0mSAFE");Check(screen.Lines()[0].Contains("SAFE"),"truncated extended-color commands recover without disconnecting");
var dir=Path.Combine(Path.GetTempPath(),"hexgate-test-"+Guid.NewGuid());
var vault=new FakeVault();
try {
 var repository=new ProfileRepository(dir,vault);
 var profile=new ConnectionProfile { Host="example.invalid",User="fixture",Authentication=AuthenticationMethod.Password };
 repository.Save(profile,"fixture-secret");
 Check(!File.ReadAllText(Path.Combine(dir,"profiles.json")).Contains("fixture-secret"),"profile JSON contains no credential plaintext");
 var saved=repository.Profiles.Single();var oldAlias=saved.CredentialAlias!;
 repository.Save(saved with {Name="Renamed"},null);Check(repository.Secret(saved)=="fixture-secret","empty edit retains credential");
 repository.Save(saved,"replacement");Check(repository.Secret(saved)=="replacement" && !vault.Values.ContainsKey(oldAlias),"replacement revokes old secure alias");
 repository.Select(saved.Id);
 var reloaded=new ProfileRepository(dir,vault);Check(reloaded.LastProfile==saved.Id && reloaded.Secret(saved)=="replacement","metadata survives repository recreation");
 repository.Delete(saved.Id);Check(repository.Profiles.Count==0 && vault.Values.Count==0,"profile deletion removes secure credential");
} finally {Directory.Delete(dir,true);}

ViewportChecks.Run(Check);
await LifetimeChecks.Run(Check);
await PreviewLifetimeChecks.Run(Check);
CredentialChecks.Run(Check);
ParserBoundsChecks.Run(Check);
ResizeChecks.Run(Check);
InteractionChecks.Run(Check);

var retired=new SshSession();await retired.DisposeAsync();
Check(!retired.Connected,"retired session connection state is safe to query");
Check(RemoteFiles.Inside("/docs","/docs/images/a.png") && !RemoteFiles.Inside("/docs","/docs-other/a.png") && !RemoteFiles.Inside("/docs","/docs/../private/a.png"),"resource directory boundary rejects sibling prefix and traversal");
var budget=new PreviewBudget();for(int i=0;i<64;i++)budget.ReserveRequest();
try {budget.ReserveRequest();throw new Exception("Budget bypassed");}catch(InvalidDataException){Check(true,"preview request count is bounded");}
try {new PreviewBudget().RecordBytes(16*1024*1024+1);throw new Exception("Budget bypassed");}catch(InvalidDataException){Check(true,"preview aggregate bytes are bounded");}

HerdrContextChecks.Run(Check);

if(args.Length==3 && args[0]=="--sftp-frame-fixture")await SftpFrameChecks.Run(args[1],int.Parse(args[2]),Check);

if(args.Length==3 && args[0]=="--auth-fixture")await AuthenticationChecks.Run(args[1],int.Parse(args[2]),Check);

if(args.Length==6 && args[0]=="--ssh-fixture") {
 var profile=new ConnectionProfile {Host=args[1],Port=int.Parse(args[2]),User=args[3]};
 await using var session=new SshSession();
 var captured=new System.Text.StringBuilder();
 session.Output+=bytes=>{lock(captured)captured.Append(Encoding.UTF8.GetString(bytes));};
 await session.Connect(profile,File.ReadAllText(args[4]),"",fp=>fp==args[5],100,32,CancellationToken.None);
 session.Send(Encoding.UTF8.GetBytes("printf 'HEXGATE_TRANSPORT_OK\\n'\r"));
 var deadline=DateTime.UtcNow.AddSeconds(10);bool found=false;
 while(!found && DateTime.UtcNow<deadline) {
  lock(captured)found=captured.ToString().Split('\n').Any(line=>line.Contains("HEXGATE_TRANSPORT_OK") && !line.Contains("printf"));
  await Task.Delay(50);
 }
 Check(found,"actual SSH PTY output and input round trip");
 await HerdrQueryChecks.Run(session,Check);
 await HerdrQueryChecks.Retirement(profile,File.ReadAllText(args[4]),args[5],Check);
 var fixture=Path.Combine(Path.GetTempPath(),"hexgate-sftp-"+Guid.NewGuid().ToString("N"));Directory.CreateDirectory(fixture);
 try {
  File.WriteAllText(Path.Combine(fixture,"document.md"),"# SFTP fixture\n한글");
  Directory.CreateDirectory(Path.Combine(fixture,"images"));File.WriteAllBytes(Path.Combine(fixture,"images","ok.png"),[1,2,3]);
  var outside=Path.Combine(Path.GetTempPath(),"hexgate-outside-"+Guid.NewGuid().ToString("N"));File.WriteAllText(outside,"outside");
  try {
   File.CreateSymbolicLink(Path.Combine(fixture,"escape.png"),outside);
   var files=await session.OpenFiles(CancellationToken.None);
   var document=await files.Document(Path.Combine(fixture,"document.md"));Check(document.Text.Contains("한글") && document.Kind=="markdown","actual bounded SFTP document read");
   Check((await files.List(fixture)).Any(e=>e.Name=="images" && e.Directory),"actual SFTP directory listing");
   Check((await files.Resource(fixture,"images/ok.png")).SequenceEqual(new byte[]{1,2,3}),"actual SFTP relative resource read");
   await using(var server=new PreviewServer(document,files,fixture,TerminalThemes.Resolve("hexgate"))) {
    using var browser=new HttpClient();using var outsider=new HttpClient();browser.DefaultRequestHeaders.Add("X-Hexgate-Preview",server.AccessToken);
    var page=await browser.GetAsync(server.Address);
    Check(page.IsSuccessStatusCode && page.Headers.GetValues("Content-Security-Policy").Single().Contains("script-src 'self'"),"trusted preview origin serves restrictive CSP");
    Check((await browser.GetByteArrayAsync(new Uri(server.Address,"images/ok.png"))).SequenceEqual(new byte[]{1,2,3}),"authenticated preview resource reads through bounded SFTP");
    Check((await outsider.GetAsync(new Uri(server.Address,"images/ok.png"))).StatusCode==System.Net.HttpStatusCode.Forbidden,"preview resource requires random origin credential");
    Check((await browser.GetAsync(new Uri(server.Address,"escape.png"))).StatusCode==System.Net.HttpStatusCode.Forbidden,"preview HTTP origin denies SFTP symlink escape");
    Check((await browser.GetAsync(new Uri(server.Address,"execute.js"))).StatusCode==System.Net.HttpStatusCode.Forbidden,"preview does not serve remote executable scripts");
    foreach(var method in new[]{HttpMethod.Post,HttpMethod.Put,HttpMethod.Head,HttpMethod.Options}) {
     using var request=new HttpRequestMessage(method,server.Address);
     using var result=await browser.SendAsync(request);
     Check(result.StatusCode==System.Net.HttpStatusCode.Forbidden,"preview rejects non-GET method: "+method.Method);
    }
    using(var request=new HttpRequestMessage(HttpMethod.Get,server.Address)) {
     request.Headers.Add("X-Hexgate-Preview","incorrect-fixture-token");
     using var result=await outsider.SendAsync(request);
     Check(result.StatusCode==System.Net.HttpStatusCode.Forbidden,"incorrect preview capability cannot retrieve a document");
    }
    using(var request=new HttpRequestMessage(HttpMethod.Get,server.Address)) {
     request.Headers.Host="untrusted.invalid:"+server.Address.Port;
     using var result=await browser.SendAsync(request);
     Check(!result.IsSuccessStatusCode,"forged Host cannot turn the preview listener into another origin");
    }
    foreach(var route in new[]{"/assets/%2f..%2fsecret.txt","/assets/../secret.txt","/remote/%252e%252e/private/protected.svg"}) {
     using var result=await browser.GetAsync(new Uri(server.Address,route));
     Check(result.StatusCode==System.Net.HttpStatusCode.Forbidden,"preview denies encoded/traversal route: "+route);
    }
    bool repeatedThemes=true;
    for(int themeRound=0;themeRound<80;themeRound++) {
     server.ApplyTheme(TerminalThemes.Resolve(themeRound%2==0?"light":"hexgate"));
     using var resource=await browser.GetAsync(new Uri(server.Address,"images/ok.png"));
     if(!resource.IsSuccessStatusCode){repeatedThemes=false;break;}
    }
    Check(repeatedThemes,"theme redraws retain document resources without exhausting a prior render budget");
    File.Delete(Path.Combine(fixture,"images/ok.png"));
    server.ApplyTheme(TerminalThemes.Resolve("light"));
    Check((await browser.GetByteArrayAsync(new Uri(server.Address,"images/ok.png"))).SequenceEqual(new byte[]{1,2,3}),"theme rerender reuses its bounded document resource snapshot");
    File.WriteAllBytes(Path.Combine(fixture,"images/ok.png"),new byte[]{1,2,3});
    server.ApplyTheme(TerminalThemes.Resolve("light"));
    Check((await browser.GetStringAsync(server.Address)).Contains("background:#f5f7fa") && (await browser.GetStringAsync(new Uri(server.Address,"../assets/render-payload.js"))).Contains("\"light\":true"),"preview theme updates both document CSS and diagram renderer");
    bool renderLimit=false;
    for(int request=0;request<65;request++) {
     using var result=await browser.GetAsync(new Uri(server.Address,"images/ok.png"));
     if(result.StatusCode==System.Net.HttpStatusCode.Forbidden){renderLimit=true;break;}
    }
    Check(renderLimit,"cached preview resources still enforce each render delivery limit");
   }
   await using(var server=new PreviewServer(document with {Kind="html",Text="<script>window.hostile=true</script>"},files,fixture,TerminalThemes.Resolve("light"))) {
    using var browser=new HttpClient();browser.DefaultRequestHeaders.Add("X-Hexgate-Preview",server.AccessToken);var page=await browser.GetAsync(server.Address);
    Check(!server.TrustedRenderer && page.Headers.GetValues("Content-Security-Policy").Single().Contains("script-src 'none'"),"untrusted HTML uses script-free origin policy");
    Check(!server.ThemeScript().Contains("window.hostile") && !server.ThemeScript().Contains("renderDocument") && server.ThemeScript().Contains("hexgate-theme"),"static HTML theme update contains only app CSS and no document script");
    Check((await browser.GetAsync(new Uri(server.Address,"../assets/render-payload.js"))).StatusCode==System.Net.HttpStatusCode.Forbidden,"untrusted HTML cannot load trusted renderer scripts");
   }
   foreach(var path in new[]{"escape.png","../"+Path.GetFileName(outside)}) {
    try {await files.Resource(fixture,path);throw new Exception("Resource escape accepted");}catch(InvalidDataException){Check(true,"actual SFTP rejects symlink or traversal escape");}
   }
   File.WriteAllBytes(Path.Combine(fixture,"large.md"),new byte[RemoteFiles.DocumentLimit+1]);
   try {await files.Document(Path.Combine(fixture,"large.md"));throw new Exception("Oversized document accepted");}catch(InvalidDataException){Check(true,"actual SFTP rejects oversized document");}
  } finally {File.Delete(outside);}
 } finally {Directory.Delete(fixture,true);}
 await LiveLifetimeChecks.Run(profile,File.ReadAllText(args[4]),args[5],Check);
 session.Resize(120,40);session.Refresh();
 await ReceiveFlowChecks.Run(profile,File.ReadAllText(args[4]),args[5],Check);
 await session.DisposeAsync();Check(!session.Connected,"connected client state remains safe after disposal");
}

sealed class FakeVault : ISecretVault
{
 public Dictionary<string,string> Values {get;}=[];
 public void Put(string alias,string value)=>Values.Add(alias,value);
 public string Get(string alias)=>Values[alias];
 public void Remove(string alias)=>Values.Remove(alias);
}
