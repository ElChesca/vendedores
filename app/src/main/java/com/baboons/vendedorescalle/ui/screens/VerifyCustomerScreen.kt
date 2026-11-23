package com.baboons.vendedorescalle.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.baboons.vendedorescalle.CustomerFicha
import com.baboons.vendedorescalle.LocationFix
import com.baboons.vendedorescalle.PendingVerification
import com.baboons.vendedorescalle.SellerCustomer
import com.baboons.vendedorescalle.VerificationDraft
import com.baboons.vendedorescalle.accuracyWarning
import com.baboons.vendedorescalle.dniVerificationError
import com.baboons.vendedorescalle.displayFields
import com.baboons.vendedorescalle.emailVerificationError
import com.baboons.vendedorescalle.phoneVerificationError
import com.baboons.vendedorescalle.formatAccuracyMeters
import com.baboons.vendedorescalle.formatCoordinate
import com.baboons.vendedorescalle.formatOrderDate
import com.baboons.vendedorescalle.hasLocationPermission
import com.baboons.vendedorescalle.isValidMapCoordinate
import com.baboons.vendedorescalle.ui.components.ErrorBanner
import com.baboons.vendedorescalle.ui.components.PrimaryButton
import com.baboons.vendedorescalle.ui.components.SectionTitle
import com.baboons.vendedorescalle.ui.theme.SemaforoAmarillo
import com.baboons.vendedorescalle.verificationEstadoLabel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

