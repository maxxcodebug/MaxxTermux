package com.maxxcodebug.maxxtermux.terminal

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class SessionState { Connecting, Connected, Closed }

/** A terminal tab. Either a local shell or an SSH connection. */
abstract class TermSession(val title: String) {
    val emulator = TerminalEmulator(80, 24)

    var state by mutableStateOf(SessionState.Connecting)
    var version by mutableIntStateOf(0)

    protected val main = Handler(Looper.getMainLooper())
    private var redrawPending = false

    abstract val subtitle: String
    open val canReconnect: Boolean get() = false

    abstract fun send(text: String)
    abstract fun resize(cols: Int, rows: Int)
    abstract fun close()
    open fun reconnect() {}

    /** Writes text to the screen (not to the remote side). Safe from any thread. */
    protected fun write(text: String) {
        emulator.process(text)
        requestRedraw()
    }

    protected fun requestRedraw() {
        synchronized(this) {
            if (redrawPending) return
            redrawPending = true
        }
        main.postDelayed({
            synchronized(this) { redrawPending = false }
            version++
        }, 33)
    }
}
