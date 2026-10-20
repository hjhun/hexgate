package dev.hexgate

import android.os.Bundle
import android.graphics.Typeface
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.view.TerminalView
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    lateinit var model: TerminalModel
    var fullScreen by mutableStateOf(false)
        private set

    fun toggleFullScreen() {
        fullScreen = !fullScreen
        applySystemBars()
    }
    private fun applySystemBars() {
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (fullScreen) hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            else show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applySystemBars()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("hexgate.fullScreen", fullScreen)
        super.onSaveInstanceState(outState)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fullScreen = savedInstanceState?.getBoolean("hexgate.fullScreen") ?: false
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        applySystemBars()
        model = (application as HexgateApplication).openWindow()
        setContent {
            val vm = model
            val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
            LaunchedEffect(vm.connecting, vm.connected) {
                if ((vm.connecting || vm.connected) && android.os.Build.VERSION.SDK_INT >= 33 &&
                    checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
                    !getPreferences(MODE_PRIVATE).getBoolean("notificationAsked", false)) {
                    getPreferences(MODE_PRIVATE).edit().putBoolean("notificationAsked", true).apply()
                    notifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            val skipAutoConnect=(applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0 && intent.getBooleanExtra("hexgate.skipAutoConnect",false)
            LaunchedEffect(vm) { if(!skipAutoConnect) vm.connectLastOnLaunch() }
            HexgateTheme(vm.themeId) {
                val light=LocalAppTheme.current.light
                SideEffect {
                    androidx.core.view.WindowCompat.getInsetsController(window,window.decorView).apply {
                        isAppearanceLightStatusBars=light
                        isAppearanceLightNavigationBars=light
                    }
                }
                Hexgate(vm, fullScreen, ::toggleFullScreen)
            }
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        (application as HexgateApplication).closeWindow(isChangingConfigurations)
    }
}

@Composable private fun Hexgate(vm: TerminalModel, fullScreen: Boolean, toggleFullScreen: () -> Unit) {
    var splitRatio by rememberSaveable { mutableFloatStateOf(0.6f) }
    var settingsOpen by remember { mutableStateOf(false) }
    var profilesOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Profile?>(null) }
    var passphrase by remember { mutableStateOf("") }
    val drawerState=rememberDrawerState(DrawerValue.Closed)
    val drawerScope=rememberCoroutineScope()
    fun fromPanel(action: () -> Unit) { drawerScope.launch { drawerState.close();action() } }
    ModalNavigationDrawer(drawerState=drawerState,gesturesEnabled=drawerState.isOpen,drawerContent={
        WorkspacePanel(vm,close={ drawerScope.launch { drawerState.close() } },
            select={ p -> fromPanel { vm.connectSaved(p) } },add={ fromPanel { editing=Profile() } },
            manage={ fromPanel { profilesOpen=true } },preview={ fromPanel { vm.togglePreview() } },
            settings={ fromPanel { settingsOpen=true } })
    }) {
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
            WorkspaceHeader(openMenu = { drawerScope.launch { drawerState.open() } }, refresh = vm::refreshTerminal, fullScreen = fullScreen, toggleFullScreen = toggleFullScreen)
            ConnectionStrip(vm)
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val wide = maxWidth >= 840.dp
                val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
                Row(Modifier.fillMaxSize()) {
                    if (wide || !vm.previewRequested) Box(Modifier.weight(if (wide && vm.previewRequested) splitRatio else 1f).fillMaxHeight()) {
                        TerminalPane(vm, Modifier.fillMaxSize())
                        if (!vm.connected && !vm.connecting && vm.profiles.isEmpty()) WorkspaceEmptyState { editing = Profile() }
                    }
                    if (vm.previewRequested) {
                        if (wide) Box(Modifier.width(12.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant).pointerInput(widthPx) {
                            detectDragGestures { change, delta -> change.consume(); splitRatio = (splitRatio + delta.x / widthPx).coerceIn(0.25f,0.75f) }
                        })
                        PreviewPane(vm, Modifier.weight(if (wide) 1f-splitRatio else 1f).fillMaxHeight())
                    }
                }
            }
            KeyboardBar(vm)
        }
    }
    }
    if (settingsOpen) SettingsDialog(vm) { settingsOpen = false }
    vm.keyPromptProfile?.let { p -> AlertDialog(onDismissRequest={ passphrase="";vm.answerKeyPassphrase(null) },title={ Text("Unlock SSH key") },text={
        Column { Text(p.name);OutlinedTextField(passphrase,{ passphrase=it },label={ Text("Key passphrase (memory only)") },visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation()) }
    },confirmButton={ TextButton(onClick={ vm.answerKeyPassphrase(passphrase);passphrase="" }) { Text("Connect") } },dismissButton={ TextButton(onClick={ passphrase="";vm.answerKeyPassphrase(null) }) { Text("Cancel") } }) }
    if (profilesOpen) AlertDialog(onDismissRequest = { profilesOpen = false }, title = { Text("SSH connections") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            vm.profiles.forEach { p ->
                Surface(Modifier.fillMaxWidth().padding(top = 12.dp), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.padding(14.dp)) {
                        Text(p.name, style = MaterialTheme.typography.titleMedium)
                        Text(p.user + "@" + p.host + ":" + p.port, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (p.herdrSession.isNotBlank()) Text("Herdr · " + p.herdrSession, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Button(enabled = !vm.connecting, onClick = { vm.connectSaved(p); passphrase = ""; profilesOpen = false }) { Text("Connect") }
                            TextButton(onClick = { editing = p; profilesOpen = false }) { Text("Edit") }
                            TextButton(onClick = { vm.delete(p) }) { Text("Delete") }
                        }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { editing = Profile(); profilesOpen = false }) { Text("Add connection") } }, dismissButton = { TextButton(onClick = { profilesOpen = false }) { Text("Close") } })
    editing?.let { p -> ProfileEditor(p, { editing = null }) { saved, key, password -> vm.save(saved, key, password); editing = null; drawerScope.launch { drawerState.open() } } }
    vm.challenge?.let { c -> AlertDialog(onDismissRequest = { vm.answerHost(false) }, title = { Text(if (c.previous == null) "Verify server identity" else "Server identity CHANGED") }, text = {
        Column { Text(c.profile.host + ":" + c.profile.port); c.previous?.let { Text("Previously trusted: $it") }; Text("New fingerprint: " + c.fingerprint); Text("Compare with the fingerprint on your PC before trusting.") }
    }, confirmButton = { TextButton(onClick = { vm.answerHost(true) }) { Text(if (c.previous == null) "Trust" else "Replace trusted key") } }, dismissButton = { TextButton(onClick = { vm.answerHost(false) }) { Text("Reject") } }) }
}

@Composable private fun TerminalPane(vm: TerminalModel, modifier: Modifier) {
    val appContext = LocalContext.current.applicationContext
    val visualTheme = LocalAppTheme.current
    val face by produceState<Typeface>(Typeface.MONOSPACE, vm.fontId) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { TerminalFonts.load(appContext, vm.fontId) }
    }
    AndroidView(modifier = modifier, factory = { context ->
        TerminalView(context, null).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setTerminalViewClient(vm.viewClient(this))
            setOnCreateContextMenuListener { menu, _, _ ->
                // Snapshot before opening the menu changes focus/selection toolbar state.
                val selected = selectedText?.takeIf { it.isNotEmpty() }
                    ?: storedSelectedText?.takeIf { it.isNotEmpty() }
                menu.add("복사").apply {
                    isEnabled = selected != null
                    setOnMenuItemClickListener { vm.copy(selected); true }
                }
                menu.add("붙여넣기").setOnMenuItemClickListener {
                    stopTextSelectionMode(); vm.paste(); true
                }
            }
            setTextSize((vm.fontSize * resources.displayMetrics.scaledDensity).toInt())
            setTypeface(face)
            tag = face
            vm.attach(this)
            requestFocus()
        }
    }, update = { v ->
        vm.applyTerminalTheme(visualTheme)
        if (v.tag !== face) { v.setTypeface(face); v.tag = face }
        v.setTextSize((vm.fontSize * v.resources.displayMetrics.scaledDensity).toInt())
        v.mMouseReportingEnabled = !vm.selection
        v.mEmulator?.let { if (it.isAutoScrollDisabled != vm.selection) it.toggleAutoScrollDisabled() }
    }, onRelease = { v -> vm.detach(v) })
    LaunchedEffect(vm.keyboardRequest) {
        if (vm.keyboardRequest > 0) vm.showSoftKeyboard()
    }
}

