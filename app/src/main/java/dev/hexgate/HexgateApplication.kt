package dev.hexgate

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/** A live SSH session belongs to the process, rather than a particular window. */
class HexgateApplication : Application(), ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
    private var windows = 0
    private var changingWindow = false
    private var session: TerminalModel? = null

    fun openWindow(): TerminalModel {
        windows++
        changingWindow = false
        return session ?: ViewModelProvider(viewModelStore,
            ViewModelProvider.AndroidViewModelFactory.getInstance(this))[TerminalModel::class.java]
            .also { session = it }
    }

    fun closeWindow(changing: Boolean) {
        windows--
        changingWindow = changing
        releaseIdleSession()
    }

    fun releaseIdleSession() {
        val current = session ?: return
        if (windows == 0 && !changingWindow && !current.connected && !current.connecting) {
            session = null
            viewModelStore.clear()
        }
    }

    fun disconnectSession() { session?.disconnect() }
}
