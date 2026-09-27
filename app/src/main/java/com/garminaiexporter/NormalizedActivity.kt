package com.garminaiexporter

data class CanonicalMetric(val label: String, val value: Double, val unit: String = "", val source: String? = null)
data class ActivityRow(val values: Map<String, Any?>)
data class WeatherData(
    val condition: String? = null, val temperature: Double? = null, val feelsLike: Double? = null,
    val dewPoint: Double? = null, val humidity: Double? = null, val windDirection: String? = null,
    val windDirectionDegrees: Double? = null, val windSpeed: Double? = null, val windGust: Double? = null,
    val precipitation: Double? = null, val station: String? = null, val source: String
)

data class NormalizedActivity(
    val source: String, val id: String, val title: String, val sportType: String,
    val startTime: String?, val timezone: String?, val device: String? = null, val gear: String? = null,
    val location: String? = null, val summary: List<CanonicalMetric> = emptyList(),
    val trainingMetrics: List<CanonicalMetric> = emptyList(), val dynamics: List<CanonicalMetric> = emptyList(),
    val power: List<CanonicalMetric> = emptyList(), var weather: WeatherData? = null,
    val heartRateZones: List<Double> = emptyList(), val powerZones: List<Double> = emptyList(),
    val laps: List<ActivityRow> = emptyList(), val splits: List<ActivityRow> = emptyList(),
    val segments: List<ActivityRow> = emptyList(), val timeSeries: List<ActivityRow> = emptyList(),
    val timeSeriesColumns: List<String> = emptyList(), val sourceNotes: List<String> = emptyList(),
    val availability: Map<String, String> = emptyMap(), val distanceMeters: Double? = null,
)
