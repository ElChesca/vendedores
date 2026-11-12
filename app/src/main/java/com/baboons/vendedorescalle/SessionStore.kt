package com.baboons.vendedorescalle

import android.content.Context
import android.util.Base64
import org.json.JSONObject

object SessionStore {
    private const val PREFS = "seller_session"

    fun load(context: Context): SellerSession? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val token = prefs.getString("token", null) ?: return null
        val payload = decodePayload(token) ?: return null
        return SellerSession(
            token = token,
            userId = payload.optLong("id"),
            businessId = payload.optLong("negocio_id"),
            sellerId = payload.optLong("vendedor_id"),
            sellerName = payload.optString("nombre"),
            businessName = payload.optString("negocio_nombre")
        ).takeIf { it.businessId > 0 && it.sellerId > 0 }
    }

    fun save(context: Context, token: String): SellerSession? {
        val payload = decodePayload(token) ?: return null
        val session = SellerSession(
            token = token,
            userId = payload.optLong("id"),
            businessId = payload.optLong("negocio_id"),
            sellerId = payload.optLong("vendedor_id"),
            sellerName = payload.optString("nombre"),
            businessName = payload.optString("negocio_nombre")
        )
        if (session.businessId <= 0 || session.sellerId <= 0) return null
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("token", token)
            .apply()
        return session
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun decodePayload(token: String): JSONObject? {
        return try {
            val payloadPart = token.split('.').getOrNull(1) ?: return null
            val decoded = Base64.decode(payloadPart, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            JSONObject(String(decoded, Charsets.UTF_8))
        } catch (_: Exception) {
            null
        }
    }
}
