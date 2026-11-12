
package com.baboons.vendedorescalle.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baboons.vendedorescalle.MapPoint
import com.baboons.vendedorescalle.SellerCustomer
import com.baboons.vendedorescalle.ui.components.EmptyState
import com.baboons.vendedorescalle.ui.theme.LogicaAccent
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

@Composable
fun MapScreen(points: List<MapPoint>, customers: List<SellerCustomer>) {
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
            EmptyState(
                "Todavia no hay recorrido",
                "El mapa va a mostrar el trayecto y los clientes del proyectado cuando haya ubicaciones disponibles.",
                Icons.Outlined.Map
            )
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth().height(240.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text("Esperando ubicaciones o clientes con coordenadas", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
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
            if (route.size > 1) Polyline(points = route, color = LogicaAccent, width = 8f)
            route.firstOrNull()?.let { Marker(state = MarkerState(position = it), title = "Inicio del recorrido") }
            if (route.size > 1) Marker(state = MarkerState(position = route.last()), title = "Ultima ubicacion")
            customerMarkers.forEach { (customer, position) ->
                Marker(state = MarkerState(position = position), title = customer.name, snippet = customer.address)
            }
        }
    }
}
