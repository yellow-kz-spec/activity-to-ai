package com.garminaiexporter

import org.json.JSONObject
import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class StravaApiClient {
    fun activity(id: Long, accessToken: String): JSONObject = get("/api/v3/activities/$id", accessToken)

    fun streams(id: Long, accessToken: String): JSONObject = runCatching {
        get("/api/v3/activities/$id/streams?keys=${STREAM_KEYS.joinToString(",")}&key_by_type=true", accessToken)
    }.getOrDefault(JSONObject())

    fun recentActivities(accessToken: String): JSONArray = getArray("/api/v3/athlete/activities?per_page=30&page=1", accessToken)

    private fun get(path: String, accessToken: String): JSONObject {
        return JSONObject(request(path, accessToken))
    }

    private fun getArray(path: String, accessToken: String): JSONArray = JSONArray(request(path, accessToken))

    private fun request(path: String, accessToken: String): String {
        val connection = URL("https://www.strava.com$path").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Accept", "application/json")
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw StravaApiException(status)
            return response
        } catch (error: java.net.SocketTimeoutException) {
            throw IOException("Strava request timed out", error)
        } finally { connection.disconnect() }
    }

    companion object {
        val STREAM_KEYS = listOf("time", "distance", "latlng", "altitude", "velocity_smooth", "heartrate", "cadence", "watts", "temp", "moving", "grade_smooth")
    }
}

class StravaApiException(val statusCode: Int) : IOException("Strava API returned HTTP $statusCode")
