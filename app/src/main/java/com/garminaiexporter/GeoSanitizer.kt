package com.garminaiexporter

import org.json.JSONArray
import org.json.JSONObject

object GeoSanitizer {
    private val blockedFragments = listOf(
        "latitude", "longitude", "polyline", "coordinate", "geolocation", "mapurl"
    )

    fun sanitize(value: Any?): Any? = when (value) {
        is JSONObject -> sanitizeObject(value)
        is JSONArray -> sanitizeArray(value)
        else -> value
    }

    private fun sanitizeObject(source: JSONObject): JSONObject {
        val result = JSONObject()
        val keys = source.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (isBlocked(key)) continue
            result.put(key, sanitize(source.opt(key)))
        }
        return result
    }

    private fun sanitizeArray(source: JSONArray): JSONArray {
        val result = JSONArray()
        for (index in 0 until source.length()) result.put(sanitize(source.opt(index)))
        return result
    }

    fun isBlocked(key: String): Boolean {
        val normalized = key.lowercase().replace("_", "").replace("-", "")
        return blockedFragments.any(normalized::contains)
    }
}
