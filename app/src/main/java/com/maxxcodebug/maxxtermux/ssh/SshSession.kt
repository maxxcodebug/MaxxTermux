package com.maxxcodebug.maxxtermux.ssh

import com.maxxcodebug.maxxtermux.build.BuildTracker
import com.maxxcodebug.maxxtermux.model.AuthType
import com.maxxcodebug.maxxtermux.model.Host
import com.maxxcodebug.maxxtermux.terminal.SessionState
import com.maxxcodebug.maxxtermux.terminal.TermSession
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.userauth.UserAuthException
import net.schmizz.sshj.userauth.method.AuthKeyboardInteractive
import net.schmizz.sshj.userauth.method.AuthPassword
import net.schmizz.sshj.userauth.method.PasswordResponseProvider
import net.schmizz.sshj.userauth.password.PasswordFinder
import net.schmizz.sshj.userauth.password.PasswordUtils
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.InputStreamReader
import java.security.PublicKey
import java.security.Security
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue

/** Trust on first use: the first fingerprint seen for a host is saved, later ones must match. */
interface KnownHosts {
    fun get(id: String): String?
    fun put(id: String, fingerprint: String)
}

/**
 * An SSH tab. Works like the ssh command in Termux: if no password is saved,
 * "user@host's password:" is asked inside the terminal and typing is hidden.
 */
