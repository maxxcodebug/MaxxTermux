package com.maxxcodebug.maxxtermux.state

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.maxxcodebug.maxxtermux.data.HostStore
import com.maxxcodebug.maxxtermux.data.SecretBox
import com.maxxcodebug.maxxtermux.model.Host
import com.maxxcodebug.maxxtermux.model.HostStatus
import com.maxxcodebug.maxxtermux.model.ProgressPrefs
import com.maxxcodebug.maxxtermux.model.Snippet
import com.maxxcodebug.maxxtermux.ssh.KnownHosts
import com.maxxcodebug.maxxtermux.ssh.SessionState
import com.maxxcodebug.maxxtermux.ssh.SshSession
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

enum class Screen { Home, Shell, Keys, Snippets, AddHost }

/** App wide state. Lives as long as the process, so SSH sessions survive screen changes. */
object AppState {
    private var appCtx: Context? = null
    private var store: HostStore? = null
    private val main = Handler(Looper.getMainLooper())

    val hosts = mutableStateListOf<Host>()
    val snippets = mutableStateListOf<Snippet>()
    val known = mutableStateMapOf<String, String>()
    val statuses = mutableStateMapOf<String, HostStatus>()
    val sessions = mutableStateListOf<SshSession>()

    var active by mutableStateOf<SshSession?>(null)
    var screen by mutableStateOf(Screen.Home)
    var hostMenu by mutableStateOf<Host?>(null)
    var fontSp by mutableFloatStateOf(DEFAULT_FONT_SP)
    val progressPrefs = ProgressPrefs()

    const val DEFAULT_FONT_SP = 13f

    val context: Context
        get() = appCtx ?: throw IllegalStateException("AppState.init was not called")

    fun init(ctx: Context) {
        if (appCtx != null) return
        appCtx = ctx.applicationContext
        val s = HostStore(ctx.applicationContext)
        store = s
        hosts.addAll(s.loadHosts())
        snippets.addAll(s.loadSnippets())
        known.putAll(s.loadKnown())
    }

    private val knownHosts = object : KnownHosts {
        override fun get(id: String): String? = known[id]
        override fun put(id: String, fingerprint: String) {
            main.post {
                known[id] = fingerprint
                store?.saveKnown(HashMap(known))
            }
        }
    }

    // ---------------------------------------------------------------- hosts

    fun addHost(host: Host) {
        hosts.add(host)
        store?.saveHosts(hosts.toList())
        screen = Screen.Home
        probe(host)
    }

    fun deleteHost(host: Host) {
        sessions.filter { it.host.id == host.id }.forEach { closeSession(it) }
        hosts.removeAll { it.id == host.id }
        statuses.remove(host.id)
        store?.saveHosts(hosts.toList())
    }

    fun probeAll() {
        hosts.toList().forEach { probe(it) }
    }

    private fun probe(h: Host) {
        statuses[h.id] = HostStatus(checking = true)
        Thread {
            var res = HostStatus(online = false)
            try {
                val t0 = System.nanoTime()
                Socket().use { s -> s.connect(InetSocketAddress(h.address, h.port), 2500) }
                res = HostStatus(online = true, latencyMs = ((System.nanoTime() - t0) / 1_000_000L).toInt())
            } catch (e: Exception) {
            }
            main.post { statuses[h.id] = res }
        }.start()
    }

    // ---------------------------------------------------------------- sessions

    fun connect(h: Host) {
        val existing = sessions.firstOrNull { it.host.id == h.id && it.state != SessionState.Closed }
        if (existing != null) {
            active = existing
            screen = Screen.Shell
            return
        }
        val s = SshSession(h, SecretBox.decrypt(h.secretEnc), SecretBox.decrypt(h.passphraseEnc), knownHosts)
        sessions.add(s)
        active = s
        screen = Screen.Shell
        s.connect()
    }

    fun closeSession(s: SshSession) {
        s.close()
        sessions.remove(s)
        if (active === s) active = sessions.lastOrNull()
        if (active == null && screen == Screen.Shell) screen = Screen.Home
    }

    // ---------------------------------------------------------------- snippets and known hosts

    fun addSnippet(group: String, text: String) {
        snippets.add(Snippet(UUID.randomUUID().toString(), group, text))
        store?.saveSnippets(snippets.toList())
    }

    fun deleteSnippet(s: Snippet) {
        snippets.removeAll { it.id == s.id }
        store?.saveSnippets(snippets.toList())
    }

    fun forgetKnown(id: String) {
        known.remove(id)
        store?.saveKnown(HashMap(known))
    }
}
