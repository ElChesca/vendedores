package com.baboons.vendedorescalle

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

const val VerificationAccuracyWarnMeters = 50f

data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float
)

data class CustomerVerificationStatus(
    val estado: String,
    val creadoEn: String?,
    val id: Long? = null
)

data class CustomerFicha(
    val id: Long,
    val name: String,
    val address: String,
    val phone: String,
    val email: String,
    val dni: String,
    val city: String,
    val postalCode: String,
    val latitude: Double?,
    val longitude: Double?,
    val odooPartnerId: Long?,
    val documentType: String,
    val lastVerification: CustomerVerificationStatus?
) {
    companion object {
        fun fromJson(json: JSONObject): CustomerFicha {
            val root = json.optJSONObject("ficha") ?: json.optJSONObject("cliente") ?: json
            return CustomerFicha(
                id = root.longOrNull("cliente_id") ?: root.longOrNull("id") ?: 0L,
                name = root.textOrEmpty("nombre"),
                address = root.textOrEmpty("direccion"),
                phone = root.textOrEmpty("telefono"),
                email = root.textOrEmpty("email"),
                dni = root.textOrEmpty("dni"),
                city = root.textOrEmpty("ciudad"),
                postalCode = root.textOrEmpty("codigo_postal"),
                latitude = root.doubleOrNull("latitud"),
                longitude = root.doubleOrNull("longitud"),
                odooPartnerId = root.longOrNull("odoo_partner_id"),
                documentType = root.textOrEmpty("tipo_documento"),
                lastVerification = root.verificationOrNull()
            )
        }
    }
}

data class FichaField(val label: String, val value: String, val missing: Boolean)

data class FichaResolution(
    val ficha: CustomerFicha,
    val note: String?,
    val noteIsError: Boolean
)

data class VerificationDraft(
    val phone: String,
    val email: String,
    val address: String,
    val dni: String,
    val city: String,
    val postalCode: String,
    val notes: String
)

data class PendingVerification(
    val customerId: Long,
    val idempotencyKey: String,
    val latitude: Double?,
    val longitude: Double?,
    val accuracyMeters: Double?,
    val phone: String,
    val email: String,
    val address: String,
    val dni: String,
    val city: String,
    val postalCode: String,
    val notes: String,
    val visitId: Long?,
    val savedAtMs: Long
) {
    fun toFix(): LocationFix? {
        val lat = latitude ?: return null
        val lng = longitude ?: return null
        return LocationFix(lat, lng, (accuracyMeters ?: 0.0).toFloat())
    }

    fun toBody(): JSONObject {
        val body = JSONObject().put("idempotency_key", idempotencyKey)
        if (latitude != null && longitude != null) {
            body.put("latitud", latitude).put("longitud", longitude)
            accuracyMeters?.let { body.put("precision_m", it) }
        }
        putText(body, "telefono", phone)
        putText(body, "email", email)
        putText(body, "direccion", address)
        putText(body, "dni", dni)
        putText(body, "ciudad", city)
        putText(body, "codigo_postal", postalCode)
        putText(body, "observaciones", notes)
        if (visitId != null && visitId > 0) body.put("visita_id", visitId)
        return body
    }

    fun toStoredJson(): JSONObject = JSONObject()
        .put("customer_id", customerId)
        .put("idempotency_key", idempotencyKey)
        .put("saved_at_ms", savedAtMs)
        .put("body", toBody())

    companion object {
        fun fromStored(json: JSONObject): PendingVerification? {
            val body = json.optJSONObject("body") ?: return null
            val customerId = json.longOrNull("customer_id") ?: return null
            val key = json.textOrEmpty("idempotency_key").ifBlank { body.textOrEmpty("idempotency_key") }
            if (customerId <= 0 || key.isBlank()) return null
            val latitude = body.doubleOrNull("latitud")
            val longitude = body.doubleOrNull("longitud")
            val pin = if (latitude != null && longitude != null) latitude to longitude else null
            return PendingVerification(
                customerId = customerId,
                idempotencyKey = key,
                latitude = pin?.first,
                longitude = pin?.second,
                accuracyMeters = if (pin == null) null else body.doubleOrNull("precision_m"),
                phone = body.textOrEmpty("telefono"),
                email = body.textOrEmpty("email"),
                address = body.textOrEmpty("direccion"),
                dni = body.textOrEmpty("dni"),
                city = body.textOrEmpty("ciudad"),
                postalCode = body.textOrEmpty("codigo_postal"),
                notes = body.textOrEmpty("observaciones"),
                visitId = body.longOrNull("visita_id"),
                savedAtMs = json.longOrNull("saved_at_ms") ?: 0L
            )
        }
    }
}

