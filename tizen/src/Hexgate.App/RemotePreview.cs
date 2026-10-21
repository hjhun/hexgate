using Hexgate.Core;
using Tizen.NUI;
using Tizen.NUI.BaseComponents;

namespace Hexgate.App;

internal sealed partial class HexgateApplication
{
    private readonly AsyncWorkGroup previewWork=new(32);
    private Task previewCleanup=Task.CompletedTask;
    private void RetireServer(PreviewServer server)
    {
        var cleanup=server.DisposeAsync().AsTask();
        previewCleanup=previewCleanup.IsCompletedSuccessfully?cleanup:Task.WhenAll(previewCleanup,cleanup);
    }
    private void RunPreviewWork(Func<Task> work)
    {
        if(!previewWork.TryRun(work))SetStatus("Preview is busy; retry after pending work completes");
    }
    private PreviewServer? preview;
    private string previewPath="";
    private int previewGeneration;
    private int browseGeneration;
    private WebView? previewWeb;
    private View? previewParent;
    private WebContext? previewContext;
    private PreviewServer? previewWebServer;
    private string previewWebTheme="";
    private int previewViewGeneration,previewRequests;
    private void DetachPreviewView()
    {
        if(previewParent!=null && previewWeb!=null)previewParent.Remove(previewWeb);
        previewParent=null;
    }
    private void RetirePreviewView()
    {
        ++previewViewGeneration;var web=previewWeb;var context=previewContext;
        DetachPreviewView();
        previewWeb=null;previewContext=null;previewWebServer=null;
        if(web==null)return;
        web.StopLoading();web.Hide();
        void DisposeView(){web.Dispose();GC.KeepAlive(context);}
        if(terminated)DisposeView();else ui.Post(_=>DisposeView(),null);
    }
    private void RetirePreview()
    {
        RetirePreviewView();
        ++previewGeneration;++browseGeneration;var previous=preview;preview=null;previewPath="";
        if(previous!=null)RetireServer(previous);
    }
    private void ShowPreview()
    {
        if(connection?.Connected!=true){SetStatus("Connect before opening remote files");return;}
        int ticket=generation,request=++browseGeneration;var previousOverlay=overlay;var target=connection;status="Opening SFTP";
        string namedSession=activeProfile?.HerdrSession??"",tty=terminal.Tty??"";
        RunPreviewWork(async()=>{
            try {
                var files=await target.OpenFiles(lifetime.Token);string home=await files.Home(lifetime.Token);
                HerdrContext? context=null;string? contextError=null;
                if(namedSession.Length!=0) {
                    try {
                        string helper=await File.ReadAllTextAsync(Tizen.Applications.Application.Current.DirectoryInfo.Resource+"helpers/herdr_context.py",lifetime.Token);
                        context=await target.QueryHerdr(helper,namedSession,tty,lifetime.Token);
                    } catch(Exception){contextError="Cannot identify this Herdr client; choose a remote file explicitly";}
                }
                DispatchUi(()=>{
                    if(ticket!=generation || request!=browseGeneration || overlay!=previousOverlay)return;
                    var focused=context?.Automatic==true?context.Panes.FirstOrDefault(p=>p.Id==context.Focused):null;
                    if(focused!=null)BrowseRemote(files,focused.Cwd,ticket);
                    else if(context?.Panes.Count>0)ChooseHerdrPane(files,context,home,ticket);
                    else {if(contextError!=null)status=contextError;BrowseRemote(files,home,ticket);}
                });
            } catch(Exception){if(ticket==generation)SetStatus("Cannot open SFTP. Check server support and connection.",ticket);}
        });
    }
    private void ChooseHerdrPane(RemoteFiles files,HerdrContext context,string home,int ticket)
    {
        int page=0;
        overlay=parent=>{
            var modal=Modal(parent,"Choose Herdr pane");
            AddLabel(modal,"Multiple or changing clients: choose a pane explicitly.",24,65,InnerWidth(modal),60,18).MultiLine=true;
            float y=145;
            foreach(var pane in context.Panes.Skip(page*8).Take(8)) {
                var chosen=pane;
                AddButton(modal,pane.Id+" · "+pane.Name,24,y,InnerWidth(modal),44,()=>{
                    if(ticket==generation)BrowseRemote(files,chosen.Cwd,ticket);
                });y+=52;
            }
            var actions=new List<(string,Action)> {
                ("Browse home",()=>BrowseRemote(files,home,ticket)),
                ("Close",()=>{++browseGeneration;overlay=null;Render();})
            };
            if(page>0)actions.Add(("Previous",()=>{page--;Render();}));
            if((page+1)*8<context.Panes.Count)actions.Add(("Next",()=>{page++;Render();}));
            Actions(modal,y+20,actions.ToArray());
        };Render();
    }
    private void BrowseRemote(RemoteFiles files,string path,int ticket,IReadOnlyList<RemoteEntry>? loaded=null)
    {
        if(ticket!=generation)return;
        int browserTicket=++browseGeneration;var previousOverlay=overlay;
        string chosenPath=path;int page=0;IReadOnlyList<RemoteEntry> entries=[];
        void Draw()
        {
            overlay=parent=>{
                var modal=Modal(parent,"Remote files");
                Input(modal,"Remote path",chosenPath,65,value=>chosenPath=value);
                float end=Actions(modal,175,("Open path",()=>OpenRemote(files,chosenPath,ticket)),
                    ("Parent folder",()=>BrowseRemote(files,RemoteFiles.Parent(path),ticket)),
                    ("Close",()=>{++browseGeneration;++previewGeneration;overlay=null;Render();}));
                int start=page*8;float y=end;
                foreach(var entry in entries.Skip(start).Take(8)) {
                    var selected=entry;AddButton(modal,(entry.Directory?"▸ ":"")+entry.Name,24,y,InnerWidth(modal),44,()=>OpenRemote(files,selected.Path,ticket));y+=52;
                }
                modal.Size=new Size(modal.Size.Width,Math.Max(850,y+100));
                if(page>0)AddButton(modal,"Previous",24,y+16,Math.Min(190,InnerWidth(modal)/2-10),44,()=>{page--;Render();});
                if(start+8<entries.Count)AddButton(modal,"Next",34+InnerWidth(modal)/2,y+16,Math.Min(190,InnerWidth(modal)/2-10),44,()=>{page++;Render();});
            };Render();
        }
        if(loaded!=null){entries=loaded;Draw();return;}
        RunPreviewWork(async()=>{
            try {
                var found=await files.List(path,lifetime.Token);
                DispatchUi(()=>{if(ticket==generation && browserTicket==browseGeneration && overlay==previousOverlay){entries=found;Draw();}});
            } catch(Exception){if(ticket==generation)SetStatus("Cannot list this remote folder",ticket);}
        });
    }
    private void OpenRemote(RemoteFiles files,string path,int ticket)
    {
        int request=++previewGeneration;var previousOverlay=overlay;status="Reading remote file";
        RunPreviewWork(async()=>{
            try {
                RemoteDocument document;
                try {document=await files.Document(path,lifetime.Token);}
                catch(Exception) {
                    var listing=await files.List(path,lifetime.Token);
                    DispatchUi(()=>{if(ticket==generation && request==previewGeneration)BrowseRemote(files,path,ticket,listing);});return;
                }
                var server=new PreviewServer(document,files,Tizen.Applications.Application.Current.DirectoryInfo.Resource+"preview",Theme);
                bool adopted=false;
                var decision=new TaskCompletionSource<bool>(TaskCreationOptions.RunContinuationsAsynchronously);
                using var cancellation=lifetime.Token.Register(()=>decision.TrySetResult(false));
                try {
                    // Always resolve ownership, even after ordinary DispatchUi starts dropping work.
                    ui.Post(_=>{
                        if(decision.Task.IsCompleted || terminated || ticket!=generation ||
                            request!=previewGeneration || overlay!=previousOverlay) {
                            decision.TrySetResult(false);return;
                        }
                        var previous=preview;preview=server;previewPath=document.Path;
                        overlay=null;status="Preview: "+document.Kind;
                        decision.TrySetResult(true);
                        if(previous!=null)RetireServer(previous);Render();
                    },null);
                    adopted=await decision.Task.ConfigureAwait(false);
                } finally {if(!adopted)await server.DisposeAsync().ConfigureAwait(false);}
            } catch(Exception){if(ticket==generation && request==previewGeneration)SetStatus("Cannot preview this path. Choose a supported document within the limits.",ticket);}
        });
    }
    private void RenderPreview(View parent,float x,float w,float h)
    {
        var server=preview!;
        AddLabel(parent,previewPath,x,viewport.TerminalTop,Math.Max(1,w-100),40,16);
        AddButton(parent,"Close",x+w-92,viewport.TerminalTop,92,40,()=>{RetirePreview();Render();});
        if(previewWeb!=null && previewWebServer==server) {
            previewWeb.Position=new Position(x*viewport.Scale,(viewport.TerminalTop+46)*viewport.Scale);previewWeb.Size=new Size(Math.Max(1,w)*viewport.Scale,Math.Max(1,h-46)*viewport.Scale);
            root!.Add(previewWeb);previewParent=root;
            if(previewWebTheme!=Theme.Id){previewWebTheme=Theme.Id;previewWeb.EvaluateJavaScript(server.ThemeScript());}
            return;
        }
        RetirePreviewView();int viewTicket=previewViewGeneration,ownerTicket=generation;
        try {
            var web=new WebView(["org.tizen.hexgate"],WebView.WebEngineType.UseSystemSetting) {
                Position=new Position(x*viewport.Scale,(viewport.TerminalTop+46)*viewport.Scale),Size=new Size(Math.Max(1,w)*viewport.Scale,Math.Max(1,h-46)*viewport.Scale)
            };
            previewWeb=web;previewWebServer=server;previewWebTheme=Theme.Id;
            root!.Add(web);previewParent=root;
            var context=web.Context;previewContext=context;
            var settings=web.Settings;
            settings.JavaScriptEnabled=server.TrustedRenderer;
            settings.WebSecurityEnabled=true;settings.FileAccessFromExternalUrlAllowed=false;
            // Tizen 10.1 Chromium aborts when private browsing is toggled after creation.
            // Keep remote content non-cacheable at both the HTTP and WebContext layers.
            settings.PluginsEnabled=false;context.CacheEnabled=false;
            context.CacheModel=WebContext.CacheModelType.DocumentViewer;
            settings.AutofillPasswordFormEnabled=false;settings.FormCandidateDataEnabled=false;
            settings.ScriptsOpenWindowsAllowed=false;settings.ClipboardEnabled=false;
            settings.MixedContentsAllowed=false;
            const string origin="https://hexgate.preview";
            // This target does not send AddCustomHeader on WebView requests. Keep the
            // capability in the native HTTP client and intercept this synthetic origin.
            context.RegisterHttpRequestInterceptedCallback(request=>{
                if(viewTicket!=previewViewGeneration || !Uri.TryCreate(request.Url,UriKind.Absolute,out var address) || address.GetLeftPart(UriPartial.Authority)!=origin) {
                    request.SetResponseStatus(403,"Blocked");request.SetResponseBody(Array.Empty<byte>());return;
                }
                if(Interlocked.Increment(ref previewRequests)>8) {
                    Interlocked.Decrement(ref previewRequests);request.SetResponseStatus(429,"Busy");request.SetResponseBody(Array.Empty<byte>());return;
                }
                if(!previewWork.TryRun(async()=>{
                    byte[] body=[];string mime="text/plain",policy=PreviewServer.Policy(false);int code=403;
                    try {
                        using var http=new HttpClient{Timeout=TimeSpan.FromSeconds(20),MaxResponseContentBufferSize=12*1024*1024};
                        http.DefaultRequestHeaders.Add("X-Hexgate-Preview",server.AccessToken);
                        var local=new UriBuilder(server.Address){Path=address.AbsolutePath,Query=address.Query};
                        using var response=await http.GetAsync(local.Uri,lifetime.Token);
                        body=await response.Content.ReadAsByteArrayAsync(lifetime.Token);code=(int)response.StatusCode;
                        mime=response.Content.Headers.ContentType?.ToString()??mime;
                        if(response.Headers.TryGetValues("Content-Security-Policy",out var values))policy=values.Single();
                    } catch(Exception){}
                    // Hold the credit until the UI response is delivered or discarded,
                    // bounding queued response bodies as well as HTTP work.
                    ui.Post(_=>{
                        Interlocked.Decrement(ref previewRequests);
                        if(terminated || viewTicket!=previewViewGeneration)return;
                        request.SetResponseStatus(code,code==200?"OK":"Blocked");
                        request.AddResponseHeader("Content-Type",mime);
                        request.AddResponseHeader("Content-Security-Policy",policy);
                        request.AddResponseHeader("Cache-Control","no-store");
                        request.AddResponseHeader("X-Content-Type-Options","nosniff");
                        request.AddResponseHeader("Referrer-Policy","no-referrer");
                        request.SetResponseBody(body);
                    },null);
                })) {
                    Interlocked.Decrement(ref previewRequests);
                    request.SetResponseStatus(429,"Busy");request.SetResponseBody(Array.Empty<byte>());
                }
            });
            web.NewWindowPolicyDecided+=(_,args)=>args.PolicyDecisionMaker.Ignore();
            web.NavigationPolicyDecided+=(_,args)=>{
                if(viewTicket!=previewViewGeneration || args.PolicyDecisionMaker.Url!=origin+"/remote/")args.PolicyDecisionMaker.Ignore();
                else args.PolicyDecisionMaker.Use();
            };
            web.PageLoadError+=(_,_)=>{if(viewTicket==previewViewGeneration)SetStatus("Preview engine could not load the local document",ownerTicket);};
            web.PageLoadFinished+=(_,_)=>{if(viewTicket==previewViewGeneration)web.EvaluateJavaScript(server.ThemeScript());};
            web.LoadUrl(origin+"/remote/");
        } catch(Exception){AddLabel(parent,"Document engine unavailable on this target",x,130,w,90);}
    }
}
