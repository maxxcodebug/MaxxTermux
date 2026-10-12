package com.maxxcodebug.maxxtermux.terminal

import kotlin.math.max
import kotlin.math.min

/**
 * A small VT100/xterm terminal emulator written for MaxxTermux.
 * Handles colors, cursor movement, erasing, scroll regions, scrollback and the alternate screen.
 * Reference used while writing: the public xterm control sequence list.
 */
class TerminalEmulator(initialCols: Int, initialRows: Int) {

    class Row(cols: Int) {
        val ch = CharArray(cols) { ' ' }
        val fg = IntArray(cols)
        val bg = IntArray(cols)
        val at = ByteArray(cols)
    }

    companion object {
        const val ATTR_BOLD = 1
        const val ATTR_UNDERLINE = 2
        const val ATTR_INVERSE = 4
        private const val MAX_SCROLLBACK = 3000

        private const val S_GROUND = 0
        private const val S_ESC = 1
        private const val S_CSI = 2
        private const val S_OSC = 3
        private const val S_OSC_ESC = 4
        private const val S_SKIP1 = 5

        private fun argb(rgb: Int): Int = (0xFF000000L or rgb.toLong()).toInt()

        val PALETTE: IntArray = IntArray(256).also { p ->
            val base = intArrayOf(
                0x1A2429, 0xF07178, 0x6FD89A, 0xF2C36B, 0x7FB4FF, 0xC792EA, 0x7FD6E8, 0xCBD5DA,
                0x546E7A, 0xFF8A8A, 0x8CF0B4, 0xFFD98A, 0x9CC8FF, 0xDDB0FF, 0xA0EBF5, 0xEAF2F5,
            )
            for (i in 0 until 16) p[i] = argb(base[i])
            for (i in 16..231) {
                val idx = i - 16
                val r = idx / 36
                val g = (idx / 6) % 6
                val b = idx % 6
                fun comp(v: Int) = if (v == 0) 0 else 55 + v * 40
                p[i] = argb((comp(r) shl 16) or (comp(g) shl 8) or comp(b))
            }
            for (i in 232..255) {
                val v = 8 + 10 * (i - 232)
                p[i] = argb((v shl 16) or (v shl 8) or v)
            }
        }
    }

    var cols = initialCols
        private set
    var rows = initialRows
        private set

    var curRow = 0
        private set
    var curCol = 0
        private set

    @Volatile
    var cursorVisible = true
        private set

    @Volatile
    var appCursorKeys = false
        private set

    @Volatile
    var bracketedPaste = false
        private set

    var respond: ((String) -> Unit)? = null

    private val lock = Any()
    private var screen = ArrayList<Row>()
    private var savedMain: ArrayList<Row>? = null
    private var inAlt = false
    private val scrollback = ArrayDeque<Row>()

    private var curFg = 0
    private var curBg = 0
    private var curAt = 0
    private var scrollTop = 0
    private var scrollBottom = initialRows - 1
    private var pendingWrap = false
    private var autoWrap = true

    private var savedRow = 0
    private var savedCol = 0
    private var savedFg = 0
    private var savedBg = 0
    private var savedAt = 0
    private var altSavedRow = 0
    private var altSavedCol = 0

    private var state = S_GROUND
    private val params = IntArray(24)
    private var pcount = 0
    private var cur = 0
    private var hasCur = false
    private var priv = ' '

    init {
        for (i in 0 until rows) screen.add(Row(cols))
    }

    // ------------------------------------------------------------------ public API

    fun process(text: String) {
        synchronized(lock) {
            for (c in text) feed(c)
        }
    }

    fun <T> read(block: (TerminalEmulator) -> T): T = synchronized(lock) { block(this) }

    fun scrollbackSize(): Int = scrollback.size

    /** Index 0 is the oldest scrollback line. Call inside [read]. */
    fun rowAtVirtual(index: Int): Row =
        if (index < scrollback.size) scrollback[index] else screen[index - scrollback.size]

