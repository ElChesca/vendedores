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
    val longitude: Double?,
    val email: String = "",
    val dni: String = "",
    val city: String = "",
    val postalCode: String = "",
    val documentType: String = ""
) {
    companion object {
        fun fromJson(json: JSONObject) = SellerCustomer(
            id = json.optLong("id"),
            name = json.optString("nombre"),
            address = json.optString("direccion"),
            phone = json.optString("telefono"),
            latitude = json.numberOrNull("latitud"),
            longitude = json.numberOrNull("longitud"),
            email = json.textOrEmpty("email"),
            dni = json.textOrEmpty("dni"),
            city = json.textOrEmpty("ciudad"),
            postalCode = json.textOrEmpty("codigo_postal"),
            documentType = json.textOrEmpty("tipo_documento")
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

data class SellerOrder(
    val name: String,
    val fecha: String,
    val cliente: String,
    val total: Double,
    val neto: Double,
    val odooOrderId: Long?,
    val ventaId: Long?,
    val state: String
) {
    val title: String
        get() = name.ifBlank {
            ventaId?.let { "V-$it" } ?: odooOrderId?.let { "Pedido $it" } ?: "Pedido"
        }

    companion object {
        fun fromJson(json: JSONObject) = SellerOrder(
            name = json.optString("name"),
            fecha = json.optString("fecha"),
            cliente = json.optString("cliente"),
            total = json.numberOrNull("total") ?: 0.0,
            neto = json.numberOrNull("neto") ?: 0.0,
            odooOrderId = json.longOrNull("odoo_order_id"),
            ventaId = json.longOrNull("venta_id"),
            state = json.optString("state")
        )
    }
}

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
    val soldVsProjectedPercent: Double,
    val contratoNuevo: Boolean = false,
    val fuente: String = "erp",
    val umbralVerde: Double = 90.0,
    val umbralAmarillo: Double = 70.0,
    val netAmount: Double = 0.0,
    val grossAmount: Double = 0.0,
    val periodTarget: Double? = null,
    val monthTarget: Double? = null,
    val monthSold: Double? = null,
    val fulfillmentPercent: Double? = null,
    val difference: Double? = null,
    val monthRemaining: Double = 0.0,
    val dailyTargetRemaining: Double = 0.0,
    val businessDaysRemaining: Int = 0,
    val semaforo: String? = null,
    val recentOrders: List<SellerOrder> = emptyList()
) {
    companion object {
        fun fromJson(json: JSONObject): SellerSummary {
            val seller = json.optJSONArray("vendedores")?.optJSONObject(0) ?: JSONObject()
            val soldAmount = seller.numberOrNull("importe_vendido") ?: 0.0
            val netAmount = seller.optionalNumber("importe_neto") ?: soldAmount
            val grossAmount = seller.optionalNumber("importe_total") ?: netAmount
            val thresholds = seller.optJSONObject("umbrales") ?: json.optJSONObject("umbrales")
            val umbralVerde = thresholds?.optionalNumber("verde") ?: 90.0
            val umbralAmarillo = thresholds?.optionalNumber("amarillo") ?: 70.0
            val fulfillment = seller.optionalNumber("cumplimiento_pct")
            val semaforo = when {
                seller.has("semaforo") -> seller.optionalString("semaforo").toSemaforo()
                fulfillment != null -> fulfillment.toSemaforo(umbralVerde, umbralAmarillo)
                else -> null
            }
            return SellerSummary(
                projectedCustomers = seller.optInt("clientes_proyectados"),
                visitedCustomers = seller.optInt("clientes_visitados"),
                customersWithSale = seller.optInt("clientes_con_venta"),
                projectedAmount = seller.numberOrNull("importe_proyectado") ?: 0.0,
                soldAmount = soldAmount,
                targetAmount = seller.numberOrNull("importe_objetivo") ?: 0.0,
                coveragePercent = seller.numberOrNull("cobertura_pct") ?: 0.0,
                conversionPercent = seller.numberOrNull("conversion_pct") ?: 0.0,
                targetPercent = seller.numberOrNull("objetivo_pct") ?: 0.0,
                soldVsProjectedPercent = seller.numberOrNull("venta_vs_proyectado_pct") ?: 0.0,
                contratoNuevo = seller.hasNewContract() || json.has("fuente") || json.has("umbrales"),
                fuente = readFuente(seller, json),
                umbralVerde = umbralVerde,
                umbralAmarillo = umbralAmarillo,
                netAmount = netAmount,
                grossAmount = grossAmount,
                periodTarget = seller.optionalNumber("objetivo_periodo"),
                monthTarget = seller.optionalNumber("objetivo_mes"),
                monthSold = seller.optionalNumber("vendido_mes"),
                fulfillmentPercent = fulfillment,
                difference = seller.optionalNumber("diferencia"),
                monthRemaining = seller.optionalNumber("falta_mes") ?: 0.0,
                dailyTargetRemaining = seller.optionalNumber("meta_diaria_restante") ?: 0.0,
                businessDaysRemaining = seller.intOrDefault("dias_habiles_restantes"),
                semaforo = semaforo,
                recentOrders = seller.orderList("ultimos_pedidos")
            )
        }
    }
}

data class MonthPurchases(
    val mes: String,
    val neto: Double,
    val total: Double,
    val cantidad: Int
)

data class CustomerPurchases(
    val clienteId: Long,
    val clienteNombre: String,
    val fuente: String,
    val compradoMes: MonthPurchases,
    val ultimosPedidos: List<SellerOrder>
) {
    companion object {
        fun fromJson(json: JSONObject): CustomerPurchases {
            val month = json.optJSONObject("comprado_mes") ?: JSONObject()
            return CustomerPurchases(
                clienteId = json.longOrNull("cliente_id") ?: json.optLong("cliente_id"),
                clienteNombre = json.optString("cliente_nombre"),
                fuente = json.optionalString("fuente").toFuente(),
                compradoMes = MonthPurchases(
                    mes = month.optString("mes"),
                    neto = month.optionalNumber("neto") ?: 0.0,
                    total = month.optionalNumber("total") ?: 0.0,
                    cantidad = month.intOrDefault("cantidad")
                ),
                ultimosPedidos = json.orderList("ultimos_pedidos")
            )
        }
    }
}

sealed interface CustomerPurchasesState {
    data object Loading : CustomerPurchasesState
    data object Unavailable : CustomerPurchasesState
    data class Error(val message: String) : CustomerPurchasesState
    data class Ready(val purchases: CustomerPurchases) : CustomerPurchasesState
}

fun customerPurchasesState(status: Int?, error: String?, json: JSONObject?): CustomerPurchasesState = when {
    status == 404 -> CustomerPurchasesState.Unavailable
    error != null -> CustomerPurchasesState.Error(error)
    else -> runCatching { CustomerPurchases.fromJson(json ?: JSONObject()) }.fold(
        onSuccess = { CustomerPurchasesState.Ready(it) },
        onFailure = { CustomerPurchasesState.Error("No se pudo leer las compras del cliente") }
    )
}

fun JSONArray.toObjectList(): List<JSONObject> = List(length()) { index -> optJSONObject(index) ?: JSONObject() }

private val newContractKeys = listOf(
    "fuente", "umbrales", "importe_neto", "importe_total", "objetivo_periodo", "objetivo_mes",
    "vendido_mes", "cumplimiento_pct", "diferencia", "falta_mes", "meta_diaria_restante",
    "dias_habiles_restantes", "semaforo", "ultimos_pedidos"
)

private fun JSONObject.hasNewContract(): Boolean = newContractKeys.any { has(it) }

private fun readFuente(seller: JSONObject, root: JSONObject): String {
    val raw = when {
        seller.has("fuente") -> seller.optionalString("fuente")
        root.has("fuente") -> root.optionalString("fuente")
        else -> "erp"
    }
    return raw.toFuente()
}

private fun String?.toFuente(): String = if (this?.trim()?.equals("odoo", ignoreCase = true) == true) "odoo" else "erp"

private fun String?.toSemaforo(): String? {
    val normalized = this?.trim()?.lowercase() ?: return null
    return normalized.takeIf { it == "verde" || it == "amarillo" || it == "rojo" }
}

private fun Double.toSemaforo(verde: Double, amarillo: Double): String = when {
    this >= verde -> "verde"
    this >= amarillo -> "amarillo"
    else -> "rojo"
}

private fun JSONObject.orderList(key: String): List<SellerOrder> {
    val array = optJSONArray(key) ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            array.optJSONObject(index)?.let { add(SellerOrder.fromJson(it)) }
        }
    }
}

private fun JSONObject.numberOrNull(key: String): Double? {
    if (!has(key) || isNull(key)) return null
    return when (val value = opt(key)) {
        null -> null
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull()
        else -> null
    }
}

private fun JSONObject.optionalNumber(key: String): Double? = numberOrNull(key)

private fun JSONObject.optionalString(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key).takeIf { it.isNotBlank() }
}

private fun JSONObject.textOrEmpty(key: String): String {
    if (!has(key) || isNull(key)) return ""
    return optString(key).trim()
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

private fun JSONObject.intOrDefault(key: String, default: Int = 0): Int {
    val number = optionalNumber(key) ?: return default
    return number.toInt()
}
