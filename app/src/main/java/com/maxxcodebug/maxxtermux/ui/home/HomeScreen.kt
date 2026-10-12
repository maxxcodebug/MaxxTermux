package com.maxxcodebug.maxxtermux.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxxcodebug.maxxtermux.design.AxColors
import com.maxxcodebug.maxxtermux.design.AxGlassDock
import com.maxxcodebug.maxxtermux.design.AxIcon
import com.maxxcodebug.maxxtermux.design.AxProgress
import com.maxxcodebug.maxxtermux.design.AxShapes
import com.maxxcodebug.maxxtermux.design.AxTab
import com.maxxcodebug.maxxtermux.design.AxText
import com.maxxcodebug.maxxtermux.model.BuildJob
import com.maxxcodebug.maxxtermux.model.Host
import com.maxxcodebug.maxxtermux.model.HostKind
import com.maxxcodebug.maxxtermux.model.ProgressPrefs
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    hosts: List<Host>,
    build: BuildJob?,
    prefs: ProgressPrefs,
    onOpenSession: () -> Unit = {},
    onHostClick: (Host) -> Unit = {},
    onAddHost: () -> Unit = {},
    onNewConnection: () -> Unit = {},
) {
    var tab by remember { mutableStateOf(AxTab.Home) }
    val online = hosts.count { it.online }

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
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                AxText("MaxxTermux", size = 28.sp, weight = FontWeight.Medium)
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(16.dp)).background(AxColors.Container),
                    contentAlignment = Alignment.Center,
                ) { AxText("AX", size = 15.sp, color = Color(0xFFBFD8E2), weight = FontWeight.Medium) }
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(AxColors.Online))
                Spacer(Modifier.width(6.dp))
                val running = if (build != null) "1 build running" else "No builds running"
                AxText("$running · $online hosts online", size = 13.sp, color = AxColors.OnMuted)
            }
            Spacer(Modifier.height(14.dp))

            if (build != null) {
                BuildCard(build, prefs, onOpenSession)
                Spacer(Modifier.height(14.dp))
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                AxText("Hosts", size = 18.sp, weight = FontWeight.Medium)
                AxText("${hosts.size} saved", size = 13.sp, color = Color(0xFF8FC3D6))
            }
            Spacer(Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                hosts.forEach { HostRow(it) { onHostClick(it) } }
                AddHostRow(onAddHost)
            }
        }

        AxGlassDock(
            selected = tab,
            onSelect = { tab = it },
            onFab = onNewConnection,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
        )
    }
}

@Composable
private fun DecorCircles() {
    Box(Modifier.offset(x = 250.dp, y = (-30).dp).size(150.dp).clip(CircleShape).background(AxColors.CircleA))
    Box(Modifier.offset(x = (-50).dp, y = 300.dp).size(130.dp).clip(CircleShape).background(AxColors.CircleB))
    Box(Modifier.offset(x = 260.dp, y = 560.dp).size(140.dp).clip(CircleShape).background(AxColors.CircleC))
}

