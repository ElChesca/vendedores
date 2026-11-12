package com.baboons.vendedorescalle

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class LocationTrackingService : Service(), LocationListener {
    private lateinit var locationManager: LocationManager
    private lateinit var locationDb: OfflineLocationDb
    private val worker = Executors.newSingleThreadScheduledExecutor()
    private val gpsWorker = Executors.newSingleThreadExecutor()
    private val syncing = AtomicBoolean(false)
    private val scheduled = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationDb = OfflineLocationDb(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopTracking()
            stopSelf()
            return START_NOT_STICKY
        }
        if (!getSharedPreferences("tracking_state", MODE_PRIVATE).getBoolean("active", false)) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundCompat()
        startTracking()
        if (scheduled.compareAndSet(false, true)) {
            worker.scheduleWithFixedDelay({ flushQueue() }, 10, 60, java.util.concurrent.TimeUnit.SECONDS)
        }
        return START_STICKY
    }

    override fun onLocationChanged(location: Location) {
        gpsWorker.execute {
            locationDb.add(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracy = location.accuracy,
                speed = location.speed,
                timestampMs = location.time
            )
            flushQueue()
        }
    }

    private fun startTracking() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return
        }
        try {
            val provider = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .firstOrNull { locationManager.isProviderEnabled(it) } ?: run { stopSelf(); return }
            locationManager.requestLocationUpdates(provider, 30_000L, 25f, this, Looper.getMainLooper())
            locationManager.getLastKnownLocation(provider)?.takeIf {
                System.currentTimeMillis() - it.time < 120_000L
            }?.let(::onLocationChanged)
        } catch (_: SecurityException) {
            stopSelf()
        }
    }

    private fun flushQueue() {
        if (!syncing.compareAndSet(false, true)) return
        gpsWorker.execute {
            val session = SessionStore.load(this)
            val points = locationDb.pending()
            if (session == null || points.isEmpty()) {
                syncing.set(false)
                return@execute
            }
            val deviceId = DeviceIdentity.id(this)
            val json = JSONArray().apply { points.forEach { put(it.toJson(deviceId)) } }
            BaboonsApi.subirUbicaciones(session, deviceId, json) { result ->
                gpsWorker.execute {
                    if (result.error == null) locationDb.removeRows(points)
                    syncing.set(false)
                }
            }
        }
    }

    private fun startForegroundCompat() {
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Bond Vendedores")
            .setContentText("Jornada activa: seguimiento de ubicacion encendido")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Seguimiento de jornada", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun stopTracking() {
        runCatching { locationManager.removeUpdates(this) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        stopTracking()
        worker.shutdownNow()
        gpsWorker.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "seller_location_tracking"
        private const val NOTIFICATION_ID = 7211
        private const val ACTION_STOP = "com.baboons.vendedorescalle.STOP_TRACKING"

        fun start(context: Context) {
            context.getSharedPreferences("tracking_state", Context.MODE_PRIVATE).edit().putBoolean("active", true).apply()
            val intent = Intent(context, LocationTrackingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            context.getSharedPreferences("tracking_state", Context.MODE_PRIVATE).edit().putBoolean("active", false).apply()
            context.startService(Intent(context, LocationTrackingService::class.java).setAction(ACTION_STOP))
        }
    }
}
