package com.marea.music.data.access

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

/** Plan al que pertenece la cuenta (se activa con un código de invitación desde la sección Suscripción). */
data class Subscription(
    val planLabel: String,
    val family: String,
    val label: String,
    val active: Boolean
)

sealed interface AccessState {
    /** Verificando la sesión guardada con el servidor. */
    data object Checking : AccessState

    /** Sin sesión. [needsNetwork] = hay sesión guardada pero no se pudo verificar. */
    data class SignedOut(
        val message: String? = null,
        val needsNetwork: Boolean = false
    ) : AccessState

    /** Sesión iniciada. Solo se usa la app si [subscription] está activa. */
    data class SignedIn(
        val name: String,
        val email: String,
        val subscription: Subscription?,
        val offline: Boolean = false,
        val message: String? = null
    ) : AccessState {
        val hasAccess: Boolean get() = subscription?.active == true
    }
}

val AccessState.hasAccess: Boolean
    get() = this is AccessState.SignedIn && this.hasAccess

/**
 * Cuentas (correo + contraseña) y suscripción por código de invitación.
 * El servidor (carpeta /server) decide quién entra; la app guarda el token de la sesión.
 */
class AccessRepository(
    context: Context,
    private val client: OkHttpClient,
    private val baseUrl: String
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow<AccessState>(
        if (prefs.getString(KEY_SESSION, null) == null) AccessState.SignedOut() else AccessState.Checking
    )
    val state: StateFlow<AccessState> = _state.asStateFlow()

    private var lastCheckMs = 0L

    private val deviceId: String
        get() = prefs.getString(KEY_DEVICE, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_DEVICE, it).apply()
        }

    private val deviceName: String
        get() = "${Build.MANUFACTURER} ${Build.MODEL}".take(60)

    /** Inicia sesión. Devuelve null si salió bien, o el mensaje de error. */
    suspend fun login(email: String, password: String): String? =
        authenticate(
            "/v1/auth/login",
            JSONObject().put("email", email.trim()).put("password", password)
        )

    /** Crea una cuenta nueva e inicia sesión. Devuelve null si salió bien, o el mensaje de error. */
    suspend fun register(name: String, email: String, password: String): String? =
        authenticate(
            "/v1/auth/register",
            JSONObject().put("name", name.trim()).put("email", email.trim()).put("password", password)
        )

    private suspend fun authenticate(path: String, body: JSONObject): String? = withContext(Dispatchers.IO) {
        body.put("deviceId", deviceId).put("deviceName", deviceName)
        // Si este teléfono ya había canjeado un código (versión anterior), el plan pasa a la cuenta.
        prefs.getString(KEY_LEGACY_TOKEN, null)?.let { body.put("legacyToken", it) }
        val request = Request.Builder()
            .url("$baseUrl$path")
            .post(body.toString().toRequestBody(JSON))
            .build()
        try {
            client.newCall(request).execute().use { resp ->
                val json = runCatching { JSONObject(resp.body?.string().orEmpty()) }.getOrNull()
                if (resp.isSuccessful && json != null && json.optString("token").isNotEmpty()) {
                    prefs.edit()
                        .putString(KEY_SESSION, json.getString("token"))
                        .remove(KEY_LEGACY_TOKEN)
                        .apply()
                    applyMe(json)
                    null
                } else {
                    json?.optString("error")?.takeIf { it.isNotEmpty() }
                        ?: "No se pudo iniciar sesión (${resp.code})"
                }
            }
        } catch (e: IOException) {
            "No se pudo conectar con el servidor. Revisa tu internet."
        } catch (e: org.json.JSONException) {
            "Respuesta inválida del servidor. Intenta de nuevo."
        }
    }

    /** Activa la suscripción con un código de invitación. Devuelve null si salió bien, o el error. */
    suspend fun redeem(rawCode: String): String? = withContext(Dispatchers.IO) {
        val token = prefs.getString(KEY_SESSION, null)
            ?: return@withContext "Inicia sesión primero."
        val body = JSONObject()
            .put("code", rawCode.trim())
            .put("deviceId", deviceId)
            .put("deviceName", deviceName)
            .toString()
            .toRequestBody(JSON)
        val request = Request.Builder()
            .url("$baseUrl/v1/subscription/redeem")
            .header("Authorization", "Bearer $token")
            .post(body)
            .build()
        try {
            client.newCall(request).execute().use { resp ->
                val json = runCatching { JSONObject(resp.body?.string().orEmpty()) }.getOrNull()
                when {
                    resp.isSuccessful && json != null -> {
                        val sub = parseSubscription(json.optJSONObject("subscription"))
                        val current = _state.value as? AccessState.SignedIn
                        if (current != null) {
                            _state.value = current.copy(subscription = sub, offline = false, message = null)
                        }
                        saveCache(_state.value)
                        lastCheckMs = System.currentTimeMillis()
                        null
                    }
                    resp.code == 401 -> {
                        signOutLocally("Tu sesión se cerró. Vuelve a iniciar sesión.")
                        "Tu sesión se cerró. Vuelve a iniciar sesión."
                    }
                    else -> json?.optString("error")?.takeIf { it.isNotEmpty() }
                        ?: "No se pudo validar el código (${resp.code})"
                }
            }
        } catch (e: IOException) {
            "No se pudo conectar con el servidor. Revisa tu internet."
        }
    }

    /** Verifica la sesión guardada. Con [force] = false se salta si se verificó hace poco. */
    suspend fun verify(force: Boolean = false) = withContext(Dispatchers.IO) {
        val token = prefs.getString(KEY_SESSION, null)
        if (token == null) {
            if (_state.value !is AccessState.SignedOut) _state.value = AccessState.SignedOut()
            return@withContext
        }
        val now = System.currentTimeMillis()
        if (!force && _state.value is AccessState.SignedIn && now - lastCheckMs < STALE_MS) {
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
                        val previous = _state.value
                        applyMe(JSONObject(resp.body?.string().orEmpty()))
                        val current = _state.value
                        if (previous.hasAccess && current is AccessState.SignedIn && !current.hasAccess) {
                            _state.value = current.copy(
                                message = "Tu acceso fue revocado o el plan se desactivó. Pide un código nuevo."
                            )
                        }
                    }
                    resp.code == 401 -> signOutLocally("Tu sesión se cerró. Vuelve a iniciar sesión.")
                    else -> offlineFallback(now)
                }
            }
        } catch (e: IOException) {
            offlineFallback(now)
        } catch (e: org.json.JSONException) {
            offlineFallback(now)
        }
    }

    /** Cierra la sesión en el servidor (si hay internet) y en el teléfono. */
    suspend fun logout() = withContext(Dispatchers.IO) {
        val token = prefs.getString(KEY_SESSION, null)
        if (token != null) {
            val request = Request.Builder()
                .url("$baseUrl/v1/auth/logout")
                .header("Authorization", "Bearer $token")
                .post(ByteArray(0).toRequestBody(JSON))
                .build()
            runCatching { client.newCall(request).execute().close() }
        }
        signOutLocally(null)
    }

    private fun applyMe(json: JSONObject) {
        val user = json.optJSONObject("user") ?: throw org.json.JSONException("Respuesta sin usuario")
        val state = AccessState.SignedIn(
            name = user.optString("name"),
            email = user.optString("email"),
            subscription = parseSubscription(json.optJSONObject("subscription")),
            offline = false
        )
        lastCheckMs = System.currentTimeMillis()
        _state.value = state
        saveCache(state)
    }

    private fun parseSubscription(json: JSONObject?): Subscription? {
        if (json == null) return null
        return Subscription(
            planLabel = json.optString("planLabel"),
            family = json.optString("family"),
            label = json.optString("label"),
            active = json.optBoolean("active", false)
        )
    }

    private fun offlineFallback(now: Long) {
        val lastOk = prefs.getLong(KEY_LAST_OK, 0L)
        val email = prefs.getString(KEY_EMAIL, null)
        if (email != null && now - lastOk < GRACE_MS) {
            val family = prefs.getString(KEY_FAMILY, null)
            _state.value = AccessState.SignedIn(
                name = prefs.getString(KEY_NAME, "").orEmpty(),
                email = email,
                subscription = family?.let {
                    Subscription(
                        planLabel = prefs.getString(KEY_PLAN, "").orEmpty(),
                        family = it,
                        label = prefs.getString(KEY_LABEL, "").orEmpty(),
                        active = true
                    )
                },
                offline = true
            )
        } else {
            _state.value = AccessState.SignedOut(
                "Conéctate a internet para verificar tu acceso.",
                needsNetwork = true
            )
        }
    }

    private fun saveCache(state: AccessState) {
        if (state !is AccessState.SignedIn) return
        val sub = state.subscription?.takeIf { it.active }
        prefs.edit().apply {
            putString(KEY_NAME, state.name)
            putString(KEY_EMAIL, state.email)
            if (sub != null) {
                putString(KEY_PLAN, sub.planLabel)
                putString(KEY_FAMILY, sub.family)
                putString(KEY_LABEL, sub.label)
            } else {
                remove(KEY_PLAN).remove(KEY_FAMILY).remove(KEY_LABEL)
            }
            putLong(KEY_LAST_OK, System.currentTimeMillis())
        }.apply()
    }

    private fun signOutLocally(message: String?) {
        prefs.edit()
            .remove(KEY_SESSION).remove(KEY_NAME).remove(KEY_EMAIL)
            .remove(KEY_PLAN).remove(KEY_FAMILY).remove(KEY_LABEL).remove(KEY_LAST_OK)
            .apply()
        lastCheckMs = 0L
        _state.value = AccessState.SignedOut(message)
    }

    private companion object {
        const val PREFS = "marea_access"
        const val KEY_SESSION = "session_token"
        /** Token de la versión anterior (acceso solo con código). */
        const val KEY_LEGACY_TOKEN = "token"
        const val KEY_DEVICE = "device_id"
        const val KEY_NAME = "user_name"
        const val KEY_EMAIL = "user_email"
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
