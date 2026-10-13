package com.maxxcodebug.maxxtermux.terminal

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.system.Os
import com.maxxcodebug.maxxtermux.state.AppState
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStream

/**
 * Local shell on the phone (/system/bin/sh).
 * Input is edited inside the app (echo, backspace, history) and sent line by line.
 * Built in commands: ssh, setup-storage, clear, help, exit.
 */
class LocalSession : TermSession("Local") {

    override val subtitle: String get() = "Local shell"

    private val lock = Any()
    private val home: File = File(AppState.context.filesDir, "home").also { it.mkdirs() }
    private var proc: Process? = null
    private var stdin: OutputStream? = null
    private val lineBuf = StringBuilder()
    private val history = ArrayList<String>()
    private var histPos = -1

    @Volatile private var busy = false
    @Volatile private var closed = false
    @Volatile private var cwd: String = home.absolutePath

    init {
        state = SessionState.Connected
        write(
            "\u001B[1mMaxxTermux\u001B[0m local terminal\r\n" +
                "\u001B[2mType\u001B[0m ssh user@host \u001B[2mto connect to a server, or\u001B[0m help\u001B[2m.\u001B[0m\r\n\r\n",
        )
        startShell()
    }

    // ------------------------------------------------------------------ shell process

    private fun startShell() {
        try {
            val pb = ProcessBuilder("/system/bin/sh")
            pb.directory(File(cwd).takeIf { it.isDirectory } ?: home)
            pb.redirectErrorStream(true)
            val env = pb.environment()
            env["HOME"] = home.absolutePath
            env["TERM"] = "xterm-256color"
            env["PATH"] = "/system/bin:/system/xbin:/vendor/bin"
            env["TMPDIR"] = File(home, "tmp").also { it.mkdirs() }.absolutePath
            val p = pb.start()
            proc = p
            stdin = p.outputStream
            busy = false
            Thread({ readLoop(p) }, "local-reader").start()
            showPrompt()
        } catch (e: Exception) {
            write("\u001B[31mCould not start the shell: ${e.message}\u001B[0m\r\n")
            state = SessionState.Closed
        }
    }

    private fun restartShell() {
        val old = proc
        proc = null
        try {
            old?.destroy()
        } catch (e: Exception) {
        }
        busy = false
        startShell()
    }

    private fun readLoop(p: Process) {
        val reader = InputStreamReader(p.inputStream, Charsets.UTF_8)
        val buf = CharArray(4096)
        val pending = StringBuilder()
        try {
            while (true) {
                val n = reader.read(buf)
                if (n < 0) break
                pending.append(buf, 0, n)
                synchronized(lock) { drain(pending) }
            }
        } catch (e: Exception) {
        }
        if (!closed && proc === p) {
            main.post {
                write("\r\n\u001B[2m[shell exited]\u001B[0m\r\n")
                state = SessionState.Closed
            }
        }
    }

    /** Output from the shell. A marker (SOH MAXX:cwd SOH) means the command finished. */
    private fun drain(pending: StringBuilder) {
        while (true) {
            val start = pending.indexOf("\u0001")
            if (start < 0) {
                emit(pending.toString())
                pending.setLength(0)
                return
            }
            if (start > 0) emit(pending.substring(0, start))
            val end = pending.indexOf("\u0001", start + 1)
            if (end < 0) {
                pending.delete(0, start)
                return
            }
            val content = pending.substring(start + 1, end)
            pending.delete(0, end + 1)
            if (content.startsWith("MAXX:")) {
                cwd = content.substring(5)
                busy = false
                showPrompt()
            }
        }
    }

    private fun emit(s: String) {
        if (s.isEmpty()) return
        write(s.replace("\r\n", "\n").replace("\n", "\r\n"))
    }

    private fun writeStdin(text: String) {
        try {
            val out = stdin
            if (out != null) {
                out.write(text.toByteArray(Charsets.UTF_8))
                out.flush()
            }
        } catch (e: Exception) {
        }
    }

