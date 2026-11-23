
package com.baboons.vendedorescalle

import org.json.JSONArray
import org.json.JSONObject

fun extractArray(json: JSONObject?, array: JSONArray?, key: String): JSONArray {
    if (array != null) return array
    if (json == null) return JSONArray()
    return json.optJSONArray(key) ?: json.optJSONObject("data")?.optJSONArray(key) ?: json.optJSONArray("data") ?: JSONArray()
}

private val localeAr = java.util.Locale.forLanguageTag("es-AR")

fun Double.pretty(): String = String.format(localeAr, "%.1f", this)

fun Double.percentLabel(): String {
    val format = java.text.NumberFormat.getNumberInstance(localeAr)
    format.minimumFractionDigits = 0
    format.maximumFractionDigits = 2
    format.isGroupingUsed = true
    return "${format.format(this)}%"
}

fun money(value: Double): String =
    java.text.NumberFormat.getCurrencyInstance(localeAr).format(value)

fun fuenteLabel(fuente: String): String = if (fuente == "odoo") "Ventas de Odoo" else "Ventas del ERP"

fun semaforoLabel(semaforo: String): String = when (semaforo) {
    "verde" -> "Verde"
    "amarillo" -> "Amarillo"
    "rojo" -> "Rojo"
    else -> semaforo
}

fun orderStateLabel(state: String): String = when (state.trim().lowercase(localeAr)) {
    "sale" -> "Confirmado"
    "draft" -> "Borrador"
    "sent" -> "Presupuesto"
    "done" -> "Cerrado"
    "cancel", "cancelled" -> "Cancelado"
    "" -> ""
    else -> state.trim().replace('_', ' ').replaceFirstChar { char ->
        if (char.isLowerCase()) char.titlecase(localeAr) else char.toString()
    }
}

fun formatOrderDate(raw: String): String {
    val value = raw.trim()
    if (value.isEmpty()) return ""
    val output = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", localeAr)
    val offset = runCatching { java.time.OffsetDateTime.parse(value) }.getOrNull()
    if (offset != null) return offset.format(output)
    val dateTimePatterns = listOf(
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd HH:mm",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ss.SSS",
        "yyyy-MM-dd'T'HH:mm"
    )
    for (pattern in dateTimePatterns) {
        val parsed = runCatching {
            java.time.LocalDateTime.parse(value, java.time.format.DateTimeFormatter.ofPattern(pattern))
        }.getOrNull() ?: continue
        return parsed.format(output)
    }
    val date = runCatching { java.time.LocalDate.parse(value.take(10)) }.getOrNull()
    return date?.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy", localeAr)) ?: value
}

fun formatMonthLabel(raw: String): String {
    val value = raw.trim()
    if (value.isEmpty()) return "el mes"
    val month = runCatching { java.time.YearMonth.parse(value.take(7)) }.getOrNull() ?: return value
    val label = month.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", localeAr))
    return label.replaceFirstChar { char ->
        if (char.isLowerCase()) char.titlecase(localeAr) else char.toString()
    }
}

fun diasHabilesLabel(dias: Int): String = when {
    dias <= 0 -> "No quedan días hábiles (lunes a sábado)"
    dias == 1 -> "1 día hábil restante (lunes a sábado)"
    else -> "$dias días hábiles restantes (lunes a sábado)"
}

fun pedidosCountLabel(cantidad: Int): String = when (cantidad) {
    1 -> "1 pedido"
    else -> "$cantidad pedidos"
}
