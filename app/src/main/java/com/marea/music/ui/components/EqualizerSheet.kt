package com.marea.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import com.marea.music.ui.theme.MareaCyan
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
import com.marea.music.player.EqualizerManager
import com.marea.music.ui.theme.BricolageGrotesque
import com.marea.music.ui.theme.MareaAccent
import com.marea.music.ui.theme.MareaBg
import com.marea.music.ui.theme.MareaCard
import com.marea.music.ui.theme.MareaOnAccent
import com.marea.music.ui.theme.MareaText
import com.marea.music.ui.theme.MareaTextSecondary

/** Ecualizador (solo Premium). Con el plan gratis explica cómo activarlo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerSheet(onDismiss: () -> Unit) {
    val state by EqualizerManager.state.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0E1C27),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .background(Glass.accentHorizontal, RoundedCornerShape(2.dp))
            )
        }
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = MareaCyan,
                        modifier = Modifier.glow(MareaAccent, spread = 8.dp, alpha = 0.45f)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Ecualizador",
                        color = MareaText,
                        fontSize = 24.sp,
                        fontFamily = BricolageGrotesque,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(Modifier.width(8.dp))
                    NeonTag("Premium")
                }
                if (state.premium && state.available) {
                    Switch(
                        checked = state.enabled,
                        onCheckedChange = EqualizerManager::setEnabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MareaOnAccent,
                            checkedTrackColor = MareaAccent,
                            uncheckedTrackColor = Color.White.copy(alpha = 0.08f),
                            uncheckedBorderColor = Color.White.copy(alpha = 0.2f)
                        )
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            when {
                !state.premium -> GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = MareaCyan)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "El ecualizador es parte de Premium.",
                        color = MareaText,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Activa tu código en Cuenta para usarlo, escuchar sin anuncios, " +
                            "descargar canciones y saltar sin límite.",
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
                                GlassChip(
                                    text = name,
                                    selected = false,
                                    onClick = {
                                        if (!state.enabled) EqualizerManager.setEnabled(true)
                                        EqualizerManager.usePreset(index)
                                    }
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
                                    thumbColor = MareaCyan,
                                    activeTrackColor = MareaAccent,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.10f)
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
                        Text("Restablecer", color = MareaCyan, fontWeight = FontWeight.SemiBold)
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
