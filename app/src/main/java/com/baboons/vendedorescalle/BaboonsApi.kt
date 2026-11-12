package com.baboons.vendedorescalle

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

data class ApiResult(val json: JSONObject?, val array: JSONArray?, val error: String?, val status: Int?)

object BaboonsApi {
    private val io = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())

    fun login(email: String, password: String, callback: (ApiResult) -> Unit) {
        val body = JSONObject().put("nombre", email).put("password", password)
        request("POST", "login", null, body, callback)
    }

    fun jornada(session: SellerSession, date: String, callback: (ApiResult) -> Unit) =
        request("GET", biz(session, "vendedores-calle/jornada/actual?fecha=${encode(date)}"), session, null, callback)

    fun iniciarJornada(session: SellerSession, date: String, callback: (ApiResult) -> Unit) =
        request("POST", biz(session, "vendedores-calle/jornada/iniciar"), session,
            JSONObject().put("fecha", date), callback)

    fun cerrarJornada(session: SellerSession, date: String, callback: (ApiResult) -> Unit) =
        request("POST", biz(session, "vendedores-calle/jornada/cerrar"), session,
            JSONObject().put("fecha", date), callback)

    fun mensajes(session: SellerSession, date: String, callback: (ApiResult) -> Unit) =
        request("GET", biz(session, "vendedores-calle/mensajes?fecha=${encode(date)}"), session, null, callback)

    fun responderMensaje(session: SellerSession, messageId: Long, accepted: Boolean, response: String,
                         callback: (ApiResult) -> Unit) = request(
        "POST", biz(session, "vendedores-calle/mensajes/$messageId/respuesta"), session,
        JSONObject().put("aceptado", accepted).put("respuesta", response), callback
    )

    fun clientesProyectables(session: SellerSession, date: String, callback: (ApiResult) -> Unit) =
        request("GET", biz(session, "vendedores-calle/clientes-proyectables?fecha=${encode(date)}"), session, null, callback)

    fun buscarClientes(session: SellerSession, query: String, callback: (ApiResult) -> Unit) =
        request("GET", biz(session, "vendedores-calle/clientes-buscar?q=${encode(query)}"), session, null, callback)

    fun getProyectado(session: SellerSession, date: String, callback: (ApiResult) -> Unit) =
        request("GET", biz(session, "vendedores-calle/proyectado?fecha=${encode(date)}"), session, null, callback)

    fun saveProyectado(session: SellerSession, date: String, items: JSONArray, callback: (ApiResult) -> Unit) =
        request("PUT", biz(session, "vendedores-calle/proyectado"), session,
            JSONObject().put("fecha", date).put("items", items), callback)

    fun agregarExcepcion(session: SellerSession, date: String, customerId: Long, reason: String,
                         callback: (ApiResult) -> Unit) = request(
        "POST", biz(session, "vendedores-calle/proyectado/excepciones"), session,
        JSONObject().put("fecha", date).put("cliente_id", customerId).put("motivo", reason), callback
    )

    fun registrarVisita(session: SellerSession, date: String, planId: Long, customerId: Long,
                        result: String, notes: String, idempotencyKey: String, callback: (ApiResult) -> Unit) = request(
        "POST", biz(session, "vendedores-calle/visitas"), session,
        JSONObject().put("fecha", date).put("proyectado_id", planId).put("cliente_id", customerId)
            .put("resultado", result).put("observaciones", notes).put("idempotency_key", idempotencyKey), callback
    )

    fun crearProspecto(session: SellerSession, body: JSONObject, callback: (ApiResult) -> Unit) =
        request("POST", biz(session, "vendedores-calle/prospectos"), session, body, callback)

    fun tablero(session: SellerSession, date: String, callback: (ApiResult) -> Unit) =
        tablero(session, date, date, callback)

    fun tablero(session: SellerSession, from: String, until: String, callback: (ApiResult) -> Unit) =
        request("GET", biz(session, "vendedores-calle/tablero?desde=${encode(from)}&hasta=${encode(until)}&vendedor_id=${session.sellerId}"), session, null, callback)

    fun miRastro(session: SellerSession, date: String, callback: (ApiResult) -> Unit) =
        request("GET", "negocios/${session.businessId}/mobile/mi-rastro?fecha=${encode(date)}", session, null, callback)

    fun subirUbicaciones(session: SellerSession, deviceId: String, points: JSONArray,
                         callback: (ApiResult) -> Unit) = request(
        "POST", "negocios/${session.businessId}/mobile/tracking", session,
        JSONObject().put("device_id", deviceId).put("posiciones", points), callback
    )

    private fun biz(session: SellerSession, path: String) = "negocios/${session.businessId}/$path"

    private fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private fun request(method: String, path: String, session: SellerSession?, body: JSONObject?, callback: (ApiResult) -> Unit) {
        io.execute {
            val result = try {
                val connection = URL(BuildConfig.BABOONS_API_BASE.trimEnd('/') + "/" + path.trimStart('/'))
                    .openConnection() as HttpURLConnection
                connection.requestMethod = method
                connection.connectTimeout = 15000
                connection.readTimeout = 20000
                connection.setRequestProperty("Accept", "application/json")
                session?.let { connection.setRequestProperty("Authorization", "Bearer ${it.token}") }
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connection.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
                }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val raw = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
                connection.disconnect()
                val isArray = raw.trimStart().startsWith("[")
                val json = if (raw.isBlank() || isArray) null else JSONObject(raw)
                val array = if (isArray) JSONArray(raw) else null
                if (status in 200..299) ApiResult(json, array, null, status)
                else ApiResult(null, null, json?.optString("error")?.takeIf { it.isNotBlank() }
                    ?: json?.optString("message")?.takeIf { it.isNotBlank() }
                    ?: "Error HTTP $status", status)
            } catch (error: Exception) {
                ApiResult(null, null, error.message ?: "No se pudo conectar con Baboons", null)
            }
            main.post { callback(result) }
        }
    }
}
