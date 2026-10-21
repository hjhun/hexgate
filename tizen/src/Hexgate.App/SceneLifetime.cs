using Tizen.NUI.BaseComponents;
using Tizen.NUI.Components;

namespace Hexgate.App;

internal sealed partial class HexgateApplication
{
    // NUI View.Dispose detaches children but does not dispose their managed wrappers.
    // Track only controls we create; Button owns its own internal labels and visuals.
    // The retained WebView/context and terminal have separate, explicit lifetimes.
    private readonly Dictionary<ScrollableBase,View> scrollBodies=new(ReferenceEqualityComparer.Instance);
    private readonly HashSet<View> sceneViews=new(ReferenceEqualityComparer.Instance);
    private readonly HashSet<View> retiredScenes=new(ReferenceEqualityComparer.Instance);

    private T Own<T>(T view) where T:View
    {
        sceneViews.Add(view);
        return view;
    }

    private void RetireScene(View scene)
    {
        if(!retiredScenes.Add(scene))return;
        // A button may rebuild its own scene from inside a native signal. Disconnect
        // from the window now, then dispose wrappers after that callback returns.
        ui.Post(_=>{
            if(!retiredScenes.Remove(scene))return;
            DisposeScene(scene);
        },null);
    }

    private void DisposeScene(View scene)
    {
        if(!sceneViews.Remove(scene))return;
        // ScrollableBase owns its internal container; we own only the body added through it.
        if(scene is ScrollableBase scroll && scrollBodies.Remove(scroll,out var body)) {
            scroll.Remove(body);DisposeScene(body);
        }
        foreach(var child in scene.Children.ToArray()) {
            if(!sceneViews.Contains(child))continue;
            scene.Remove(child);
            DisposeScene(child);
        }
        scene.Dispose();
    }
}
