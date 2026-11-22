package com.pego.music.data.access

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

sealed interface AccessState {
    /** Verificando el acceso guardado con el servidor. */
    data object Checking : AccessState

    /** Sin acceso. [needsNetwork] = hay sesión guardada pero no se pudo verificar. */
    data class Locked(
        val message: String? = null,
        val needsNetwork: Boolean = false
    ) : AccessState

    data class Granted(
        val planLabel: String,
        val family: String,
        val label: String,
        val offline: Boolean = false
    ) : AccessState
}

/**
 * Control de acceso privado (planes Dúo / Familiar por invitación).
 * El servidor (carpeta /server) decide quién entra; la app guarda un token por dispositivo.
 */
class AccessRepository(
    context: Context,
    private val client: OkHttpClient,
    private val baseUrl: String
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow<AccessState>(
        if (prefs.getString(KEY_TOKEN, null) == null) AccessState.Locked() else AccessState.Checking
    )
    val state: StateFlow<AccessState> = _state.asStateFlow()

    private var lastCheckMs = 0L

    private val deviceId: String
        get() = prefs.getString(KEY_DEVICE, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_DEVICE, it).apply()
        }

    /** Canjea un código de invitación. Devuelve null si salió bien, o el mensaje de error. */
    suspend fun redeem(rawCode: String): String? = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("code", rawCode.trim())
            .put("deviceId", deviceId)
            .put("deviceName", "${Build.MANUFACTURER} ${Build.MODEL}".take(60))
            .toString()
            .toRequestBody(JSON)
        val request = Request.Builder().url("$baseUrl/v1/redeem").post(body).build()
        try {
            client.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                val json = runCatching { JSONObject(text) }.getOrNull()
                if (resp.isSuccessful && json != null && json.optString("token").isNotEmpty()) {
                    saveSession(json)
                    lastCheckMs = System.currentTimeMillis()
                    _state.value = granted(json, offline = false)
                    null
                } else {
                    json?.optString("error")?.takeIf { it.isNotEmpty() }
                        ?: "No se pudo validar el código (${resp.code})"
                }
            }
        } catch (e: IOException) {
            "No se pudo conectar con el servidor. Revisa tu internet."
        }
    }

    /** Verifica la sesión guardada. Con [force] = false se salta si se verificó hace poco. */
    suspend fun verify(force: Boolean = false) = withContext(Dispatchers.IO) {
        val token = prefs.getString(KEY_TOKEN, null)
        if (token == null) {
            _state.value = AccessState.Locked()
            return@withContext
        }
        val now = System.currentTimeMillis()
        if (!force && _state.value is AccessState.Granted && now - lastCheckMs < STALE_MS) {
            return@withContext
        }
        val request = Request.Builder()
            .url("$baseUrl/v1/me")
            .header("Authorization", "Bearer $token")
            .build()
        try {
            client.newCall(request).execute().use { resp ->
                when {
                    resp.isSuccessful -> {
                        val json = JSONObject(resp.body?.string().orEmpty())
                        saveSession(json, keepToken = true)
                        lastCheckMs = now
                        _state.value = granted(json, offline = false)
                    }
                    resp.code == 401 || resp.code == 403 -> {
                        clearSession()
                        _state.value = AccessState.Locked(
                            "Tu acceso fue revocado o el plan se desactivó. Pide un código nuevo."
                        )
                    }
                    else -> offlineFallback(now)
                }
            }
        } catch (e: IOException) {
            offlineFallback(now)
        } catch (e: org.json.JSONException) {
            offlineFallback(now)
        }
    }

    fun logout() {
        clearSession()
        _state.value = AccessState.Locked()
    }

    private fun offlineFallback(now: Long) {
        val lastOk = prefs.getLong(KEY_LAST_OK, 0L)
        val family = prefs.getString(KEY_FAMILY, null)
        if (family != null && now - lastOk < GRACE_MS) {
            _state.value = AccessState.Granted(
                planLabel = prefs.getString(KEY_PLAN, "").orEmpty(),
                family = family,
                label = prefs.getString(KEY_LABEL, "").orEmpty(),
                offline = true
            )
        } else {
            _state.value = AccessState.Locked(
                "Conéctate a internet para verificar tu acceso.",
                needsNetwork = true
            )
        }
    }

    private fun granted(json: JSONObject, offline: Boolean) = AccessState.Granted(
        planLabel = json.optString("planLabel"),
        family = json.optString("family"),
        label = json.optString("label"),
        offline = offline
    )

    private fun saveSession(json: JSONObject, keepToken: Boolean = false) {
        prefs.edit().apply {
            if (!keepToken) putString(KEY_TOKEN, json.getString("token"))
            putString(KEY_PLAN, json.optString("planLabel"))
            putString(KEY_FAMILY, json.optString("family"))
            putString(KEY_LABEL, json.optString("label"))
            putLong(KEY_LAST_OK, System.currentTimeMillis())
        }.apply()
    }

    private fun clearSession() {
        prefs.edit()
            .remove(KEY_TOKEN).remove(KEY_PLAN).remove(KEY_FAMILY)
            .remove(KEY_LABEL).remove(KEY_LAST_OK)
            .apply()
    }

    private companion object {
        const val PREFS = "pego_access"
        const val KEY_TOKEN = "token"
        const val KEY_DEVICE = "device_id"
        const val KEY_PLAN = "plan"
        const val KEY_FAMILY = "family"
        const val KEY_LABEL = "label"
        const val KEY_LAST_OK = "last_ok"

        val JSON = "application/json; charset=utf-8".toMediaType()

        /** Reverificar como máximo cada 15 minutos al volver a la app. */
        const val STALE_MS = 15L * 60L * 1000L

        /** Sin internet, se permite usar la app hasta 7 días desde la última verificación. */
        const val GRACE_MS = 7L * 24L * 60L * 60L * 1000L
    }
}
