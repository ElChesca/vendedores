
package com.baboons.vendedorescalle

import org.json.JSONArray
import org.json.JSONObject

fun extractArray(json: JSONObject?, array: JSONArray?, key: String): JSONArray {
    if (array != null) return array
    if (json == null) return JSONArray()
    return json.optJSONArray(key) ?: json.optJSONObject("data")?.optJSONArray(key) ?: json.optJSONArray("data") ?: JSONArray()
}

fun Double.pretty(): String = String.format(java.util.Locale("es", "AR"), "%.1f", this)

fun money(value: Double): String =
    java.text.NumberFormat.getCurrencyInstance(java.util.Locale("es", "AR")).format(value)
