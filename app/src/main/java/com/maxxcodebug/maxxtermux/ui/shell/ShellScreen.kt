package com.maxxcodebug.maxxtermux.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxxcodebug.maxxtermux.design.AxButton
import com.maxxcodebug.maxxtermux.design.AxChoiceChip
import com.maxxcodebug.maxxtermux.design.AxColors
import com.maxxcodebug.maxxtermux.design.AxIconButton
import com.maxxcodebug.maxxtermux.design.AxText
import com.maxxcodebug.maxxtermux.ssh.SessionState
import com.maxxcodebug.maxxtermux.ssh.SshSession
import com.maxxcodebug.maxxtermux.state.AppState
import com.maxxcodebug.maxxtermux.state.Screen
import com.maxxcodebug.maxxtermux.terminal.TerminalView
import kotlinx.coroutines.delay

private val CTRL_KEYS = listOf(
    "^C" to "\u0003",
    "^D" to "\u0004",
    "^Z" to "\u001A",
    "^R" to "\u0012",
    "^L" to "\u000C",
    "^A" to "\u0001",
    "^E" to "\u0005",
    "^W" to "\u0017",
    "^_" to "\u001F",
)

@Composable
fun ShellScreen() {
    val session = AppState.active
    if (session == null) {
        EmptyShell()
        return
    }

    var scrollOffset by remember(session) { mutableIntStateOf(0) }
    var ctrl by remember { mutableStateOf(false) }
    var showSnippets by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboardManager.current

    fun send(text: String) {
        scrollOffset = 0
        session.send(text)
    }

    fun arrow(c: Char) {
        send(if (session.emulator.appCursorKeys) "\u001BO$c" else "\u001B[$c")
    }

    fun focusKeyboard() {
        try {
            focus.requestFocus()
        } catch (e: Exception) {
        }
        keyboard?.show()
    }

    fun typed(text: String) {
        var t = text
        if (ctrl) {
            ctrl = false
            if (t.length == 1) {
                val lc = t[0].lowercaseChar()
                if (lc in 'a'..'z') t = (lc.code and 0x1f).toChar().toString()
            }
        }
        send(t)
    }

    val onHardwareKey: (KeyEvent) -> Boolean = hw@{ ev ->
        if (ev.type != KeyEventType.KeyDown) return@hw false
        when (ev.key) {
            Key.Enter, Key.NumPadEnter -> {
                send("\r")
                true
            }
            Key.Backspace -> {
                send("\u007f")
                true
            }
            Key.Tab -> {
                send("\t")
                true
            }
            Key.Escape -> {
                send("\u001B")
                true
            }
            Key.DirectionUp -> {
                arrow('A')
                true
            }
            Key.DirectionDown -> {
                arrow('B')
                true
            }
            Key.DirectionRight -> {
                arrow('C')
                true
            }
            Key.DirectionLeft -> {
                arrow('D')
                true
            }
            else -> {
                if (ev.isCtrlPressed) {
                    val u = ev.nativeKeyEvent.getUnicodeChar(0)
                    if (u in 'a'.code..'z'.code) {
                        send((u and 0x1f).toChar().toString())
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }
            }
        }
    }

    LaunchedEffect(session) {
        delay(200)
        focusKeyboard()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(AxColors.Bg)
            .systemBarsPadding()
            .imePadding()
            .padding(horizontal = 10.dp),
    ) {
        ShellHeader(session) {
            clipboard.setText(AnnotatedString(session.emulator.dumpText(400)))
        }

        Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(20.dp))) {
            TerminalView(
                session = session,
                fontSp = AppState.fontSp,
                scrollOffset = scrollOffset,
                onScrollChange = { scrollOffset = it },
                onTap = { focusKeyboard() },
            )
            KeyCatcher(
                focus = focus,
                onText = { typed(it) },
                onBackspace = { send("\u007f") },
                onEnter = { send("\r") },
                onHardwareKey = onHardwareKey,
            )
        }

        Spacer(Modifier.size(8.dp))

        if (showSnippets) {
            SnippetPanel(onPaste = { send(it) }, onRun = { send(it + "\r") })
            Spacer(Modifier.size(8.dp))
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            KeyCap("Esc", Modifier.weight(1f)) { send("\u001B") }
            KeyCap("Tab", Modifier.weight(1f)) { send("\t") }
            KeyCap("Ctrl", Modifier.weight(1f), selected = ctrl) { ctrl = !ctrl }
            KeyCap("↑", Modifier.weight(1f)) { arrow('A') }
            KeyCap("↓", Modifier.weight(1f)) { arrow('B') }
            KeyCap("←", Modifier.weight(1f)) { arrow('D') }
            KeyCap("→", Modifier.weight(1f)) { arrow('C') }
            KeyCap("Paste", Modifier.weight(1.4f)) {
                val text = clipboard.getText()?.text
                if (!text.isNullOrEmpty()) {
                    send(if (session.emulator.bracketedPaste) "\u001B[200~$text\u001B[201~" else text)
                }
            }
        }
        Spacer(Modifier.size(6.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            KeyCap("Snippets", selected = showSnippets) { showSnippets = !showSnippets }
            CTRL_KEYS.forEach { (label, code) -> KeyCap(label) { send(code) } }
            KeyCap("^0") { AppState.fontSp = AppState.DEFAULT_FONT_SP }
            KeyCap("A−") { AppState.fontSp = (AppState.fontSp - 1f).coerceAtLeast(8f) }
            KeyCap("A+") { AppState.fontSp = (AppState.fontSp + 1f).coerceAtMost(28f) }
            listOf("~", "/", "|", "-", "_", "\\", "$").forEach { sym -> KeyCap(sym) { typed(sym) } }
        }
    }
}

