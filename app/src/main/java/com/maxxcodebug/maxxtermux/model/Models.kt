package com.maxxcodebug.maxxtermux.model

import androidx.compose.ui.graphics.Color
import com.maxxcodebug.maxxtermux.design.AxColors
import com.maxxcodebug.maxxtermux.design.AxProgressStyle

enum class HostKind { Server, Embedded }

enum class AuthType { Password, Key }

data class Host(
    val id: String,
    val name: String,
    val user: String,
    val address: String,
    val port: Int = 22,
    val kind: HostKind = HostKind.Server,
    val authType: AuthType = AuthType.Password,
    /** Encrypted password or private key text (see SecretBox). */
    val secretEnc: String = "",
    /** Encrypted key passphrase, optional. */
    val passphraseEnc: String = "",
)

data class HostStatus(
    val checking: Boolean = false,
    val online: Boolean = false,
    val latencyMs: Int? = null,
)

data class Snippet(val id: String, val group: String, val text: String)

data class BuildJob(
    val command: String,
    val hostName: String,
    val percent: Int,
    val done: Int,
    val total: Int,
    val startedAt: Long,
)

/** Saved in DataStore later. Chosen on the Settings page. */
data class ProgressPrefs(
    val style: AxProgressStyle = AxProgressStyle.WavyRing,
    val thicknessDp: Int = 6,
    val animateWave: Boolean = true,
    val color: Color = AxColors.AccentSoft,
)
