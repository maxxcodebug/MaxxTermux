package com.maxxcodebug.maxxtermux.ui.addhost

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxxcodebug.maxxtermux.data.SecretBox
import com.maxxcodebug.maxxtermux.design.AxButton
import com.maxxcodebug.maxxtermux.design.AxButtonKind
import com.maxxcodebug.maxxtermux.design.AxChoiceChip
import com.maxxcodebug.maxxtermux.design.AxColors
import com.maxxcodebug.maxxtermux.design.AxField
import com.maxxcodebug.maxxtermux.design.AxScreenHeader
import com.maxxcodebug.maxxtermux.design.AxText
import com.maxxcodebug.maxxtermux.model.AuthType
import com.maxxcodebug.maxxtermux.model.Host
import com.maxxcodebug.maxxtermux.model.HostKind
import com.maxxcodebug.maxxtermux.state.AppState
import com.maxxcodebug.maxxtermux.state.Screen
import java.util.UUID

@Composable
fun AddHostScreen() {
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("22") }
    var user by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(HostKind.Server) }
    var auth by remember { mutableStateOf(AuthType.Password) }
    var secret by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun save() {
        val addr = address.trim()
        val usr = user.trim()
        val p = port.trim().toIntOrNull()
        when {
            addr.isEmpty() -> error = "Enter the host address."
            usr.isEmpty() -> error = "Enter the user name."
            p == null || p !in 1..65535 -> error = "Port must be between 1 and 65535."
            secret.isEmpty() -> error = if (auth == AuthType.Password) "Enter the password." else "Paste the private key."
            else -> {
                AppState.addHost(
                    Host(
                        id = UUID.randomUUID().toString(),
                        name = name.trim().ifEmpty { addr },
                        user = usr,
                        address = addr,
                        port = p,
                        kind = kind,
                        authType = auth,
                        secretEnc = SecretBox.encrypt(secret),
                        passphraseEnc = SecretBox.encrypt(passphrase),
                    ),
                )
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(AxColors.Bg)
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        AxScreenHeader("Add host", "New connection", onBack = { AppState.screen = Screen.Home })

        AxField("Name (optional)", name, { name = it })
        Spacer(Modifier.height(12.dp))
        AxField("Address", address, { address = it }, keyboardType = KeyboardType.Uri)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AxField("User", user, { user = it }, Modifier.weight(1f))
            AxField("Port", port, { port = it }, Modifier.weight(0.5f), keyboardType = KeyboardType.Number)
        }

        Spacer(Modifier.height(16.dp))
        AxText("Type", size = 12.sp, color = AxColors.OnMuted, modifier = Modifier.padding(start = 6.dp, bottom = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AxChoiceChip("Server", kind == HostKind.Server) { kind = HostKind.Server }
            AxChoiceChip("Device", kind == HostKind.Embedded) { kind = HostKind.Embedded }
        }

        Spacer(Modifier.height(16.dp))
        AxText("Sign in with", size = 12.sp, color = AxColors.OnMuted, modifier = Modifier.padding(start = 6.dp, bottom = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AxChoiceChip("Password", auth == AuthType.Password) { auth = AuthType.Password }
            AxChoiceChip("Private key", auth == AuthType.Key) { auth = AuthType.Key }
        }

        Spacer(Modifier.height(12.dp))
        if (auth == AuthType.Password) {
            AxField("Password", secret, { secret = it }, password = true)
        } else {
            AxField(
                "Private key (paste the full text)",
                secret,
                { secret = it },
                singleLine = false,
                minHeight = 140.dp,
                mono = true,
            )
            Spacer(Modifier.height(12.dp))
            AxField("Key passphrase (optional)", passphrase, { passphrase = it }, password = true)
        }

        val err = error
        if (err != null) {
            Spacer(Modifier.height(12.dp))
            AxText(err, size = 14.sp, color = Color(0xFFF09595))
        }

        Spacer(Modifier.height(20.dp))
        AxButton("Save host", onClick = { save() }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        AxButton(
            "Cancel",
            onClick = { AppState.screen = Screen.Home },
            modifier = Modifier.fillMaxWidth(),
            kind = AxButtonKind.Secondary,
        )
        Spacer(Modifier.height(24.dp))
    }
}
