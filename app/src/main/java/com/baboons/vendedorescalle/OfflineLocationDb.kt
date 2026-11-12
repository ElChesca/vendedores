package com.baboons.vendedorescalle

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues
import org.json.JSONArray
import org.json.JSONObject

data class QueuedLocation(
    val rowId: Long,
    val timestampMs: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val speed: Float,
    val sequence: Long
) {
    fun toJson(deviceId: String) = JSONObject()
        .put("lat", latitude)
        .put("lng", longitude)
        .put("accuracy", accuracy.toDouble())
        .put("speed", speed.toDouble())
        .put("ts", timestampMs)
        .put("device_id", deviceId)
        .put("sequence", sequence)
}

class OfflineLocationDb(context: Context) : SQLiteOpenHelper(context, "street_locations.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE gps_queue (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            timestamp_ms INTEGER NOT NULL,
            latitude REAL NOT NULL,
            longitude REAL NOT NULL,
            accuracy REAL NOT NULL,
            speed REAL NOT NULL,
            sequence INTEGER NOT NULL UNIQUE
        )""")
        db.execSQL("CREATE INDEX gps_queue_timestamp ON gps_queue(timestamp_ms)")
        db.execSQL("""CREATE TABLE gps_history (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            timestamp_ms INTEGER NOT NULL,
            latitude REAL NOT NULL,
            longitude REAL NOT NULL
        )""")
        db.execSQL("CREATE INDEX gps_history_timestamp ON gps_history(timestamp_ms)")
        db.execSQL("CREATE TABLE gps_meta (name TEXT PRIMARY KEY, value INTEGER NOT NULL)")
        db.execSQL("INSERT INTO gps_meta(name, value) VALUES ('sequence', 0)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    @Synchronized
    fun add(latitude: Double, longitude: Double, accuracy: Float, speed: Float, timestampMs: Long): QueuedLocation {
        val db = writableDatabase
        val sequence = db.rawQuery("SELECT value + 1 FROM gps_meta WHERE name = 'sequence'", null).use { cursor ->
            cursor.moveToFirst()
            cursor.getLong(0)
        }
        db.execSQL("UPDATE gps_meta SET value = ? WHERE name = 'sequence'", arrayOf(sequence))
        val values = ContentValues().apply {
            put("timestamp_ms", timestampMs)
            put("latitude", latitude)
            put("longitude", longitude)
            put("accuracy", accuracy)
            put("speed", speed)
            put("sequence", sequence)
        }
        val rowId = db.insertOrThrow("gps_queue", null, values)
        db.insertOrThrow("gps_history", null, ContentValues().apply {
            put("timestamp_ms", timestampMs); put("latitude", latitude); put("longitude", longitude)
        })
        return QueuedLocation(rowId, timestampMs, latitude, longitude, accuracy, speed, sequence)
    }

    @Synchronized
    fun pending(limit: Int = 80): List<QueuedLocation> = readableDatabase.rawQuery(
        "SELECT id, timestamp_ms, latitude, longitude, accuracy, speed, sequence FROM gps_queue ORDER BY id LIMIT ?",
        arrayOf(limit.toString())
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(
                QueuedLocation(cursor.getLong(0), cursor.getLong(1), cursor.getDouble(2), cursor.getDouble(3),
                    cursor.getFloat(4), cursor.getFloat(5), cursor.getLong(6))
            )
        }
    }

    @Synchronized
    fun removeRows(rows: List<QueuedLocation>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            rows.forEach { db.delete("gps_queue", "id = ?", arrayOf(it.rowId.toString())) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun allPoints(fromMs: Long, untilMs: Long, limit: Int = 1500): List<MapPoint> = readableDatabase.rawQuery(
        "SELECT latitude, longitude FROM gps_history WHERE timestamp_ms >= ? AND timestamp_ms < ? ORDER BY timestamp_ms DESC LIMIT ?",
        arrayOf(fromMs.toString(), untilMs.toString(), limit.toString())
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(MapPoint(cursor.getDouble(0), cursor.getDouble(1)))
        }.asReversed()
    }

    /** Último punto guardado, de cualquier día. Ignora coordenadas inválidas o (0,0). */
    @Synchronized
    fun lastPoint(): MapPoint? = readableDatabase.rawQuery(
        "SELECT latitude, longitude FROM gps_history ORDER BY timestamp_ms DESC LIMIT 20",
        null
    ).use { cursor ->
        while (cursor.moveToNext()) {
            val latitude = cursor.getDouble(0)
            val longitude = cursor.getDouble(1)
            if (isValidMapCoordinate(latitude, longitude)) return@use MapPoint(latitude, longitude)
        }
        null
    }
}

object DeviceIdentity {
    fun id(context: Context): String {
        val prefs = context.getSharedPreferences("device_identity", Context.MODE_PRIVATE)
        val existing = prefs.getString("id", null)
        if (existing != null) return existing
        val created = java.util.UUID.randomUUID().toString()
        prefs.edit().putString("id", created).apply()
        return created
    }
}
