package dev.hexgate

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable fun HexgateTheme(themeId: String, content: @Composable () -> Unit) {
    val theme=AppThemes.resolve(themeId,androidx.compose.foundation.isSystemInDarkTheme())
    val bg=Color(theme.background);val fg=Color(theme.foreground);val accent=Color(theme.accent)
    val surface=Color(AppThemes.blend(theme.background,theme.foreground,if(theme.light) .02f else .04f))
    val variant=Color(AppThemes.blend(theme.background,theme.foreground,.10f))
    val muted=Color(AppThemes.blend(theme.foreground,theme.background,.18f))
    val border=if(theme.id=="amber") accent else Color(AppThemes.blend(theme.background,theme.foreground,.35f))
    val container=Color(AppThemes.blend(theme.background,theme.accent,.18f))
    val base=if(theme.light) lightColorScheme() else darkColorScheme()
    val scheme=base.copy(primary=accent,onPrimary=Color(AppThemes.ink(theme.accent)),primaryContainer=container,onPrimaryContainer=fg,
        secondary=accent,onSecondary=Color(AppThemes.ink(theme.accent)),secondaryContainer=container,onSecondaryContainer=fg,
        background=bg,onBackground=fg,surface=surface,onSurface=fg,surfaceVariant=variant,onSurfaceVariant=muted,
        outline=border,outlineVariant=Color(AppThemes.blend(theme.background,theme.accent,if(theme.id=="amber") .65f else .22f)),
        surfaceTint=Color.Transparent,surfaceContainer=surface,surfaceContainerLow=surface,surfaceContainerHigh=variant,
        surfaceContainerHighest=variant,surfaceContainerLowest=bg,surfaceBright=variant,surfaceDim=bg)
    CompositionLocalProvider(LocalAppTheme provides theme) {
        MaterialTheme(colorScheme=scheme, shapes=Shapes(small=RoundedCornerShape(8.dp),medium=RoundedCornerShape(12.dp),large=RoundedCornerShape(16.dp)),content=content)
    }
}

