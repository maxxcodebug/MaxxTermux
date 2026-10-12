package com.maxxcodebug.maxxtermux.build

sealed interface BuildEvent {
    data class Progress(val percent: Int, val done: Int, val total: Int) : BuildEvent
    data object Success : BuildEvent
    data class Failure(val reason: String) : BuildEvent
}

/**
 * Reads AOSP build output (ninja) line by line.
 * Progress lines look like:  [ 82% 41013/50020] //frameworks:services
 */
object BuildOutputParser {
    private val progress = Regex("""\[\s*(\d{1,3})%\s+(\d+)/(\d+)\]""")
    private val ansi = Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]")

    fun stripAnsi(line: String): String = ansi.replace(line, "").trim('\r', '\n', ' ')

    fun parse(rawLine: String): BuildEvent? {
        val line = stripAnsi(rawLine)
        progress.find(line)?.let { m ->
            val (p, d, t) = m.destructured
            return BuildEvent.Progress(p.toInt().coerceIn(0, 100), d.toInt(), t.toInt())
        }
        if (line.contains("build completed successfully")) return BuildEvent.Success
        if (line.startsWith("FAILED:") ||
            line.contains("build failed") ||
            line.startsWith("ninja: build stopped")
        ) {
            return BuildEvent.Failure(line.take(120))
        }
        return null
    }
}
