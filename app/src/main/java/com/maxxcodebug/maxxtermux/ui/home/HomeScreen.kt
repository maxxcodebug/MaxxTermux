package com.maxxcodebug.maxxtermux.ui.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxxcodebug.maxxtermux.R
import com.maxxcodebug.maxxtermux.build.BuildTracker
import com.maxxcodebug.maxxtermux.design.AxButton
import com.maxxcodebug.maxxtermux.design.AxButtonKind
import com.maxxcodebug.maxxtermux.design.AxColors
import com.maxxcodebug.maxxtermux.design.AxIcon
import com.maxxcodebug.maxxtermux.design.AxProgress
import com.maxxcodebug.maxxtermux.design.AxShapes
import com.maxxcodebug.maxxtermux.design.AxText
import com.maxxcodebug.maxxtermux.design.axAppear
import com.maxxcodebug.maxxtermux.design.axClickable
import com.maxxcodebug.maxxtermux.model.BuildJob
import com.maxxcodebug.maxxtermux.model.Host
import com.maxxcodebug.maxxtermux.model.HostKind
import com.maxxcodebug.maxxtermux.model.HostStatus
import com.maxxcodebug.maxxtermux.model.ProgressPrefs
import com.maxxcodebug.maxxtermux.ssh.SshSession
import com.maxxcodebug.maxxtermux.state.AppState
import com.maxxcodebug.maxxtermux.state.Screen
import com.maxxcodebug.maxxtermux.terminal.SessionState
import kotlinx.coroutines.delay

@Composable
fun HomeScreen() {
    val hosts = AppState.hosts
    val build = BuildTracker.current
    val prefs = AppState.progressPrefs
    val connected = AppState.sessions.count { it is SshSession && it.state == SessionState.Connected }
    val online = hosts.count { AppState.statuses[it.id]?.online == true }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    LaunchedEffect(hosts.size) { AppState.probeAll() }

    Box(Modifier.fillMaxSize().background(AxColors.Bg)) {
        DecorCircles()

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .systemBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 110.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp).axAppear(0),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.ic_logo),
                        contentDescription = "MaxxTermux logo",
                        modifier = Modifier.size(46.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        AxText("MaxxTermux", size = 26.sp, weight = FontWeight.Medium)
                        if (AppState.userName.isNotBlank()) {
                            AxText("Hi, ${AppState.userName}", size = 13.sp, color = AxColors.OnMuted)
                        }
                    }
                }
                Box(
                    Modifier
                        .size(44.dp)
                        .axClickable { AppState.profileOpen = true }
                        .clip(RoundedCornerShape(16.dp))
                        .background(AxColors.Container),
                    contentAlignment = Alignment.Center,
                ) { AxText(AppState.initials(), size = 15.sp, color = Color(0xFFBFD8E2), weight = FontWeight.Medium) }
            }
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.padding(horizontal = 4.dp).axAppear(1).clickable { AppState.probeAll() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(7.dp).clip(CircleShape)
                        .background(if (connected > 0) AxColors.Online else AxColors.Offline),
                )
                Spacer(Modifier.width(6.dp))
                val line = if (hosts.isEmpty()) {
                    "No saved hosts yet"
                } else {
                    "$connected connected · $online of ${hosts.size} online"
                }
                AxText(line, size = 13.sp, color = AxColors.OnMuted)
            }
            Spacer(Modifier.height(14.dp))

            Column(Modifier.animateContentSize()) {
                if (build != null) {
                    Box(Modifier.axAppear(0)) { BuildCard(build, prefs, now) }
                    Spacer(Modifier.height(14.dp))
                }

                if (hosts.isEmpty()) {
                    Box(Modifier.axAppear(2)) { EmptyCard() }
                    Spacer(Modifier.height(14.dp))
                } else {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 6.dp).axAppear(2),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        AxText("Saved hosts", size = 18.sp, weight = FontWeight.Medium)
                        AxText("${hosts.size}", size = 13.sp, color = Color(0xFF8FC3D6))
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    hosts.forEachIndexed { i, h ->
                        val hasSession = AppState.sessions.any {
                            it is SshSession && it.host.id == h.id && it.state == SessionState.Connected
                        }
                        HostRow(h, AppState.statuses[h.id], hasSession, i + 3)
                    }
                    AddHostRow(hosts.size + 3)
                }
            }
        }

        val menu = AppState.hostMenu
        if (menu != null) HostMenu(menu)
    }
}

