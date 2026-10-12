package com.maxxcodebug.maxxtermux.ssh

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.maxxcodebug.maxxtermux.build.BuildTracker
import com.maxxcodebug.maxxtermux.model.AuthType
import com.maxxcodebug.maxxtermux.model.Host
import com.maxxcodebug.maxxtermux.terminal.TerminalEmulator
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.userauth.password.PasswordFinder
import net.schmizz.sshj.userauth.password.PasswordUtils
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.InputStreamReader
import java.security.PublicKey
import java.security.Security
import java.util.concurrent.Executors

/** Trust on first use: the first fingerprint seen for a host is saved, later ones must match. */
interface KnownHosts {
    fun get(id: String): String?
    fun put(id: String, fingerprint: String)
}

enum class SessionState { Connecting, Connected, Closed }

class SshSession(
    val host: Host,
    private val secret: String,
    private val passphrase: String,
    private val known: KnownHosts,
) {
    val emulator = TerminalEmulator(80, 24)

    var state by mutableStateOf(SessionState.Connecting)
    var error by mutableStateOf<String?>(null)
    var version by mutableIntStateOf(0)

    @Volatile
    var lastCommand: String = ""

    private val main = Handler(Looper.getMainLooper())
    private val writer = Executors.newSingleThreadExecutor()

    @Volatile private var client: SSHClient? = null
    @Volatile private var shell: Session.Shell? = null
    @Volatile private var cols = 80
    @Volatile private var rows = 24
    @Volatile private var closedByUser = false
    private var redrawPending = false
    private val lineBuf = StringBuilder()

    init {
        emulator.respond = { send(it) }
    }

    // ---------------------------------------------------------------- connect

    fun connect() {
        closedByUser = false
        state = SessionState.Connecting
        error = null
        emulator.process("\u001B[2mConnecting to ${host.user}@${host.address}:${host.port} ...\u001B[0m\r\n")
        requestRedraw()
        Thread({ doConnect() }, "ssh-" + host.name).start()
    }

    private fun doConnect() {
        try {
            installBouncyCastle()
            val c = SSHClient()
            c.addHostKeyVerifier(Tofu("${host.address}:${host.port}", known))
            c.connectTimeout = 10000
            c.connect(host.address, host.port)
            try {
                c.connection.keepAlive.keepAliveInterval = 15
            } catch (e: Throwable) {
            }
            if (host.authType == AuthType.Password) {
                c.authPassword(host.user, secret)
            } else {
                val finder: PasswordFinder? =
                    if (passphrase.isEmpty()) null else PasswordUtils.createOneOff(passphrase.toCharArray())
                val keys = c.loadKeys(secret.trim() + "\n", null, finder)
                c.authPublickey(host.user, keys)
            }
            val s = c.startSession()
            s.allocatePTY("xterm-256color", cols, rows, 0, 0, emptyMap())
            val sh = s.startShell()
            client = c
            shell = sh
            main.post {
                state = SessionState.Connected
                version++
            }
            readLoop(sh)
        } catch (t: Throwable) {
            fail(t)
        }
    }

    private fun readLoop(sh: Session.Shell) {
        val reader = InputStreamReader(sh.inputStream, Charsets.UTF_8)
        val buf = CharArray(4096)
        try {
            while (true) {
                val n = reader.read(buf)
                if (n < 0) break
                val text = String(buf, 0, n)
                emulator.process(text)
                main.post { BuildTracker.onText(this, text) }
                requestRedraw()
            }
            if (!closedByUser) emulator.process("\r\n\u001B[2m[connection closed]\u001B[0m\r\n")
        } catch (t: Throwable) {
            if (!closedByUser) {
                emulator.process("\r\n\u001B[31m[connection lost: ${t.message}]\u001B[0m\r\n")
            }
        } finally {
            cleanup()
            main.post {
                state = SessionState.Closed
                version++
            }
        }
    }

    private fun fail(t: Throwable) {
        if (closedByUser) return
        var msg = t.message ?: t.javaClass.simpleName
        if (msg.contains("verify", ignoreCase = true) || msg.contains("host key", ignoreCase = true)) {
            msg = "Host key does not match the saved fingerprint. If the server was reinstalled, remove it in Keys. ($msg)"
        } else if (msg.contains("Auth fail", ignoreCase = true) ||
            msg.contains("Exhausted available authentication", ignoreCase = true)
        ) {
            msg = "Authentication failed. Check the user name and password or key. ($msg)"
        }
        emulator.process("\r\n\u001B[31m$msg\u001B[0m\r\n")
        cleanup()
        main.post {
            error = msg
            state = SessionState.Closed
            version++
        }
    }

    private fun cleanup() {
        try {
            shell?.close()
        } catch (e: Throwable) {
        }
        try {
            client?.disconnect()
        } catch (e: Throwable) {
        }
        shell = null
        client = null
    }

    fun close() {
        closedByUser = true
        BuildTracker.forget(this)
        Thread { cleanup() }.start()
        state = SessionState.Closed
    }

    // ---------------------------------------------------------------- input / size

    fun send(text: String) {
        if (text.isEmpty()) return
        trackCommand(text)
        val bytes = text.toByteArray(Charsets.UTF_8)
        writer.execute {
            try {
                val out = shell?.outputStream
                if (out != null) {
                    out.write(bytes)
                    out.flush()
                }
            } catch (e: Throwable) {
            }
        }
    }

    private fun trackCommand(text: String) {
        for (ch in text) {
            when {
                ch == '\r' -> {
                    val line = lineBuf.toString().trim()
                    if (line.isNotEmpty()) lastCommand = line
                    lineBuf.setLength(0)
                }
                ch == '\u007f' -> if (lineBuf.isNotEmpty()) lineBuf.setLength(lineBuf.length - 1)
                ch >= ' ' -> lineBuf.append(ch)
            }
        }
    }

    fun resize(newCols: Int, newRows: Int) {
        if (newCols == cols && newRows == rows) return
        cols = newCols
        rows = newRows
        emulator.resize(newCols, newRows)
        val sh = shell
        if (sh != null) {
            writer.execute {
                try {
                    sh.changeWindowDimensions(newCols, newRows, 0, 0)
                } catch (e: Throwable) {
                }
            }
        }
        requestRedraw()
    }

    private fun requestRedraw() {
        synchronized(this) {
            if (redrawPending) return
            redrawPending = true
        }
        main.postDelayed({
            synchronized(this) { redrawPending = false }
            version++
        }, 33)
    }

    // ---------------------------------------------------------------- helpers

    private class Tofu(private val id: String, private val known: KnownHosts) : HostKeyVerifier {
        override fun verify(hostname: String?, port: Int, key: PublicKey?): Boolean {
            if (key == null) return false
            val fp = SecurityUtils.getFingerprint(key)
            val saved = known.get(id)
            return if (saved == null) {
                known.put(id, fp)
                true
            } else {
                saved == fp
            }
        }

        override fun findExistingAlgorithms(hostname: String?, port: Int): MutableList<String> = mutableListOf()
    }

    companion object {
        @Volatile
        private var bcInstalled = false

        private fun installBouncyCastle() {
            if (bcInstalled) return
            try {
                Security.removeProvider("BC")
                Security.insertProviderAt(BouncyCastleProvider(), 1)
            } catch (e: Throwable) {
            }
            bcInstalled = true
        }
    }
}
