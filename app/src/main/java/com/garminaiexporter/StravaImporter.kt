package com.garminaiexporter

import android.content.Context
import android.util.Log
import java.time.Instant
import java.util.concurrent.Executors

class StravaImporter(context: Context) {
    private val auth = StravaAuthManager(context)
    private val executor = Executors.newSingleThreadExecutor()

    fun import(sharedText: String?, callback: (Result<ExportDocument>) -> Unit) {
        val url = StravaShareParser.extractUrl(sharedText)
            ?: return callback(Result.failure(ImportException("Could not identify the Strava activity.")))
        stage("Extracted URL: $url")
        if (!auth.isStravaConnected()) return callback(Result.failure(ImportException("Strava authorization expired. Please reconnect Strava.")))
        auth.getValidAccessToken { tokenResult ->
            tokenResult.onFailure {
                callback(Result.failure(ImportException(if (!auth.isStravaConnected()) "Strava authorization expired. Please reconnect Strava." else "Could not download activity from Strava.")))
            }.onSuccess { token ->
                executor.execute {
                    val result = runCatching {
                        stage("Resolving Strava share link")
                        val resolved = if (StravaShareParser.activityId(url) != null) url else runCatching { StravaLinkResolver().resolve(url) }.getOrNull()
                        val id = StravaShareParser.activityId(resolved) ?: throw ImportException("Could not identify the Strava activity.", true)
                        stage("Resolved activity ID: $id")
                        stage("Fetching Strava activity")
                        val api = StravaApiClient()
                        val activity = api.activity(id, token)
                        val streams = api.streams(id, token)
                        stage("Mapping Strava activity")
                        val normalized = StravaActivityMapper.map(activity, streams)
                        enrichWeather(activity, normalized)
                        stage("Generating Markdown")
                        MarkdownExporter.create(normalized)
                    }.recoverCatching { error ->
                        if (error is ImportException) throw error
                        if (error is StravaApiException && error.statusCode == 401) {
                            auth.disconnect()
                            throw ImportException("Strava authorization expired. Please reconnect Strava.")
                        }
                        throw ImportException("Could not download activity from Strava.")
                    }
                    callback(result)
                }
            }
        }
    }

    data class Candidate(val id: Long, val name: String, val sport: String, val start: String)

    fun recentActivities(callback: (Result<List<Candidate>>) -> Unit) {
        auth.getValidAccessToken { tokenResult ->
            tokenResult.onFailure { callback(Result.failure(ImportException("Strava authorization expired. Please reconnect Strava."))) }
                .onSuccess { token -> executor.execute {
                    callback(runCatching {
                        val array = StravaApiClient().recentActivities(token)
                        (0 until array.length()).mapNotNull { index -> array.optJSONObject(index)?.let { item ->
                            item.optLong("id").takeIf { it > 0 }?.let { Candidate(it, item.optString("name", "Strava activity"), item.optString("sport_type", item.optString("type")), item.optString("start_date_local")) }
                        }}
                    })
                }}
        }
    }

    fun importActivity(id: Long, callback: (Result<ExportDocument>) -> Unit) {
        auth.getValidAccessToken { tokenResult ->
            tokenResult.onFailure { callback(Result.failure(ImportException("Strava authorization expired. Please reconnect Strava."))) }
                .onSuccess { token -> executor.execute { callback(fetchAndMap(id, token)) } }
        }
    }

    private fun fetchAndMap(id: Long, token: String): Result<ExportDocument> = runCatching {
        val api = StravaApiClient()
        val raw = api.activity(id, token)
        val normalized = StravaActivityMapper.map(raw, api.streams(id, token))
        enrichWeather(raw, normalized)
        MarkdownExporter.create(normalized)
    }.recoverCatching { error ->
        if (error is StravaApiException && error.statusCode == 401) auth.disconnect()
        throw ImportException(if (error is StravaApiException && error.statusCode == 401) "Strava authorization expired. Please reconnect Strava." else "Could not download activity from Strava.")
    }

    private fun enrichWeather(raw: org.json.JSONObject, activity: NormalizedActivity) {
        val coordinates = raw.optJSONArray("start_latlng")?.takeIf { it.length() >= 2 } ?: return
        val latitude = coordinates.optDouble(0, Double.NaN).takeUnless(Double::isNaN) ?: return
        val longitude = coordinates.optDouble(1, Double.NaN).takeUnless(Double::isNaN) ?: return
        val time = ActivityTime.instant(raw.optString("start_date_local", raw.optString("start_date")), raw.optString("timezone")) ?: return
        stage("Fetching weather")
        activity.weather = runCatching { OpenMeteoWeatherClient().weather(latitude, longitude, time)?.let(::weatherData) }
            .onFailure { Log.w(TAG, "Weather enrichment failed: ${it.javaClass.simpleName}") }.getOrNull()
    }

    private fun weatherData(o: org.json.JSONObject) = WeatherData(
        condition = o.optString("condition").takeIf(String::isNotBlank), temperature = o.optDouble("temperature", Double.NaN).takeUnless(Double::isNaN),
        feelsLike = o.optDouble("feelsLike", Double.NaN).takeUnless(Double::isNaN), dewPoint = o.optDouble("dewPoint", Double.NaN).takeUnless(Double::isNaN),
        humidity = o.optDouble("humidity", Double.NaN).takeUnless(Double::isNaN), windDirection = o.optString("windDirection").takeIf(String::isNotBlank),
        windDirectionDegrees = o.optDouble("windDirectionDegrees", Double.NaN).takeUnless(Double::isNaN), windSpeed = o.optDouble("windSpeed", Double.NaN).takeUnless(Double::isNaN),
        windGust = o.optDouble("windGust", Double.NaN).takeUnless(Double::isNaN), precipitation = o.optDouble("precipitation", Double.NaN).takeUnless(Double::isNaN),
        station = o.optString("station").takeIf(String::isNotBlank), source = o.optString("source", "Open-Meteo historical")
    )

    private fun stage(message: String) { if (BuildConfig.DEBUG) Log.d(TAG, message) }
    class ImportException(message: String, val canChooseRecent: Boolean = false) : Exception(message)
    companion object { private const val TAG = "StravaImport" }
}
