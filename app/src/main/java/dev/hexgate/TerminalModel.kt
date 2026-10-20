package dev.hexgate

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import java.lang.ref.WeakReference
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class HostChallenge(val profile: Profile, val previous: String?, val fingerprint: String, val answer: CompletableFuture<Boolean>)

class TerminalModel(app: Application) : AndroidViewModel(app) {
    val store = ProfileStore(app)
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val remoteWorker = Executors.newFixedThreadPool(2)
    private var connection = SshConnection()
    @Volatile private var connectTicket = 0
    private var activeProfile: Profile? = store.lastProfile()
    private var view = WeakReference<TerminalView>(null)
    private val selectionClipboard = SelectionClipboardGrant()
    var profiles by mutableStateOf(store.profiles()); private set
    var status by mutableStateOf("Disconnected"); private set
    var connected by mutableStateOf(false); private set
    var connecting by mutableStateOf(false); private set
    var challenge by mutableStateOf<HostChallenge?>(null); private set
    var ctrl by mutableStateOf(false)
    var alt by mutableStateOf(false)
    var selection by mutableStateOf(false)
    var autoConnect by mutableStateOf(store.autoConnect()); private set
    fun updateAutoConnect(enabled: Boolean) { autoConnect=enabled;store.saveAutoConnect(enabled) }
    var themeId by mutableStateOf(store.themeId()); private set
    fun updateTheme(id: String) { themeId=AppThemes.validId(id);store.saveThemeId(themeId) }
    var keyPromptProfile by mutableStateOf<Profile?>(null); private set
    private var initialConnectionHandled = false
    private var profileRequestTicket = 0
    val currentProfileId: String? get() = activeProfile?.id
    fun connectLastOnLaunch() {
        if (initialConnectionHandled) return
        initialConnectionHandled=true
        if (autoConnect) store.lastProfile()?.let { connectSaved(it) }
    }
    fun connectSaved(profile: Profile, passphrase: String? = null) {
        if (connected && activeProfile?.id==profile.id) return
        if (connecting) disconnect()
        if (profile.authMethod == AuthMethod.PASSWORD || passphrase != null) {
            disconnect();connect(profile,passphrase.orEmpty());return
        }
        val request=++profileRequestTicket
        val version=connectTicket
        remoteWorker.execute {
            try {
                val encrypted=SshConnection.keyNeedsPassphrase(store.privateKey(profile))
                main.post {
                    if (request==profileRequestTicket && version==connectTicket) {
                        if(encrypted) keyPromptProfile=profile else connectSaved(profile,"")
                    }
                }
            } catch (_: Exception) {
                main.post { if(request==profileRequestTicket && version==connectTicket) status="Cannot read saved key; edit the profile and import it again." }
            }
        }
    }
    fun answerKeyPassphrase(passphrase: String?) {
        val profile=keyPromptProfile ?: return
        keyPromptProfile=null
        if(passphrase!=null) connectSaved(profile,passphrase)
    }
    var fontId by mutableStateOf(store.fontId()); private set
    fun updateFont(id: String) { fontId = TerminalFonts.resolve(id).id; store.saveFontId(fontId) }
    var fontSize by androidx.compose.runtime.mutableFloatStateOf(store.fontSize()); private set
    fun updateFontSize(size: Float) { fontSize = size.coerceIn(8f,28f); store.saveFontSize(fontSize) }
    var previewRequested by mutableStateOf(false)
    var entries by mutableStateOf<List<RemoteEntry>>(emptyList()); private set
    var browserPath by mutableStateOf(""); private set
    var previewDocument by mutableStateOf<RemoteDocument?>(null); private set
    var previewError by mutableStateOf(""); private set
    var previewBusy by mutableStateOf(false); private set
    var remoteFiles: RemoteFiles? = null; private set
    var recentFiles by mutableStateOf<List<String>>(emptyList()); private set
    private var previewTicket = 0
    private var remoteTty = ""
    var paneChoices by mutableStateOf<List<HerdrPane>>(emptyList()); private set
    var selectedPane by mutableStateOf<HerdrPane?>(null); private set
    var terminal: TerminalSession = makeTerminal(); private set
    private fun makeTerminal(target: SshConnection = connection): TerminalSession = TerminalSession(object : TerminalSession.Transport {
        override fun send(data: ByteArray) { try { target.send(data) } catch (_: Exception) { status = "Input queue full; retry" } }
        override fun resize(columns: Int, rows: Int) { try { target.resize(columns, rows) } catch (_: Exception) { status = "Resize queue full" } }
    }, object : QuietSessionClient() {
        override fun onTitleChanged(session: TerminalSession) {
            val title = session.emulator.title.orEmpty()
            if (title.startsWith("hexgate-tty:")) {
                val tty = title.removePrefix("hexgate-tty:")
                if (tty.matches(Regex("/dev/pts/[0-9]+"))) remoteTty = tty
            }
        }
        override fun onTextChanged(session: TerminalSession) { view.get()?.onScreenUpdated() }
        override fun onColorsChanged(session: TerminalSession) { view.get()?.invalidate() }
        override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
            if (session === terminal && selectionClipboard.consume(session, android.os.SystemClock.uptimeMillis(), text)) copy(text)
        }
    })
    private var themedSession: TerminalSession? = null
    private var terminalThemeId = ""
    fun applyTerminalTheme(theme: AppTheme) {
        if(themedSession===terminal && terminalThemeId==theme.id) return
        val properties=java.util.Properties().apply {
            setProperty("foreground",theme.css(theme.foreground));setProperty("background",theme.css(theme.background));setProperty("cursor",theme.css(theme.foreground))
            theme.ansi.forEachIndexed { i,color -> setProperty("color$i",theme.css(color)) }
        }
        com.termux.terminal.TerminalColors.COLOR_SCHEME.updateWith(properties)
        terminal.emulator.mColors.reset()
        themedSession=terminal;terminalThemeId=theme.id
        view.get()?.onScreenUpdated()
    }
    var keyboardRequest by androidx.compose.runtime.mutableIntStateOf(0); private set
    fun requestKeyboard() {
        selection = false
        if (view.get() == null) previewRequested = false
        keyboardRequest++
    }
    fun showSoftKeyboard(target: TerminalView? = view.get()) {
        val terminalView = target ?: return
        terminalView.requestFocus()
        terminalView.post {
            if (terminalView.isAttachedToWindow && terminalView.hasFocus()) {
                val input = terminalView.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                input.showSoftInput(terminalView, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                androidx.core.view.ViewCompat.getWindowInsetsController(terminalView)
                    ?.show(androidx.core.view.WindowInsetsCompat.Type.ime())
            }
        }
    }
    fun attach(v: TerminalView) {
        view.get()?.takeIf { it !== v }?.detachSession()
        view = WeakReference(v)
        v.attachSession(terminal)
    }
    fun detach(v: TerminalView) {
        selectionClipboard.clear()
        if (view.get() === v) view.clear()
        v.detachSession()
    }
    fun save(profile: Profile, key: String, password: String? = null) {
        store.save(profile, key.ifBlank { null }, password); profiles = store.profiles()
    }
    fun delete(profile: Profile) { store.delete(profile); profiles = store.profiles() }
    fun connect(profile: Profile, passphrase: String = "") {
        selectionClipboard.clear()
        if (connecting || connected) return
        require(profile.host.isNotBlank() && profile.user.isNotBlank() && profile.port in 1..65535)
        val attempt = ++connectTicket
        activeProfile = profile
        recentFiles = store.recentFiles(profile.id)
        remoteTty = ""; selectedPane = null; paneChoices = emptyList()
        ++previewTicket; previewDocument = null; entries = emptyList(); remoteFiles = null; previewBusy = false
        try {
            androidx.core.content.ContextCompat.startForegroundService(getApplication(),
                android.content.Intent(getApplication(), ConnectionService::class.java))
        } catch (_: RuntimeException) {
            status = "Open Hexgate to start a background connection"
            return
        }
        connecting = true; status = "Connecting to ${profile.name}"
        connection = SshConnection()
        terminal = makeTerminal()
        view.get()?.attachSession(terminal)
        val target = connection
        worker.execute {
            try {
                val privateKey = if (profile.authMethod == AuthMethod.PRIVATE_KEY) store.privateKey(profile) else ""
                val password = if (profile.authMethod == AuthMethod.PASSWORD) store.password(profile) else null
                target.connect(profile, privateKey, passphrase, password) { fingerprint ->
                    val old = store.trusted(profile.host, profile.port)
                    if (old == fingerprint) true else {
                        val answer = CompletableFuture<Boolean>()
                        main.post { if (attempt == connectTicket) challenge = HostChallenge(profile, old, fingerprint, answer) else answer.complete(false) }
                        val accepted = try { answer.get(90, TimeUnit.SECONDS) } catch (_: Exception) { false }
                        main.post { if (challenge?.answer === answer) challenge = null }
                        if (accepted && attempt == connectTicket) store.trust(profile.host, profile.port, fingerprint)
                        accepted
                    }
                }
                main.post { if (connection === target && attempt == connectTicket) { store.saveLastProfile(profile); connected = true; connecting = false; status = profile.name; remoteFiles = RemoteFiles(target) } }
                target.read { bytes ->
                    val latch = CountDownLatch(1)
                    main.post { try { if (connection === target && attempt == connectTicket) terminal.receive(bytes) } finally { latch.countDown() } }
                    check(latch.await(10, TimeUnit.SECONDS)) { "Terminal consumer stalled" }
                }
                main.post { if (connection === target && attempt == connectTicket) status = "Connection closed; reconnect to resume" }
            } catch (_: Exception) {
                main.post { if (connection === target && attempt == connectTicket) status = "Connection failed. Check address, authentication settings and host trust." }
            } finally {
                target.shutdown()
                main.post { if (connection === target && attempt == connectTicket) { connected = false; connecting = false; stopConnectionService() } }
            }
        }
    }
    fun togglePreview() {
        previewRequested = !previewRequested
        if (!previewRequested) return
        val profile = activeProfile
        if (profile == null || profile.herdrSession.isBlank()) { browse(); return }
        val remote = remoteFiles ?: run { previewError = "Connect first"; return }
        val tty = remoteTty
        val ticket = ++previewTicket
        previewBusy = true; previewError = ""; previewDocument = null; entries = emptyList(); selectedPane = null; paneChoices = emptyList()
        remoteWorker.execute {
            try {
                val helper = getApplication<Application>().assets.open("herdr_context.py").bufferedReader().use { it.readText() }
                val context = remote.captureHerdr(profile.herdrSession,tty,helper)
                main.post {
                    if (ticket == previewTicket) {
                        paneChoices = context.panes
                        previewBusy = false
                        val focused = context.panes.find { it.id == context.focused }
                        if (context.automatic && focused != null) choosePane(focused)
                        else previewError = context.reason.ifBlank { "Select a pane explicitly; automatic focus is unavailable." }
                    }
                }
            } catch (_: Exception) { main.post { if (ticket == previewTicket) { previewBusy = false; previewError = "Cannot identify Herdr pane. Check session name and Herdr version, or explicitly browse home." } } }
        }
    }
    fun choosePane(pane: HerdrPane) { selectedPane = pane; paneChoices = emptyList(); browse(pane.cwd) }
    fun browse(path: String? = null) {
        val remote = remoteFiles ?: run { previewError = "Connect first"; return }
        val ticket = ++previewTicket
        previewBusy = true; previewError = ""; previewDocument = null
        remoteWorker.execute {
            try {
                val resolved = path ?: remote.home()
                val list = remote.list(resolved)
                main.post { if (ticket == previewTicket) { browserPath = resolved; entries = list; previewBusy = false } }
            } catch (_: Exception) { main.post { if (ticket == previewTicket) { previewError = "Cannot list remote directory"; previewBusy = false } } }
        }
    }
    fun openDocument(path: String) {
        val remote = remoteFiles ?: return
        val ticket = ++previewTicket
        previewBusy = true; previewError = ""
        remoteWorker.execute {
            try {
                val doc = remote.document(path)
                main.post { if (ticket == previewTicket) { previewDocument = doc; recentFiles = (listOf(doc.path) + recentFiles.filterNot { it == doc.path }).take(12); activeProfile?.let { store.saveRecentFiles(it.id,recentFiles) }; previewBusy = false } }
            } catch (_: Exception) { main.post { if (ticket == previewTicket) { previewError = "Cannot open document. Supported: Markdown, HTML, Mermaid, draw.io; limit 2 MiB."; previewBusy = false } } }
        }
    }
    fun answerHost(accept: Boolean) { challenge?.answer?.complete(accept); challenge = null }
    private fun stopConnectionService() {
        getApplication<Application>().stopService(android.content.Intent(getApplication(), ConnectionService::class.java))
    }
    fun refreshTerminal() {
        view.get()?.onScreenUpdated()
        view.get()?.invalidate()
        if (connected) connection.refreshDisplay()
    }
    fun disconnect() {
        selectionClipboard.clear()
        ++profileRequestTicket;keyPromptProfile=null
        ++connectTicket
        answerHost(false)
        connected = false; connecting = false; stopConnectionService(); status = "Disconnected; remote work is not stopped"
        ++previewTicket; remoteFiles = null; previewDocument = null; entries = emptyList()
        val target = connection
        Thread { target.shutdown() }.start()
    }
    fun reconnect(passphrase: String = "") { activeProfile?.let { connect(it, passphrase) } }
    fun sendKey(value: String) { terminal.write(value) }
    fun sendAccessory(keyCode: Int) {
        val mods = (if (ctrl) com.termux.terminal.KeyHandler.KEYMOD_CTRL else 0) or (if (alt) com.termux.terminal.KeyHandler.KEYMOD_ALT else 0)
        val code = com.termux.terminal.KeyHandler.getCode(keyCode,mods,terminal.emulator.isCursorKeysApplicationMode,terminal.emulator.isKeypadApplicationMode)
        if (code != null) terminal.write(code)
        ctrl = false; alt = false
    }
    fun copy(selected: String? = view.get()?.selectedText) {
        val text = selected?.takeIf { it.isNotEmpty() } ?: return
        getApplication<Application>().getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Terminal", text))
    }
    fun paste() {
        val clip = getApplication<Application>().getSystemService(ClipboardManager::class.java).primaryClip ?: return
        val text = clip.getItemAt(0).coerceToText(getApplication()).toString()
        if (text.toByteArray().size <= 1024 * 1024) terminal.emulator.paste(text) else status = "Paste exceeds 1 MiB"
    }
    fun viewClient(v: TerminalView) = object : QuietViewClient() {
        override fun shouldEnforceCharBasedInput() = true
        override fun onRemoteSelectionCopy(enabled: Boolean) {
            if (view.get() !== v) return
            if (enabled) selectionClipboard.arm(terminal, android.os.SystemClock.uptimeMillis())
            else selectionClipboard.clear()
        }
        override fun onKeyDown(keyCode: Int, e: android.view.KeyEvent?, session: TerminalSession?): Boolean {
            if ((e?.isCtrlPressed == true || ctrl) && e?.isShiftPressed == true && !e.isAltPressed) {
                val selected = v.selectedText?.takeIf { it.isNotEmpty() }
                if (keyCode == android.view.KeyEvent.KEYCODE_C) {
                    copy(selected); ctrl = false; alt = false; return true
                }
                if (keyCode == android.view.KeyEvent.KEYCODE_V) {
                    v.stopTextSelectionMode(); paste(); ctrl = false; alt = false; return true
                }
            }
            if (keyCode !in setOf(android.view.KeyEvent.KEYCODE_CTRL_LEFT,android.view.KeyEvent.KEYCODE_CTRL_RIGHT,android.view.KeyEvent.KEYCODE_ALT_LEFT,android.view.KeyEvent.KEYCODE_ALT_RIGHT)) main.post { ctrl = false; alt = false }
            return false
        }
        override fun readControlKey(): Boolean = ctrl
        override fun readAltKey(): Boolean = alt
        override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession?): Boolean { ctrl = false; alt = false; return false }
        override fun onScale(scale: Float): Float { updateFontSize(fontSize * scale); return 1f }
        override fun onSingleTapUp(e: android.view.MotionEvent?) {
            if (!selection && e?.isFromSource(android.view.InputDevice.SOURCE_MOUSE) != true) showSoftKeyboard(v)
        }
    }
    private val connectivity = app.getSystemService(android.net.ConnectivityManager::class.java)
    private val networkCallback = object : android.net.ConnectivityManager.NetworkCallback() {
        override fun onLost(network: android.net.Network) {
            main.post {
                if (connected || connecting) { disconnect(); status = "Network changed. Reconnect to resume Herdr." }
            }
        }
    }
    init { connectivity.registerDefaultNetworkCallback(networkCallback) }
    override fun onCleared() {
        connectivity.unregisterNetworkCallback(networkCallback)
        disconnect(); worker.shutdownNow(); remoteWorker.shutdownNow()
    }
}