    fun dumpText(maxLines: Int): String = synchronized(lock) {
        val lines = ArrayList<String>()
        for (r in scrollback) lines.add(String(r.ch).trimEnd())
        for (r in screen) lines.add(String(r.ch).trimEnd())
        while (lines.isNotEmpty() && lines[lines.size - 1].isEmpty()) lines.removeAt(lines.size - 1)
        lines.takeLast(maxLines).joinToString("\n")
    }

    fun resize(newCols: Int, newRows: Int) {
        synchronized(lock) {
            if (newCols == cols && newRows == rows) return
            val saved = savedMain
            if (inAlt) {
                fit(screen, newCols, newRows, false)
                if (saved != null) fit(saved, newCols, newRows, false)
            } else {
                fit(screen, newCols, newRows, true)
            }
            cols = newCols
            rows = newRows
            scrollTop = 0
            scrollBottom = newRows - 1
            curRow = curRow.coerceIn(0, rows - 1)
            curCol = curCol.coerceIn(0, cols - 1)
            pendingWrap = false
        }
    }

    // ------------------------------------------------------------------ resize helpers

    private fun fit(list: ArrayList<Row>, newCols: Int, newRows: Int, primary: Boolean) {
        while (list.size > newRows && list.size - 1 > curRow) list.removeAt(list.size - 1)
        while (list.size > newRows) {
            val top = list.removeAt(0)
            if (primary) {
                scrollback.addLast(top)
                if (scrollback.size > MAX_SCROLLBACK) scrollback.removeFirst()
                curRow = max(0, curRow - 1)
            }
        }
        while (list.size < newRows) {
            if (primary && scrollback.isNotEmpty()) {
                list.add(0, scrollback.removeLast())
                curRow++
            } else {
                list.add(Row(newCols))
            }
        }
        for (i in list.indices) {
            val r = list[i]
            if (r.ch.size != newCols) list[i] = resized(r, newCols)
        }
    }

    private fun resized(r: Row, n: Int): Row {
        val nr = Row(n)
        val m = min(n, r.ch.size)
        System.arraycopy(r.ch, 0, nr.ch, 0, m)
        System.arraycopy(r.fg, 0, nr.fg, 0, m)
        System.arraycopy(r.bg, 0, nr.bg, 0, m)
        System.arraycopy(r.at, 0, nr.at, 0, m)
        return nr
    }

    // ------------------------------------------------------------------ parser

    private fun feed(c: Char) {
        when (state) {
            S_GROUND -> ground(c)
            S_ESC -> esc(c)
            S_CSI -> csi(c)
            S_OSC -> {
                if (c == '\u0007') state = S_GROUND else if (c == '\u001B') state = S_OSC_ESC
            }
            S_OSC_ESC -> state = S_GROUND
            S_SKIP1 -> state = S_GROUND
        }
    }

    private fun ground(c: Char) {
        when {
            c == '\u001B' -> state = S_ESC
            c == '\n' || c == '\u000B' || c == '\u000C' -> lineFeed()
            c == '\r' -> {
                curCol = 0
                pendingWrap = false
            }
            c == '\b' -> {
                if (curCol > 0) curCol--
                pendingWrap = false
            }
            c == '\t' -> {
                curCol = min((curCol / 8 + 1) * 8, cols - 1)
                pendingWrap = false
            }
            c < ' ' || c == '\u007f' -> Unit
            else -> put(c)
        }
    }

    private fun esc(c: Char) {
        state = S_GROUND
        when (c) {
            '[' -> {
                pcount = 0
                cur = 0
                hasCur = false
                priv = ' '
                state = S_CSI
            }
            ']' -> state = S_OSC
            '(', ')', '*', '+', '#', '%' -> state = S_SKIP1
            '7' -> saveCursor()
            '8' -> restoreCursor()
            'M' -> reverseIndex()
            'D' -> lineFeed()
            'E' -> {
                curCol = 0
                lineFeed()
            }
            'c' -> fullReset()
            else -> Unit
        }
    }

