package com.marea.music.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marea.music.R
import com.marea.music.data.access.AccessRepository
import com.marea.music.data.access.AccessState
import com.marea.music.data.access.FamilyDetails
import com.marea.music.data.access.PlanOption
import androidx.compose.runtime.produceState
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.foundation.layout.Row
import com.marea.music.ui.theme.BricolageGrotesque
import com.marea.music.ui.theme.MareaAccent
import com.marea.music.ui.theme.MareaBg
import com.marea.music.ui.theme.MareaCard
import com.marea.music.ui.theme.MareaOnAccent
import com.marea.music.ui.theme.MareaText
import com.marea.music.ui.theme.MareaTextSecondary
import kotlinx.coroutines.launch

/**
 * Muestra [content] cuando hay sesión iniciada. Sin sesión: inicio de sesión / registro.
 * Sin suscripción la app funciona en el plan gratis (anuncios y saltos limitados);
 * el código se activa en la pestaña Cuenta.
 */
@Composable
fun AccessGate(
    access: AccessRepository,
    content: @Composable () -> Unit
) {
    val state by access.state.collectAsState()
    when (val s = state) {
        AccessState.Checking -> Box(
            modifier = Modifier.fillMaxSize().background(MareaBg),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = MareaAccent)
        }

        is AccessState.SignedOut -> LoginScreen(access = access, state = s)
        is AccessState.SignedIn -> content()
    }
}

