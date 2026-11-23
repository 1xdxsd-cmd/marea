package com.marea.music.player

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.Equalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EqualizerBand(
    val index: Int,
    /** Frecuencia central en Hz. */
    val centerHz: Int,
    /** Nivel en milibelios. */
    val level: Int
)

data class EqualizerUiState(
    /** false si el teléfono no ofrece ecualizador o el reproductor aún no arrancó. */
    val available: Boolean = false,
    val enabled: Boolean = false,
    val premium: Boolean = false,
    val minLevel: Int = -1500,
    val maxLevel: Int = 1500,
    val bands: List<EqualizerBand> = emptyList(),
    val presets: List<String> = emptyList()
)

/**
 * Ecualizador del sistema (android.media.audiofx) enganchado a la sesión de audio del
 * reproductor. Solo se aplica con Premium; los ajustes se guardan en el teléfono.
 */
object EqualizerManager {
    private const val PREFS = "marea_equalizer"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_LEVELS = "levels"

    private var prefs: SharedPreferences? = null
    private var equalizer: Equalizer? = null
    private var sessionId: Int = 0
    private var premium = false

    private val _state = MutableStateFlow(EqualizerUiState())
    val state: StateFlow<EqualizerUiState> = _state.asStateFlow()

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            publish()
        }
    }

    /** Lo llama el servicio de reproducción cuando conoce la sesión de audio del reproductor. */
    fun attach(context: Context, audioSessionId: Int) {
        init(context)
        if (audioSessionId <= 0 || (audioSessionId == sessionId && equalizer != null)) return
        release()
        sessionId = audioSessionId
        equalizer = runCatching { Equalizer(0, audioSessionId) }.getOrNull()
        restoreLevels()
        applyEnabled()
        publish()
    }

    fun release() {
        runCatching { equalizer?.release() }
        equalizer = null
        sessionId = 0
        publish()
    }

    fun setPremium(value: Boolean) {
        if (premium == value) return
        premium = value
        applyEnabled()
        publish()
    }

    fun setEnabled(value: Boolean) {
        prefs?.edit()?.putBoolean(KEY_ENABLED, value)?.apply()
        applyEnabled()
        publish()
    }

    fun setBandLevel(band: Int, level: Int) {
        val eq = equalizer ?: return
        val range = eq.bandLevelRange
        runCatching { eq.setBandLevel(band.toShort(), level.coerceIn(range[0].toInt(), range[1].toInt()).toShort()) }
        saveLevels()
        publish()
    }

    fun usePreset(index: Int) {
        val eq = equalizer ?: return
        runCatching { eq.usePreset(index.toShort()) }
        saveLevels()
        publish()
    }

    fun reset() {
        val eq = equalizer ?: return
        for (b in 0 until eq.numberOfBands) runCatching { eq.setBandLevel(b.toShort(), 0) }
        saveLevels()
        publish()
    }

    private fun applyEnabled() {
        val wanted = premium && (prefs?.getBoolean(KEY_ENABLED, false) ?: false)
        runCatching { equalizer?.enabled = wanted }
    }

    private fun saveLevels() {
        val eq = equalizer ?: return
        val levels = (0 until eq.numberOfBands).joinToString(",") { b ->
            runCatching { eq.getBandLevel(b.toShort()).toString() }.getOrDefault("0")
        }
        prefs?.edit()?.putString(KEY_LEVELS, levels)?.apply()
    }

    private fun restoreLevels() {
        val eq = equalizer ?: return
        val saved = prefs?.getString(KEY_LEVELS, null)?.split(",")?.mapNotNull { it.toShortOrNull() } ?: return
        if (saved.size != eq.numberOfBands.toInt()) return
        saved.forEachIndexed { b, level -> runCatching { eq.setBandLevel(b.toShort(), level) } }
    }

    private fun publish() {
        val eq = equalizer
        val enabledPref = prefs?.getBoolean(KEY_ENABLED, false) ?: false
        if (eq == null) {
            _state.value = EqualizerUiState(available = false, enabled = enabledPref, premium = premium)
            return
        }
        _state.value = runCatching {
            val range = eq.bandLevelRange
            EqualizerUiState(
                available = true,
                enabled = enabledPref,
                premium = premium,
                minLevel = range[0].toInt(),
                maxLevel = range[1].toInt(),
                bands = (0 until eq.numberOfBands).map { b ->
                    EqualizerBand(
                        index = b,
                        centerHz = eq.getCenterFreq(b.toShort()) / 1000,
                        level = eq.getBandLevel(b.toShort()).toInt()
                    )
                },
                presets = (0 until eq.numberOfPresets).map { p -> eq.getPresetName(p.toShort()) }
            )
        }.getOrDefault(EqualizerUiState(available = false, enabled = enabledPref, premium = premium))
    }
}