sealed interface VerificationSubmitResult {
    data class Accepted(val id: Long?, val estado: String, val idempotent: Boolean) : VerificationSubmitResult
    data class Rejected(val message: String, val status: Int) : VerificationSubmitResult
    data class Offline(val message: String) : VerificationSubmitResult
}

fun SellerCustomer.toFicha(): CustomerFicha = CustomerFicha(
    id = id,
    name = name,
    address = address,
    phone = phone,
    email = email,
    dni = dni,
    city = city,
    postalCode = postalCode,
    latitude = latitude,
    longitude = longitude,
    odooPartnerId = null,
    documentType = documentType,
    lastVerification = null
)

fun CustomerFicha.displayFields(): List<FichaField> {
    val location = if (latitude != null && longitude != null && isValidMapCoordinate(latitude, longitude)) {
        "${formatCoordinate(latitude)}, ${formatCoordinate(longitude)}"
    } else {
        ""
    }
    return listOf(
        FichaField("Teléfono", phone.trim(), phone.isBlank()),
        FichaField("Email", email.trim(), email.isBlank()),
        FichaField("Dirección", address.trim(), address.isBlank()),
        FichaField("CUIT/DNI", dni.trim(), dni.isBlank()),
        FichaField("Tipo de documento", documentType.trim(), documentType.isBlank()),
        FichaField("Ciudad", city.trim(), city.isBlank()),
        FichaField("Código postal", postalCode.trim(), postalCode.isBlank()),
        FichaField("Ubicación", location, location.isBlank())
    )
}

fun resolveCustomerFicha(
    status: Int?,
    error: String?,
    json: JSONObject?,
    customer: SellerCustomer
): FichaResolution {
    if (status == 404) {
        return FichaResolution(
            customer.toFicha(),
            "La ficha del ERP no está disponible. Se muestran los datos que ya tiene la app.",
            false
        )
    }
    if (status == 503) {
        return FichaResolution(
            customer.toFicha(),
            "Función no disponible todavía en el servidor. Se muestran los datos que ya tiene la app.",
            true
        )
    }
    if (status != null && status in 200..299 && error == null) {
        val parsed = runCatching { CustomerFicha.fromJson(json ?: JSONObject()) }.getOrNull()
        if (parsed != null) {
            return FichaResolution(
                parsed.copy(
                    id = parsed.id.takeIf { it > 0 } ?: customer.id,
                    name = parsed.name.ifBlank { customer.name }
                ),
                null,
                false
            )
        }
        return FichaResolution(
            customer.toFicha(),
            "No se pudo leer la ficha. Se muestran los datos que ya tiene la app.",
            true
        )
    }
    if (status == null) {
        return FichaResolution(
            customer.toFicha(),
            "Sin conexión. Se muestran los datos que ya tiene la app.",
            false
        )
    }
    return FichaResolution(
        customer.toFicha(),
        error?.takeIf { it.isNotBlank() } ?: "No se pudo leer la ficha. Se muestran los datos que ya tiene la app.",
        true
    )
}

private val emailPattern = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

fun phoneVerificationError(raw: String): String? {
    val value = raw.trim()
    if (value.isEmpty()) return null
    if (value.count { it.isDigit() } < 6) return "El teléfono tiene que tener al menos 6 dígitos."
    return null
}