@Composable
private fun LoginScreen(
    access: AccessRepository,
    state: AccessState.SignedOut
) {
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var registering by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val canSubmit = !loading && email.isNotBlank() && password.isNotEmpty() &&
        (!registering || (name.isNotBlank() && confirm.isNotEmpty()))

    fun submit() {
        if (!canSubmit) return
        if (registering && password.length < 8) {
            error = "La contraseña debe tener al menos 8 caracteres"
            return
        }
        if (registering && password != confirm) {
            error = "Las contraseñas no coinciden"
            return
        }
        loading = true
        error = null
        focus.clearFocus()
        scope.launch {
            error = if (registering) {
                access.register(name, email, password)
            } else {
                access.login(email, password)
            }
            loading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MareaBg)
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.marea_logo),
            contentDescription = null,
            modifier = Modifier.size(88.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Marea",
            color = MareaText,
            fontSize = 44.sp,
            fontFamily = BricolageGrotesque,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = when {
                state.needsNetwork -> "No pudimos verificar tu acceso"
                registering -> "Crea tu cuenta"
                else -> "Inicia sesión para continuar"
            },
            color = MareaTextSecondary,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(28.dp))

        if (state.needsNetwork) {
            Button(
                onClick = { scope.launch { access.verify(force = true) } },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MareaAccent,
                    contentColor = MareaOnAccent
                )
            ) {
                Text("Reintentar", fontWeight = FontWeight.Bold)
            }
        } else {
            if (registering) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(80) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Nombre") },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                    shape = RoundedCornerShape(16.dp)
                )
                Spacer(Modifier.height(12.dp))
            }
            OutlinedTextField(
                value = email,
                onValueChange = { email = it.trim().take(120) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Correo") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                shape = RoundedCornerShape(16.dp)
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it.take(128) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(if (registering) "Contraseña (mín. 8)" else "Contraseña") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = if (registering) ImeAction.Next else ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focus.moveFocus(FocusDirection.Down) },
                    onDone = { submit() }
                ),
                shape = RoundedCornerShape(16.dp)
            )
            if (registering) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it.take(128) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Repite la contraseña") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    shape = RoundedCornerShape(16.dp)
                )
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = ::submit,
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MareaAccent,
                    contentColor = MareaOnAccent
                )
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = MareaOnAccent
                    )
                } else {
                    Text(if (registering) "Crear cuenta" else "Iniciar sesión", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = {
                    registering = !registering
                    error = null
                    confirm = ""
                },
                enabled = !loading
            ) {
                Text(
                    text = if (registering) "¿Ya tienes cuenta? Inicia sesión" else "¿No tienes cuenta? Regístrate",
                    color = MareaAccent
                )
            }
        }

        val message = error ?: state.message
        if (message != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = message,
                color = if (error != null) MaterialTheme.colorScheme.error else MareaTextSecondary,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * Sección Cuenta / Suscripción: muestra el plan de la cuenta y permite activarlo con
 * un código de invitación (MAREA-XXXX-XXXX). También sirve para cerrar sesión.
 */
@Composable
fun SubscriptionScreen(
    access: AccessRepository,
    state: AccessState.SignedIn,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var code by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }

    fun submit() {
        if (loading || code.isBlank()) return
        loading = true
        error = null
        success = null
        focus.clearFocus()
        scope.launch {
            error = access.redeem(code)
            if (error == null) {
                code = ""
                success = "¡Listo! Ya tienes Premium."
            }
            loading = false
        }
    }

    val sub = state.subscription

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp)
    ) {
        Text(
            text = "Cuenta",
            color = MareaText,
            fontSize = 32.sp,
            fontFamily = BricolageGrotesque,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.height(4.dp))
        Text(state.name, color = MareaText, style = MaterialTheme.typography.titleMedium)
        Text(state.email, color = MareaTextSecondary, style = MaterialTheme.typography.bodyMedium)

        Spacer(Modifier.height(28.dp))
        Text(
            text = "Suscripción",
            color = MareaText,
            fontSize = 22.sp,
            fontFamily = BricolageGrotesque,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MareaCard, RoundedCornerShape(18.dp))
                .padding(18.dp)
        ) {
            if (sub != null && sub.active) {
                Text("Premium · Plan ${sub.planLabel}", color = MareaAccent, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(4.dp))
                Text(sub.family, color = MareaText, style = MaterialTheme.typography.bodyLarge)
                sub.price?.let { price ->
                    Text("$$price ${sub.currency} al mes", color = MareaTextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = sub.expiresAt?.let { expiryText(it) } ?: "Sin fecha de vencimiento",
                    color = MareaText,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                if (state.offline) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Sin conexión: se verificará cuando vuelvas a tener internet.",
                        color = MareaTextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                Text("Plan gratis", color = MareaText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(6.dp))
                if (sub != null && sub.expired && sub.expiresAt != null) {
                    Text(
                        "Tu plan ${sub.planLabel} venció el ${formatDate(sub.expiresAt)}. Pide que lo renueven o activa otro código.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    "Con anuncios y hasta 6 saltos de canción por hora. Ingresa el código de " +
                        "invitación de tu plan para pasarte a Premium: sin anuncios, saltos sin " +
                        "límite y ecualizador.",
                    color = MareaTextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))
                PlansList(access = access)
                Spacer(Modifier.height(16.dp))
                Text(
                    "¿Ya tienes tu código? Escríbelo aquí:",
                    color = MareaText,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().take(16) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Código (MAREA-XXXX-XXXX)") },
                    isError = error != null,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    shape = RoundedCornerShape(16.dp)
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = ::submit,
                    enabled = !loading && code.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MareaAccent,
                        contentColor = MareaOnAccent
                    )
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp,
                            color = MareaOnAccent
                        )
                    } else {
                        Text("Activar", fontWeight = FontWeight.Bold)
                    }
                }
            }

            val message = error ?: success ?: state.message
            if (message != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = message,
                    color = if (error != null) MaterialTheme.colorScheme.error else MareaTextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        val details = sub?.takeIf { it.active }?.details
        if (details != null) {
            Spacer(Modifier.height(20.dp))
            FamilySection(access = access, details = details)
        }
        Spacer(Modifier.height(28.dp))
        OutlinedButton(
            onClick = { scope.launch { access.logout() } },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(25.dp)
        ) {
            Text("Cerrar sesión", color = MareaText, fontWeight = FontWeight.Bold)
        }
    }
}