@Composable
private fun BuildCard(job: BuildJob, prefs: ProgressPrefs, onOpen: () -> Unit) {
    val pct = (job.progress * 100).roundToInt()
    Column(
        Modifier.fillMaxWidth().clip(AxShapes.Card).background(AxColors.Teal).padding(18.dp),
    ) {
        if (prefs.style.isRing) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
                    AxProgress(
                        job.progress, prefs.style, Modifier.fillMaxSize(),
                        prefs.thicknessDp.dp, prefs.color, prefs.animateWave,
                    )
                    AxText("$pct%", size = 20.sp, color = AxColors.OnTeal, weight = FontWeight.Medium)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    AxText("Running now", size = 12.sp, color = AxColors.AccentSoft)
                    AxText(job.command, size = 22.sp, color = AxColors.OnTeal, weight = FontWeight.Medium)
                    AxText("${job.target} · ${job.hostName}", size = 13.sp, color = AxColors.AccentSoft)
                    AxText("Elapsed ${job.elapsed}", size = 12.sp, color = AxColors.OnTealMuted)
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Column {
                    AxText("Running now", size = 12.sp, color = AxColors.AccentSoft)
                    AxText(job.command, size = 22.sp, color = AxColors.OnTeal, weight = FontWeight.Medium)
                }
                AxText("$pct%", size = 20.sp, color = AxColors.OnTeal, weight = FontWeight.Medium)
            }
            AxText("${job.target} · ${job.hostName} · ${job.elapsed}", size = 13.sp, color = AxColors.AccentSoft)
            Spacer(Modifier.height(12.dp))
            AxProgress(
                job.progress, prefs.style,
                Modifier.fillMaxWidth().height(28.dp),
                prefs.thicknessDp.dp, prefs.color, prefs.animateWave,
            )
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.weight(1f).clip(AxShapes.Button).background(AxColors.Accent)
                    .clickable { onOpen() }.padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) { AxText("Open session", size = 14.sp, color = AxColors.OnAccent, weight = FontWeight.Medium) }
            Box(
                Modifier.size(46.dp).clip(AxShapes.Button).background(AxColors.TealDeep),
                contentAlignment = Alignment.Center,
            ) { AxIcon(Icons.Rounded.Notifications, tint = AxColors.AccentSoft, size = 20.dp) }
        }
    }
}

private data class HostStyle(
    val bg: Color, val iconBg: Color, val text: Color, val sub: Color,
    val iconShape: Shape, val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private fun styleFor(kind: HostKind) = when (kind) {
    HostKind.Server -> HostStyle(
        AxColors.IndigoBg, AxColors.IndigoIcon, AxColors.IndigoText, AxColors.IndigoSub,
        AxShapes.IconServer, Icons.Rounded.Dns,
    )
    HostKind.Local -> HostStyle(
        AxColors.GreenBg, AxColors.GreenIcon, AxColors.GreenText, AxColors.GreenSub,
        AxShapes.IconDevice, Icons.Rounded.PhoneAndroid,
    )
    HostKind.Embedded -> HostStyle(
        AxColors.RedBg, AxColors.RedIcon, AxColors.RedText, AxColors.RedSub,
        AxShapes.IconEmbedded, Icons.Rounded.Memory,
    )
}

@Composable
private fun HostRow(host: Host, onClick: () -> Unit) {
    val s = styleFor(host.kind)
    Row(
        Modifier.fillMaxWidth().clip(AxShapes.Row).background(s.bg)
            .clickable { onClick() }.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(s.iconShape).background(s.iconBg), contentAlignment = Alignment.Center) {
            AxIcon(s.icon, tint = s.text, size = 24.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            AxText(host.name, size = 16.sp, color = s.text, weight = FontWeight.Medium, maxLines = 1)
            AxText(
                if (host.kind == HostKind.Local) "Local shell" else "${host.user}@${host.address.substringBefore('.')}",
                size = 12.sp, color = s.sub, mono = host.kind != HostKind.Local, maxLines = 1,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            if (host.online) {
                AxText(
                    host.latencyMs?.let { "$it ms" } ?: "Ready",
                    size = 12.sp, color = AxColors.Online,
                )
                host.cpuPercent?.let { AxText("CPU $it%", size = 11.sp, color = s.sub) }
            } else {
                AxText("Offline", size = 12.sp, color = AxColors.Offline)
            }
        }
    }
}

@Composable
private fun AddHostRow(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(AxShapes.Row)
            .border(1.5.dp, Color(0xFF3A5662), AxShapes.Row)
            .clickable { onClick() }.padding(16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AxIcon(Icons.Rounded.Add, tint = Color(0xFF8FC3D6), size = 20.dp)
        Spacer(Modifier.width(8.dp))
        AxText("Add host", size = 15.sp, color = Color(0xFF8FC3D6))
    }
}