    private fun csi(c: Char) {
        when {
            c == '\u001B' -> state = S_ESC
            c in '0'..'9' -> {
                if (cur < 100000) cur = cur * 10 + (c - '0')
                hasCur = true
            }
            c == ';' || c == ':' -> pushParam()
            c == '?' || c == '>' || c == '=' || c == '!' -> priv = c
            c in ' '..'/' -> Unit
            c in '@'..'~' -> {
                if (hasCur || pcount > 0) pushParam()
                exec(c)
                state = S_GROUND
            }
            else -> Unit
        }
    }

    private fun pushParam() {
        if (pcount < params.size) params[pcount++] = if (hasCur) cur else 0
        cur = 0
        hasCur = false
    }

    private fun p(i: Int, def: Int): Int = if (i < pcount && params[i] != 0) params[i] else def

    // ------------------------------------------------------------------ actions

    private fun blankRow(): Row {
        val r = Row(cols)
        if (curBg != 0) java.util.Arrays.fill(r.bg, curBg)
        return r
    }

    private fun clearCells(r: Row, from: Int, to: Int) {
        val end = min(to, r.ch.size)
        for (i in max(0, from) until end) {
            r.ch[i] = ' '
            r.fg[i] = 0
            r.bg[i] = curBg
            r.at[i] = 0
        }
    }

    private fun put(c: Char) {
        if (pendingWrap) {
            if (autoWrap) {
                curCol = 0
                lineFeed()
            }
            pendingWrap = false
        }
        val r = screen[curRow]
        r.ch[curCol] = c
        r.fg[curCol] = curFg
        r.bg[curCol] = curBg
        r.at[curCol] = curAt.toByte()
        if (curCol == cols - 1) pendingWrap = true else curCol++
    }

    private fun lineFeed() {
        if (curRow == scrollBottom) scrollUp(1) else if (curRow < rows - 1) curRow++
    }

    private fun reverseIndex() {
        if (curRow == scrollTop) scrollDown(1) else if (curRow > 0) curRow--
    }

    private fun scrollUp(n: Int) {
        repeat(n) {
            val removed = screen.removeAt(scrollTop)
            if (scrollTop == 0 && scrollBottom == rows - 1 && !inAlt) {
                scrollback.addLast(removed)
                if (scrollback.size > MAX_SCROLLBACK) scrollback.removeFirst()
            }
            screen.add(scrollBottom, blankRow())
        }
    }

    private fun scrollDown(n: Int) {
        repeat(n) {
            screen.removeAt(scrollBottom)
            screen.add(scrollTop, blankRow())
        }
    }

    private fun moveRow(d: Int) {
        curRow = (curRow + d).coerceIn(0, rows - 1)
        pendingWrap = false
    }

    private fun moveCol(d: Int) {
        curCol = (curCol + d).coerceIn(0, cols - 1)
        pendingWrap = false
    }

    private fun saveCursor() {
        savedRow = curRow
        savedCol = curCol
        savedFg = curFg
        savedBg = curBg
        savedAt = curAt
    }

    private fun restoreCursor() {
        curRow = savedRow.coerceIn(0, rows - 1)
        curCol = savedCol.coerceIn(0, cols - 1)
        curFg = savedFg
        curBg = savedBg
        curAt = savedAt
        pendingWrap = false
    }

    private fun enterAlt() {
        if (inAlt) return
        altSavedRow = curRow
        altSavedCol = curCol
        savedMain = screen
        screen = ArrayList()
        for (i in 0 until rows) screen.add(Row(cols))
        inAlt = true
        scrollTop = 0
        scrollBottom = rows - 1
    }