fun emailVerificationError(raw: String): String? {
    val value = raw.trim()
    if (value.isEmpty()) return null
    if (!emailPattern.matches(value)) return "El email no tiene un formato válido."
    return null
}

fun dniVerificationError(raw: String): String? {
    if (raw.trim().isEmpty()) return null
    if (dniDigits(raw) == null) return "El CUIT/DNI tiene que tener solo dígitos, entre 7 y 11."
    return null
}

fun verificationFieldErrors(phone: String, email: String, dni: String): List<String> =
    listOfNotNull(phoneVerificationError(phone), emailVerificationError(email), dniVerificationError(dni))

fun verificationPayloadError(draft: VerificationDraft, fix: LocationFix?): String? {
    val hasData = listOf(draft.phone, draft.email, draft.address, draft.dni, draft.city, draft.postalCode)
        .any { it.isNotBlank() }
    val hasPin = fix != null && isValidMapCoordinate(fix.latitude, fix.longitude)
    if (!hasData && !hasPin) return "Hay que cargar al menos un dato o fijar la ubicación."
    return null
}

fun dniDigits(raw: String): String? {
    val value = raw.trim()
    if (value.isEmpty()) return ""
    var compact = value.uppercase().replace(" ", "")
    if (compact.startsWith("AR")) {
        val rest = compact.removePrefix("AR")
        if (rest.isNotEmpty() && rest.all { it.isDigit() || it == '-' || it == '.' || it == '/' }) compact = rest
    }
    if (!compact.all { it.isDigit() || it == '-' || it == '.' || it == '/' }) return null
    val digits = compact.filter { it.isDigit() }
    if (digits.isEmpty() || digits.all { it == '0' } || digits.length !in 7..11) return null
    return digits
}

fun normalizeDni(raw: String): String = dniDigits(raw).orEmpty()

fun accuracyWarning(accuracyMeters: Float): String? = when {
    !accuracyMeters.isFinite() || accuracyMeters <= 0f ->
        "No se informó una precisión confiable. Podés enviar igual."
    accuracyMeters > VerificationAccuracyWarnMeters ->
        "La precisión es de ${accuracyMeters.roundToInt()} m, peor que 50 m. Podés enviar igual."
    else -> null
}

fun formatCoordinate(value: Double): String = String.format(java.util.Locale.US, "%.6f", value)

fun formatAccuracyMeters(accuracyMeters: Float): String {
    if (!accuracyMeters.isFinite() || accuracyMeters <= 0f) return "sin dato"
    return "${accuracyMeters.roundToInt()} m"
}

fun verificationEstadoLabel(estado: String): String = when (estado.trim().lowercase()) {
    "pendiente" -> "Pendiente de aprobación"
    "aprobada", "aprobado" -> "Aprobada"
    "rechazada", "rechazado" -> "Rechazada"
    "empujado" -> "Aprobada y enviada"
    "error_push" -> "Aprobada, con error al enviar"
    "" -> "Pendiente de aprobación"
    else -> estado.trim().replaceFirstChar { char ->
        if (char.isLowerCase()) char.titlecase() else char.toString()
    }
}

fun interpretVerification(result: ApiResult): VerificationSubmitResult {
    val status = result.status
    if (status != null && status in 200..299 && result.error == null) {
        val estado = result.json?.textOrEmpty("estado")?.ifBlank { "pendiente" } ?: "pendiente"
        val id = result.json?.longOrNull("id")
        val idempotent = result.json?.optBoolean("idempotente", false) == true
        return VerificationSubmitResult.Accepted(id, estado, idempotent)
    }
    if (status == null) {
        return VerificationSubmitResult.Offline(
            "Sin conexión. La verificación quedó guardada y se reintenta con la misma clave."
        )
    }
    return VerificationSubmitResult.Rejected(readableVerificationError(result.error, status), status)
}

fun shouldKeepPending(outcome: VerificationSubmitResult): Boolean = when (outcome) {
    is VerificationSubmitResult.Accepted -> false
    is VerificationSubmitResult.Offline -> true
    is VerificationSubmitResult.Rejected -> outcome.status == 408 || outcome.status == 429 || outcome.status >= 500
}

