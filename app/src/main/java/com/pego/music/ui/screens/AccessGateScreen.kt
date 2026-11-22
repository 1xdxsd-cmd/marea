package com.pego.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pego.music.data.access.AccessRepository
import com.pego.music.data.access.AccessState
import com.pego.music.ui.theme.PegoBlack
import com.pego.music.ui.theme.PegoGreen
import com.pego.music.ui.theme.PegoTextSecondary
import kotlinx.coroutines.launch

/** Muestra [content] solo si el servidor confirma que este dispositivo tiene acceso. */
@Composable
fun AccessGate(
    access: AccessRepository,
    content: @Composable () -> Unit
) {
    val state by access.state.collectAsState()
    when (val s = state) {
        AccessState.Checking -> Box(
            modifier = Modifier.fillMaxSize().background(PegoBlack),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = PegoGreen)
        }

        is AccessState.Locked -> AccessGateScreen(access = access, state = s)
        is AccessState.Granted -> content()
    }
}

@Composable
private fun AccessGateScreen(
    access: AccessRepository,
    state: AccessState.Locked
) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        if (loading || code.isBlank()) return
        loading = true
        error = null
        scope.launch {
            error = access.redeem(code)
            loading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PegoBlack)
            .systemBarsPadding()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "PEGO",
            color = PegoGreen,
            fontSize = 44.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (state.needsNetwork) {
                "No pudimos verificar tu acceso"
            } else {
                "Esta app es privada. Ingresa el código de invitación de tu plan."
            },
            color = PegoTextSecondary,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(28.dp))

        if (state.needsNetwork) {
            Button(
                onClick = { scope.launch { access.verify(force = true) } },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PegoGreen)
            ) {
                Text("Reintentar", fontWeight = FontWeight.Bold)
            }
        } else {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.uppercase().take(16) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Código (PEGO-XXXX-XXXX)") },
                isError = error != null,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                shape = RoundedCornerShape(16.dp)
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = ::submit,
                enabled = !loading && code.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PegoGreen,
                    contentColor = androidx.compose.ui.graphics.Color.Black
                )
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = androidx.compose.ui.graphics.Color.Black
                    )
                } else {
                    Text("Entrar", fontWeight = FontWeight.Bold)
                }
            }
        }

        val message = error ?: state.message
        if (message != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = message,
                color = if (error != null) MaterialTheme.colorScheme.error else PegoTextSecondary,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
