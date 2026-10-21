using Hexgate.Core;
using Tizen.NUI;
using Tizen.NUI.BaseComponents;
using Tizen.NUI.Components;
using Path=System.IO.Path;

namespace Hexgate.App;

internal sealed partial class HexgateApplication
{
    private ScrollableBase? modalScroll;
    private float modalOffset;
    private string? modalTitle;
    private void CaptureModalScroll(){if(modalScroll!=null)modalOffset=modalScroll.ScrollPosition.Y;modalScroll=null;}
    private void ResetModalScroll(){modalTitle=null;modalOffset=0;}
    private ScrollableBase ScrollSurface(View parent,View body,float x,float y,float w,float h,ScrollableBase.Direction direction)
    {
        var scroll=Own(new ScrollableBase {Position=new Position(x,y),Size=new Size(w,h),ScrollingDirection=direction,
            Layout=new LinearLayout {LinearOrientation=direction==ScrollableBase.Direction.Vertical?LinearLayout.Orientation.Vertical:LinearLayout.Orientation.Horizontal}});
        scroll.Add(body);scrollBodies.Add(scroll,body);parent.Add(scroll);return scroll;
    }
    private View Modal(View parent,string title)
    {
        if(modalTitle!=title){modalTitle=title;modalOffset=0;}
        var shade=Own(new View {Size=new Size(width,height),BackgroundColor=new Color(0,0,0,.7f)});parent.Add(shade);
        shade.TouchEvent+=(_,_)=>true;
        float w=viewport.ModalWidth,h=viewport.ModalHeight;
        var body=Own(new View {Size=new Size(w,850),BackgroundColor=Background});
        var scroll=ScrollSurface(shade,body,Math.Max(0,(width-w)/2),Math.Max(0,(height-h)/2),w,h,ScrollableBase.Direction.Vertical);
        modalScroll=scroll;scroll.Focusable=true;int epoch=renderEpoch;bool restored=false;
        body.Relayout+=(_,_)=>{
            if(restored)return;restored=true;
            DispatchUi(()=>{if(epoch==renderEpoch && modalScroll==scroll)scroll.ScrollTo(Math.Clamp(modalOffset,0,Math.Max(0,body.Size.Height-h)),false);});
        };
        AddLabel(body,title,24,16,InnerWidth(body),44,w<600?22:27);return body;
    }
    private static float InnerWidth(View parent)=>Math.Max(1,Math.Min(760,parent.Size.Width-48));
    private void RevealModalField(TextField field)
    {
        var scroll=modalScroll;if(scroll==null)return;int epoch=renderEpoch;
        DispatchUi(()=>{
            if(epoch!=renderEpoch || modalScroll!=scroll)return;
            float y=field.Position.Y,bottom=y+field.Size.Height,position=scroll.ScrollPosition.Y;
            if(y<position)scroll.ScrollTo(Math.Max(0,y-12),false);
            else if(bottom>position+scroll.Size.Height)scroll.ScrollTo(bottom-scroll.Size.Height+12,false);
        });
    }
    private TextField Input(View parent,string label,string value,float y,Action<string> update,bool hidden=false,float x=24,float? w=null)
    {
        float fieldWidth=Math.Max(1,Math.Min(w??InnerWidth(parent),parent.Size.Width-x-24));
        AddLabel(parent,label,x,y,fieldWidth,30,18);
        var field=Own(new TextField {Text=value,PlaceholderText=label,PixelSize=22,Position=new Position(x,y+32),Size=new Size(fieldWidth,44),
            Focusable=true,VerticalAlignment=VerticalAlignment.Center,
            Padding=new Extents(12,12,0,0),CornerRadius=8,
            BorderlineWidth=1,BorderlineColor=Theme.Id=="amber"?Accent:Blend(.35f),
            BackgroundColor=SurfaceVariant,TextColor=Foreground,PlaceholderTextColor=Blend(.82f)});
        var inputMethod=new InputMethod {AutoCapital=InputMethod.AutoCapitalType.None,
            PanelLayout=hidden?InputMethod.PanelLayoutType.Password:InputMethod.PanelLayoutType.Normal};
        field.InputMethodSettings=inputMethod.OutputMap;field.GetInputMethodContext().TextPrediction=false;
        if(hidden) {
            var map=new PropertyMap();map.Add(HiddenInputProperty.Mode,new PropertyValue((int)HiddenInputModeType.HideAll));
            map.Add(HiddenInputProperty.SubstituteCharacter,new PropertyValue(0x2022));field.HiddenInputSettings=map;
        }
        int epoch=renderEpoch;field.TextChanged+=(_,_)=>{if(epoch==renderEpoch)update(field.Text);};
        inputFields[label]=field;parent.Add(field);return field;
    }
    private float Actions(View body,float y,params (string Label,Action Work)[] entries)
    {
        float inner=InnerWidth(body);int columns=Math.Max(1,Math.Min(entries.Length,(int)((inner+16)/236)));
        float w=(inner-16*(columns-1))/columns;
        for(int i=0;i<entries.Length;i++)AddButton(body,entries[i].Label,24+(i%columns)*(w+16),y+(i/columns)*60,w,48,entries[i].Work);
        float end=y+MathF.Ceiling((float)entries.Length/columns)*60;
        body.Size=new Size(body.Size.Width,Math.Max(body.Size.Height,end+24));return end;
    }
    private void ShowEditor(ConnectionProfile original)
    {
        var draft=original;string credential="",keyPath="",error="",port=original.Port.ToString();
        void Close(){credential="";overlay=null;Render();}
        overlay=parent=> {
            var modal=Modal(parent,"Connection settings");float inner=InnerWidth(modal),shift=0;
            Input(modal,"Name",draft.Name,72,s=>draft=draft with{Name=s});
            if(inner>=600) {
                Input(modal,"Host / DDNS",draft.Host,158,s=>draft=draft with{Host=s},w:inner-224);
                Input(modal,"SSH port",port,158,s=>port=s,x:24+inner-204,w:204);
            } else {
                Input(modal,"Host / DDNS",draft.Host,158,s=>draft=draft with{Host=s});
                Input(modal,"SSH port",port,244,s=>port=s);shift+=86;
            }
            Input(modal,"SSH username",draft.User,244+shift,s=>draft=draft with{User=s});
            float choiceWidth=Math.Min(220,(inner-16)/2);
            AddButton(modal,draft.Authentication==AuthenticationMethod.Password?"● Password":"Password",24,330+shift,choiceWidth,44,()=>{
                draft=draft with {Authentication=AuthenticationMethod.Password};credential="";Render();});
            AddButton(modal,draft.Authentication==AuthenticationMethod.PrivateKey?"● Private key":"Private key",40+choiceWidth,330+shift,choiceWidth,44,()=>{
                draft=draft with {Authentication=AuthenticationMethod.PrivateKey};credential="";Render();});
            if(draft.Authentication==AuthenticationMethod.Password)
                Input(modal,"SSH password (blank keeps saved value)",credential,388+shift,s=>credential=s,true);
            else {
                Input(modal,"Private-key file path (blank keeps saved key)",keyPath,388+shift,s=>keyPath=s,w:inner>=600?inner-220:inner);
                float importY=inner>=600?420+shift:476+shift;
                AddButton(modal,"Import key",inner>=600?24+inner-204:24,importY,Math.Min(204,inner),44,()=>{
                    try {
                        var path=Path.IsPathRooted(keyPath)?keyPath:Path.Combine(Tizen.Applications.Application.Current.DirectoryInfo.Data,keyPath);
                        using var stream=File.OpenRead(path);if(stream.Length>128*1024)throw new InvalidDataException();
                        using var reader=new StreamReader(stream);credential=reader.ReadToEnd();error="Private key imported; press Save.";
                    } catch(FileNotFoundException){error="Private-key file not found. Check the path.";}
                    catch(UnauthorizedAccessException){error="Private-key file access denied. Choose a readable file.";}
                    catch(InvalidDataException){error="Private key exceeds 128 KiB.";}
                    catch(Exception){error="Cannot read private key. Check the file and permissions.";}Render();
                });
                if(inner<600)shift+=60;
            }
            Input(modal,"Herdr session (optional)",draft.HerdrSession,476+shift,s=>draft=draft with {HerdrSession=s});
            Input(modal,"Startup command (optional)",draft.StartupCommand,562+shift,s=>draft=draft with {StartupCommand=s});
            AddLabel(modal,error,24,650+shift,inner,72,18).MultiLine=true;
            var actions=new List<(string,Action)> {
                ("Save",()=>{
                    try {
                        if(!int.TryParse(port,out var parsed))throw new ArgumentException();
                        profiles.Save(draft with {Host=draft.Host.Trim(),User=draft.User.Trim(),Port=parsed},credential.Length==0?null:credential);
                        status=CredentialCleanupStatus("Connection saved");Close();
                    } catch(Exception){error="Check fields and enter a credential. Secure storage must be available.";Render();}
                }), ("Cancel",Close)
            };
            if(profiles.Profiles.Any(p=>p.Id==original.Id))actions.Add(("Delete",()=>{
                overlay=owner=>{
                    var confirm=Modal(owner,"Delete this connection?");AddLabel(confirm,original.Name,24,90,InnerWidth(confirm),60);
                    Actions(confirm,200,("Delete credentials",()=>{try{profiles.Delete(original.Id);status=CredentialCleanupStatus("Connection deleted");Close();}catch(Exception){status="Credential deletion failed; retry";Close();}}),("Keep connection",Close));
                };Render();
            }));
            Actions(modal,730+shift,actions.ToArray());
        };Render();
    }
    private string CredentialCleanupStatus(string success)=>profiles.PendingCredentialCleanup==0
        ? success : "Secure credential cleanup pending; retry in Settings.";
    private void ShowPassphrase(ConnectionProfile profile)
    {
        string passphrase="";
        overlay=parent=>{
            var modal=Modal(parent,"Unlock private key");
            Input(modal,"Key passphrase (blank if unencrypted)",passphrase,90,s=>passphrase=s,true);
            Actions(modal,210,("Connect",()=>{var value=passphrase;passphrase="";overlay=null;_=Connect(profile,value);}),
                ("Cancel",()=>{passphrase="";overlay=null;Render();}));
        };Render();
    }
    private void ShowSettings()
    {
        overlay=parent=>{
            var modal=Modal(parent,"Settings");float inner=InnerWidth(modal);AddLabel(modal,"Theme",24,90,inner,40);
            int columns=Math.Max(1,Math.Min(3,(int)((inner+16)/250))),index=0;float w=(inner-16*(columns-1))/columns;
            foreach(var theme in TerminalThemes.Presets) {
                var chosen=theme;AddButton(modal,(Theme.Id==chosen.Id?"● ":"")+chosen.Label,24+(index%columns)*(w+16),145+(index/columns)*58,w,48,
                    ()=>{profiles.Preferences(profiles.AutoConnect,chosen.Id,profiles.FontSize);Render();});index++;
            }
            float shift=(MathF.Ceiling((float)index/columns)-2)*58;
            AddLabel(modal,"Terminal font size: "+profiles.FontSize,24,270+shift,inner,40);
            float half=(inner-16)/2;
            AddButton(modal,"Smaller",24,330+shift,half,48,()=>{profiles.Preferences(profiles.AutoConnect,profiles.Theme,Math.Max(12,profiles.FontSize-2));Render();});
            AddButton(modal,"Larger",40+half,330+shift,half,48,()=>{profiles.Preferences(profiles.AutoConnect,profiles.Theme,Math.Min(36,profiles.FontSize+2));Render();});
            AddButton(modal,profiles.AutoConnect?"Auto-connect: on":"Auto-connect: off",24,430+shift,inner,48,()=>{profiles.Preferences(!profiles.AutoConnect,profiles.Theme,profiles.FontSize);Render();});
            AddLabel(modal,profiles.PendingCredentialCleanup==0
                ? "Credentials use the Tizen secure repository.\nNo plaintext fallback is permitted."
                : "Secure credential cleanup pending: "+profiles.PendingCredentialCleanup+".\nRetry cleanup; it also retries on the next launch.",24,510+shift,inner,inner<400?130:90).MultiLine=true;
            var actions=new List<(string,Action)> {("Done",()=>{overlay=null;Render();}),("Close Hexgate",Exit)};
            if(profiles.PendingCredentialCleanup!=0)actions.Add(("Retry credential cleanup",()=>{profiles.RetryCredentialCleanup();status=CredentialCleanupStatus("Credential cleanup complete");Render();}));
            Actions(modal,650+shift,actions.ToArray());
        };Render();
    }
    private void ShowKeyboard()
    {
        string text="";
        overlay=parent=>{
            var modal=Modal(parent,"Terminal input");var field=Input(modal,"Text to send",text,90,s=>text=s);
            Actions(modal,220,("Send text",()=>{Send(text);overlay=null;imeActive=false;Render();}),
                ("Send + Enter",()=>{Send(text+"\r");overlay=null;imeActive=false;Render();}),
                ("Cancel",()=>{overlay=null;imeActive=false;Render();}));
            field.KeyInputFocus=true;RevealModalField(field);
        };Render();
    }
}