@Composable private fun ProfileEditor(original: Profile, dismiss: () -> Unit, save: (Profile, String, String?) -> Unit) {
    var name by remember { mutableStateOf(original.name) }
    var host by remember { mutableStateOf(original.host) }
    var port by remember { mutableStateOf(original.port.toString()) }
    var user by remember { mutableStateOf(original.user) }
    var command by remember { mutableStateOf(original.command) }
    var herdrSession by remember { mutableStateOf(original.herdrSession) }
    var key by remember { mutableStateOf("") }
    var authMethod by remember { mutableStateOf(original.authMethod) }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            importing = true
            try {
                key = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    requireNotNull(context.contentResolver.openInputStream(uri)).use { SshConnection.readBounded(it,128*1024).toString(Charsets.UTF_8) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { error = "Cannot import key (limit 128 KiB)" }
            finally { importing = false }
        }
    }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Connection profile") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Name") })
            OutlinedTextField(host, { host = it }, label = { Text("Host / DDNS") }, singleLine = true)
            OutlinedTextField(port, { port = it }, label = { Text("SSH port") }, singleLine = true)
            OutlinedTextField(user, { user = it }, label = { Text("Ubuntu user") }, singleLine = true)
            Text("Authentication", style = MaterialTheme.typography.labelLarge)
            Row {
                FilterChip(selected = authMethod == AuthMethod.PRIVATE_KEY,
                    onClick = { authMethod = AuthMethod.PRIVATE_KEY; password = "" }, label = { Text("Private key") })
                Spacer(Modifier.width(8.dp))
                FilterChip(selected = authMethod == AuthMethod.PASSWORD,
                    onClick = { authMethod = AuthMethod.PASSWORD }, label = { Text("Password") })
            }
            if (authMethod == AuthMethod.PASSWORD) {
                OutlinedTextField(password, { password = it }, label = { Text("SSH password") },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Password))
                Text("Stored encrypted with Android Keystore protection. Leave empty to keep a saved password.")
            } else {
                TextButton(enabled = !importing, onClick = { picker.launch(arrayOf("*/*")) }) { Text(if (importing) "Importing key…" else if (key.isBlank()) "Import private key" else "Private key imported") }
                Text("Leave key unchanged when editing. Key passphrases are requested at connection time.")
            }
            OutlinedTextField(herdrSession, { herdrSession = it }, label = { Text("Herdr session (optional)") }, singleLine = true)
            OutlinedTextField(command, { command = it }, label = { Text("Startup command (optional)") })
            Text("A Herdr session takes precedence over the startup command and reattaches on each connection.")
            if (error.isNotBlank()) Text(error)
        }
    }, confirmButton = { TextButton(enabled = !importing, onClick = {
        if (host.isBlank() || user.isBlank() || port.toIntOrNull() !in 1..65535 || (herdrSession.isNotBlank() && !herdrSession.matches(Regex("[A-Za-z0-9_-]{1,80}")))) error = "Enter host, user and a valid port" else try { save(original.copy(name = name, host = host.trim(), port = port.toInt(), user = user.trim(), command = command, herdrSession = herdrSession, authMethod = authMethod), key,
            password.takeIf { authMethod == AuthMethod.PASSWORD && it.isNotEmpty() });password = "" } catch (_: Exception) { error = "Could not store credentials. Enter a password for password authentication." }
    }) { Text("Save") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

@Composable private fun PreviewPane(vm: TerminalModel, modifier: Modifier) {
    val visualTheme=LocalAppTheme.current
    Surface(modifier) {
        Column {
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp)) {
                TextButton(onClick = { vm.browse(vm.browserPath.ifBlank { null }) }) { Text("Files") }
                TextButton(onClick = { vm.browse() }) { Text("Home") }
                TextButton(onClick = { vm.previewDocument?.let { vm.openDocument(it.path) } }) { Text("Refresh") }
                TextButton(onClick = { vm.previewRequested = false }) { Text("Close preview") }
            }
            vm.selectedPane?.let { pane -> Text("Pane ${pane.id} · ${pane.cwd}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp)) }
            if (vm.paneChoices.isNotEmpty()) {
                Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                    vm.paneChoices.forEach { pane -> TextButton(onClick = { vm.choosePane(pane) }) { Text("${pane.id} · ${pane.cwd}") } }
                }
            }
            if (vm.previewBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (vm.previewError.isNotBlank()) Text(vm.previewError, Modifier.padding(12.dp))
            val doc = vm.previewDocument
            val remote = vm.remoteFiles
            if (doc != null && remote != null) {
                Text(doc.path, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(8.dp))
                key(doc.kind == "html",visualTheme.id) {
                    AndroidView(modifier = Modifier.weight(1f).fillMaxWidth(), factory = { context -> DocumentPreview(context, doc.kind != "html",visualTheme) },
                        update = { v -> v.onOpen = { vm.openDocument(it) }; v.show(doc, remote) },
                        onRelease = { v -> v.suspendPreview(); v.destroy() })
                }
            } else {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp)) {
                    Text(vm.browserPath.ifBlank { "Connect to browse remote files" })
                    TextButton(onClick = { vm.browse(vm.browserPath.substringBeforeLast('/').ifBlank { "/" }) }) { Text("Parent directory") }
                    if (vm.recentFiles.isNotEmpty()) {
                        Text("Recent documents")
                        vm.recentFiles.forEach { path -> TextButton(onClick = { vm.openDocument(path) }) { Text(path) } }
                    }
                    vm.entries.forEach { entry ->
                        TextButton(onClick = { if (entry.directory) vm.browse(entry.path) else vm.openDocument(entry.path) }) { Text((if (entry.directory) "▸ " else "  ") + entry.name) }
                    }
                }
            }
        }
    }
}
