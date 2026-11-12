package com.baboons.vendedorescalle

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.delay

private enum class SellerTab(val label: String) { TODAY("Hoy"), MAP("Mapa"), PLAN("Proyectado"), STATS("Resumen") }

class MainActivity : ComponentActivity() {
    private var afterLocationPermission: (() -> Unit)? = null
    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        afterLocationPermission?.invoke()
        afterLocationPermission = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { SellerApp() } }
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
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Bond Vendedores", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("${session!!.sellerName} - ${session!!.businessName}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { LocationTrackingService.stop(context); SessionStore.clear(context); session = null }) { Text("Salir") }
                }
            },
            bottomBar = {
                NavigationBar {
                    SellerTab.entries.forEach { item ->
                        NavigationBarItem(selected = tab == item, onClick = { tab = item }, icon = {}, label = { Text(item.label) })
                    }
                }
            },
            floatingActionButton = {
                if (jornadaAbierta) FloatingActionButton(onClick = { prospectDialog = true }) { Text("+") }
            }
        ) { inset ->
            Column(Modifier.fillMaxSize().padding(inset)) {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp)) }
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
                    OutlinedTextField(prospectName, { prospectName = it }, label = { Text("Nombre del comercio *") }, singleLine = true)
                    OutlinedTextField(prospectPhone, { prospectPhone = it }, label = { Text("Telefono") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                    OutlinedTextField(prospectEmail, { prospectEmail = it }, label = { Text("Correo") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                    OutlinedTextField(prospectCuit, { prospectCuit = it }, label = { Text("CUIT") }, singleLine = true)
                    OutlinedTextField(prospectAddress, { prospectAddress = it }, label = { Text("Direccion") }, singleLine = true)
                    OutlinedTextField(prospectProvince, { prospectProvince = it }, label = { Text("Provincia") }, singleLine = true)
                    OutlinedTextField(prospectLocality, { prospectLocality = it }, label = { Text("Localidad") }, singleLine = true)
                    OutlinedTextField(prospectNotes, { prospectNotes = it }, label = { Text("Notas") })
                }
            },
            confirmButton = { TextButton(onClick = {
                if (prospectName.isBlank()) { error = "Completa el nombre del comercio"; return@TextButton }
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
            }) { Text("Registrar") } },
            dismissButton = { TextButton(onClick = { prospectDialog = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun LoginScreen(email: String, onEmail: (String) -> Unit, password: String, onPassword: (String) -> Unit,
                        busy: Boolean, error: String?, onLogin: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center) {
        Text("Iniciar sesion", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp)); Text("Ingresa con tu usuario de Baboons")
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(email, onEmail, Modifier.fillMaxWidth(), label = { Text("Correo") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(password, onPassword, Modifier.fillMaxWidth(), label = { Text("Contrasena") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
        Spacer(Modifier.height(20.dp))
        Button(onClick = onLogin, enabled = !busy && email.isNotBlank() && password.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Entrar")
        }
    }
}

@Composable
private fun TodayScreen(session: SellerSession, date: String, zone: String, open: Boolean, messages: List<DailyMessage>,
                        pending: Boolean, busy: Boolean, onStart: () -> Unit, onRespond: (DailyMessage, Boolean, String) -> Unit,
                        onRefresh: () -> Unit, onClose: () -> Unit) {
    val replies = remember { mutableStateMapOf<Long, String>() }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Spacer(Modifier.height(2.dp)); Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Text(if (open) "Jornada en curso" else "Tu dia empieza aca", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("$date${if (zone.isNotBlank()) " - Zona $zone" else ""}", modifier = Modifier.padding(top = 4.dp))
                if (!open) Button(onClick = onStart, enabled = !busy, modifier = Modifier.padding(top = 12.dp)) { Text("Comenzar jornada") }
                else {
                    Text("Seguimiento de ubicacion activo", modifier = Modifier.padding(top = 6.dp))
                    TextButton(onClick = onClose) { Text("Cerrar jornada") }
                }
            }
        } }
        item { Text("Mensajes del administrador", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        if (messages.isEmpty()) item { Text("No hay mensajes para hoy.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(messages, key = { it.id }) { message ->
            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(message.title.ifBlank { "Mensaje del dia" }, fontWeight = FontWeight.SemiBold)
                    Text(message.content, modifier = Modifier.padding(top = 6.dp))
                    if (message.required) Text("Requiere aceptacion", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                    when (message.accepted) {
                        true -> Text("Aceptado", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                        false -> Text("No aceptado", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                        null -> Column(Modifier.padding(top = 8.dp)) {
                            OutlinedTextField(
                                value = replies[message.id].orEmpty(),
                                onValueChange = { replies[message.id] = it },
                                label = { Text("Respuesta o comentario") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onRespond(message, true, replies[message.id].orEmpty().ifBlank { "Aceptado" }) }) {
                                    Text(if (message.required) "Leido y aceptado" else "Enviar respuesta")
                                }
                                if (!message.required) TextButton(onClick = { onRespond(message, false, replies[message.id].orEmpty().ifBlank { "No aceptado" }) }) { Text("No acepto") }
                            }
                        }
                    }
                }
            }
        }
        if (pending && open) item { Text("Acepta los mensajes obligatorios para habilitar el proyectado.", color = MaterialTheme.colorScheme.error) }
        item { OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text("Actualizar") } }
    }
}

@Composable
private fun PlanScreen(customers: List<SellerCustomer>, plan: List<PlanEntry>, open: Boolean, pending: Boolean, busy: Boolean,
                       onVisit: (PlanEntry, String, String) -> Unit,
                       onSave: (Map<Long, Double>) -> Unit, onRefresh: () -> Unit,
                       onAddException: (Long, String) -> Unit,
                       onSearchCustomers: (String, (List<SellerCustomer>) -> Unit) -> Unit) {
    val selected = remember { mutableStateMapOf<Long, Double>() }
    var visitTarget by remember { mutableStateOf<PlanEntry?>(null) }
    var visitResult by remember { mutableStateOf("venta") }
    var visitNotes by remember { mutableStateOf("") }
    var exceptionDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf(listOf<SellerCustomer>()) }
    var chosenCustomer by remember { mutableStateOf<SellerCustomer?>(null) }
    var exceptionReason by remember { mutableStateOf("") }

    LaunchedEffect(plan) { selected.clear(); plan.forEach { selected[it.customerId] = it.estimatedAmount } }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Text("Proyectado del dia", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Clientes habilitados para tu zona y el dia de hoy", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
        when {
            !open -> Text("Comenza la jornada para armar el proyectado.")
            pending -> Text("Primero lee y acepta los mensajes obligatorios.", color = MaterialTheme.colorScheme.error)
            customers.isEmpty() -> Text("No hay clientes asignados para hoy. Podes agregar excepciones o actualizar la lista.")
            else -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(customers, key = { it.id }) { customer ->
                    val amount = selected[customer.id]
                    val planEntry = plan.firstOrNull { it.customerId == customer.id }
                    Card {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(amount != null, { checked -> if (checked) selected[customer.id] = 0.0 else selected.remove(customer.id) })
                            Column(Modifier.weight(1f)) {
                                Text(customer.name, fontWeight = FontWeight.SemiBold)
                                Text(customer.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (planEntry != null) TextButton(onClick = { visitTarget = planEntry }) { Text("Registrar visita") }
                            }
                            if (amount != null) OutlinedTextField(amount.takeIf { it > 0 }?.toString().orEmpty(), { raw -> selected[customer.id] = raw.toDoubleOrNull() ?: 0.0 },
                                Modifier.width(110.dp), label = { Text("Estimado") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                exceptionDialog = true; searchQuery = ""; searchResults = emptyList(); chosenCustomer = null; exceptionReason = ""
            }, enabled = open && !pending, modifier = Modifier.weight(1.2f)) {
                Text("+ Excepcion")
            }
            OutlinedButton(onClick = onRefresh, modifier = Modifier.weight(0.9f)) { Text("Actualizar") }
            Button(onClick = { onSave(selected.toMap()) }, enabled = open && !pending && selected.isNotEmpty() && !busy, modifier = Modifier.weight(1f)) { Text("Guardar (${selected.size})") }
        }
    }
    if (exceptionDialog) {
        AlertDialog(
            onDismissRequest = { exceptionDialog = false },
            title = { Text("Cliente fuera de zona") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (chosenCustomer == null) {
                        OutlinedTextField(searchQuery, { searchQuery = it }, label = { Text("Buscar nombre o direccion") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Button(onClick = {
                            if (searchQuery.trim().length >= 2) onSearchCustomers(searchQuery.trim()) { searchResults = it }
                        }, enabled = searchQuery.trim().length >= 2, modifier = Modifier.fillMaxWidth()) {
                            Text("Buscar")
                        }
                        if (searchResults.isEmpty() && searchQuery.length >= 2) {
                            Text("Sin coincidencias", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        searchResults.forEach { c ->
                            Card(modifier = Modifier.fillMaxWidth().clickable { chosenCustomer = c }) {
                                Column(Modifier.padding(10.dp)) {
                                    Text(c.name, fontWeight = FontWeight.Bold)
                                    Text(c.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    } else {
                        Text("Seleccionado: ${chosenCustomer!!.name}", fontWeight = FontWeight.Bold)
                        Text(chosenCustomer!!.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(exceptionReason, { exceptionReason = it }, label = { Text("Motivo de la excepcion *") }, modifier = Modifier.fillMaxWidth())
                        TextButton(onClick = { chosenCustomer = null }) { Text("Cambiar cliente") }
                    }
                }
            },
            confirmButton = {
                if (chosenCustomer != null) {
                    Button(onClick = {
                        if (exceptionReason.isNotBlank()) {
                            onAddException(chosenCustomer!!.id, exceptionReason.trim())
                            exceptionDialog = false
                        }
                    }, enabled = exceptionReason.isNotBlank()) {
                        Text("Agregar")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { exceptionDialog = false }) { Text("Cancelar") }
            }
        )
    }
    visitTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = { visitTarget = null },
            title = { Text("Resultado de la visita") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("venta" to "Hubo venta", "sin_venta" to "Visitado, sin venta", "no_encontrado" to "No se pudo visitar").forEach { (value, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { visitResult = value }) {
                            RadioButton(selected = visitResult == value, onClick = { visitResult = value }); Text(label)
                        }
                    }
                    OutlinedTextField(visitNotes, { visitNotes = it }, label = { Text("Observaciones") })
                }
            },
            confirmButton = { TextButton(onClick = { onVisit(entry, visitResult, visitNotes); visitTarget = null; visitNotes = "" }) { Text("Guardar visita") } },
            dismissButton = { TextButton(onClick = { visitTarget = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun StatsScreen(summary: SellerSummary?, onRange: (String, String) -> Unit) {
    var periodDays by remember { mutableIntStateOf(1) }
    val until = LocalDate.now()
    val from = until.minusDays((periodDays - 1).toLong())
    LaunchedEffect(periodDays) { onRange(from.toString(), until.toString()) }
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(if (periodDays == 1) "Resumen de hoy" else "Resumen de los ultimos $periodDays dias", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1 to "Hoy", 7 to "7 dias", 30 to "30 dias").forEach { (days, label) ->
                    FilterChip(selected = periodDays == days, onClick = { periodDays = days }, label = { Text(label) })
                }
            }
        }
        if (summary == null) item { Text("Todavia no hay datos del tablero.") }
        else {
            item { MetricCard("Proyectado - visitado", "${summary.visitedCustomers} / ${summary.projectedCustomers}", "${summary.coveragePercent.pretty()}% de cobertura") }
            item { MetricCard("Con venta", "${summary.customersWithSale} clientes", "${summary.conversionPercent.pretty()}% de conversion") }
            item { MetricCard("Vendido", money(summary.soldAmount), "${summary.targetPercent.pretty()}% del objetivo de ${money(summary.targetAmount)}") }
            item { MetricCard("Venta vs. proyectado", "${summary.soldVsProjectedPercent.pretty()}%", "Proyeccion ${money(summary.projectedAmount)}") }
        }
        item { OutlinedButton(onClick = { onRange(from.toString(), until.toString()) }, modifier = Modifier.fillMaxWidth()) { Text("Actualizar indicadores") } }
    }
}

@Composable
private fun MetricCard(title: String, value: String, detail: String) {
    Card { Column(Modifier.fillMaxWidth().padding(18.dp)) {
        Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
        Text(detail)
    } }
}

@Composable
private fun MapScreen(points: List<MapPoint>, customers: List<SellerCustomer>) {
    val route = remember(points) { points.map { LatLng(it.latitude, it.longitude) } }
    val customerMarkers = remember(customers) {
        customers.mapNotNull { customer ->
            if (customer.latitude != null && customer.longitude != null) customer to LatLng(customer.latitude, customer.longitude) else null
        }
    }
    val allCoordinates = route + customerMarkers.map { it.second }
    if (allCoordinates.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Recorrido del dia", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("El mapa va a mostrar el recorrido y los clientes del proyectado cuando haya ubicaciones disponibles.")
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth().height(240.dp)) {
                Box(contentAlignment = Alignment.Center) { Text("Esperando ubicaciones o clientes con coordenadas") }
            }
        }
    } else {
        val cameraPositionState = rememberCameraPositionState {
            position = CameraPosition.fromLatLngZoom(allCoordinates.first(), 14f)
        }
        var mapLoaded by remember { mutableStateOf(false) }
        var cameraAdjusted by remember { mutableStateOf(false) }
        LaunchedEffect(mapLoaded, allCoordinates) {
            if (mapLoaded && !cameraAdjusted && allCoordinates.isNotEmpty()) {
                if (allCoordinates.size == 1) {
                    cameraPositionState.position = CameraPosition.fromLatLngZoom(allCoordinates.first(), 15f)
                } else {
                    val bounds = LatLngBounds.builder().apply { allCoordinates.forEach { coordinate -> include(coordinate) } }.build()
                    cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 72))
                }
                cameraAdjusted = true
            }
        }
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings = MapUiSettings(compassEnabled = true, mapToolbarEnabled = false, zoomControlsEnabled = false),
            onMapLoaded = { mapLoaded = true }
        ) {
            if (route.size > 1) Polyline(points = route, color = Color(0xFF176B53), width = 6f)
            route.firstOrNull()?.let { Marker(state = MarkerState(position = it), title = "Inicio del recorrido") }
            if (route.size > 1) Marker(state = MarkerState(position = route.last()), title = "Ultima ubicacion")
            customerMarkers.forEach { (customer, position) ->
                Marker(state = MarkerState(position = position), title = customer.name, snippet = customer.address)
            }
        }
    }
}

private fun extractArray(json: JSONObject?, array: JSONArray?, key: String): JSONArray {
    if (array != null) return array
    if (json == null) return JSONArray()
    return json.optJSONArray(key) ?: json.optJSONObject("data")?.optJSONArray(key) ?: json.optJSONArray("data") ?: JSONArray()
}

private fun Double.pretty() = String.format(java.util.Locale("es", "AR"), "%.1f", this)
private fun money(value: Double) = java.text.NumberFormat.getCurrencyInstance(java.util.Locale("es", "AR")).format(value)
