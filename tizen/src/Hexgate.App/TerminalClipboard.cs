using Hexgate.Core;
using System.Text;
using Tizen.NUI;
using Tizen.NUI.BaseComponents;
using Tizen.NUI.Components;

namespace Hexgate.App;

internal sealed partial class HexgateApplication
{
    private bool shiftPressed,controlPressed,altPressed,controlLatch,altLatch;
    private Button? controlButton,altButton;
    private Action<View>? clipboardOverlay;
    private const string ClipboardMime="text/plain;charset=utf-8";
    private void FocusTerminal(){imeActive=false;if(terminalView!=null)FocusManager.Instance.SetCurrentFocusView(terminalView);}
    private void WriteClipboard(string text)
    {
        if(text.Length==0){SetStatus("Select terminal text first");return;}
        if(Encoding.UTF8.GetByteCount(text)>TerminalInteraction.ClipboardLimit){SetStatus("Clipboard exceeds 1 MiB");return;}
        try {if(!Clipboard.Instance.SetData(ClipboardMime,text))SetStatus("System clipboard is unavailable");else SetStatus("Selection copied");}
        catch(Exception){SetStatus("System clipboard is unavailable");}
    }
    private void CopySelection(){WriteClipboard(terminalView?.Interaction.SelectedText()??"");FocusTerminal();}
    private void PasteClipboard()
    {
        int ticket=generation;var view=terminalView;var target=connection;
        if(view==null || target?.Connected!=true)return;
        try {
            Clipboard.Instance.GetData(ClipboardMime,(success,clip)=>{
                string value=success?clip.Data??"":"";
                DispatchUi(()=>{
                if(ticket!=generation || view!=terminalView)return;
                if(!success){SetStatus("No text is available in the system clipboard");return;}
                try {byte[] data=view.Interaction.Paste(value);view.Interaction.Cancel();target.Send(data);FocusTerminal();}
                catch(Exception){SetStatus("Paste unavailable or exceeds the 1 MiB input limit");}
            });});
        } catch(Exception){SetStatus("System clipboard is unavailable");}
    }
    private void ShowClipboardMenu(float x,float y)
    {
        if(root==null || chromeRoot==null || overlay!=null)return;int epoch=renderEpoch;
        (x,y)=viewport.ToLogicalScreen(x,y);
        overlay=parent=>{
            var shade=Own(new View{Size=new Size(width,height),BackgroundColor=Color.Transparent});parent.Add(shade);
            bool closed=false;
            void Close(){if(closed || epoch!=renderEpoch)return;closed=true;parent.Remove(shade);RetireScene(shade);overlay=null;clipboardOverlay=null;FocusTerminal();}
            var panel=Own(new View{Position=new Position(Math.Clamp(x,8,Math.Max(8,width-260)),Math.Clamp(y,8,Math.Max(8,height-180))),Size=new Size(252,164),BackgroundColor=Background});shade.Add(panel);
            AddButton(panel,"Copy selection",8,8,236,44,()=>{CopySelection();Close();});
            AddButton(panel,"Paste",8,60,236,44,()=>{PasteClipboard();Close();});
            AddButton(panel,"Close",8,112,236,44,Close);
            shade.TouchEvent+=(_,args)=>{if(args.Touch.GetPointCount()>0 && args.Touch.GetState(0)==PointStateType.Down && args.Touch.GetHitView(0)==shade)Close();return true;};
        };
        clipboardOverlay=overlay;overlay(chromeRoot);
    }
    private int AccessoryRows(float available)=>1;
    private void RenderTerminalKeys(View parent,float left)
    {
        float available=Math.Max(1,width-left-16),w=80;
        var body=Own(new View {Size=new Size(13*(w+6)-6,44)});
        ScrollSurface(parent,body,left,height-66,available,44,Tizen.NUI.Components.ScrollableBase.Direction.Horizontal);
        var entries=new (string Label,Action Action)[]{
            ("Ctrl",()=>{controlLatch=!controlLatch;UpdateModifierButtons();FocusTerminal();}),
            ("Alt",()=>{altLatch=!altLatch;UpdateModifierButtons();FocusTerminal();}),
            ("Esc",()=>SendKey("Escape","")),("Tab",()=>SendKey("Tab","")),("Enter",()=>SendKey("Return","")),
            ("↑",()=>SendKey("Up","")),("↓",()=>SendKey("Down","")),("←",()=>SendKey("Left","")),("→",()=>SendKey("Right","")),
            ("Copy",CopySelection),("Paste",PasteClipboard),
            ("Select",()=>{if(terminalView!=null){terminalView.Interaction.Cancel();terminalView.Interaction.LocalSelectionMode=!terminalView.Interaction.LocalSelectionMode;SetStatus(terminalView.Interaction.LocalSelectionMode?"Local selection mode":"Remote mouse mode");}FocusTerminal();}),
            ("Keyboard",()=>{imeActive=true;ShowKeyboard();})
        };
        for(int i=0;i<entries.Length;i++) {
            var item=entries[i];var button=AddButton(body,item.Label,i*(w+6),0,w,44,item.Action);button.TextLabel.PixelSize=16;
            if(i==0)controlButton=button;if(i==1)altButton=button;
        }
        UpdateModifierButtons();
    }
    private void UpdateModifierButtons()
    {
        if(controlButton!=null)controlButton.BackgroundColor=controlLatch?Blend(.30f):SurfaceVariant;
        if(altButton!=null)altButton.BackgroundColor=altLatch?Blend(.30f):SurfaceVariant;
    }
    private void SendKey(string name,string text)
    {
        terminalView?.Interaction.Cancel();
        Send(TerminalKeys.Encode(name,text,controlPressed||controlLatch,altPressed||altLatch,shiftPressed,terminal.Engine.ApplicationCursor));
        controlLatch=altLatch=false;UpdateModifierButtons();FocusTerminal();
    }
}