    private fun showPrompt() {
        val c = cwd
        val h = home.absolutePath
        val shown = if (c == h) "~" else if (c.startsWith("$h/")) "~" + c.substring(h.length) else c
        val col = emulator.read { it.curCol }
        if (col != 0) write("\r\n")
        write("\u001B[32mlocal\u001B[0m:\u001B[34m$shown\u001B[0m$ ")
    }

    // ------------------------------------------------------------------ input

    override fun send(text: String) {
        synchronized(lock) {
            var i = 0
            while (i < text.length) {
                val ch = text[i]
                if (ch == '\u001B') {
                    if (i + 1 < text.length && text[i + 1] == '[') {
                        var j = i + 2
                        while (j < text.length && text[j] !in '@'..'~') j++
                        if (j < text.length) onCsi(text[j])
                        i = j + 1
                    } else if (i + 2 < text.length && text[i + 1] == 'O') {
                        onCsi(text[i + 2])
                        i += 3
                    } else {
                        i++
                    }
                    continue
                }
                when {
                    ch == '\r' || ch == '\n' -> submit()
                    ch == '\u007f' || ch == '\b' -> backspace()
                    ch == '\u0003' -> ctrlC()
                    ch == '\u0015' -> eraseLine()
                    ch == '\u0017' -> deleteWord()
                    ch == '\u000C' -> clearScreen()
                    ch < ' ' -> Unit
                    else -> {
                        lineBuf.append(ch)
                        write(ch.toString())
                    }
                }
                i++
            }
        }
    }

    private fun onCsi(fin: Char) {
        when (fin) {
            'A' -> historyStep(-1)
            'B' -> historyStep(1)
        }
    }

    private fun backspace() {
        if (lineBuf.isNotEmpty()) {
            lineBuf.setLength(lineBuf.length - 1)
            write("\b \b")
        }
    }

    private fun eraseLine() {
        while (lineBuf.isNotEmpty()) backspace()
    }

    private fun deleteWord() {
        while (lineBuf.isNotEmpty() && lineBuf[lineBuf.length - 1] == ' ') backspace()
        while (lineBuf.isNotEmpty() && lineBuf[lineBuf.length - 1] != ' ') backspace()
    }

    private fun setLine(s: String) {
        eraseLine()
        lineBuf.append(s)
        write(s)
    }

    private fun historyStep(dir: Int) {
        if (history.isEmpty()) return
        if (histPos == -1) {
            if (dir > 0) return
            histPos = history.size
        }
        val next = histPos + dir
        if (next < 0) return
        if (next >= history.size) {
            histPos = -1
            setLine("")
            return
        }
        histPos = next
        setLine(history[next])
    }

    private fun clearScreen() {
        write("\u001B[2J\u001B[H")
        if (busy) return
        val c = cwd
        val h = home.absolutePath
        val shown = if (c == h) "~" else if (c.startsWith("$h/")) "~" + c.substring(h.length) else c
        write("\u001B[32mlocal\u001B[0m:\u001B[34m$shown\u001B[0m$ $lineBuf")
    }

    private fun ctrlC() {
        write("^C\r\n")
        lineBuf.setLength(0)
        histPos = -1
        if (busy) interruptChild() else showPrompt()
    }

    private fun shellPid(): Int {
        val p = proc ?: return -1
        try {
            val m = p.javaClass.getMethod("pid")
            return (m.invoke(p) as Number).toInt()
        } catch (e: Throwable) {
        }
        try {
            val f = p.javaClass.getDeclaredField("pid")
            f.isAccessible = true
            return f.getInt(p)
        } catch (e: Throwable) {
        }
        return -1
    }

    private fun interruptChild() {
        val pid = shellPid()
        var ok = false
        if (pid > 0) {
            try {
                val r = ProcessBuilder("/system/bin/pkill", "-INT", "-P", pid.toString()).start()
                ok = r.waitFor() == 0
            } catch (e: Exception) {
            }
        }
        if (!ok) restartShell()
    }

