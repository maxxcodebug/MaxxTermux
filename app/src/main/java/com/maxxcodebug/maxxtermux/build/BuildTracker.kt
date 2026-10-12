package com.maxxcodebug.maxxtermux.build

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.maxxcodebug.maxxtermux.model.BuildJob
import com.maxxcodebug.maxxtermux.service.BuildService
import com.maxxcodebug.maxxtermux.ssh.SshSession
import com.maxxcodebug.maxxtermux.state.AppState

/**
 * Watches terminal output of every session for AOSP build progress lines
 * and forwards them to the notification service. Runs on the main thread.
 */
object BuildTracker {
    /** The running build shown on Home. Set by BuildService. */
    var current by mutableStateOf<BuildJob?>(null)

    @Volatile
    var lastSession: SshSession? = null

    private val buffers = HashMap<SshSession, StringBuilder>()

    fun onText(session: SshSession, text: String) {
        val sb = buffers.getOrPut(session) { StringBuilder() }
        sb.append(text)
        var start = 0
        for (i in 0 until sb.length) {
            val c = sb[i]
            if (c == '\n' || c == '\r') {
                if (i > start) handleLine(session, sb.substring(start, i))
                start = i + 1
            }
        }
        if (start > 0) sb.delete(0, start)
        if (sb.length > 4000) sb.setLength(0)
    }

    private fun handleLine(session: SshSession, line: String) {
        val event = BuildOutputParser.parse(line) ?: return
        lastSession = session
        val job = session.lastCommand.ifBlank { "Build" }
        BuildService.report(AppState.context, event, job, session.host.name)
    }

    /** Called by the notification Stop action: sends Ctrl+C to the build session. */
    fun interrupt() {
        lastSession?.send("\u0003")
    }

    fun forget(session: SshSession) {
        buffers.remove(session)
        if (lastSession === session) lastSession = null
    }
}