@Composable
private fun DecorCircles() {
    Box(Modifier.offset(x = 250.dp, y = (-30).dp).size(150.dp).clip(CircleShape).background(AxColors.CircleA))
    Box(Modifier.offset(x = (-50).dp, y = 300.dp).size(130.dp).clip(CircleShape).background(AxColors.CircleB))
    Box(Modifier.offset(x = 260.dp, y = 560.dp).size(140.dp).clip(CircleShape).background(AxColors.CircleC))
}

private fun formatElapsed(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%dh %02dm".format(s / 3600, (s % 3600) / 60) else "%dm %02ds".format(s / 60, s % 60)
}

@Composable
private fun EmptyCard() {
    Column(
        Modifier.fillMaxWidth().clip(AxShapes.Card).background(AxColors.Teal).padding(20.dp),
    ) {
        AxText("Connect like in Termux", size = 22.sp, color = AxColors.OnTeal, weight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        AxText(
            "Open the terminal and type  ssh user@host . The password is asked right there, and you can save it for next time.",
            size = 14.sp,
            color = AxColors.AccentSoft,
        )
        Spacer(Modifier.height(16.dp))
        AxButton("Open terminal", onClick = { AppState.openTerminal() }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        AxButton(
            "Add host with a form",
            onClick = { AppState.screen = Screen.AddHost },
            modifier = Modifier.fillMaxWidth(),
            kind = AxButtonKind.Secondary,
        )
    }
}

@Composable
private fun BuildCard(job: BuildJob, prefs: ProgressPrefs, now: Long) {
    val progress = job.percent / 100f
    Column(
        Modifier.fillMaxWidth().clip(AxShapes.Card).background(AxColors.Teal).padding(18.dp),
    ) {
        if (prefs.style.isRing) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
                    AxProgress(
                        progress, prefs.style, Modifier.fillMaxSize(),
                        prefs.thicknessDp.dp, prefs.color, prefs.animateWave,
                    )
                    AxText("${job.percent}%", size = 20.sp, color = AxColors.OnTeal, weight = FontWeight.Medium)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    AxText("Running now", size = 12.sp, color = AxColors.AccentSoft)
                    AxText(job.command, size = 22.sp, color = AxColors.OnTeal, weight = FontWeight.Medium, maxLines = 1)
                    AxText(job.hostName, size = 13.sp, color = AxColors.AccentSoft, maxLines = 1)
                    AxText(
                        "${job.done} / ${job.total} · ${formatElapsed(now - job.startedAt)}",
                        size = 12.sp,
                        color = AxColors.OnTealMuted,
                    )
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(Modifier.weight(1f)) {
                    AxText("Running now", size = 12.sp, color = AxColors.AccentSoft)
                    AxText(job.command, size = 22.sp, color = AxColors.OnTeal, weight = FontWeight.Medium, maxLines = 1)
                }
                AxText("${job.percent}%", size = 20.sp, color = AxColors.OnTeal, weight = FontWeight.Medium)
            }
            AxText(
                "${job.hostName} · ${job.done} / ${job.total} · ${formatElapsed(now - job.startedAt)}",
                size = 13.sp,
                color = AxColors.AccentSoft,
            )
            Spacer(Modifier.height(12.dp))
            AxProgress(
                progress, prefs.style,
                Modifier.fillMaxWidth().height(28.dp),
                prefs.thicknessDp.dp, prefs.color, prefs.animateWave,
            )
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.weight(1f)
                    .axClickable { if (AppState.active != null) AppState.screen = Screen.Shell }
                    .clip(AxShapes.Button).background(AxColors.Accent)
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) { AxText("Open terminal", size = 14.sp, color = AxColors.OnAccent, weight = FontWeight.Medium) }
            Box(
                Modifier
                    .axClickable { BuildTracker.interrupt() }
                    .clip(AxShapes.Button).background(AxColors.TealDeep)
                    .padding(horizontal = 18.dp, vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) { AxText("Stop", size = 14.sp, color = AxColors.AccentSoft, weight = FontWeight.Medium) }
        }
    }
}

