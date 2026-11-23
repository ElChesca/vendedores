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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.baboons.vendedorescalle.ui.components.TestingEnvironmentBanner
import com.baboons.vendedorescalle.ui.screens.CustomerDetailScreen
import com.baboons.vendedorescalle.ui.screens.LoginScreen
import com.baboons.vendedorescalle.ui.screens.MapScreen
import com.baboons.vendedorescalle.ui.screens.PlanScreen
import com.baboons.vendedorescalle.ui.screens.StatsScreen
import com.baboons.vendedorescalle.ui.screens.TodayScreen
import com.baboons.vendedorescalle.ui.screens.VerifyCustomerScreen
import com.baboons.vendedorescalle.ui.theme.BondTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
        android.util.Log.i(
            "BondVendedores",
            "env=${if (BuildConfig.IS_TESTING) "testing" else "prod"} api=${BuildConfig.BABOONS_API_BASE}"
        )
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
        var selectedCustomer by remember { mutableStateOf<SellerCustomer?>(null) }
        var purchasesState by remember { mutableStateOf<CustomerPurchasesState>(CustomerPurchasesState.Loading) }
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
        var verifyCustomer by remember { mutableStateOf<SellerCustomer?>(null) }
        var verifyFromDetail by remember { mutableStateOf(false) }
        var ficha by remember { mutableStateOf<CustomerFicha?>(null) }
        var fichaNote by remember { mutableStateOf<String?>(null) }
        var fichaNoteIsError by remember { mutableStateOf(false) }
        var fichaLoading by remember { mutableStateOf(false) }
        var locationFix by remember { mutableStateOf<LocationFix?>(null) }
        var locating by remember { mutableStateOf(false) }
        var locationError by remember { mutableStateOf<String?>(null) }
        var verifyBusy by remember { mutableStateOf(false) }
        var verifyError by remember { mutableStateOf<String?>(null) }
        var submittedEstado by remember { mutableStateOf<String?>(null) }
        var submittedIdempotent by remember { mutableStateOf(false) }
        var pendingVerification by remember { mutableStateOf<PendingVerification?>(null) }
        val today = remember { LocalDate.now().toString() }
        val dayStart = remember(today) { LocalDate.parse(today).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
        val dayEnd = remember(today) { LocalDate.parse(today).plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
        val db = remember { OfflineLocationDb(context) }
        val outbox = remember { VerificationOutbox(context) }
        val dailyVisits = remember { DailyVisitStore(context) }
        val verificationInFlight = remember { mutableSetOf<String>() }
        val scope = rememberCoroutineScope()
        var routePoints by remember { mutableStateOf(db.allPoints(dayStart, dayEnd)) }
        val pendingRequired = messages.any { it.required && it.accepted != true }
        val snackbarHostState = remember { SnackbarHostState() }

        fun fail(message: String?) { busy = false; error = message }
        fun sendVerification(seller: SellerSession, pending: PendingVerification) {
            val key = pending.idempotencyKey
            if (!verificationInFlight.add(key)) {
                if (verifyCustomer?.id == pending.customerId) verifyBusy = true
                return
            }
            if (verifyCustomer?.id == pending.customerId) {
                verifyBusy = true
                verifyError = null
            }
            BaboonsApi.enviarVerificacion(seller, pending.customerId, pending.toBody()) { result ->
                verificationInFlight.remove(key)
                val outcome = interpretVerification(result)
                val visible = verifyCustomer?.id == pending.customerId
                when (outcome) {
                    is VerificationSubmitResult.Accepted -> {
                        outbox.remove(pending.customerId)
                        if (visible) {
                            verifyBusy = false
                            verifyError = null
                            pendingVerification = null
                            submittedEstado = outcome.estado
                            submittedIdempotent = outcome.idempotent
                            ficha = (ficha ?: verifyCustomer?.toFicha())?.copy(
                                lastVerification = CustomerVerificationStatus(outcome.estado, null, outcome.id)
                            )
                        } else {
                            notice = if (outcome.estado.equals("pendiente", ignoreCase = true)) {
                                "Enviado, pendiente de aprobación"
                            } else {
                                "Verificación enviada (${verificationEstadoLabel(outcome.estado)})"
                            }
                        }
                    }
                    is VerificationSubmitResult.Offline -> {
                        if (visible) {
                            verifyBusy = false
                            verifyError = outcome.message
                            pendingVerification = outbox.get(pending.customerId)
                        }
                    }
                    is VerificationSubmitResult.Rejected -> {
                        if (!shouldKeepPending(outcome)) outbox.remove(pending.customerId)
                        if (visible) {
                            verifyBusy = false
                            verifyError = outcome.message
                            pendingVerification = outbox.get(pending.customerId)
                        }
                    }
                }
            }
        }
        fun flushPending(seller: SellerSession) {
            outbox.all().forEach { pending -> sendVerification(seller, pending) }
        }
        fun openVerify(customer: SellerCustomer, fromDetail: Boolean) {
            val pending = outbox.get(customer.id)
            verifyFromDetail = fromDetail
            pendingVerification = pending
            locationFix = pending?.toFix()?.takeIf { isValidMapCoordinate(it.latitude, it.longitude) }
            ficha = null
            fichaNote = null
            fichaNoteIsError = false
            fichaLoading = false
            locating = false
            locationError = null
            verifyBusy = false
            verifyError = null
            submittedEstado = null
            submittedIdempotent = false
            verifyCustomer = customer
        }
        fun closeVerify() {
            verifyCustomer = null
            verifyFromDetail = false
            verifyBusy = false
            locating = false
        }
        fun loadFicha(customer: SellerCustomer) {
            val current = session ?: return
            fichaLoading = true
            val requestedId = customer.id
            BaboonsApi.fichaCliente(current, requestedId) { result ->
                if (verifyCustomer?.id != requestedId) return@fichaCliente
                fichaLoading = false
                val resolved = resolveCustomerFicha(result.status, result.error, result.json, customer)
                ficha = resolved.ficha
                fichaNote = resolved.note
                fichaNoteIsError = resolved.noteIsError
            }
        }
        fun submitVerification(draft: VerificationDraft) {
            val customer = verifyCustomer ?: return
            val current = session ?: return
            val fix = locationFix?.takeIf { isValidMapCoordinate(it.latitude, it.longitude) }
            val fieldErrors = verificationFieldErrors(draft.phone, draft.email, draft.dni)
            val payloadError = verificationPayloadError(draft, fix)
            if (fieldErrors.isNotEmpty() || payloadError != null) {
                verifyError = (fieldErrors + listOfNotNull(payloadError)).joinToString(" ")
                return
            }
            val pending = PendingVerification(
                customerId = customer.id,
                idempotencyKey = pendingVerification?.idempotencyKey ?: UUID.randomUUID().toString(),
                latitude = fix?.latitude,
                longitude = fix?.longitude,
                accuracyMeters = fix?.accuracyMeters?.toDouble(),
                phone = draft.phone.trim(),
                email = draft.email.trim(),
                address = draft.address.trim(),
                dni = normalizeDni(draft.dni),
                city = draft.city.trim(),
                postalCode = draft.postalCode.trim(),
                notes = draft.notes.trim(),
                visitId = dailyVisits.get(today, customer.id),
                savedAtMs = System.currentTimeMillis()
            )
            outbox.save(pending)
            pendingVerification = pending
            submittedEstado = null
            submittedIdempotent = false
            sendVerification(current, pending)
        }
        fun retryVerification() {
            val current = session ?: return
            val pending = pendingVerification ?: verifyCustomer?.let { outbox.get(it.id) } ?: return
            pendingVerification = pending
            locationFix = pending.toFix()?.takeIf { isValidMapCoordinate(it.latitude, it.longitude) } ?: locationFix
            sendVerification(current, pending)
        }
        fun fixLocation() {
            if (verifyCustomer == null) return
            if (!hasLocationPermission(context)) {
                locationError = "Para fijar la ubicación tenés que habilitar el permiso de ubicación."
                return
            }
            locating = true
            locationError = null
            scope.launch {
                val fix = withTimeoutOrNull(12_000) { fusedHighAccuracyFix(context) }
                locating = false
                if (verifyCustomer == null) return@launch
                if (fix == null) {
                    locationError = "No se pudo obtener el GPS. Intentá de nuevo en un lugar con mejor señal."
                } else {
                    locationFix = fix
                    locationError = null
                }
            }
        }
        fun openCustomer(customer: SellerCustomer) {
            val current = session ?: return
            selectedCustomer = customer
            purchasesState = CustomerPurchasesState.Loading
            val requestedId = customer.id
            BaboonsApi.pedidosCliente(current, requestedId) { result ->
                if (selectedCustomer?.id != requestedId) return@pedidosCliente
                purchasesState = customerPurchasesState(result.status, result.error, result.json)
            }
        }
        fun loadDay(s: SellerSession) {
            flushPending(s)
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
        LaunchedEffect(verifyCustomer?.id) {
            val customer = verifyCustomer ?: return@LaunchedEffect
            val current = session ?: return@LaunchedEffect
            loadFicha(customer)
            val pending = pendingVerification
            if (pending != null && pending.customerId == customer.id) sendVerification(current, pending)
        }
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
                Column(Modifier.fillMaxWidth()) {
                    TestingEnvironmentBanner()
                    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            BrandLogos(Modifier.weight(1f), height = 28.dp)
                            TextButton(onClick = {
                                LocationTrackingService.stop(context)
                                SessionStore.clear(context)
                                verifyCustomer = null
                                selectedCustomer = null
                                session = null
                            }) { Text("Salir") }
                        }
                        Text(session!!.sellerName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                        Text(session!!.businessName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            bottomBar = {
                NavigationBar {
                    SellerTab.entries.forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = {
                                selectedCustomer = null
                                verifyCustomer = null
                                verifyFromDetail = false
                                tab = item
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) }
                        )
                    }
                }
            },
            floatingActionButton = {
                // En Mapa se oculta para no superponerse con "Mi ubicación";
                // el alta de potenciales sigue disponible en Jornada, Proyectado e Indicadores.
                if (jornadaAbierta && tab != SellerTab.MAP && selectedCustomer == null && verifyCustomer == null) {
                    FloatingActionButton(onClick = { prospectDialog = true }, containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                        Icon(Icons.Outlined.Add, contentDescription = "Nuevo potencial")
                    }
                }
            }
        ) { inset ->
            Column(Modifier.fillMaxSize().padding(inset)) {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { ErrorBanner(it, Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
                val verifying = verifyCustomer
                val customer = selectedCustomer
                if (verifying != null) {
                    VerifyCustomerScreen(
                        customer = verifying,
                        ficha = ficha,
                        fichaNote = fichaNote,
                        fichaNoteIsError = fichaNoteIsError,
                        fichaLoading = fichaLoading,
                        locationFix = locationFix,
                        locating = locating,
                        locationError = locationError,
                        visitId = dailyVisits.get(today, verifying.id),
                        pending = pendingVerification,
                        busy = verifyBusy,
                        error = verifyError,
                        submittedEstado = submittedEstado,
                        submittedIdempotent = submittedIdempotent,
                        onBack = { if (verifyFromDetail) closeVerify() else { closeVerify(); selectedCustomer = null } },
                        onFixLocation = { fixLocation() },
                        onLocationError = { locationError = it },
                        onSubmit = { submitVerification(it) },
                        onRetry = { retryVerification() }
                    )
                } else if (customer != null) {
                    CustomerDetailScreen(
                        customer = customer,
                        purchasesState = purchasesState,
                        onBack = { selectedCustomer = null },
                        onRetry = { openCustomer(customer) },
                        onVerify = { openVerify(customer, fromDetail = true) }
                    )
                } else when (tab) {
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
                    SellerTab.MAP -> MapScreen(
                        routePoints,
                        customers.filter { customer -> plan.any { it.customerId == customer.id } },
                        onCustomer = ::openCustomer,
                        onVerify = { openVerify(it, fromDetail = false) }
                    )
                    SellerTab.PLAN -> PlanScreen(customers, plan, jornadaAbierta, pendingRequired, busy, onVisit = { entry, result, notes ->
                        BaboonsApi.registrarVisita(session!!, today, entry.proyectadoId, entry.customerId, result, notes, UUID.randomUUID().toString()) { response ->
                            if (response.error != null) error = response.error else {
                                response.json?.optLong("id")?.takeIf { it > 0 }?.let { dailyVisits.remember(today, entry.customerId, it) }
                                notice = "Visita registrada"
                                loadDay(session!!)
                            }
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
                    }, onOpenCustomer = ::openCustomer, onVerify = { openVerify(it, fromDetail = false) })
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
