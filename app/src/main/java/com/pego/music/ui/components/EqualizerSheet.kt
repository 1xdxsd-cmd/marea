package com.pego.music.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pego.music.player.EqualizerManager
import com.pego.music.ui.theme.BricolageGrotesque
import com.pego.music.ui.theme.MareaAccent
import com.pego.music.ui.theme.MareaBg
import com.pego.music.ui.theme.MareaCard
import com.pego.music.ui.theme.MareaOnAccent
import com.pego.music.ui.theme.MareaText
import com.pego.music.ui.theme.MareaTextSecondary

/** Ecualizador (solo Premium). Con el plan gratis explica cómo activarlo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerSheet(onDismiss: () -> Unit) {
    val state by EqualizerManager.state.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MareaBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ecualizador",
                    color = MareaText,
                    fontSize = 24.sp,
                    fontFamily = BricolageGrotesque,
                    fontWeight = FontWeight.Bold
                )
                if (state.premium && state.available) {
                    Switch(
                        checked = state.enabled,
                        onCheckedChange = EqualizerManager::setEnabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MareaOnAccent,
                            checkedTrackColor = MareaAccent
                        )
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            when {
                !state.premium -> {
                    Text(
                        "El ecualizador es parte de Premium.",
                        color = MareaText,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Activa tu código de invitación en Cuenta → Suscripción para usarlo, " +
                            "escuchar sin anuncios y saltar canciones sin límite.",
                        color = MareaTextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                !state.available -> Text(
                    "Reproduce una canción para usar el ecualizador. Si sigue sin aparecer, " +
                        "tu teléfono no lo ofrece.",
                    color = MareaTextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )

                else -> {
                    if (state.presets.isNotEmpty()) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            state.presets.forEachIndexed { index, name ->
                                AssistChip(
                                    onClick = {
                                        if (!state.enabled) EqualizerManager.setEnabled(true)
                                        EqualizerManager.usePreset(index)
                                    },
                                    label = { Text(name) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = MareaCard,
                                        labelColor = MareaText
                                    )
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    state.bands.forEach { band ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = formatHz(band.centerHz),
                                color = MareaTextSecondary,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.width(64.dp)
                            )
                            Slider(
                                value = band.level.toFloat(),
                                onValueChange = {
                                    if (!state.enabled) EqualizerManager.setEnabled(true)
                                    EqualizerManager.setBandLevel(band.index, it.toInt())
                                },
                                valueRange = state.minLevel.toFloat()..state.maxLevel.toFloat(),
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = MareaAccent,
                                    activeTrackColor = MareaAccent,
                                    inactiveTrackColor = MareaCard
                                )
                            )
                            Text(
                                text = "%+d dB".format(band.level / 100),
                                color = MareaTextSecondary,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.width(52.dp).padding(start = 8.dp)
                            )
                        }
                    }

                    TextButton(onClick = EqualizerManager::reset) {
                        Text("Restablecer", color = MareaAccent)
                    }
                }
            }
        }
    }
}

private fun formatHz(hz: Int): String =
    when {
        hz < 1000 -> "$hz Hz"
        hz % 1000 < 50 || hz >= 10_000 -> "${(hz + 500) / 1000} kHz"
        else -> "${hz / 1000}.${(hz % 1000) / 100} kHz"
    }
