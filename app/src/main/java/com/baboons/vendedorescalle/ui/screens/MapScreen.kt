package com.baboons.vendedorescalle.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.baboons.vendedorescalle.MapPoint
import com.baboons.vendedorescalle.OfflineLocationDb
import com.baboons.vendedorescalle.SanLuisCapital
import com.baboons.vendedorescalle.SanLuisFallbackZoom
import com.baboons.vendedorescalle.SellerCustomer
import com.baboons.vendedorescalle.SellerLocationZoom
import com.baboons.vendedorescalle.boundsFor
import com.baboons.vendedorescalle.fusedCurrentLocation
import com.baboons.vendedorescalle.hasLocationPermission
import com.baboons.vendedorescalle.initialCameraPosition
import com.baboons.vendedorescalle.isValidMapCoordinate
import com.baboons.vendedorescalle.resolveSellerLocation
import com.baboons.vendedorescalle.sellerLocationSynchronous
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraMoveStartedReason
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.baboons.vendedorescalle.ui.theme.LogicaAccent
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun MapScreen(points: List<MapPoint>, customers: List<SellerCustomer>) {
    val context = LocalContext.current
    val db = remember { OfflineLocationDb(context) }
    val route = remember(points) {
        points.map { LatLng(it.latitude, it.longitude) }.filter { isValidMapCoordinate(it.latitude, it.longitude) }
    }
    val customerMarkers = remember(customers) {
        customers.mapNotNull { customer ->
            val latitude = customer.latitude
            val longitude = customer.longitude
            if (latitude != null && longitude != null && isValidMapCoordinate(latitude, longitude)) {
                customer to LatLng(latitude, longitude)
            } else {
                null
            }
        }
    }
    val customerCoordinates = remember(customerMarkers) { customerMarkers.map { it.second } }

    var hasPermission by remember { mutableStateOf(hasLocationPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        hasPermission = granted[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    var sellerLocation by remember { mutableStateOf(sellerLocationSynchronous(context, db)) }
    val cameraPositionState = rememberCameraPositionState {
        position = initialCameraPosition(sellerLocation, customerCoordinates)
    }
    var mapLoaded by remember { mutableStateOf(false) }
    var initialCameraApplied by remember { mutableStateOf(false) }
    var fittedWithSeller by remember { mutableStateOf(false) }
    var fittedWithCustomers by remember { mutableStateOf(false) }
    var userMovedCamera by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(hasPermission) {
        resolveSellerLocation(context, db)?.let { sellerLocation = it }
    }

    LaunchedEffect(cameraPositionState.isMoving) {
        if (cameraPositionState.isMoving &&
            cameraPositionState.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE
        ) {
            userMovedCamera = true
        }
    }

    suspend fun fitInitialCamera(seller: LatLng?, customersForBounds: List<LatLng>) {
        val validSeller = seller?.takeIf { isValidMapCoordinate(it.latitude, it.longitude) }
        val validCustomers = customersForBounds.filter { isValidMapCoordinate(it.latitude, it.longitude) }
        when {
            validSeller != null && validCustomers.isNotEmpty() -> {
                val bounds = boundsFor(listOf(validSeller) + validCustomers)
                runCatching {
                    cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 120))
                }.onFailure {
                    cameraPositionState.position = CameraPosition.fromLatLngZoom(bounds.center, 13f)
                }
            }
            validSeller != null -> {
                cameraPositionState.animate(CameraUpdateFactory.newCameraPosition(
                    CameraPosition.fromLatLngZoom(validSeller, SellerLocationZoom)
                ))
            }
            validCustomers.size == 1 -> {
                cameraPositionState.animate(CameraUpdateFactory.newCameraPosition(
                    CameraPosition.fromLatLngZoom(validCustomers.first(), SellerLocationZoom)
                ))
            }
            validCustomers.size > 1 -> {
                val bounds = boundsFor(validCustomers)
                runCatching {
                    cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 120))
                }.onFailure {
                    cameraPositionState.position = CameraPosition.fromLatLngZoom(bounds.center, 13f)
                }
            }
            else -> {
                cameraPositionState.animate(CameraUpdateFactory.newCameraPosition(
                    CameraPosition.fromLatLngZoom(SanLuisCapital, SanLuisFallbackZoom)
                ))
            }
        }
    }

    LaunchedEffect(mapLoaded, sellerLocation, customerCoordinates) {
        if (!mapLoaded || userMovedCamera) return@LaunchedEffect
        val hasCustomers = customerCoordinates.isNotEmpty()
        val alreadyFitted = initialCameraApplied &&
            (sellerLocation == null || fittedWithSeller) &&
            (!hasCustomers || fittedWithCustomers)
        if (alreadyFitted) return@LaunchedEffect
        fitInitialCamera(sellerLocation, customerCoordinates)
        initialCameraApplied = true
        if (sellerLocation != null) fittedWithSeller = true
        if (hasCustomers) fittedWithCustomers = true
    }

    fun recenterOnMe() {
        if (!hasPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }
        scope.launch {
            val current = withTimeoutOrNull(4_000) { fusedCurrentLocation(context) }
                ?: resolveSellerLocation(context, db)
            if (current != null) {
                sellerLocation = current
                cameraPositionState.animate(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition.fromLatLngZoom(current, SellerLocationZoom)
                    )
                )
            } else {
                cameraPositionState.animate(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition.fromLatLngZoom(SanLuisCapital, SanLuisFallbackZoom)
                    )
                )
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = hasPermission),
            uiSettings = MapUiSettings(
                compassEnabled = true,
                mapToolbarEnabled = false,
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false
            ),
            onMapLoaded = { mapLoaded = true }
        ) {
            if (route.size > 1) Polyline(points = route, color = LogicaAccent, width = 8f)
            route.firstOrNull()?.let { Marker(state = MarkerState(position = it), title = "Inicio del recorrido") }
            if (route.size > 1) Marker(state = MarkerState(position = route.last()), title = "Última ubicación")
            customerMarkers.forEach { (customer, position) ->
                Marker(state = MarkerState(position = position), title = customer.name, snippet = customer.address)
            }
        }
        if (route.isEmpty() && customerMarkers.isEmpty()) {
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp, start = 16.dp, end = 16.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shadowElevation = 2.dp
            ) {
                Text(
                    "Todavía no hay recorrido. El mapa arranca en tu ubicación o en San Luis.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
        ExtendedFloatingActionButton(
            onClick = { recenterOnMe() },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            icon = { Icon(Icons.Outlined.MyLocation, contentDescription = null) },
            text = { Text("Mi ubicación") }
        )
    }
}