fun readableVerificationError(raw: String?, status: Int): String {
    val text = raw?.trim().orEmpty()
    val generic = text.isBlank() || text == "Error HTTP $status"
    return when (status) {
        409 -> if (generic) "El ERP no aceptó la verificación." else text
        401, 403 -> "No se pudo autorizar el envío. Volvé a iniciar sesión."
        404 -> "El cliente no está en este negocio."
        408, 429 -> "El ERP no respondió a tiempo. La verificación quedó guardada para reintentar."
        503 -> "Función no disponible todavía en el servidor. La verificación quedó guardada para reintentar."
        in 500..599 -> "El ERP no pudo guardar la verificación. Quedó guardada para reintentar."
        else -> if (generic) "No se pudo enviar la verificación (HTTP $status)." else text
    }
}

fun encodePendingList(items: List<PendingVerification>): String {
    val array = JSONArray()
    items.forEach { array.put(it.toStoredJson()) }
    return array.toString()
}

fun decodePendingList(raw: String?): List<PendingVerification> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.let { PendingVerification.fromStored(it) }?.let { add(it) }
            }
        }
    }.getOrDefault(emptyList())
}

class VerificationOutbox(context: Context) {
    private val prefs = context.getSharedPreferences("verification_outbox", Context.MODE_PRIVATE)

    @Synchronized
    fun all(): List<PendingVerification> = decodePendingList(prefs.getString(KEY, null))

    @Synchronized
    fun get(customerId: Long): PendingVerification? = all().firstOrNull { it.customerId == customerId }

    @Synchronized
    fun save(item: PendingVerification) {
        val next = all().filterNot { it.customerId == item.customerId } + item
        prefs.edit().putString(KEY, encodePendingList(next)).apply()
    }

    @Synchronized
    fun remove(customerId: Long) {
        val next = all().filterNot { it.customerId == customerId }
        prefs.edit().putString(KEY, encodePendingList(next)).apply()
    }

    private companion object {
        const val KEY = "items"
    }
}

class DailyVisitStore(context: Context) {
    private val prefs = context.getSharedPreferences("daily_visits", Context.MODE_PRIVATE)

    fun remember(date: String, customerId: Long, visitId: Long) {
        if (visitId <= 0) return
        prefs.edit().putLong(key(date, customerId), visitId).apply()
    }

    fun get(date: String, customerId: Long): Long? {
        val name = key(date, customerId)
        if (!prefs.contains(name)) return null
        return prefs.getLong(name, 0L).takeIf { it > 0 }
    }

    private fun key(date: String, customerId: Long) = "$date:$customerId"
}

private fun JSONObject.verificationOrNull(): CustomerVerificationStatus? {
    if (!has("ultima_verificacion") || isNull("ultima_verificacion")) return null
    val obj = optJSONObject("ultima_verificacion") ?: return null
    val estado = obj.textOrEmpty("estado")
    if (estado.isEmpty()) return null
    return CustomerVerificationStatus(
        estado,
        obj.textOrEmpty("creado_en").takeIf { it.isNotEmpty() },
        obj.longOrNull("id")
    )
}

private fun putText(body: JSONObject, key: String, value: String) {
    val text = value.trim()
    if (text.isNotEmpty()) body.put(key, text)
}

private fun JSONObject.textOrEmpty(key: String): String {
    if (!has(key) || isNull(key)) return ""
    return optString(key).trim()
}

private fun JSONObject.doubleOrNull(key: String): Double? {
    if (!has(key) || isNull(key)) return null
    return when (val value = opt(key)) {
        null -> null
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull()
        else -> null
    }
}

private fun JSONObject.longOrNull(key: String): Long? {
    if (!has(key) || isNull(key)) return null
    return when (val value = opt(key)) {
        null -> null
        is Number -> value.toLong()
        is String -> value.toLongOrNull()
        else -> null
    }
}