@Composable
private fun EmptyShell() {
    Column(
        Modifier.fillMaxSize().background(AxColors.Bg).systemBarsPadding().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AxText("No active session", size = 22.sp, weight = FontWeight.Medium)
        Spacer(Modifier.size(8.dp))
        AxText("Pick a host on Home to connect.", size = 14.sp, color = AxColors.OnMuted)
        Spacer(Modifier.size(20.dp))
        AxButton("Go to hosts", onClick = { AppState.screen = Screen.Home })
    }
}

@Composable
private fun ShellHeader(session: SshSession, onCopy: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AxIconButton(Icons.AutoMirrored.Rounded.ArrowBack, { AppState.screen = Screen.Home })
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                AxText(session.host.name, size = 17.sp, weight = FontWeight.Medium, maxLines = 1)
                val status: Pair<String, Color> = when (session.state) {
                    SessionState.Connecting -> Pair("Connecting...", AxColors.AccentSoft)
                    SessionState.Connected -> Pair("${session.host.user}@${session.host.address}", AxColors.Online)
                    SessionState.Closed -> Pair("Disconnected", AxColors.Offline)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(status.second))
                    Spacer(Modifier.width(6.dp))
                    AxText(status.first, size = 12.sp, color = AxColors.OnMuted, maxLines = 1)
                }
            }
            if (session.state == SessionState.Closed) {
                AxChoiceChip("Reconnect", true) { session.connect() }
                Spacer(Modifier.width(8.dp))
            }
            AxIconButton(Icons.Rounded.ContentCopy, onCopy)
            Spacer(Modifier.width(8.dp))
            AxIconButton(Icons.Rounded.Close, { AppState.closeSession(session) })
        }
        if (AppState.sessions.size > 1) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AppState.sessions.toList().forEach { s ->
                    AxChoiceChip(s.host.name, s === session) { AppState.active = s }
                }
            }
        }
    }
}

@Composable
private fun KeyCap(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) AxColors.Accent else AxColors.Container)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        AxText(
            label,
            size = 14.sp,
            color = if (selected) AxColors.OnAccent else AxColors.OnSurface,
            weight = FontWeight.Medium,
            mono = true,
        )
    }
}

@Composable
private fun SnippetPanel(onPaste: (String) -> Unit, onRun: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 190.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(AxColors.Surface)
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
    ) {
        AxText("Tap to paste · hold to run", size = 11.sp, color = AxColors.OnMuted)
        val groups = AppState.snippets.groupBy { it.group }
        for ((group, items) in groups) {
            Spacer(Modifier.size(8.dp))
            AxText(group, size = 12.sp, color = Color(0xFF8FC3D6))
            Spacer(Modifier.size(4.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items.forEach { sn ->
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(AxColors.Teal)
                            .pointerInput(sn.id) {
                                detectTapGestures(
                                    onTap = { onPaste(sn.text) },
                                    onLongPress = { onRun(sn.text) },
                                )
                            }
                            .padding(horizontal = 11.dp, vertical = 8.dp),
                    ) {
                        AxText(sn.text, size = 12.sp, color = AxColors.OnTeal, mono = true, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Invisible text field that receives the soft keyboard and hardware keys. */
@Composable
private fun KeyCatcher(
    focus: FocusRequester,
    onText: (String) -> Unit,
    onBackspace: () -> Unit,
    onEnter: () -> Unit,
    onHardwareKey: (KeyEvent) -> Boolean,
) {
    var tfv by remember { mutableStateOf(TextFieldValue(" ", TextRange(1))) }
    BasicTextField(
        value = tfv,
        onValueChange = { nv ->
            val t = nv.text
            when {
                t.isEmpty() -> onBackspace()
                t == " " -> Unit
                t.startsWith(" ") -> onText(t.substring(1).replace('\n', '\r'))
                else -> onText(t.replace('\n', '\r'))
            }
            tfv = TextFieldValue(" ", TextRange(1))
        },
        modifier = Modifier
            .size(1.dp)
            .alpha(0f)
            .focusRequester(focus)
            .onPreviewKeyEvent { onHardwareKey(it) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { onEnter() }),
    )
}
