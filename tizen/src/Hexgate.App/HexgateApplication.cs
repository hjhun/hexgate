using Hexgate.Core;
using System.Text;
using Tizen.NUI;
using Tizen.NUI.BaseComponents;
using Tizen.NUI.Components;

namespace Hexgate.App;

internal sealed partial class HexgateApplication : NUIApplication
{
    private TerminalScreen terminal=new();
    private ProfileRepository profiles=null!;
    private SynchronizationContext ui=null!;
    private View? root,chromeRoot;
    private WindowViewport viewport;
    private int renderQueued;
    private NativeTerminalView? terminalView;
    private TextLabel? statusLabel;
    private SshSession? connection;
    private ConnectionProfile? activeProfile;
    private CancellationTokenSource? connecting;
    private readonly CancellationTokenSource lifetime=new();
    private bool menu=true,imeActive;
    private volatile bool terminated;
    private Task connectionCleanup=Task.CompletedTask,frameTask=Task.CompletedTask;
    private CancellationToken applicationToken;
    private UiStatusDispatcher? statusDispatcher;
    private int generation,frameQueued;
    private string status="Ready";
    private Action<View>? overlay;
    private TaskCompletionSource<bool>? hostAnswer;
    private float width,height;
    private int renderEpoch;
    private readonly Dictionary<string,TextField> inputFields=[];
    protected override void OnCreate()
    {
        base.OnCreate();applicationToken=lifetime.Token;ui=SynchronizationContext.Current ?? new Tizen.Applications.TizenSynchronizationContext();
        FocusManager.Instance.EnableDefaultAlgorithm(true);
        FontClient.Instance.AddCustomFontDirectory(Tizen.Applications.Application.Current.DirectoryInfo.Resource+"fonts");
        try {
            var vault=new SecureCredentialStore();vault.VerifyRoundTrip();
            profiles=new ProfileRepository(Tizen.Applications.Application.Current.DirectoryInfo.Data,vault);
            status=CredentialCleanupStatus("Ready");
        } catch(Exception) {ShowStorageUnavailable();return;}
        statusDispatcher=new UiStatusDispatcher(ui,()=>Volatile.Read(ref generation),text=>{
            status=text;if(statusLabel!=null)statusLabel.Text=text;
        });
        SubscribeTerminal();
        Window.Default.Resized+=WindowResized;Window.Default.InsetsChanged+=WindowResized;Window.Default.InterceptKeyEvent+=InterceptKey;
        Render();
        if(profiles.AutoConnect && profiles.Profiles.FirstOrDefault(p=>p.Id==profiles.LastProfile) is {} last)Select(last);
    }
    private void ShowStorageUnavailable()
    {
        // Keep a native, fail-closed explanation visible instead of crashing before first render.
        var size=Window.Default.WindowSize;
        root=Own(new View {Size=new Size(size.Width,size.Height),BackgroundColor=new Color("#0b1119FF")});
        root.Add(Own(new TextLabel {
            Text="Secure storage or profile metadata is unavailable.\nCredentials cannot be saved or used.\nClose Hexgate and retry after resolving the storage problem.",
            Position=new Position(32,32),Size=new Size(Math.Max(100,size.Width-64),180),
            MultiLine=true,PixelSize=24,TextColor=new Color("#e5ebf2FF"),VerticalAlignment=VerticalAlignment.Center
        }));
        var close=Own(new Button {Text="Close",Position=new Position(32,240),Size=new Size(200,56)});
        close.Clicked+=(_,_)=>Exit();root.Add(close);Window.Default.GetDefaultLayer().Add(root);
    }
    private void DispatchUi(Action work)=>ui.Post(_=>{if(!terminated)work();},null);
    private void SubscribeTerminal()
    {
        var owner=connection;int ticket=generation;
        terminal.Changed+=QueueFrame;
        terminal.Response+=bytes=>{if(ticket!=Volatile.Read(ref generation) || owner?.Connected!=true)return;try {owner.Send(bytes);}catch(InvalidOperationException){SetStatus("Input queue full",ticket);}};
    }
    private void QueueFrame()
    {
        if(terminated || Interlocked.Exchange(ref frameQueued,1)!=0)return;
        frameTask=PresentFrame();
    }
    private async Task PresentFrame()
    {
        try {
            await Task.Delay(33,applicationToken).ConfigureAwait(false);
            DispatchUi(()=>{Interlocked.Exchange(ref frameQueued,0);terminalView?.Paint();});
        } catch(OperationCanceledException){Interlocked.Exchange(ref frameQueued,0);}
    }
    private void SetStatus(string text,int? ticket=null)=>statusDispatcher?.Post(text,ticket??Volatile.Read(ref generation));
    private bool TryReadViewport(out WindowViewport value)
    {
        value=default;
        try {
            var size=Window.Default.WindowSize;var insets=Window.Default.GetInsets();
            return WindowViewport.TryCreate(size.Width,size.Height,insets.Start,insets.Top,insets.End,insets.Bottom,out value);
        } catch(Exception){return false;}
    }
    private void WindowResized(object? sender,EventArgs args)
    {
        if(Interlocked.Exchange(ref renderQueued,1)!=0)return;
        DispatchUi(()=>{
            Interlocked.Exchange(ref renderQueued,0);
            if(TryReadViewport(out var next) && next!=viewport)Render();
        });
    }
    private void Render()
    {
        if(!TryReadViewport(out var next))return;
        string? focusedField=inputFields.FirstOrDefault(entry=>entry.Value.KeyInputFocus).Key;
        CaptureModalScroll();
        if(overlay==clipboardOverlay){overlay=null;clipboardOverlay=null;}
        if(overlay==null)ResetModalScroll();
        ++renderEpoch;
        foreach(var previousField in inputFields.Values)previousField.KeyInputFocus=false;
        inputFields.Clear();controlButton=null;altButton=null;
        viewport=next;width=next.LogicalWidth;height=next.LogicalHeight;
        FocusManager.Instance.ResetFocusFinderRootView();
        DetachPreviewView();RetireTerminalView();
        if(root!=null){Window.Default.GetDefaultLayer().Remove(root);RetireScene(root);}
        root=Own(new View {Position=new Position(next.X,next.Y),Size=new Size(next.Width,next.Height),BackgroundColor=Background,
            ClippingMode=ClippingModeType.ClipToBoundingBox});
        chromeRoot=Own(new View {Size=new Size(width,height),Scale=new Vector3(next.Scale,next.Scale,1),
            PivotPoint=PivotPoint.TopLeft,PositionUsesPivotPoint=true});
        var menuButton=AddButton(chromeRoot,"☰",16,12,60,44,()=>{menu=!menu;Render();});menuButton.BackgroundColor=Color.Transparent;
        chromeRoot.Add(Own(new ImageView(IconPath("hexgate_portal.png")){Position=new Position(92,14),Size=new Size(40,40)}));
        AddLabel(chromeRoot,"HEXGATE",146,12,Math.Max(1,Math.Min(190,width-290)),44,next.Compact?22:26);
        statusLabel=next.Compact?AddLabel(chromeRoot,status,16,64,Math.Max(1,width-32),28,16)
            :AddLabel(chromeRoot,status,350,16,Math.Max(1,width-550),40,19);
        AddIconButton(chromeRoot,"Refresh terminal","ic_refresh.svg",width-128,12,Refresh);
        AddIconButton(chromeRoot,fullScreen?"Exit full screen":"Enter full screen",fullScreen?"ic_fullscreen_exit.svg":"ic_fullscreen.svg",width-68,12,ToggleFullScreen);
        float left=menu && !next.Compact?294:16;
        terminalView=new NativeTerminalView(terminal) {Position=new Position(left*next.Scale,next.TerminalTop*next.Scale),
            ClippingMode=ClippingModeType.ClipToBoundingBox};
        bool singlePreview=preview!=null && width<1100;
        float terminalWidth=preview!=null && !singlePreview?Math.Max(1,(width-left-32)/2):Math.Max(1,width-left-16);
        root.Add(terminalView);
        terminalView.Configure(terminalWidth*next.Scale,Math.Max(1,height-next.TerminalTop-80)*next.Scale,
            (int)Math.Round(profiles.FontSize*next.Scale),Theme);
        if(singlePreview)terminalView.Hide();
        var inputView=terminalView;int inputTicket=generation;
        inputView.Modifiers=()=> (shiftPressed,controlPressed||controlLatch);
        inputView.FocusRequested+=FocusTerminal;inputView.SelectionChanged+=QueueFrame;
        inputView.PasteRequested+=PasteClipboard;
        inputView.ContextRequested+=(x,y)=>DispatchUi(()=>{if(inputTicket==generation && inputView==terminalView)ShowClipboardMenu(x,y);});
        inputView.Interaction.CopyRequested+=text=>DispatchUi(()=>{if(inputTicket==generation && inputView==terminalView)WriteClipboard(text);});
        if(!singlePreview)RenderTerminalKeys(chromeRoot,left);
        if(preview!=null) {
            preview.ApplyTheme(Theme);
            float previewLeft=singlePreview?left:left+terminalWidth+16;
            RenderPreview(chromeRoot,previewLeft,Math.Max(1,width-previewLeft-16),height-next.TerminalTop-(singlePreview?16:80));
        }
        if(menu)RenderDrawer(chromeRoot);
        root.Add(chromeRoot);
        Window.Default.GetDefaultLayer().Add(root);
        if(overlay!=null)overlay(chromeRoot);
        // The terminal is a physical sibling of scaled chrome. Include both in
        // focus search so input can return from the IME to the terminal.
        FocusManager.Instance.SetFocusFinderRootView(overlay!=null && modalScroll!=null?modalScroll:root);
        if(focusedField!=null && inputFields.TryGetValue(focusedField,out var field)){field.KeyInputFocus=true;RevealModalField(field);}
        var editing=inputFields.Values.FirstOrDefault(value=>value.KeyInputFocus);
        if(overlay!=null && editing!=null)FocusManager.Instance.SetCurrentFocusView(editing);
        else if(overlay!=null && modalScroll!=null)FocusManager.Instance.SetCurrentFocusView(modalScroll);
        else if(overlay==null)FocusManager.Instance.SetCurrentFocusView(!menu && connection?.Connected==true && !singlePreview?terminalView:menuButton);
        QueueResize();
    }
    private TerminalTheme Theme=>TerminalThemes.Resolve(profiles.Theme);
    private Color Background=>new Color(Theme.Background);
    private Color Foreground=>new Color(Theme.Foreground);
    private Color Accent=>new Color(Theme.Accent);
    private Color Blend(float weight,string? target=null)
    {
        int bg=Convert.ToInt32(Theme.Background.Substring(1,6),16),fg=Convert.ToInt32((target??Theme.Foreground).Substring(1,6),16);
        int Channel(int shift)=>(int)(((bg>>shift)&255)*(1-weight)+((fg>>shift)&255)*weight);
        return new Color(Channel(16)/255f,Channel(8)/255f,Channel(0)/255f,1);
    }
    private Color SurfaceVariant=>Blend(.10f);
    private static string IconPath(string name)=>Tizen.Applications.Application.Current.DirectoryInfo.Resource+"icons/"+name;
    private TextLabel AddLabel(View parent,string text,float x,float y,float w,float h,float pixels=20)
    {
        var label=Own(new TextLabel {Text=text,Position=new Position(x,y),Size=new Size(w,h),PixelSize=pixels,TextColor=Foreground,VerticalAlignment=VerticalAlignment.Center});parent.Add(label);return label;
    }
    private Button AddButton(View parent,string text,float x,float y,float w,float h,Action action)
    {
        var button=Own(new Button {Text=text,Position=new Position(x,y),Size=new Size(w,h),Focusable=true,Name="Hexgate-"+text});
        button.BackgroundColor=text.StartsWith("● ",StringComparison.Ordinal)?Blend(.18f,Theme.Accent):SurfaceVariant;button.TextColor=Foreground;
        button.CornerRadius=8;button.Padding=new Extents(12,12,0,0);
        button.TextLabel.PixelSize=19;button.TextLabel.VerticalAlignment=VerticalAlignment.Center;
        button.TextLabel.HorizontalAlignment=HorizontalAlignment.Center;
        int epoch=renderEpoch;
        button.Clicked+=(_,_)=>DispatchUi(()=>{if(epoch==renderEpoch)action();});
        parent.Add(button);return button;
    }
    private void AddIconButton(View parent,string label,string icon,float x,float y,Action action)
    {
        var button=AddButton(parent,"",x,y,48,44,action);button.Name=label;button.BackgroundColor=Color.Transparent;button.Padding=new Extents(0,0,0,0);
        var image=Own(new ImageView(IconPath(icon)){Position=new Position(10,8),Size=new Size(28,28),ImageColor=Accent,Sensitive=false});button.Add(image);
    }
    private int profilePage;
    private void RenderDrawer(View parent)
    {
        if(viewport.Compact) {
            var shade=Own(new View {Position=new Position(0,viewport.TerminalTop-4),Size=new Size(width,Math.Max(1,height-viewport.TerminalTop+4)),BackgroundColor=new Color(0,0,0,.4f)});
            int epoch=renderEpoch;shade.TouchEvent+=(_,args)=>{
                if(args.Touch.GetPointCount()>0 && args.Touch.GetState(0)==PointStateType.Down)
                    DispatchUi(()=>{if(epoch==renderEpoch){menu=false;Render();}});
                return true;
            };parent.Add(shade);
        }
        float w=Math.Max(1,Math.Min(268,width-24)),h=Math.Max(1,height-viewport.TerminalTop-8);
        var pane=Own(new View {Position=new Position(12,viewport.TerminalTop-4),Size=new Size(w,h),BackgroundColor=Blend(Theme.Light?.02f:.04f)});parent.Add(pane);
        var body=Own(new View {Size=new Size(w,h)});AddLabel(body,"Connections",12,8,Math.Max(1,w-24),38,23);
        var list=profiles.Profiles;profilePage=Math.Clamp(profilePage,0,Math.Max(0,(list.Count-1)/8));float y=58;
        foreach(var p in list.Skip(profilePage*8).Take(8)) {
            AddButton(body,p.Name,10,y,Math.Max(1,w-84),44,()=>Select(p));
            AddButton(body,"Edit",w-70,y,60,44,()=>ShowEditor(p));y+=54;
        }
        if(list.Count>8) {
            if(profilePage>0)AddButton(body,"Previous",10,y,(w-30)/2,44,()=>{profilePage--;Render();});
            if((profilePage+1)*8<list.Count)AddButton(body,"Next",20+(w-30)/2,y,(w-30)/2,44,()=>{profilePage++;Render();});
            y+=54;
        }
        AddButton(body,"Add connection",10,y+6,Math.Max(1,w-20),44,()=>ShowEditor(new()));y+=60;
        if(activeProfile!=null){AddButton(body,"Disconnect",10,y,Math.Max(1,w-20),44,()=>{Disconnect();Render();});y+=54;}
        float contentHeight=Math.Max(h,y+128);body.Size=new Size(w,contentHeight);
        AddButton(body,preview==null?"Preview":"Choose preview",10,contentHeight-112,Math.Max(1,w-20),44,ShowPreview);
        AddButton(body,"Settings",10,contentHeight-56,Math.Max(1,w-20),44,ShowSettings);
        ScrollSurface(pane,body,0,0,w,h,ScrollableBase.Direction.Vertical);
    }
    private void Select(ConnectionProfile profile)
    {
        if(connection?.Connected==true && activeProfile?.Id==profile.Id){menu=false;Render();return;}
        // Let the key parser request a passphrase only for an encrypted key.
        // Ordinary keys, including last-profile auto-connect, need no confirmation dialog.
        _=Connect(profile,"");
    }
    private async Task Connect(ConnectionProfile profile,string passphrase)
    {
        Disconnect();int ticket=++generation;
        activeProfile=profile;connecting=CancellationTokenSource.CreateLinkedTokenSource(lifetime.Token);
        var token=connecting.Token;var target=new SshSession();connection=target;
        RetireTerminalView();terminal.Changed-=QueueFrame;terminal.Dispose();
        var sessionScreen=new TerminalScreen();terminal=sessionScreen;SubscribeTerminal();
        menu=false;status="Connecting to "+profile.Name;Render();
        int columns=terminalView!.Columns,rows=terminalView.Rows;
        target.Closed+=()=>DispatchUi(()=>{if(ticket==generation){Disconnect();status="Connection closed; reconnect to resume";Render();}});
        target.Output+=bytes=>{if(ticket==Volatile.Read(ref generation))sessionScreen.Feed(bytes);};
        try {
            await Task.Run(async()=>{
                var secret=profiles.Secret(profile);
                await target.Connect(profile,secret,passphrase,fingerprint=>VerifyHost(profile,fingerprint,ticket,token),columns,rows,token);
            },token);
            if(ticket!=generation)return;
            profiles.Select(profile.Id);status=profile.Name+" — connected";Render();
        } catch(Exception error) {
            bool unlock=error is Renci.SshNet.Common.SshPassPhraseNullOrEmptyException &&
                profile.Authentication==AuthenticationMethod.PrivateKey && passphrase.Length==0 && !token.IsCancellationRequested;
            string failure="Connection failed. Check credentials, server and host trust.";
            try {await target.DisposeAsync();}catch(Exception){unlock=false;failure="Connection cleanup failed; restart Hexgate before retrying.";}
            // Cleanup yields. A new connection may already own the UI when it finishes.
            if(ticket==generation && connection==target) {
                status=failure;connection=null;connecting?.Dispose();connecting=null;
                if(unlock){status="Encrypted key requires a passphrase";ShowPassphrase(profile);}
                else Render();
            }
        }
    }
    private bool VerifyHost(ConnectionProfile profile,string fingerprint,int ticket,CancellationToken token)
    {
        if(profiles.Trusted(profile)==fingerprint)return true;
        var answer=new TaskCompletionSource<bool>(TaskCreationOptions.RunContinuationsAsynchronously);
        DispatchUi(()=>{
            if(ticket!=generation){answer.TrySetResult(false);return;}
            hostAnswer=answer;
            overlay=parent=>{
                var modal=Modal(parent,"Verify server identity");
                AddLabel(modal,profile.Host+":"+profile.Port,24,80,InnerWidth(modal),48);
                AddLabel(modal,profiles.Trusted(profile)==null?"First connection — compare this fingerprint on your server.":"WARNING: server identity changed. Verify before replacing.",24,140,InnerWidth(modal),68).MultiLine=true;
                AddLabel(modal,fingerprint,24,216,InnerWidth(modal),96,20).MultiLine=true;
                Actions(modal,320,("Trust",()=>{profiles.Trust(profile,fingerprint);hostAnswer=null;overlay=null;answer.TrySetResult(true);Render();}),
                    ("Reject",()=>{hostAnswer=null;overlay=null;answer.TrySetResult(false);Render();}));
            };Render();
        });
        try{return answer.Task.WaitAsync(TimeSpan.FromSeconds(90),token).GetAwaiter().GetResult();}
        catch {DispatchUi(()=>{if(hostAnswer==answer){hostAnswer=null;overlay=null;Render();}});return false;}
    }
    private void Disconnect()
    {
        terminalView?.Interaction.Cancel();RetirePreview();
        ++generation;hostAnswer?.TrySetResult(false);hostAnswer=null;overlay=null;
        connecting?.Cancel();connecting?.Dispose();connecting=null;
        var target=connection;connection=null;
        if(target!=null) {
            var cleanup=target.DisposeAsync().AsTask();
            connectionCleanup=connectionCleanup.IsCompletedSuccessfully?cleanup:Task.WhenAll(connectionCleanup,cleanup);
        }
        status="Disconnected";
    }
    private void Send(string text)
    {
        try{connection?.Send(Encoding.UTF8.GetBytes(text));}catch(InvalidOperationException){SetStatus("Input queue full; retry");}
    }
    private void QueueResize()
    {
        var target=connection;var view=terminalView;if(target==null||view==null)return;
        int cols=view.Columns,rows=view.Rows,ticket=generation;
        // Resize only enqueues a command; preserve UI event order while the SSH writer
        // performs the actual network operation off-thread.
        if(ticket==generation)try{target.Resize(cols,rows);}catch(Exception){SetStatus("Display resize failed; reconnect if needed",ticket);}
    }
    private void Refresh()
    {
        terminalView?.ClearFrame();terminalView?.Paint();var target=connection;var view=terminalView;
        if(target==null||view==null)return;
        target.Refresh();
    }
    private bool fullScreen;
    private void ToggleFullScreen(){fullScreen=!fullScreen;Window.Default.SetFullScreen(fullScreen);Render();}
    private void KeyPressed(object? sender,Window.KeyEventArgs args)
    {
        string key=args.Key.KeyPressedName;bool pressed=args.Key.State==Key.StateType.Down;
        if(key is "Shift_L" or "Shift_R"){shiftPressed=pressed;return;}
        if(key is "Control_L" or "Control_R"){controlPressed=pressed;return;}
        if(key is "Alt_L" or "Alt_R"){altPressed=pressed;return;}
        shiftPressed=args.Key.IsShiftModifier();controlPressed=args.Key.IsCtrlModifier();altPressed=args.Key.IsAltModifier();
        if(!pressed)return;
        if(IsShellNavigationKey(key)) {
            var direction=key switch {"Up"=>View.FocusDirection.Up,"Down"=>View.FocusDirection.Down,
                "Left"=>View.FocusDirection.Left,"Right"=>View.FocusDirection.Right,_=>View.FocusDirection.Clockwise};
            FocusManager.Instance.EnableFocusIndicator(true);FocusManager.Instance.MoveFocus(direction);return;
        }
        if(overlay!=null) {if(key is "Escape" or "XF86Back"){hostAnswer?.TrySetResult(false);hostAnswer=null;overlay=null;imeActive=false;Render();}return;}
        if(key=="XF86Back") {
            if(preview!=null)RetirePreview();else menu=!menu;Render();return;
        }
        if(imeActive || FocusManager.Instance.GetCurrentFocusView()!=terminalView)return;
        if(key is "Menu" or "XF86Menu" || (key=="F10" && shiftPressed)) {
            var point=terminalView!.ScreenPosition;ShowClipboardMenu(point.X+20,point.Y+20);return;
        }
        if(controlPressed && shiftPressed && key.Equals("c",StringComparison.OrdinalIgnoreCase)){CopySelection();return;}
        if(controlPressed && shiftPressed && key.Equals("v",StringComparison.OrdinalIgnoreCase)){PasteClipboard();return;}
        SendKey(key,args.Key.KeyString??"");
    }
    private bool IsShellNavigationKey(string key)=>key is "Up" or "Down" or "Left" or "Right" or "Tab"
        && FocusManager.Instance.GetCurrentFocusView()!=terminalView && !inputFields.Values.Any(field=>field.KeyInputFocus);
    private bool InterceptKey(object? sender,Window.KeyEventArgs args)
    {
        // System home/audio/power keys belong to the platform, even with terminal focus.
        string name=args.Key.KeyPressedName;
        if(name.StartsWith("XF86",StringComparison.Ordinal) && name is not ("XF86Back" or "XF86Menu"))return false;
        // Decide ownership before a focused UI button can move focus in its handler.
        bool own=IsShellNavigationKey(name) || name=="XF86Back" || (overlay==null && FocusManager.Instance.GetCurrentFocusView()==terminalView) ||
            (overlay!=null && args.Key.KeyPressedName is "Escape" or "XF86Back");
        KeyPressed(sender,args);return own;
    }
    private void RetireTerminalView()
    {
        var view=terminalView;terminalView=null;
        if(view!=null){root?.Remove(view);view.Dispose();}
    }
    protected override void OnPause(){terminalView?.Interaction.Cancel();shiftPressed=controlPressed=altPressed=controlLatch=altLatch=false;UpdateModifierButtons();base.OnPause();}
    protected override void OnResume(){base.OnResume();if(root!=null)DispatchUi(()=>{terminalView?.ClearFrame();terminalView?.Paint();QueueResize();});}
    protected override void OnTerminate()
    {
        terminated=true;FocusManager.Instance.ResetFocusFinderRootView();statusDispatcher?.Dispose();lifetime.Cancel();Disconnect();
        Window.Default.Resized-=WindowResized;Window.Default.InsetsChanged-=WindowResized;Window.Default.InterceptKeyEvent-=InterceptKey;
        terminal.Changed-=QueueFrame;RetireTerminalView();terminal.Dispose();
        if(root!=null){Window.Default.GetDefaultLayer().Remove(root);DisposeScene(root);root=null;}
        foreach(var scene in retiredScenes.ToArray())DisposeScene(scene);
        retiredScenes.Clear();inputFields.Clear();statusLabel=null;controlButton=altButton=null;
        base.OnTerminate();
    }
    private static void Main(string[] args)
    {
        using var app=new HexgateApplication();app.Run(args);
        // The NUI loop has returned. Join transport/frame cleanup without blocking its UI thread.
        try {Task.WhenAll(app.connectionCleanup,app.frameTask,app.previewWork.CompleteAsync(),
            app.previewCleanup).GetAwaiter().GetResult();}
        catch(Exception){Tizen.Log.Warn("HEXGATE","Session shutdown reported a cleanup failure");}
        finally {app.lifetime.Dispose();}
    }
}