@Composable
fun VerifyCustomerScreen(
    customer: SellerCustomer,
    ficha: CustomerFicha?,
    fichaNote: String?,
    fichaNoteIsError: Boolean,
    fichaLoading: Boolean,
    locationFix: LocationFix?,
    locating: Boolean,
    locationError: String?,
    visitId: Long?,
    pending: PendingVerification?,
    busy: Boolean,
    error: String?,
    submittedEstado: String?,
    submittedIdempotent: Boolean,
    onBack: () -> Unit,
    onFixLocation: () -> Unit,
    onLocationError: (String) -> Unit,
    onSubmit: (VerificationDraft) -> Unit,
    onRetry: () -> Unit
) {
    val context = LocalContext.current
    var phone by remember(customer.id) { mutableStateOf("") }
    var email by remember(customer.id) { mutableStateOf("") }
    var address by remember(customer.id) { mutableStateOf("") }
    var dni by remember(customer.id) { mutableStateOf("") }
    var city by remember(customer.id) { mutableStateOf("") }
    var postalCode by remember(customer.id) { mutableStateOf("") }
    var notes by remember(customer.id) { mutableStateOf("") }
    var seeded by remember(customer.id) { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val allowed = granted[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (allowed) onFixLocation()
        else onLocationError("Para fijar la ubicación tenés que habilitar el permiso de ubicación.")
    }

    LaunchedEffect(customer.id, ficha, pending?.idempotencyKey) {
        if (seeded) return@LaunchedEffect
        if (pending != null) {
            phone = pending.phone
            email = pending.email
            address = pending.address
            dni = pending.dni
            city = pending.city
            postalCode = pending.postalCode
            notes = pending.notes
            seeded = true
        } else if (ficha != null) {
            phone = ficha.phone
            email = ficha.email
            address = ficha.address
            dni = ficha.dni
            city = ficha.city
            postalCode = ficha.postalCode
            seeded = true
        }
    }

    fun requestFix() {
        if (hasLocationPermission(context)) onFixLocation()
        else permissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    val phoneError = phoneVerificationError(phone)
    val emailError = emailVerificationError(email)
    val dniError = dniVerificationError(dni)
    val draft = VerificationDraft(phone, email, address, dni, city, postalCode, notes)

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Volver", modifier = Modifier.padding(start = 6.dp))
        }
        Text("Verificar cliente", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(customer.name.ifBlank { "Cliente" }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        if (submittedEstado != null) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (submittedEstado.equals("pendiente", ignoreCase = true)) {
                            "Enviado, pendiente de aprobación"
                        } else {
                            "Enviado"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        "Última verificación: ${verificationEstadoLabel(submittedEstado)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (submittedIdempotent) {
                        Text(
                            "Ya estaba registrada con esta clave.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        if (fichaLoading && ficha == null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("Cargando ficha…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        fichaNote?.takeIf { it.isNotBlank() }?.let { note ->
            if (fichaNoteIsError) ErrorBanner(note) else {
                Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        ficha?.let { current ->
            SectionTitle("Datos actuales", "En rojo, lo que falta en la ficha")
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    current.displayFields().forEach { field ->
                        FichaDataRow(field.label, field.value, field.missing)
                    }
                    current.odooPartnerId?.let { partnerId ->
                        FichaDataRow("Odoo", partnerId.toString(), missing = false)
                    }
                    val last = current.lastVerification
                    if (submittedEstado == null) {
                        if (last == null) {
                            Text(
                                "Sin verificaciones anteriores",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            val whenText = last.creadoEn?.takeIf { it.isNotBlank() }?.let { " · ${formatOrderDate(it)}" }.orEmpty()
                            Text(
                                "Última verificación: ${verificationEstadoLabel(last.estado)}$whenText",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        SectionTitle("Ubicación", "GPS de alta precisión. Si no lo fijás, tiene que haber al menos un dato.")
        OutlinedButton(
            onClick = { requestFix() },
            enabled = !locating && !busy,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Icon(Icons.Outlined.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Fijar ubicación aquí", modifier = Modifier.padding(start = 8.dp))
        }
        if (locating) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Buscando GPS…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        locationError?.let { ErrorBanner(it) }
        locationFix?.let { fix ->
            Text(
                "${formatCoordinate(fix.latitude)}, ${formatCoordinate(fix.longitude)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Precisión: ${formatAccuracyMeters(fix.accuracyMeters)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            accuracyWarning(fix.accuracyMeters)?.let { warning ->
                Text(warning, style = MaterialTheme.typography.bodyMedium, color = SemaforoAmarillo)
            }
            if (isValidMapCoordinate(fix.latitude, fix.longitude)) {
                VerificationMiniMap(fix.latitude, fix.longitude)
            }
        }

        SectionTitle("Datos a enviar")
        if (visitId != null) {
            Text(
                "Se vincula a la visita de hoy.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedTextField(
            phone, { phone = it },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            label = { Text("Teléfono") },
            singleLine = true,
            isError = phoneError != null,
            supportingText = phoneError?.let { message -> { Text(message) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            shape = MaterialTheme.shapes.small
        )
        OutlinedTextField(
            email, { email = it },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            label = { Text("Email") },
            singleLine = true,
            isError = emailError != null,
            supportingText = emailError?.let { message -> { Text(message) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            shape = MaterialTheme.shapes.small
        )
        OutlinedTextField(
            address, { address = it },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            label = { Text("Dirección") },
            singleLine = true,
            shape = MaterialTheme.shapes.small
        )
        OutlinedTextField(
            dni, { dni = it },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            label = { Text("CUIT/DNI") },
            singleLine = true,
            isError = dniError != null,
            supportingText = dniError?.let { message -> { Text(message) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = MaterialTheme.shapes.small
        )
        OutlinedTextField(
            city, { city = it },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            label = { Text("Ciudad") },
            singleLine = true,
            shape = MaterialTheme.shapes.small
        )
        OutlinedTextField(
            postalCode, { postalCode = it },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            label = { Text("Código postal") },
            singleLine = true,
            shape = MaterialTheme.shapes.small
        )
        OutlinedTextField(
            notes, { notes = it },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            label = { Text("Observaciones") },
            minLines = 2,
            shape = MaterialTheme.shapes.small
        )

        if (pending != null && submittedEstado == null) {
            Text(
                "Hay un envío guardado en el teléfono. El reintento usa la misma clave.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        error?.let { ErrorBanner(it) }
        PrimaryButton(
            text = "Enviar verificación",
            onClick = { onSubmit(draft) },
            enabled = !locating,
            busy = busy
        )
        if (pending != null && submittedEstado == null && !busy) {
            OutlinedButton(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = MaterialTheme.shapes.medium
            ) { Text("Reintentar") }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun FichaDataRow(label: String, value: String, missing: Boolean) {
    val color = if (missing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (missing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            if (missing) "Falta" else value,
            style = MaterialTheme.typography.bodyLarge,
            color = color,
            fontWeight = if (missing) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun VerificationMiniMap(latitude: Double, longitude: Double) {
    val pin = LatLng(latitude, longitude)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(pin, 17f)
    }
    val markerState = remember(latitude, longitude) { MarkerState(pin) }
    LaunchedEffect(latitude, longitude) {
        val target = CameraPosition.fromLatLngZoom(LatLng(latitude, longitude), 17f)
        runCatching {
            cameraPositionState.animate(CameraUpdateFactory.newCameraPosition(target))
        }.onFailure { cameraPositionState.position = target }
    }
    GoogleMap(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(20.dp)),
        cameraPositionState = cameraPositionState,
        uiSettings = MapUiSettings(
            compassEnabled = false,
            mapToolbarEnabled = false,
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false,
            scrollGesturesEnabled = false,
            zoomGesturesEnabled = false,
            tiltGesturesEnabled = false,
            rotationGesturesEnabled = false
        )
    ) {
        Marker(state = markerState, title = "Ubicación fijada")
    }
}
