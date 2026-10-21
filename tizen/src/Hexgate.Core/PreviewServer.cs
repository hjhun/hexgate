using System.Net;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;

namespace Hexgate.Core;

/// <summary>Ephemeral loopback document origin, authenticated before any resource is served.</summary>
public sealed class PreviewServer : IAsyncDisposable
{
    private readonly HttpListener listener=new();
    private readonly CancellationTokenSource lifetime=new();
    private readonly SemaphoreSlim slots=new(4,4);
    private readonly string token=Convert.ToHexString(RandomNumberGenerator.GetBytes(32));
    private readonly RemoteDocument document;
    private readonly RemoteFiles files;
    private readonly string assets;
    private TerminalTheme theme;
    private PreviewBudget budget=new();
    private readonly PreviewBudget snapshotBudget=new();
    private readonly SemaphoreSlim snapshotGate=new(1,1);
    private readonly Dictionary<string,byte[]> snapshots=new(StringComparer.Ordinal);
    private readonly Task loop;
    private readonly List<Task> requests=[];
    private readonly object disposalGate=new();
    private Task? disposal;
    public Uri Address {get;}
    public string AccessToken=>token;
    public bool TrustedRenderer=>document.Kind!="html";
    public void ApplyTheme(TerminalTheme selected)
    {
        var previous=Interlocked.Exchange(ref theme,selected);
        // A local light/dark transition rerenders diagrams in the existing document.
        // Bound each render's delivery while retaining the original bounded SFTP snapshot.
        if(TrustedRenderer && previous.Light!=selected.Light)
            Volatile.Write(ref budget,new PreviewBudget());
    }
    public PreviewServer(RemoteDocument document,RemoteFiles files,string assets,TerminalTheme theme)
    {
        this.document=document;this.files=files;this.assets=assets;this.theme=theme;
        for(int i=0;;i++) {
            var prefix="http://127.0.0.1:"+RandomNumberGenerator.GetInt32(49152,65536)+"/";
            listener.Prefixes.Clear();listener.Prefixes.Add(prefix);
            try {listener.Start();Address=new Uri(prefix+"remote/");break;}
            catch(HttpListenerException) when(i<7){}
        }
        loop=Accept();
    }
    private async Task Accept()
    {
        try {
            while(!lifetime.IsCancellationRequested) {
                var request=await listener.GetContextAsync().WaitAsync(lifetime.Token).ConfigureAwait(false);
                // Acquire before dispatch so outstanding requests cannot grow without bound.
                if(!await slots.WaitAsync(TimeSpan.FromSeconds(5),lifetime.Token).ConfigureAwait(false)) {
                    request.Response.StatusCode=429;request.Response.Close();continue;
                }
                var work=Respond(request);
                lock(requests){requests.RemoveAll(t=>t.IsCompleted);requests.Add(work);}
            }
        } catch(Exception) when(lifetime.IsCancellationRequested){}
    }
    private async Task Respond(HttpListenerContext context)
    {
        try {
            var request=context.Request;var response=context.Response;var url=request.Url!;
            if(request.HttpMethod!="GET" || url.Host!="127.0.0.1" || url.Port!=Address.Port ||
                (request.Cookies["hexgate-preview"]?.Value!=token && request.Headers["X-Hexgate-Preview"]!=token)) {
                response.StatusCode=403;return;
            }
            if(request.Headers["X-Hexgate-Preview"]==token)response.SetCookie(new Cookie("hexgate-preview",token,"/"){HttpOnly=true});
            response.Headers["Cache-Control"]="no-store";
            response.Headers["X-Content-Type-Options"]="nosniff";
            response.Headers["Referrer-Policy"]="no-referrer";
            response.Headers["Content-Security-Policy"]=Policy(TrustedRenderer);
            string path=Uri.UnescapeDataString(url.AbsolutePath);byte[] body;string mime;
            if(path=="/remote/") {body=Encoding.UTF8.GetBytes(Page());mime="text/html; charset=utf-8";}
            else if(path.StartsWith("/assets/",StringComparison.Ordinal) && TrustedRenderer) {
                string name=path[8..];mime="application/javascript; charset=utf-8";
                if(name=="render-payload.js") {
                    string text=document.Kind=="mermaid"?"```mermaid\n"+document.Text+"\n```":document.Text;
                    body=Encoding.UTF8.GetBytes("window.hexgateRenderPayload="+JsonSerializer.Serialize(new {kind=document.Kind,text,light=theme.Light})+";window.renderDocument(window.hexgateRenderPayload);");
                } else {
                    if(name is not ("renderer-init.js" or "drawio-viewer.js" or "preview.js")){response.StatusCode=403;return;}
                    body=await File.ReadAllBytesAsync(Path.Combine(assets,name),lifetime.Token).ConfigureAwait(false);
                }
            } else if(path.StartsWith("/remote/",StringComparison.Ordinal)) {
                string relative=path[8..];mime=ResourceMime(relative);
                var renderBudget=Volatile.Read(ref budget);renderBudget.ReserveRequest();
                body=await ResourceSnapshot(relative).ConfigureAwait(false);renderBudget.RecordBytes(body.Length);
            } else {response.StatusCode=403;return;}
            response.ContentType=mime;response.ContentLength64=body.Length;
            await response.OutputStream.WriteAsync(body,lifetime.Token).ConfigureAwait(false);
        } catch(Exception) {try{context.Response.StatusCode=403;}catch(ObjectDisposedException){}}
        finally {try {context.Response.Close();}finally {slots.Release();}}
    }
    public static string Policy(bool trusted)=>"default-src 'none'; script-src "+(trusted?"'self'":"'none'")+"; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src "+(trusted?"data:":"'none'")+"; connect-src "+(trusted?"'self'":"'none'")+"; frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'";
    private static string ResourceMime(string path)=>Path.GetExtension(path).ToLowerInvariant() switch {
        ".png"=>"image/png", ".jpg" or ".jpeg"=>"image/jpeg", ".gif"=>"image/gif", ".webp"=>"image/webp", ".svg"=>"image/svg+xml",
        ".css"=>"text/css", ".drawio" or ".xml"=>"application/xml", _=>throw new InvalidDataException("Unsupported preview resource")
    };
    private async Task<byte[]> ResourceSnapshot(string relative)
    {
        await snapshotGate.WaitAsync(lifetime.Token).ConfigureAwait(false);
        try {
            if(snapshots.TryGetValue(relative,out var cached))return cached;
            snapshotBudget.ReserveRequest();
            var bytes=await files.Resource(document.Root,relative,lifetime.Token).ConfigureAwait(false);
            snapshotBudget.RecordBytes(bytes.Length);snapshots.Add(relative,bytes);return bytes;
        } finally {snapshotGate.Release();}
    }
    private static string Style(TerminalTheme selected)=>
        $"body{{font:16px/1.65 sans-serif;background:{selected.Background[..7]};color:{selected.Foreground[..7]};padding:20px;overflow-wrap:anywhere}}pre{{overflow:auto;padding:12px}}a{{color:{selected.Accent[..7]}}}img,svg{{max-width:100%;height:auto}}table{{border-collapse:collapse}}td,th{{border:1px solid {selected.Accent[..7]};padding:6px}}";
    public string ThemeScript()
    {
        var selected=Volatile.Read(ref theme);
        string diagrams=TrustedRenderer?
            "var p=window.hexgateRenderPayload;if(p&&p.light!=="+(selected.Light?"true":"false")+"){p.light="+(selected.Light?"true":"false")+";window.renderDocument(p);}":"";
        // Only app-owned CSS and a Boolean cross this boundary. Static HTML stays
        // JavaScript-disabled; never interpolate its document text into evaluation.
        return "(()=>{var s=document.getElementById('hexgate-theme');if(!s)return false;s.textContent="+
            JsonSerializer.Serialize(Style(selected))+";"+diagrams+"return true;})()";
    }
    private string Page()
    {
        string css=Style(Volatile.Read(ref theme));
        string body=TrustedRenderer?"<main id=\"document\"></main><script src=\"/assets/renderer-init.js\"></script><script src=\"/assets/drawio-viewer.js\"></script><script src=\"/assets/preview.js\"></script><script src=\"/assets/render-payload.js\"></script>":document.Text;
        return "<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><style id=\"hexgate-theme\">"+css+"</style></head><body>"+body+"</body></html>";
    }
    public ValueTask DisposeAsync()
    {
        lock(disposalGate)return new(disposal??=Task.Run(DisposeCore));
    }
    private async Task DisposeCore()
    {
        lifetime.Cancel();listener.Close();try{await loop.ConfigureAwait(false);}catch(Exception){}
        Task[] pending;lock(requests)pending=requests.ToArray();
        try{await Task.WhenAll(pending).ConfigureAwait(false);}catch(Exception){}
        snapshots.Clear();snapshotGate.Dispose();lifetime.Dispose();slots.Dispose();
    }
}