    private fun submit() {
        val line = lineBuf.toString()
        lineBuf.setLength(0)
        histPos = -1
        write("\r\n")
        if (busy) {
            writeStdin(line + "\n")
            return
        }
        val t = line.trim()
        if (t.isEmpty()) {
            showPrompt()
            return
        }
        if (history.isEmpty() || history[history.size - 1] != line) history.add(line)
        if (builtin(t)) return
        busy = true
        writeStdin(line + "\nprintf '\\001MAXX:%s\\001' \"\$PWD\"\n")
    }

    // ------------------------------------------------------------------ built in commands

    private fun builtin(t: String): Boolean {
        val args = t.split(Regex("\\s+"))
        when (args[0]) {
            "clear" -> {
                write("\u001B[2J\u001B[H")
                showPrompt()
            }
            "exit", "logout" -> main.post { AppState.closeSession(this) }
            "help" -> {
                write(
                    "Built in commands\r\n" +
                        "  ssh [-p port] [user@]host   connect over SSH (password is asked here)\r\n" +
                        "  setup-storage               give this terminal access to phone storage\r\n" +
                        "  clear                       clear the screen\r\n" +
                        "  exit                        close this tab\r\n" +
                        "Everything else runs in the phone's shell (ls, cd, cat, cp, ...).\r\n",
                )
                showPrompt()
            }
            "ssh" -> ssh(args)
            "setup-storage", "termux-setup-storage" -> setupStorage()
            else -> return false
        }
        return true
    }

    private fun ssh(args: List<String>) {
        var port = 22
        var user = ""
        var target: String? = null
        var i = 1
        while (i < args.size) {
            val a = args[i]
            when {
                a == "-p" && i + 1 < args.size -> {
                    port = args[i + 1].toIntOrNull() ?: 22
                    i++
                }
                a.startsWith("-p") && a.length > 2 -> port = a.substring(2).toIntOrNull() ?: 22
                a == "-l" && i + 1 < args.size -> {
                    user = args[i + 1]
                    i++
                }
                a == "-i" || a == "-o" || a == "-F" || a == "-J" -> {
                    write("\u001B[33m$a is not supported yet and was ignored.\u001B[0m\r\n")
                    i++
                }
                a.startsWith("-") -> Unit
                target == null -> target = a
            }
            i++
        }
        var host = target
        if (host == null) {
            write("usage: ssh [-p port] [user@]host\r\n")
            showPrompt()
            return
        }
        if (host.contains("@")) {
            user = host.substringBefore("@")
            host = host.substringAfter("@")
        }
        val h = host
        val u = user
        val p = port
        main.post { AppState.openSsh(u, h, p) }
        write("\u001B[2mOpening SSH to $h ...\u001B[0m\r\n")
        showPrompt()
    }

    private fun setupStorage() {
        val ctx = AppState.context
        val granted = if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager() else true
        if (!granted) {
            write("Allow \"All files access\" for MaxxTermux, come back and run setup-storage again.\r\n")
            try {
                val i = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + ctx.packageName),
                )
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(i)
            } catch (e: Exception) {
                try {
                    val i = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(i)
                } catch (e2: Exception) {
                }
            }
        } else {
            try {
                val link = File(home, "storage")
                if (!link.exists()) Os.symlink("/storage/emulated/0", link.absolutePath)
                write("Done. Your phone storage is at ~/storage  (try: cd ~/storage && ls)\r\n")
            } catch (e: Exception) {
                write("\u001B[31m${e.message}\u001B[0m\r\n")
            }
        }
        showPrompt()
    }

    // ------------------------------------------------------------------ size / close

    override fun resize(cols: Int, rows: Int) {
        emulator.resize(cols, rows)
        requestRedraw()
    }

    override fun close() {
        closed = true
        state = SessionState.Closed
        try {
            proc?.destroy()
        } catch (e: Exception) {
        }
    }
}
