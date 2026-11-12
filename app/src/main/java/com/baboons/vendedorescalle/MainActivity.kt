package com.baboons.vendedorescalle

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.baboons.vendedorescalle.ui.components.BrandLogos
import com.baboons.vendedorescalle.ui.components.ErrorBanner
import com.baboons.vendedorescalle.ui.screens.LoginScreen
import com.baboons.vendedorescalle.ui.screens.MapScreen
import com.baboons.vendedorescalle.ui.screens.PlanScreen
import com.baboons.vendedorescalle.ui.screens.StatsScreen
import com.baboons.vendedorescalle.ui.screens.TodayScreen
import com.baboons.vendedorescalle.ui.theme.BondTheme
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

private enum class SellerTab(val label: String, val icon: ImageVector) {
    TODAY("Jornada", Icons.Outlined.CalendarMonth),
    PLAN("Proyectado", Icons.Outlined.Route),
    MAP("Mapa", Icons.Outlined.Map),
    STATS("Indicadores", Icons.Outlined.Insights)
}

class MainActivity : ComponentActivity() {
    private var afterLocationPermission: (() -> Unit)? = null
    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        afterLocationPermission?.invoke()
        afterLocationPermission = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { BondTheme { SellerApp() } }
    }

    private fun withLocationPermission(action: () -> Unit) {
        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) permissions += Manifest.permission.ACCESS_FINE_LOCATION
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) permissions += Manifest.permission.ACCESS_COARSE_LOCATION
        if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        if (permissions.isEmpty()) action() else {
            afterLocationPermission = action
            locationPermission.launch(permissions.toTypedArray())
        }
    }

    @Composable
    private fun SellerApp() {
        val context = LocalContext.current
        var session by remember { mutableStateOf(SessionStore.load(context)) }
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        var jornadaAbierta by remember { mutableStateOf(false) }
        var zona by remember { mutableStateOf("") }
        var messages by remember { mutableStateOf(listOf<DailyMessage>()) }
        var customers by remember { mutableStateOf(listOf<SellerCustomer>()) }
        var plan by remember { mutableStateOf(listOf<PlanEntry>()) }
        var summary by remember { mutableStateOf<SellerSummary?>(null) }
        var tab by remember { mutableStateOf(SellerTab.TODAY) }
        var notice by remember { mutableStateOf<String?>(null) }
        var prospectDialog by remember { mutableStateOf(false) }
        var prospectName by remember { mutableStateOf("") }
        var prospectPhone by remember { mutableStateOf("") }
        var prospectEmail by remember { mutableStateOf("") }
        var prospectCuit by remember { mutableStateOf("") }
        var prospectAddress by remember { mutableStateOf("") }
        var prospectProvince by remember { mutableStateOf("") }
        var prospectLocality by remember { mutableStateOf("") }
        var prospectNotes by remember { mutableStateOf("") }
        val today = remember { LocalDate.now().toString() }
        val dayStart = remember(today) { LocalDate.parse(today).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
        val dayEnd = remember(today) { LocalDate.parse(today).plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
        val db = remember { OfflineLocationDb(context) }
        var routePoints by remember { mutableStateOf(db.allPoints(dayStart, dayEnd)) }
        val pendingRequired = messages.any { it.required && it.accepted != true }
        val snackbarHostState = remember { SnackbarHostState() }

        fun fail(message: String?) { busy = false; error = message }
        fun loadDay(s: SellerSession) {
            busy = true
            BaboonsApi.jornada(s, today) { result ->
                busy = false
                if (result.error != null) { error = result.error; return@jornada }
                val data = result.json ?: JSONObject()
                val j = data.optJSONObject("jornada") ?: data
                val state = j.optString("estado").lowercase()
                jornadaAbierta = j.optBoolean("abierta") || j.optBoolean("iniciada") || state in setOf("abierta", "iniciada", "activa")
                zona = data.optJSONObject("zona")?.optString("nombre")?.takeIf { it.isNotBlank() }
                    ?: data.optString("zona_nombre")
                BaboonsApi.mensajes(s, today) { mr ->
                    if (mr.error == null) messages = extractArray(mr.json, mr.array, "mensajes").toObjectList().map { DailyMessage.fromJson(it) }
                    BaboonsApi.clientesProyectables(s, today) { cr ->
                        if (cr.error == null) customers = extractArray(cr.json, cr.array, "clientes").toObjectList().map { SellerCustomer.fromJson(it) }
                    }
                    BaboonsApi.getProyectado(s, today) { pr ->
                        if (pr.error == null) {
                            val planId = pr.json?.optLong("proyectado_id") ?: 0L
                            plan = extractArray(pr.json, pr.array, "items").toObjectList().map { PlanEntry.fromJson(it, planId) }
                        }
                    }
                    BaboonsApi.tablero(s, today) { sr -> if (sr.error == null) summary = SellerSummary.fromJson(sr.json ?: JSONObject()) }
                }
            }
        }
        LaunchedEffect(notice) { notice?.let { snackbarHostState.showSnackbar(it); notice = null } }
        LaunchedEffect(session) { session?.let(::loadDay) }
        LaunchedEffect(tab, jornadaAbierta) {
            if (tab == SellerTab.MAP && jornadaAbierta) while (true) { routePoints = db.allPoints(dayStart, dayEnd); delay(15_000) }
        }

        if (session == null) {
            LoginScreen(email, { email = it }, password, { password = it }, busy, error) {
                busy = true; error = null
                BaboonsApi.login(email.trim(), password) { result ->
                    val token = result.json?.optString("token").orEmpty()
                    session = if (result.error == null && token.isNotBlank()) SessionStore.save(context, token) else null
                    if (session == null) fail(result.error ?: "La cuenta no tiene un vendedor asociado.") else error = null
                }
            }
            return
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        BrandLogos(Modifier.weight(1f), height = 28.dp)
                        TextButton(onClick = { LocationTrackingService.stop(context); SessionStore.clear(context); session = null }) { Text("Salir") }
                    }
                    Text(session!!.sellerName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                    Text(session!!.businessName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            bottomBar = {
                NavigationBar {
                    SellerTab.entries.forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) }
                        )
                    }
                }
            },
            floatingActionButton = {
                if (jornadaAbierta) {
                    FloatingActionButton(onClick = { prospectDialog = true }, containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                        Icon(Icons.Outlined.Add, contentDescription = "Nuevo potencial")
                    }
                }
            }
        ) { inset ->
            Column(Modifier.fillMaxSize().padding(inset)) {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { ErrorBanner(it, Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
                when (tab) {
                    SellerTab.TODAY -> TodayScreen(session!!, today, zona, jornadaAbierta, messages, pendingRequired, busy,
                        onStart = { withLocationPermission {
                            val hasLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            if (!hasLocation) {
                                error = "Para comenzar la jornada tenes que habilitar el permiso de ubicacion."
                            } else {
                                busy = true
                                BaboonsApi.iniciarJornada(session!!, today) { result ->
                                    busy = false
                                    if (result.error != null) error = result.error else {
                                        jornadaAbierta = true
                                        runCatching { LocationTrackingService.start(context) }
                                            .onFailure { error = "No se pudo iniciar el seguimiento: ${it.message.orEmpty()}" }
                                        loadDay(session!!)
                                    }
                                }
                            }
                        } },
                        onRespond = { message, accepted, response ->
                            BaboonsApi.responderMensaje(session!!, message.id, accepted, response) { result ->
                                if (result.error != null) error = result.error else {
                                    messages = messages.map { if (it.id == message.id) it.copy(accepted = accepted) else it }
                                    notice = "Respuesta enviada"
                                }
                            }
                        }, onRefresh = { loadDay(session!!) }, onClose = {
                            BaboonsApi.cerrarJornada(session!!, today) { result ->
                                if (result.error != null) error = result.error else {
                                    jornadaAbierta = false
                                    LocationTrackingService.stop(context)
                                    notice = "Jornada cerrada"
                                    loadDay(session!!)
                                }
                            }
                        })
                    SellerTab.MAP -> MapScreen(routePoints, customers.filter { customer -> plan.any { it.customerId == customer.id } })
                    SellerTab.PLAN -> PlanScreen(customers, plan, jornadaAbierta, pendingRequired, busy, onVisit = { entry, result, notes ->
                        BaboonsApi.registrarVisita(session!!, today, entry.proyectadoId, entry.customerId, result, notes, UUID.randomUUID().toString()) { response ->
                            if (response.error != null) error = response.error else { notice = "Visita registrada"; loadDay(session!!) }
                        }
                    }, onSave = { amounts ->
                        val items = JSONArray()
                        amounts.forEach { (customerId, amount) -> items.put(JSONObject().put("cliente_id", customerId).put("importe_estimado", amount)) }
                        BaboonsApi.saveProyectado(session!!, today, items) { result ->
                            if (result.error != null) error = result.error else { notice = "Proyectado guardado"; loadDay(session!!) }
                        }
                    }, onRefresh = { loadDay(session!!) }, onAddException = { customerId, reason ->
                        BaboonsApi.agregarExcepcion(session!!, today, customerId, reason) { result ->
                            if (result.error != null) error = result.error else { notice = "Excepcion agregada al proyectado"; loadDay(session!!) }
                        }
                    }, onSearchCustomers = { query, callback ->
                        BaboonsApi.buscarClientes(session!!, query) { result ->
                            val list = extractArray(result.json, result.array, "clientes").toObjectList().map { SellerCustomer.fromJson(it) }
                            callback(list)
                        }
                    })
                    SellerTab.STATS -> StatsScreen(summary, onRange = { from, until ->
                        BaboonsApi.tablero(session!!, from, until) { result ->
                            if (result.error != null) error = result.error else summary = SellerSummary.fromJson(result.json ?: JSONObject())
                        }
                    })
                }
            }
        }
        if (prospectDialog) AlertDialog(
            onDismissRequest = { prospectDialog = false },
            title = { Text("Nuevo potencial") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(prospectName, { prospectName = it }, label = { Text("Nombre del comercio *") }, singleLine = true, shape = MaterialTheme.shapes.small)
                    OutlinedTextField(prospectPhone, { prospectPhone = it }, label = { Text("Telefono") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), shape = MaterialTheme.shapes.small)
                    OutlinedTextField(prospectEmail, { prospectEmail = it }, label = { Text("Correo") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), shape = MaterialTheme.shapes.small)
                    OutlinedTextField(prospectCuit, { prospectCuit = it }, label = { Text("CUIT") }, singleLine = true, shape = MaterialTheme.shapes.small)
                    OutlinedTextField(prospectAddress, { prospectAddress = it }, label = { Text("Direccion") }, singleLine = true, shape = MaterialTheme.shapes.small)
                    OutlinedTextField(prospectProvince, { prospectProvince = it }, label = { Text("Provincia") }, singleLine = true, shape = MaterialTheme.shapes.small)
                    OutlinedTextField(prospectLocality, { prospectLocality = it }, label = { Text("Localidad") }, singleLine = true, shape = MaterialTheme.shapes.small)
                    OutlinedTextField(prospectNotes, { prospectNotes = it }, label = { Text("Notas") }, shape = MaterialTheme.shapes.small)
                }
            },
            confirmButton = { Button(onClick = {
                if (prospectName.isBlank()) { error = "Completa el nombre del comercio"; return@Button }
                val payload = JSONObject().put("nombre", prospectName.trim()).put("telefono", prospectPhone.trim())
                    .put("email", prospectEmail.trim()).put("cuit", prospectCuit.trim())
                    .put("direccion", prospectAddress.trim()).put("provincia", prospectProvince.trim())
                    .put("localidad", prospectLocality.trim()).put("notas", prospectNotes.trim())
                    .put("idempotency_key", UUID.randomUUID().toString())
                BaboonsApi.crearProspecto(session!!, payload) { result ->
                    if (result.error != null) error = result.error else {
                        notice = if (result.json?.optBoolean("email_enviado") == true) "Potencial registrado; administracion recibio el aviso" else "Potencial registrado"
                        prospectDialog = false; prospectName = ""; prospectPhone = ""; prospectEmail = ""; prospectCuit = ""
                        prospectAddress = ""; prospectProvince = ""; prospectLocality = ""; prospectNotes = ""
                    }
                }
            }, shape = MaterialTheme.shapes.medium) { Text("Registrar") } },
            dismissButton = { TextButton(onClick = { prospectDialog = false }) { Text("Cancelar") } }
        )
    }
}