/** Personas del plan Dúo / Familiar. El dueño (quien puso el código) suma o quita correos. */
@Composable
private fun FamilySection(
    access: AccessRepository,
    details: FamilyDetails
) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }

    fun run(block: suspend () -> String?, okMessage: String?) {
        if (busy) return
        busy = true
        error = null
        info = null
        scope.launch {
            error = block()
            if (error == null) info = okMessage
            busy = false
        }
    }

    Text(
        text = "Personas en tu plan (${details.members.size}/${details.seats})",
        color = MareaText,
        fontSize = 20.sp,
        fontFamily = BricolageGrotesque,
        fontWeight = FontWeight.Bold
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = when {
            details.seats <= 1 -> "El plan Individual es solo para ti."
            details.isOwner -> "Tú pusiste el código, así que puedes sumar a otras personas con su correo."
            else -> "${details.ownerName ?: "El dueño del plan"} te compartió este plan."
        } + " Cada cuenta reproduce en un solo dispositivo a la vez.",
        color = MareaTextSecondary,
        style = MaterialTheme.typography.bodySmall
    )
    Spacer(Modifier.height(12.dp))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MareaCard, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        details.members.forEach { m ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = m.name + when {
                            m.isYou -> " (tú)"
                            else -> ""
                        },
                        color = MareaText,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    val sub = listOfNotNull(if (m.isOwner) "Dueño del plan" else null, m.email).joinToString(" · ")
                    if (sub.isNotEmpty()) {
                        Text(sub, color = MareaTextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (details.isOwner && !m.isYou) {
                    TextButton(
                        onClick = { run({ access.removeFamilyMember(m.id) }, "${m.name} ya no está en tu plan") },
                        enabled = !busy
                    ) {
                        Text("Quitar", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        details.pendingEmails.forEach { p ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(p.email, color = MareaText, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Pendiente: entra cuando cree su cuenta con este correo",
                        color = MareaTextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                TextButton(
                    onClick = { run({ access.removePendingEmail(p.id) }, null) },
                    enabled = !busy
                ) {
                    Text("Cancelar", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (details.isOwner && details.seats > 1) {
        Spacer(Modifier.height(12.dp))
        if (details.seatsFree > 0) {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it.trim().take(120) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Correo de la persona") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = {
                    if (email.isNotBlank()) run({ access.addFamilyEmail(email).also { if (it == null) email = "" } }, "Listo, ya puede disfrutar tu plan")
                }),
                shape = RoundedCornerShape(16.dp)
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    run({ access.addFamilyEmail(email).also { if (it == null) email = "" } }, "Listo, ya puede disfrutar tu plan")
                },
                enabled = !busy && email.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MareaAccent,
                    contentColor = MareaOnAccent
                )
            ) {
                Text("Agregar persona (${details.seatsFree} cupo${if (details.seatsFree == 1) "" else "s"} libre${if (details.seatsFree == 1) "" else "s"})", fontWeight = FontWeight.Bold)
            }
        } else {
            Text("Tu plan ya no tiene cupos libres.", color = MareaTextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }

    val message = error ?: info
    if (message != null) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            color = if (error != null) MaterialTheme.colorScheme.error else MareaTextSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun formatDate(ms: Long): String =
    SimpleDateFormat("d 'de' MMMM 'de' yyyy", Locale("es")).format(java.util.Date(ms))

private fun expiryText(ms: Long): String {
    val days = ((ms - System.currentTimeMillis()) / 86_400_000L).coerceAtLeast(0L)
    val left = when (days) {
        0L -> "vence hoy"
        1L -> "queda 1 día"
        else -> "quedan $days días"
    }
    return "Vence el ${formatDate(ms)} ($left)"
}

/** Planes que se pueden contratar, con precio y cuántas personas incluyen. */
@Composable
private fun PlansList(access: AccessRepository) {
    val plans by produceState(initialValue = emptyList<PlanOption>(), access) {
        value = access.fetchPlans()
    }
    Text(
        "Planes Premium",
        color = MareaText,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
    )
    Spacer(Modifier.height(8.dp))
    if (plans.isEmpty()) {
        CircularProgressIndicator(color = MareaAccent, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        return
    }
    plans.forEach { plan ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .background(MareaBg, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(plan.label, color = MareaText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    text = when (plan.seats) {
                        1 -> "1 persona"
                        else -> "Hasta ${plan.seats} personas"
                    } + " · sin anuncios · saltos ilimitados · ecualizador",
                    color = MareaTextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.size(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text("$${plan.price}", color = MareaAccent, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                Text("${plan.currency}/mes", color = MareaTextSecondary, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    Text(
        "Para contratar un plan pide tu código de invitación.",
        color = MareaTextSecondary,
        style = MaterialTheme.typography.bodySmall
    )
}