    private fun exitAlt() {
        if (!inAlt) return
        val main = savedMain
        if (main != null) screen = main
        savedMain = null
        inAlt = false
        curRow = altSavedRow.coerceIn(0, rows - 1)
        curCol = altSavedCol.coerceIn(0, cols - 1)
        scrollTop = 0
        scrollBottom = rows - 1
    }

    private fun fullReset() {
        screen = ArrayList()
        for (i in 0 until rows) screen.add(Row(cols))
        savedMain = null
        inAlt = false
        curRow = 0
        curCol = 0
        curFg = 0
        curBg = 0
        curAt = 0
        scrollTop = 0
        scrollBottom = rows - 1
        pendingWrap = false
        autoWrap = true
        cursorVisible = true
        appCursorKeys = false
        bracketedPaste = false
    }

    private fun exec(c: Char) {
        val n1 = p(0, 1)
        when (c) {
            'A' -> moveRow(-n1)
            'B', 'e' -> moveRow(n1)
            'C', 'a' -> moveCol(n1)
            'D' -> moveCol(-n1)
            'E' -> {
                moveRow(n1)
                curCol = 0
            }
            'F' -> {
                moveRow(-n1)
                curCol = 0
            }
            'G', '`' -> {
                curCol = (n1 - 1).coerceIn(0, cols - 1)
                pendingWrap = false
            }
            'd' -> {
                curRow = (n1 - 1).coerceIn(0, rows - 1)
                pendingWrap = false
            }
            'H', 'f' -> {
                curRow = (p(0, 1) - 1).coerceIn(0, rows - 1)
                curCol = (p(1, 1) - 1).coerceIn(0, cols - 1)
                pendingWrap = false
            }
            'J' -> eraseDisplay(if (pcount > 0) params[0] else 0)
            'K' -> eraseLine(if (pcount > 0) params[0] else 0)
            'L' -> insertLines(n1)
            'M' -> deleteLines(n1)
            'P' -> deleteChars(n1)
            '@' -> insertChars(n1)
            'X' -> clearCells(screen[curRow], curCol, curCol + n1)
            'S' -> scrollUp(n1)
            'T' -> scrollDown(n1)
            'm' -> sgr()
            'r' -> setRegion()
            's' -> saveCursor()
            'u' -> restoreCursor()
            'h' -> mode(true)
            'l' -> mode(false)
            'n' -> {
                if (p(0, 0) == 6) respond?.invoke("\u001B[${curRow + 1};${curCol + 1}R")
                else if (p(0, 0) == 5) respond?.invoke("\u001B[0n")
            }
            'c' -> if (priv != '>') respond?.invoke("\u001B[?1;2c")
            else -> Unit
        }
    }

    private fun eraseDisplay(mode: Int) {
        when (mode) {
            0 -> {
                clearCells(screen[curRow], curCol, cols)
                for (i in curRow + 1 until rows) clearCells(screen[i], 0, cols)
            }
            1 -> {
                for (i in 0 until curRow) clearCells(screen[i], 0, cols)
                clearCells(screen[curRow], 0, curCol + 1)
            }
            2 -> for (i in 0 until rows) clearCells(screen[i], 0, cols)
            3 -> {
                scrollback.clear()
                for (i in 0 until rows) clearCells(screen[i], 0, cols)
            }
        }
    }

    private fun eraseLine(mode: Int) {
        val r = screen[curRow]
        when (mode) {
            0 -> clearCells(r, curCol, cols)
            1 -> clearCells(r, 0, curCol + 1)
            2 -> clearCells(r, 0, cols)
        }
    }

    private fun insertLines(n: Int) {
        if (curRow < scrollTop || curRow > scrollBottom) return
        repeat(min(n, scrollBottom - curRow + 1)) {
            screen.removeAt(scrollBottom)
            screen.add(curRow, blankRow())
        }
    }

