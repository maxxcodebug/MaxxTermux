package com.maxxcodebug.maxxtermux.model

import com.maxxcodebug.maxxtermux.design.AxColors
import com.maxxcodebug.maxxtermux.design.AxProgressStyle
import androidx.compose.ui.graphics.Color

enum class HostKind { Server, Local, Embedded }

data class Host(
    val name: String,
    val user: String,
    val address: String,
    val kind: HostKind,
    val online: Boolean,
    val latencyMs: Int? = null,
    val cpuPercent: Int? = null,
)

data class BuildJob(
    val command: String,
    val target: String,
    val hostName: String,
    val progress: Float,
    val elapsed: String,
)

/** Saved in DataStore later. Chosen on the Settings page. */
data class ProgressPrefs(
    val style: AxProgressStyle = AxProgressStyle.WavyRing,
    val thicknessDp: Int = 6,
    val animateWave: Boolean = true,
    val color: Color = AxColors.AccentSoft,
)

object SampleData {
    val hosts = listOf(
        Host("ServerHive", "maxx", "kitkat.serverhive.in", HostKind.Server, true, 24, 78),
        Host("This phone", "local", "localhost", HostKind.Local, true),
        Host("Pi homelab", "pi", "192.168.1.20", HostKind.Embedded, false),
    )
    val build = BuildJob("mka bacon", "VoltageOS", "ServerHive", 0.82f, "1h 12m")
    val progressPrefs = ProgressPrefs()
}