class SshSession(
    val host: Host,
    private var secret: String?,
    private val passphrase: String,
    private val known: KnownHosts,
    private val onSavePassword: (Host, String) -> Unit,
) : TermSession(host.name.ifBlank { host.address }) {

    private enum class InMode { None, Shell, Password, Line, Key }

    @Volatile
    var lastCommand: String = ""

    @Volatile private var loginUser: String = host.user
    @Volatile private var mode = InMode.None
    @Volatile private var closedByUser = false
    @Volatile private var client: SSHClient? = null
    @Volatile private var shell: Session.Shell? = null
    @Volatile private var cols = 80
    @Volatile private var rows = 24

    private val writer = Executors.newSingleThreadExecutor()
    private val inputQueue = LinkedBlockingQueue<String>()
    private val inBuf = StringBuilder()
    private val lineBuf = StringBuilder()

    override val subtitle: String
        get() = (if (loginUser.isBlank()) "" else "$loginUser@") + host.address +
            (if (host.port != 22) ":${host.port}" else "")

    override val canReconnect: Boolean get() = true

    init {
        emulator.respond = { if (mode == InMode.Shell) sendToShell(it) }
    }

    // ---------------------------------------------------------------- connect

    fun connect() {
        closedByUser = false
        mode = InMode.None
        state = SessionState.Connecting
        write("\u001B[2mConnecting to ${host.address}:${host.port} ...\u001B[0m\r\n")
        Thread({ doConnect() }, "ssh-" + host.address).start()
    }

    override fun reconnect() = connect()

    private fun doConnect() {
        try {
            installBouncyCastle()

            var user = loginUser
            if (user.isBlank()) {
                user = (ask("login as: ", InMode.Line) ?: return abort()).trim()
                if (user.isEmpty()) return abort()
                loginUser = user
            }

            val c = SSHClient()
            c.addHostKeyVerifier(
                Tofu("${host.address}:${host.port}", known) { fp ->
                    write("\u001B[2mNew host key trusted: $fp\u001B[0m\r\n")
                },
            )
            c.connectTimeout = 10000
            c.connect(host.address, host.port)
            try {
                c.connection.keepAlive.keepAliveInterval = 15
            } catch (e: Throwable) {
            }
            client = c

            if (!authenticate(c, user)) return abort()

            val s = c.startSession()
            s.allocatePTY("xterm-256color", cols, rows, 0, 0, emptyMap())
            val sh = s.startShell()
            shell = sh
            mode = InMode.Shell
            main.post {
                state = SessionState.Connected
                version++
            }
            readLoop(sh)
        } catch (t: Throwable) {
            fail(t)
        }
    }

    /** Returns true when logged in. Asks for the password in the terminal, up to 3 tries. */
    private fun authenticate(c: SSHClient, user: String): Boolean {
        if (host.authType == AuthType.Key) {
            val finder: PasswordFinder? =
                if (passphrase.isEmpty()) null else PasswordUtils.createOneOff(passphrase.toCharArray())
            val keys = c.loadKeys((secret ?: "").trim() + "\n", null, finder)
            c.authPublickey(user, keys)
            return true
        }

        var pw = secret
        var typed = false
        var attempts = 0
        while (attempts < 3) {
            if (pw == null) {
                pw = ask("$user@${host.address}'s password: ", InMode.Password) ?: return false
                typed = true
            }
            try {
                c.auth(
                    user,
                    AuthPassword(PasswordUtils.createOneOff(pw.toCharArray())),
                    AuthKeyboardInteractive(PasswordResponseProvider(PasswordUtils.createOneOff(pw.toCharArray()))),
                )
                secret = pw
                if (typed) offerSave(user, pw)
                return true
            } catch (e: UserAuthException) {
                attempts++
                if (attempts < 3) {
                    write("Permission denied, please try again.\r\n")
                } else {
                    write("\u001B[31mPermission denied (password).\u001B[0m\r\n")
                }
                pw = null
                secret = null
            }
        }
        return false
    }

    private fun offerSave(user: String, pw: String) {
        val a = ask("Save this password for next time? (y/n) ", InMode.Key)
        if (a != null && a.equals("y", ignoreCase = true)) {
            val h = host.copy(user = user)
            main.post { onSavePassword(h, pw) }
            write("\u001B[2mSaved. Tap the host on Home to connect without typing.\u001B[0m\r\n")
        }
    }

    private fun abort() {
        write("\r\n\u001B[2m[cancelled]\u001B[0m\r\n")
        cleanup()
        main.post {
            state = SessionState.Closed
            version++
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
            if (!closedByUser) write("\r\n\u001B[2m[connection closed]\u001B[0m\r\n")
        } catch (t: Throwable) {
            if (!closedByUser) write("\r\n\u001B[31m[connection lost: ${t.message}]\u001B[0m\r\n")
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
            msg = "Authentication failed. ($msg)"
        }
        write("\r\n\u001B[31m$msg\u001B[0m\r\n")
        cleanup()
        main.post {
            state = SessionState.Closed
            version++
        }
    }

    private fun cleanup() {
        mode = InMode.None
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

    override fun close() {
        closedByUser = true
        BuildTracker.forget(this)
        inputQueue.offer(CANCEL)
        Thread { cleanup() }.start()
        state = SessionState.Closed
    }

    // ---------------------------------------------------------------- prompts in the terminal

    private fun ask(prompt: String, m: InMode): String? {
        inBuf.setLength(0)
        inputQueue.clear()
        write(prompt)
        mode = m
        val v = try {
            inputQueue.take()
        } catch (e: InterruptedException) {
            CANCEL
        }
        mode = InMode.None
        return if (v == CANCEL) null else v
    }

    private fun promptInput(text: String) {
        for (ch in text) {
            when {
                mode == InMode.Key && ch >= ' ' -> {
                    write("$ch\r\n")
                    inputQueue.offer(ch.toString())
                    return
                }
                ch == '\r' || ch == '\n' -> {
                    val value = inBuf.toString()
                    inBuf.setLength(0)
                    write("\r\n")
                    inputQueue.offer(value)
                    return
                }
                ch == '\u0003' -> {
                    inBuf.setLength(0)
                    write("^C\r\n")
                    inputQueue.offer(CANCEL)
                    return
                }
                ch == '\u007f' || ch == '\b' -> {
                    if (inBuf.isNotEmpty()) {
                        inBuf.setLength(inBuf.length - 1)
                        if (mode == InMode.Line) write("\b \b")
                    }
                }
                ch < ' ' -> Unit
                else -> {
                    inBuf.append(ch)
                    if (mode == InMode.Line) write(ch.toString())
                }
            }
        }
    }

    // ---------------------------------------------------------------- input / size

    override fun send(text: String) {
        if (text.isEmpty()) return
        when (mode) {
            InMode.Shell -> sendToShell(text)
            InMode.None -> if (text.contains('\u0003')) close()
            else -> promptInput(text)
        }
    }

    private fun sendToShell(text: String) {
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

    override fun resize(cols: Int, rows: Int) {
        if (cols == this.cols && rows == this.rows) return
        this.cols = cols
        this.rows = rows
        emulator.resize(cols, rows)
        val sh = shell
        if (sh != null) {
            writer.execute {
                try {
                    sh.changeWindowDimensions(cols, rows, 0, 0)
                } catch (e: Throwable) {
                }
            }
        }
        requestRedraw()
    }

    // ---------------------------------------------------------------- helpers

    private class Tofu(
        private val id: String,
        private val known: KnownHosts,
        private val onNew: (String) -> Unit,
    ) : HostKeyVerifier {
        override fun verify(hostname: String?, port: Int, key: PublicKey?): Boolean {
            if (key == null) return false
            val fp = SecurityUtils.getFingerprint(key)
            val saved = known.get(id)
            return if (saved == null) {
                known.put(id, fp)
                onNew(fp)
                true
            } else {
                saved == fp
            }
        }

        override fun findExistingAlgorithms(hostname: String?, port: Int): MutableList<String> = mutableListOf()
    }

    companion object {
        private const val CANCEL = "\u0000cancel"

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