    private fun deleteLines(n: Int) {
        if (curRow < scrollTop || curRow > scrollBottom) return
        repeat(min(n, scrollBottom - curRow + 1)) {
            screen.removeAt(curRow)
            screen.add(scrollBottom, blankRow())
        }
    }

    private fun deleteChars(n: Int) {
        val r = screen[curRow]
        val k = min(n, cols - curCol)
        val moved = cols - curCol - k
        if (moved > 0) {
            System.arraycopy(r.ch, curCol + k, r.ch, curCol, moved)
            System.arraycopy(r.fg, curCol + k, r.fg, curCol, moved)
            System.arraycopy(r.bg, curCol + k, r.bg, curCol, moved)
            System.arraycopy(r.at, curCol + k, r.at, curCol, moved)
        }
        clearCells(r, cols - k, cols)
    }

    private fun insertChars(n: Int) {
        val r = screen[curRow]
        val k = min(n, cols - curCol)
        val moved = cols - curCol - k
        if (moved > 0) {
            System.arraycopy(r.ch, curCol, r.ch, curCol + k, moved)
            System.arraycopy(r.fg, curCol, r.fg, curCol + k, moved)
            System.arraycopy(r.bg, curCol, r.bg, curCol + k, moved)
            System.arraycopy(r.at, curCol, r.at, curCol + k, moved)
        }
        clearCells(r, curCol, curCol + k)
    }

    private fun setRegion() {
        val top = p(0, 1) - 1
        val bottom = p(1, rows) - 1
        if (pcount == 0) {
            scrollTop = 0
            scrollBottom = rows - 1
        } else if (top < bottom && bottom < rows) {
            scrollTop = top
            scrollBottom = bottom
        }
        curRow = 0
        curCol = 0
        pendingWrap = false
    }

    private fun mode(on: Boolean) {
        if (priv != '?') return
        for (k in 0 until pcount) {
            when (params[k]) {
                1 -> appCursorKeys = on
                7 -> autoWrap = on
                25 -> cursorVisible = on
                47, 1047, 1049 -> if (on) enterAlt() else exitAlt()
                2004 -> bracketedPaste = on
            }
        }
    }

    private fun sgr() {
        if (pcount == 0) {
            curFg = 0
            curBg = 0
            curAt = 0
            return
        }
        var i = 0
        while (i < pcount) {
            val v = params[i]
            when {
                v == 0 -> {
                    curFg = 0
                    curBg = 0
                    curAt = 0
                }
                v == 1 -> curAt = curAt or ATTR_BOLD
                v == 4 -> curAt = curAt or ATTR_UNDERLINE
                v == 7 -> curAt = curAt or ATTR_INVERSE
                v == 22 -> curAt = curAt and ATTR_BOLD.inv()
                v == 24 -> curAt = curAt and ATTR_UNDERLINE.inv()
                v == 27 -> curAt = curAt and ATTR_INVERSE.inv()
                v in 30..37 -> curFg = PALETTE[v - 30]
                v in 40..47 -> curBg = PALETTE[v - 40]
                v in 90..97 -> curFg = PALETTE[8 + v - 90]
                v in 100..107 -> curBg = PALETTE[8 + v - 100]
                v == 39 -> curFg = 0
                v == 49 -> curBg = 0
                v == 38 || v == 48 -> {
                    var color = 0
                    var used = 0
                    if (i + 2 < pcount && params[i + 1] == 5) {
                        color = PALETTE[params[i + 2].coerceIn(0, 255)]
                        used = 2
                    } else if (i + 4 < pcount && params[i + 1] == 2) {
                        val r = params[i + 2].coerceIn(0, 255)
                        val g = params[i + 3].coerceIn(0, 255)
                        val b = params[i + 4].coerceIn(0, 255)
                        color = argb((r shl 16) or (g shl 8) or b)
                        used = 4
                    }
                    if (used > 0) {
                        if (v == 38) curFg = color else curBg = color
                        i += used
                    }
                }
            }
            i++
        }
    }
}