private class HostLook(
    val bg: Color,
    val iconBg: Color,
    val text: Color,
    val sub: Color,
    val iconShape: Shape,
    val icon: ImageVector,
)

private fun lookFor(kind: HostKind) = when (kind) {
    HostKind.Server -> HostLook(
        AxColors.IndigoBg, AxColors.IndigoIcon, AxColors.IndigoText, AxColors.IndigoSub,
        AxShapes.IconServer, Icons.Rounded.Dns,
    )
    HostKind.Embedded -> HostLook(
        AxColors.GreenBg, AxColors.GreenIcon, AxColors.GreenText, AxColors.GreenSub,
        AxShapes.IconEmbedded, Icons.Rounded.Memory,
    )
}

@Composable
private fun HostRow(host: Host, status: HostStatus?, connected: Boolean, index: Int) {
    val s = lookFor(host.kind)
    Row(
        Modifier
            .fillMaxWidth()
            .axAppear(index)
            .clip(AxShapes.Row)
            .background(s.bg)
            .pointerInput(host.id) {
                detectTapGestures(
                    onTap = { AppState.connect(host) },
                    onLongPress = { AppState.hostMenu = host },
                )
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(s.iconShape).background(s.iconBg), contentAlignment = Alignment.Center) {
            AxIcon(s.icon, tint = s.text, size = 24.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            AxText(host.name, size = 16.sp, color = s.text, weight = FontWeight.Medium, maxLines = 1)
            AxText("${host.user}@${host.address}", size = 12.sp, color = s.sub, mono = true, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            when {
                connected -> AxText("Connected", size = 12.sp, color = AxColors.Online)
                status == null || status.checking -> AxText("Checking", size = 12.sp, color = s.sub)
                status.online -> AxText(
                    status.latencyMs?.let { "$it ms" } ?: "Online",
                    size = 12.sp,
                    color = AxColors.Online,
                )
                else -> AxText("Offline", size = 12.sp, color = AxColors.Offline)
            }
        }
    }
}

@Composable
private fun AddHostRow(index: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .axAppear(index)
            .clip(AxShapes.Row)
            .border(1.5.dp, Color(0xFF3A5662), AxShapes.Row)
            .clickable { AppState.screen = Screen.AddHost }
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AxIcon(Icons.Rounded.Add, tint = Color(0xFF8FC3D6), size = 20.dp)
        Spacer(Modifier.width(8.dp))
        AxText("Add host with a form", size = 15.sp, color = Color(0xFF8FC3D6))
    }
}

@Composable
private fun HostMenu(host: Host) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .pointerInput(Unit) { detectTapGestures { AppState.hostMenu = null } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(32.dp)
                .clip(AxShapes.Card)
                .background(AxColors.Surface)
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(20.dp),
        ) {
            AxText(host.name, size = 20.sp, weight = FontWeight.Medium)
            AxText("${host.user}@${host.address}:${host.port}", size = 13.sp, color = AxColors.OnMuted, mono = true)
            Spacer(Modifier.height(16.dp))
            AxButton(
                "Delete host",
                onClick = {
                    AppState.deleteHost(host)
                    AppState.hostMenu = null
                },
                modifier = Modifier.fillMaxWidth(),
                kind = AxButtonKind.Danger,
            )
            Spacer(Modifier.height(8.dp))
            AxButton(
                "Cancel",
                onClick = { AppState.hostMenu = null },
                modifier = Modifier.fillMaxWidth(),
                kind = AxButtonKind.Secondary,
            )
        }
    }
}
