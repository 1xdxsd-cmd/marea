package com.marea.music.player

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.LoudnessEnhancer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sube el volumen por encima del máximo del teléfono para canciones que suenan bajito
 * (android.media.audiofx.LoudnessEnhancer). Disponible en todos los planes.
 */
object VolumeBoostManager {
    /** Niveles en dB que se ofrecen en el reproductor. */
    val LEVELS_DB = listOf(0, 3, 6, 9)

    private const val PREFS = "marea_volume"
    private const val KEY_DB = "boost_db"

    private var prefs: SharedPreferences? = null
    private var enhancer: LoudnessEnhancer? = null
    private var sessionId = 0

    private val _boostDb = MutableStateFlow(0)
    val boostDb: StateFlow<Int> = _boostDb.asStateFlow()

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            _boostDb.value = prefs?.getInt(KEY_DB, 0)?.takeIf { it in LEVELS_DB } ?: 0
        }
    }

    fun attach(context: Context, audioSessionId: Int) {
        init(context)
        if (audioSessionId <= 0 || (audioSessionId == sessionId && enhancer != null)) return
        release()
        sessionId = audioSessionId
        enhancer = runCatching { LoudnessEnhancer(audioSessionId) }.getOrNull()
        apply()
    }

    fun setBoost(db: Int) {
        val value = db.takeIf { it in LEVELS_DB } ?: 0
        _boostDb.value = value
        prefs?.edit()?.putInt(KEY_DB, value)?.apply()
        apply()
    }

    fun release() {
        runCatching { enhancer?.release() }
        enhancer = null
        sessionId = 0
    }

    private fun apply() {
        val fx = enhancer ?: return
        runCatching {
            val db = _boostDb.value
            fx.setTargetGain(db * 100)
            fx.enabled = db > 0
        }
    }
}