@Composable fun WorkspaceHeader(openMenu: () -> Unit, refresh: () -> Unit, fullScreen: Boolean, toggleFullScreen: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=60.dp).padding(horizontal=12.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
        Row(Modifier.heightIn(min=48.dp).clickable(onClick=openMenu).semantics { contentDescription="Open menu" },verticalAlignment=Alignment.CenterVertically) {
            Text("☰",fontSize=24.sp,color=MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Image(painterResource(R.drawable.hexgate_portal),contentDescription=null,Modifier.size(32.dp))
            Spacer(Modifier.width(10.dp))
            Text("HEXGATE",style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold,letterSpacing=1.5.sp)
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick=refresh, modifier=Modifier.semantics { contentDescription="Refresh terminal" }) {
            Icon(painterResource(R.drawable.ic_refresh), contentDescription=null, tint=MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick=toggleFullScreen, modifier=Modifier.semantics {
            contentDescription=if(fullScreen) "Exit full screen" else "Enter full screen"
        }) {
            Icon(painterResource(if(fullScreen) R.drawable.ic_fullscreen_exit else R.drawable.ic_fullscreen),
                contentDescription=null, tint=MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable fun WorkspacePanel(vm: TerminalModel, close: () -> Unit, select: (Profile) -> Unit,
    add: () -> Unit, manage: () -> Unit, preview: () -> Unit, settings: () -> Unit) {
    ModalDrawerSheet(Modifier.widthIn(max=320.dp).fillMaxHeight().imePadding()) {
        Column(Modifier.fillMaxHeight().padding(horizontal=12.dp,vertical=16.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("Workspace",Modifier.weight(1f).padding(start=12.dp),style=MaterialTheme.typography.titleLarge)
                TextButton(onClick=close) { Text("Close") }
            }
            Spacer(Modifier.height(16.dp))
            Text("CONNECTIONS",Modifier.padding(horizontal=16.dp,vertical=8.dp),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                if(vm.profiles.isEmpty()) Text("No saved connections",Modifier.padding(16.dp),style=MaterialTheme.typography.bodyMedium)
                vm.profiles.forEach { profile ->
                    NavigationDrawerItem(selected=profile.id==vm.currentProfileId,onClick={ select(profile) },label={
                        Column(Modifier.padding(vertical=8.dp)) {
                            Text(profile.name,maxLines=1,overflow=TextOverflow.Ellipsis)
                            Text("${profile.user}@${profile.host}:${profile.port}",style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                        }
                    })
                }
                TextButton(onClick=add,Modifier.fillMaxWidth()) { Text("Add connection") }
                TextButton(onClick=manage,Modifier.fillMaxWidth()) { Text("Manage connections") }
            }
            HorizontalDivider(Modifier.padding(vertical=8.dp))
            NavigationDrawerItem(label={ Text(if(vm.previewRequested) "Close preview" else "Preview") },selected=vm.previewRequested,onClick=preview)
            if(vm.connected || vm.connecting) NavigationDrawerItem(label={ Text("Disconnect") },selected=false,onClick={ vm.disconnect();close() })
            NavigationDrawerItem(label={ Text("Settings") },selected=false,onClick=settings)
        }
    }
}

@Composable fun ConnectionStrip(vm: TerminalModel) {
    val indicator = when { vm.connected -> MaterialTheme.colorScheme.secondary; vm.connecting -> MaterialTheme.colorScheme.primary; else -> MaterialTheme.colorScheme.onSurfaceVariant }
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).background(indicator, RoundedCornerShape(3.dp)))
        Spacer(Modifier.width(8.dp))
        Text(vm.status, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (vm.connected) Text("SSH", style = MaterialTheme.typography.labelSmall, color = indicator)
    }
}

@Composable private fun Accessory(label: String, selected: Boolean = false, action: () -> Unit) {
    Surface(onClick = action, modifier = Modifier.heightIn(min = 44.dp).widthIn(min = 44.dp),
        shape = RoundedCornerShape(7.dp), color = if(selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if(selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface) {
        Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) { Text(label, style = MaterialTheme.typography.labelLarge) }
    }
}

@Composable fun KeyboardBar(vm: TerminalModel) {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Accessory("Keyboard") { vm.requestKeyboard() }
        Accessory("Ctrl", vm.ctrl) { vm.ctrl = !vm.ctrl }
        Accessory("Alt", vm.alt) { vm.alt = !vm.alt }
        listOf("Esc" to android.view.KeyEvent.KEYCODE_ESCAPE, "Tab" to android.view.KeyEvent.KEYCODE_TAB,
            "Enter" to android.view.KeyEvent.KEYCODE_ENTER,
            "↑" to android.view.KeyEvent.KEYCODE_DPAD_UP, "↓" to android.view.KeyEvent.KEYCODE_DPAD_DOWN,
            "←" to android.view.KeyEvent.KEYCODE_DPAD_LEFT, "→" to android.view.KeyEvent.KEYCODE_DPAD_RIGHT).forEach { (label, code) -> Accessory(label) { vm.sendAccessory(code) } }
        Spacer(Modifier.width(4.dp))
        Accessory("Select", vm.selection) { vm.selection = !vm.selection }
        Accessory("Copy") { vm.copy() }; Accessory("Paste") { vm.paste() }
    }
}

@Composable fun WorkspaceEmptyState(connect: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Image(painterResource(R.drawable.hexgate_portal), contentDescription = null, Modifier.size(112.dp))
        Spacer(Modifier.height(20.dp))
        Text("Your workspace, through the gate.", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text("Connect to your PC over SSH.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Button(onClick = connect) { Text("Add a connection") }
    }
}

@Composable fun SettingsDialog(vm: TerminalModel, dismiss: () -> Unit) {
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val sampleFace by produceState<android.graphics.Typeface>(android.graphics.Typeface.MONOSPACE, vm.fontId) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { TerminalFonts.load(appContext, vm.fontId) }
    }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Settings") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("Connect to last session on launch",Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                Switch(checked=vm.autoConnect,onCheckedChange={ vm.updateAutoConnect(it) })
            }
            Text("Keep SSH active when the screen is off. Android may require a battery optimization exception.", style=MaterialTheme.typography.bodySmall)
            TextButton(onClick = {
                val power=appContext.getSystemService(android.os.PowerManager::class.java)
                val action=if(power.isIgnoringBatteryOptimizations(appContext.packageName))
                    android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                    else android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                val intent=android.content.Intent(action).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                if(action==android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    intent.data=android.net.Uri.parse("package:"+appContext.packageName)
                try { appContext.startActivity(intent) }
                catch (_: android.content.ActivityNotFoundException) {
                    appContext.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.parse("package:"+appContext.packageName)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }) { Text("Background connection permission") }
            Text("THEME",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Column {
                (listOf("system" to "Follow system")+AppThemes.presets.map { it.id to it.label }).forEach { (id,label) ->
                    Row(Modifier.fillMaxWidth().clickable { vm.updateTheme(id) }.padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
                        RadioButton(selected=vm.themeId==id,onClick=null);Spacer(Modifier.width(10.dp));Text(label)
                    }
                }
            }
            Text("Remote apps and documents may keep their own explicit colors.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text("FONT FAMILY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TerminalFonts.choices.forEach { font ->
                Surface(onClick = { vm.updateFont(font.id) }, shape = RoundedCornerShape(8.dp), color = if(vm.fontId == font.id) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = vm.fontId == font.id, onClick = null)
                        Spacer(Modifier.width(10.dp))
                        Column { Text(font.label); Text("Nerd Font Mono · OFL 1.1", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
            Surface(color = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(8.dp)) {
                Text("0O 1lI  ┌─┐  >_\n한글    ", Modifier.fillMaxWidth().padding(12.dp), fontFamily = androidx.compose.ui.text.font.FontFamily(sampleFace), fontSize = vm.fontSize.sp)
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Font size", Modifier.weight(1f)); TextButton(onClick = { vm.updateFontSize(vm.fontSize-1) }) { Text("A−") }
                Text("${vm.fontSize.toInt()} sp", style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = { vm.updateFontSize(vm.fontSize+1) }) { Text("A+") }
            }
            Text("All three families allow redistribution under their bundled licenses. Korean uses Android font fallback. Terminal ligatures are not enabled.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }, confirmButton = { TextButton(onClick = dismiss) { Text("Done") } })
}
