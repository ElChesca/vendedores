package com.baboons.vendedorescalle

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** San Luis capital: fallback cuando no hay ubicación ni clientes con coordenadas. */
val SanLuisCapital = LatLng(-33.3017, -66.3378)
const val SanLuisFallbackZoom = 13f
const val SellerLocationZoom = 15f

fun isValidMapCoordinate(latitude: Double, longitude: Double): Boolean {
    if (!latitude.isFinite() || !longitude.isFinite()) return false
    if (kotlin.math.abs(latitude) < 0.0001 && kotlin.math.abs(longitude) < 0.0001) return false
    return latitude in -90.0..90.0 && longitude in -180.0..180.0
}

fun LatLng.isValidMapCoordinate(): Boolean = isValidMapCoordinate(latitude, longitude)

fun hasLocationPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
}

/** Última ubicación conocida del dispositivo, síncrona (LocationManager). */
fun deviceLastKnownLocation(context: Context): LatLng? {
    if (!hasLocationPermission(context)) return null
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val providers = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.PASSIVE_PROVIDER
    )
    val best = providers.mapNotNull { provider ->
        runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
    }.filter { isValidMapCoordinate(it.latitude, it.longitude) }
        .maxByOrNull { it.time }
    return best?.let { LatLng(it.latitude, it.longitude) }
}

suspend fun fusedLastKnownLocation(context: Context): LatLng? {
    if (!hasLocationPermission(context)) return null
    val client = LocationServices.getFusedLocationProviderClient(context)
    return awaitLocation(runCatching { client.lastLocation }.getOrNull())
}

/** Intento breve de ubicación actual, para el botón "Mi ubicación". */
suspend fun fusedCurrentLocation(context: Context): LatLng? {
    if (!hasLocationPermission(context)) return null
    val client = LocationServices.getFusedLocationProviderClient(context)
    val cancellation = CancellationTokenSource()
    return try {
        awaitLocation(
            runCatching {
                client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellation.token)
            }.getOrNull()
        )
    } finally {
        cancellation.cancel()
    }
}

private suspend fun awaitLocation(task: Task<Location>?): LatLng? {
    if (task == null) return null
    return suspendCancellableCoroutine { continuation ->
        task.addOnSuccessListener { location ->
            val point = location?.takeIf { isValidMapCoordinate(it.latitude, it.longitude) }
                ?.let { LatLng(it.latitude, it.longitude) }
            if (continuation.isActive) continuation.resume(point)
        }.addOnFailureListener {
            if (continuation.isActive) continuation.resume(null)
        }.addOnCanceledListener {
            if (continuation.isActive) continuation.resume(null)
        }
    }
}

/**
 * Prioridad: Fused / LocationManager, después el último punto de la DB local.
 * Nunca devuelve (0,0) ni coordenadas inválidas.
 */
suspend fun resolveSellerLocation(context: Context, db: OfflineLocationDb): LatLng? {
    fusedLastKnownLocation(context)?.let { return it }
    deviceLastKnownLocation(context)?.let { return it }
    val stored = db.lastPoint() ?: return null
    return LatLng(stored.latitude, stored.longitude).takeIf { it.isValidMapCoordinate() }
}

fun sellerLocationSynchronous(context: Context, db: OfflineLocationDb): LatLng? {
    deviceLastKnownLocation(context)?.let { return it }
    val stored = db.lastPoint() ?: return null
    return LatLng(stored.latitude, stored.longitude).takeIf { it.isValidMapCoordinate() }
}

fun initialCameraPosition(seller: LatLng?, customerCoordinates: List<LatLng>): CameraPosition {
    val customers = customerCoordinates.filter { it.isValidMapCoordinate() }
    val sellerValid = seller?.takeIf { it.isValidMapCoordinate() }
    return when {
        sellerValid != null && customers.isEmpty() -> CameraPosition.fromLatLngZoom(sellerValid, SellerLocationZoom)
        sellerValid == null && customers.size == 1 -> CameraPosition.fromLatLngZoom(customers.first(), SellerLocationZoom)
        sellerValid == null && customers.isEmpty() -> CameraPosition.fromLatLngZoom(SanLuisCapital, SanLuisFallbackZoom)
        else -> {
            // Varios puntos: el centro de los bounds basta para el primer frame;
            // después del onMapLoaded se encuadra con padding.
            val bounds = boundsFor(listOfNotNull(sellerValid) + customers)
            CameraPosition.fromLatLngZoom(bounds.center, 13f)
        }
    }
}

fun boundsFor(points: List<LatLng>): LatLngBounds {
    val builder = LatLngBounds.builder()
    points.forEach { builder.include(it) }
    return builder.build()
}
