package com.baboons.vendedorescalle

import org.json.JSONArray
import org.json.JSONObject

data class SellerSession(
    val token: String,
    val userId: Long,
    val businessId: Long,
    val sellerId: Long,
    val sellerName: String,
    val businessName: String
)

data class DailyMessage(
    val id: Long,
    val title: String,
    val content: String,
    val required: Boolean,
    val accepted: Boolean?
) {
    companion object {
        fun fromJson(json: JSONObject) = DailyMessage(
            id = json.optLong("id"),
            title = json.optString("titulo"),
            content = json.optString("contenido"),
            required = json.optBoolean("obligatorio"),
            accepted = if (json.isNull("aceptado")) null else json.optBoolean("aceptado")
        )
    }
}

data class SellerCustomer(
    val id: Long,
    val name: String,
    val address: String,
    val phone: String,
    val latitude: Double?,
    val longitude: Double?
) {
    companion object {
        fun fromJson(json: JSONObject) = SellerCustomer(
            id = json.optLong("id"),
            name = json.optString("nombre"),
            address = json.optString("direccion"),
            phone = json.optString("telefono"),
            latitude = json.numberOrNull("latitud"),
            longitude = json.numberOrNull("longitud")
        )
    }
}

data class PlanEntry(
    val id: Long,
    val proyectadoId: Long,
    val customerId: Long,
    val order: Int,
    val estimatedAmount: Double,
    val exception: Boolean,
    val exceptionReason: String
) {
    companion object {
        fun fromJson(json: JSONObject, planId: Long = 0) = PlanEntry(
            id = json.optLong("id"),
            proyectadoId = json.optLong("proyectado_id").takeIf { it > 0 } ?: planId,
            customerId = json.optLong("cliente_id"),
            order = json.optInt("orden"),
            estimatedAmount = json.optDouble("importe_estimado"),
            exception = json.optBoolean("es_excepcion"),
            exceptionReason = if (json.isNull("motivo_excepcion")) "" else json.optString("motivo_excepcion")
        )
    }
}

data class MapPoint(val latitude: Double, val longitude: Double)

data class SellerSummary(
    val projectedCustomers: Int,
    val visitedCustomers: Int,
    val customersWithSale: Int,
    val projectedAmount: Double,
    val soldAmount: Double,
    val targetAmount: Double,
    val coveragePercent: Double,
    val conversionPercent: Double,
    val targetPercent: Double,
    val soldVsProjectedPercent: Double
) {
    companion object {
        fun fromJson(json: JSONObject): SellerSummary {
            val seller = json.optJSONArray("vendedores")?.optJSONObject(0) ?: JSONObject()
            return SellerSummary(
                projectedCustomers = seller.optInt("clientes_proyectados"),
                visitedCustomers = seller.optInt("clientes_visitados"),
                customersWithSale = seller.optInt("clientes_con_venta"),
                projectedAmount = seller.numberOrNull("importe_proyectado") ?: 0.0,
                soldAmount = seller.numberOrNull("importe_vendido") ?: 0.0,
                targetAmount = seller.numberOrNull("importe_objetivo") ?: 0.0,
                coveragePercent = seller.numberOrNull("cobertura_pct") ?: 0.0,
                conversionPercent = seller.numberOrNull("conversion_pct") ?: 0.0,
                targetPercent = seller.numberOrNull("objetivo_pct") ?: 0.0,
                soldVsProjectedPercent = seller.numberOrNull("venta_vs_proyectado_pct") ?: 0.0
            )
        }
    }
}

fun JSONArray.toObjectList(): List<JSONObject> = List(length()) { index -> optJSONObject(index) ?: JSONObject() }

private fun JSONObject.numberOrNull(key: String): Double? {
    if (isNull(key)) return null
    return when (val value = opt(key)) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull()
        else -> null
    }
}
