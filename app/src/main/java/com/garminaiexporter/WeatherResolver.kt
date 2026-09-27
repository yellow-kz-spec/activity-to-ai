package com.garminaiexporter

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.abs

fun interface HistoricalWeatherClient {
    fun weather(latitude: Double, longitude: Double, time: Instant): JSONObject?
}

object WeatherResolver {
    private const val TAG = "GarminWeather"

    fun resolve(payload: JSONObject, fallback: HistoricalWeatherClient): JSONObject {
        val embedded = normalizeGarmin(payload.optJSONObject("weather"))
        if (embedded != null) {
            Log.d(TAG, "Garmin weather found")
            payload.put("weather", embedded)
            return payload
        }
        Log.d(TAG, "Garmin weather not found")
        payload.remove("weather")
        val activity = payload.optJSONObject("activity") ?: return payload.also {
            Log.d(TAG, "Weather fallback unavailable: activity metadata missing")
        }
        val summary = activity.optJSONObject("summaryDTO") ?: JSONObject()
        val latitude = firstNumber(summary, "startLatitude", "latitude")
        val longitude = firstNumber(summary, "startLongitude", "longitude")
        val time = parseTime(summary.optString("startTimeGMT", summary.optString("startTimeLocal")))
        if (latitude == null || longitude == null || time == null) {
            Log.d(TAG, "Weather fallback unavailable: activity time/location missing")
            return payload
        }
        Log.d(TAG, "Weather fallback attempted")
        return try {
            val weather = fallback.weather(latitude, longitude, time)
            if (weather == null || !hasUsefulValue(weather)) {
                Log.d(TAG, "Weather fallback unavailable")
            } else {
                payload.put("weather", weather)
                Log.d(TAG, "Weather fallback successful")
            }
            payload
        } catch (error: Exception) {
            Log.w(TAG, "Weather fallback failed: ${error.javaClass.simpleName}")
            payload
        }
    }

    private fun normalizeGarmin(source: JSONObject?): JSONObject? {
        if (source == null) return null
        val result = JSONObject()
        putText(result, "condition", source.optJSONObject("weatherTypeDTO")?.optString("desc"))
        putNumber(result, "temperature", source, "temp")
        putNumber(result, "feelsLike", source, "apparentTemp")
        putNumber(result, "dewPoint", source, "dewPoint")
        putNumber(result, "humidity", source, "relativeHumidity")
        putNumber(result, "windSpeed", source, "windSpeed")
        putNumber(result, "windGust", source, "windGust")
        putNumber(result, "windDirectionDegrees", source, "windDirection")
        putText(result, "windDirection", source.optString("windDirectionCompassPoint"))
        putNumber(result, "precipitation", source, "precipitation")
        putText(result, "station", source.optJSONObject("weatherStationDTO")?.optString("name"))
        result.put("source", "Garmin")
        return result.takeIf(::hasUsefulValue)
    }

    private fun parseTime(value: String): Instant? {
        if (value.isBlank()) return null
        return runCatching { Instant.parse(value) }.getOrElse {
            runCatching { LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME).toInstant(ZoneOffset.UTC) }.getOrNull()
        }
    }

    private fun firstNumber(source: JSONObject, vararg keys: String): Double? = keys.firstNotNullOfOrNull { key ->
        if (!source.has(key) || source.isNull(key)) null else source.optDouble(key, Double.NaN).takeUnless(Double::isNaN)
    }

    private fun putNumber(target: JSONObject, targetKey: String, source: JSONObject, sourceKey: String) {
        if (!source.has(sourceKey) || source.isNull(sourceKey)) return
        source.optDouble(sourceKey, Double.NaN).takeUnless(Double::isNaN)?.let { target.put(targetKey, it) }
    }

    private fun putText(target: JSONObject, key: String, value: String?) {
        value?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", true) }?.let { target.put(key, it) }
    }

    private fun hasUsefulValue(source: JSONObject): Boolean = source.keys().asSequence().any { key ->
        val value = source.opt(key)
        key != "source" && value != null && value != JSONObject.NULL && value.toString().isNotBlank()
    }
}

class OpenMeteoWeatherClient : HistoricalWeatherClient {
    override fun weather(latitude: Double, longitude: Double, time: Instant): JSONObject? {
        val date = time.atZone(ZoneOffset.UTC).toLocalDate()
        val query = linkedMapOf(
            "latitude" to latitude.toString(),
            "longitude" to longitude.toString(),
            "start_date" to date.toString(),
            "end_date" to date.toString(),
            "hourly" to "temperature_2m,apparent_temperature,relative_humidity_2m,precipitation,weather_code,wind_speed_10m,wind_direction_10m,wind_gusts_10m",
            "temperature_unit" to "fahrenheit",
            "wind_speed_unit" to "mph",
            "timezone" to "UTC"
        ).entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, Charsets.UTF_8.name())}" }
        val connection = URL("https://archive-api.open-meteo.com/v1/archive?$query").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            if (connection.responseCode !in 200..299) return null
            val response = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            return normalize(response, time)
        } finally {
            connection.disconnect()
        }
    }

    private fun normalize(response: JSONObject, target: Instant): JSONObject? {
        val hourly = response.optJSONObject("hourly") ?: return null
        val times = hourly.optJSONArray("time") ?: return null
        val index = (0 until times.length()).minByOrNull { i ->
            val instant = runCatching { LocalDateTime.parse(times.optString(i)).toInstant(ZoneOffset.UTC) }.getOrNull()
            if (instant == null) Long.MAX_VALUE else abs(instant.epochSecond - target.epochSecond)
        } ?: return null
        val out = JSONObject().put("source", "Open-Meteo historical")
        number(hourly, "temperature_2m", index)?.let { out.put("temperature", it) }
        number(hourly, "apparent_temperature", index)?.let { out.put("feelsLike", it) }
        number(hourly, "relative_humidity_2m", index)?.let { out.put("humidity", it) }
        number(hourly, "precipitation", index)?.let { out.put("precipitation", it) }
        number(hourly, "wind_speed_10m", index)?.let { out.put("windSpeed", it) }
        number(hourly, "wind_direction_10m", index)?.let { out.put("windDirectionDegrees", it) }
        number(hourly, "wind_gusts_10m", index)?.let { out.put("windGust", it) }
        number(hourly, "weather_code", index)?.toInt()?.let { out.put("condition", weatherCode(it)) }
        return out.takeIf { it.length() > 1 }
    }

    private fun number(hourly: JSONObject, key: String, index: Int): Double? {
        val array: JSONArray = hourly.optJSONArray(key) ?: return null
        if (array.isNull(index)) return null
        return array.optDouble(index, Double.NaN).takeUnless(Double::isNaN)
    }

    private fun weatherCode(code: Int): String = when (code) {
        0 -> "Clear sky"
        1 -> "Mainly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        51, 53, 55, 56, 57 -> "Drizzle"
        61, 63, 65, 66, 67 -> "Rain"
        71, 73, 75, 77 -> "Snow"
        80, 81, 82 -> "Rain showers"
        85, 86 -> "Snow showers"
        95, 96, 99 -> "Thunderstorm"
        else -> "Weather code $code"
    }
}
