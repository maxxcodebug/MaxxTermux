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
import androidx.compose.ui.graphics.Color
import com.maxxcodebug.maxxtermux.data.HostStore
import com.maxxcodebug.maxxtermux.data.SecretBox
import com.maxxcodebug.maxxtermux.design.AxProgressStyle
import com.maxxcodebug.maxxtermux.model.AuthType
import com.maxxcodebug.maxxtermux.model.Host
import com.maxxcodebug.maxxtermux.model.HostStatus
import com.maxxcodebug.maxxtermux.model.ProgressPrefs
import com.maxxcodebug.maxxtermux.model.Snippet
import com.maxxcodebug.maxxtermux.ssh.KnownHosts
import com.maxxcodebug.maxxtermux.ssh.SshSession
import com.maxxcodebug.maxxtermux.terminal.LocalSession
import com.maxxcodebug.maxxtermux.terminal.TermSession
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

enum class Screen { Home, Shell, Keys, Snippets, AddHost, Settings }

/** App wide state. Lives as long as the process, so terminal tabs survive screen changes. */
object AppState {
    private var appCtx: Context? = null
    private var store: HostStore? = null
    private val main = Handler(Looper.getMainLooper())

    val hosts = mutableStateListOf<Host>()
    val snippets = mutableStateListOf<Snippet>()
    val known = mutableStateMapOf<String, String>()
    val statuses = mutableStateMapOf<String, HostStatus>()
    val sessions = mutableStateListOf<TermSession>()

    var active by mutableStateOf<TermSession?>(null)
    var screen by mutableStateOf(Screen.Home)
    var hostMenu by mutableStateOf<Host?>(null)
    var fontSp by mutableFloatStateOf(DEFAULT_FONT_SP)

    var userName by mutableStateOf("")
    var profileOpen by mutableStateOf(false)
    private var profileSkipped = false
    var progressPrefs by mutableStateOf(ProgressPrefs())

    const val DEFAULT_FONT_SP = 13f

    val accentChoices = listOf(Color(0xFFBFE6F5), Color(0xFFC9C6FF), Color(0xFFA6F0C6), Color(0xFFFFC9A8))

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

        val o = s.loadSettings()
        userName = o.optString("name", "")
        profileSkipped = o.optBoolean("skipped", false)
        val style = try {
            AxProgressStyle.valueOf(o.optString("style", "WavyRing"))
        } catch (e: Exception) {
            AxProgressStyle.WavyRing
        }
        progressPrefs = ProgressPrefs(
            style = style,
            thicknessDp = o.optInt("thickness", 6),
            animateWave = o.optBoolean("animate", true),
            color = accentChoices[o.optInt("color", 0).coerceIn(0, accentChoices.size - 1)],
        )
        profileOpen = userName.isBlank() && !profileSkipped
    }

    // ---------------------------------------------------------------- profile and settings

    fun initials(): String {
        val parts = userName.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (parts.isEmpty()) return "AX"
        return parts.take(2).joinToString("") { it.substring(0, 1).uppercase() }
    }

    fun setUserName(name: String) {
        userName = name.trim()
        profileOpen = false
        persistSettings()
    }

    fun skipProfile() {
        profileSkipped = true
        profileOpen = false
        persistSettings()
    }

    fun updatePrefs(p: ProgressPrefs) {
        progressPrefs = p
        persistSettings()
    }

    private fun persistSettings() {
        val o = JSONObject()
        o.put("name", userName)
        o.put("skipped", profileSkipped)
        o.put("style", progressPrefs.style.name)
        o.put("thickness", progressPrefs.thicknessDp)
        o.put("animate", progressPrefs.animateWave)
        o.put("color", accentChoices.indexOf(progressPrefs.color).coerceAtLeast(0))
        store?.saveSettings(o)
    }

    // ---------------------------------------------------------------- hosts

    private val knownHosts = object : KnownHosts {
        override fun get(id: String): String? = known[id]
        override fun put(id: String, fingerprint: String) {
            main.post {
                known[id] = fingerprint
                store?.saveKnown(HashMap(known))
            }
        }
    }

    fun addHost(host: Host) {
        hosts.add(host)
        store?.saveHosts(hosts.toList())
        screen = Screen.Home
        probe(host)
    }

    fun deleteHost(host: Host) {
        sessions.filter { it is SshSession && it.host.id == host.id }.forEach { closeSession(it) }
        hosts.removeAll { it.id == host.id }
        statuses.remove(host.id)
        store?.saveHosts(hosts.toList())
    }

    /** Called after the user answers "y" to "Save this password?" in the terminal. */
    fun saveTypedHost(h: Host, password: String) {
        val enc = SecretBox.encrypt(password)
        val idx = hosts.indexOfFirst {
            it.address.equals(h.address, ignoreCase = true) && it.port == h.port && it.user == h.user
        }
        if (idx >= 0) {
            hosts[idx] = hosts[idx].copy(secretEnc = enc, authType = AuthType.Password)
        } else {
            hosts.add(h.copy(secretEnc = enc, authType = AuthType.Password))
        }
        store?.saveHosts(hosts.toList())
        hosts.lastOrNull { it.address == h.address }?.let { probe(it) }
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

    // ---------------------------------------------------------------- terminal tabs

    fun newLocal(): LocalSession {
        val s = LocalSession()
        sessions.add(s)
        active = s
        screen = Screen.Shell
        return s
    }

    fun openTerminal() {
        if (active == null) {
            newLocal()
        } else {
            screen = Screen.Shell
        }
    }

    /** Tap on a saved host in Home. Uses the saved password or key. */
    fun connect(h: Host) {
        val existing = sessions.firstOrNull {
            it is SshSession && it.host.id == h.id && it.state != com.maxxcodebug.maxxtermux.terminal.SessionState.Closed
        }
        if (existing != null) {
            active = existing
            screen = Screen.Shell
            return
        }
        startSsh(h)
    }

    /** From the ssh command typed in the local terminal. */
    fun openSsh(user: String, address: String, port: Int) {
        val saved = hosts.firstOrNull {
            it.address.equals(address, ignoreCase = true) && it.port == port &&
                (user.isBlank() || it.user == user)
        }
        val host = saved ?: Host(
            id = UUID.randomUUID().toString(),
            name = address,
            user = user,
            address = address,
            port = port,
        )
        startSsh(host)
    }

    private fun startSsh(h: Host) {
        val secret = SecretBox.decrypt(h.secretEnc).ifEmpty { null }
        val pass = SecretBox.decrypt(h.passphraseEnc)
        val s = SshSession(h, secret, pass, knownHosts) { host, pw -> saveTypedHost(host, pw) }
        sessions.add(s)
        active = s
        screen = Screen.Shell
        s.connect()
    }

    fun closeSession(s: TermSession) {
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
