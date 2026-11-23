package com.marea.music.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Posición de la canción que suena. Se lee solo donde hace falta (barra de progreso y letra)
 * para que el resto de la app no se recomponga 4 veces por segundo.
 */
val LocalPlaybackPosition = staticCompositionLocalOf<StateFlow<Long>> { MutableStateFlow(0L) }
