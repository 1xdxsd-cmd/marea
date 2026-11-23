package com.marea.music.data.access

import com.marea.music.player.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Mantiene el acceso al día mientras la app está abierta:
 * - Revisa la suscripción cada minuto, así si se quita Premium se nota sin cerrar sesión.
 * - Un solo dispositivo reproduciendo por cuenta: al empezar a sonar toma la cuenta y, si otro
 *   teléfono la toma después, este se pausa.
 */
class AccessMonitor(
    private val access: AccessRepository,
    private val player: PlayerController
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun start() {
        // Premium (sin anuncios, saltos ilimitados, ecualizador) sigue a la suscripción en vivo.
        scope.launch {
            access.state.collect { player.setPremium(it.hasAccess) }
        }

        scope.launch {
            while (true) {
                delay(VERIFY_EVERY_MS)
                if (access.state.value is AccessState.SignedIn) access.verify(force = true)
            }
        }

        scope.launch {
            player.uiState
                .map { it.isPlaying }
                .distinctUntilChanged()
                .collectLatest { playing ->
                    if (!playing || access.state.value !is AccessState.SignedIn) return@collectLatest
                    access.playbackHeartbeat(claim = true)
                    while (true) {
                        delay(HEARTBEAT_EVERY_MS)
                        if (access.playbackHeartbeat(claim = false) == false) {
                            player.pauseWithMessage("Tu cuenta empezó a sonar en otro dispositivo")
                            break
                        }
                    }
                }
        }
    }

    private companion object {
        const val VERIFY_EVERY_MS = 60_000L
        const val HEARTBEAT_EVERY_MS = 30_000L
    }
}
