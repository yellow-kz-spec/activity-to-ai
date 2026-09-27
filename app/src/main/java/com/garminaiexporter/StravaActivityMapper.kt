package com.garminaiexporter

import org.json.JSONArray
import org.json.JSONObject

object StravaActivityMapper {
    fun map(activity: JSONObject, streams: JSONObject): NormalizedActivity {
        val sport = canonicalSport(activity.optString("sport_type", activity.optString("type", "other")))
        fun metric(key: String, label: String, unit: String = "", factor: Double = 1.0, source: String? = null) = number(activity, key)?.let { CanonicalMetric(label, it * factor, unit, source) }
        val running = sport == "running"
        val cadenceFactor = if (running) 2.0 else 1.0
        val cadenceUnit = if (running) "spm" else "rpm"
        val summary = listOfNotNull(metric("distance", "Distance", "km", .001), metric("moving_time", "Moving duration", "s"), metric("elapsed_time", "Elapsed duration", "s"), metric("average_speed", "Average speed", "m/s"), metric("max_speed", "Maximum speed", "m/s"), metric("total_elevation_gain", "Elevation gain", "m"), metric("calories", "Calories", "kcal"), metric("average_heartrate", "Average heart rate", "bpm"), metric("max_heartrate", "Maximum heart rate", "bpm")) + if (running) listOfNotNull(number(activity, "average_speed")?.takeIf { it > 0 }?.let { CanonicalMetric("Average pace", 1000 / it, "s/km") }) else emptyList()
        val training = listOfNotNull(metric("suffer_score", "Relative Effort", source = "Strava"))
        val dynamics = listOfNotNull(metric("average_cadence", "Average cadence", cadenceUnit, cadenceFactor))
        val power = listOfNotNull(metric("average_watts", "Average Power", "W"), metric("max_watts", "Maximum Power", "W"), metric("weighted_average_watts", "Weighted Average Power", "W"), metric("kilojoules", "Work", "kJ"))
        val streamMap = streams.keys().asSequence().mapNotNull { key -> streams.optJSONObject(key)?.optJSONArray("data")?.let { key to it } }.toMap()
        val columns = linkedMapOf("time" to "elapsed_s", "distance" to "distance_m", "heartrate" to "heart_rate_bpm", "velocity_smooth" to "speed_mps", "altitude" to "elevation_m", "watts" to "power_w", "cadence" to if (running) "cadence_spm" else "cadence", "grade_smooth" to "grade_pct", "moving" to "moving", "grade_adjusted_speed" to "grade_adjusted_speed_mps")
        val present = columns.filterKeys(streamMap::containsKey)
        val count = present.keys.maxOfOrNull { streamMap.getValue(it).length() } ?: 0
        val timeSeries = (0 until count).map { index -> ActivityRow(present.map { (raw, canonical) -> canonical to streamValue(streamMap.getValue(raw), index, if (raw == "cadence") cadenceFactor else 1.0) }.toMap()) }
        val powerNote = if (power.isEmpty()) emptyList() else listOf(if (activity.optBoolean("device_watts", false)) "Power is device-measured according to Strava." else "Power may be estimated by Strava; the API did not identify device-measured watts.")
        return NormalizedActivity(source = "Strava", id = activity.opt("id")?.toString().orEmpty(), title = text(activity, "name") ?: "Strava activity", sportType = sport,
            startTime = ActivityTime.normalizedLocal(text(activity, "start_date_local") ?: text(activity, "start_date"), text(activity, "timezone")), timezone = text(activity, "timezone")?.substringAfterLast(' '), device = text(activity, "device_name"), gear = gear(activity.optJSONObject("gear")),
            location = listOfNotNull(text(activity, "location_city"), text(activity, "location_state"), text(activity, "location_country")).distinct().joinToString(", ").ifBlank { null }, summary = summary, trainingMetrics = training, dynamics = dynamics, power = power,
            laps = rows(activity.optJSONArray("laps"), ::lap), splits = rows(activity.optJSONArray("splits_metric") ?: activity.optJSONArray("splits_standard"), ::split), segments = rows(activity.optJSONArray("segment_efforts"), ::segment), timeSeries = timeSeries, timeSeriesColumns = present.values.toList(),
            sourceNotes = listOfNotNull("Relative Effort is a Strava-derived metric.".takeIf { training.isNotEmpty() }) + powerNote,
            availability = availability(summary, power, dynamics, training, timeSeries), distanceMeters = number(activity, "distance"))
    }

    private fun lap(o: JSONObject, i: Int) = ActivityRow(linkedMapOf("Lap" to (number(o, "lap_index")?.toInt() ?: i + 1), "Distance" to number(o, "distance"), "Time" to number(o, "elapsed_time"), "Moving time" to number(o, "moving_time"), "Average speed" to number(o, "average_speed"), "Avg HR" to number(o, "average_heartrate"), "Max HR" to number(o, "max_heartrate"), "Avg Power" to number(o, "average_watts"), "Max Power" to number(o, "max_watts"), "Cadence" to number(o, "average_cadence"), "Elevation gain" to number(o, "total_elevation_gain")).filterValues { it != null })
    private fun split(o: JSONObject, i: Int) = ActivityRow(linkedMapOf("Split" to (number(o, "split")?.toInt() ?: i + 1), "Distance" to number(o, "distance"), "Time" to number(o, "elapsed_time"), "Moving time" to number(o, "moving_time"), "Average speed" to number(o, "average_speed"), "GAP speed" to number(o, "average_grade_adjusted_speed"), "Average HR" to number(o, "average_heartrate"), "Elevation change" to number(o, "elevation_difference")).filterValues { it != null })
    private fun segment(o: JSONObject, i: Int): ActivityRow { val s = o.optJSONObject("segment"); return ActivityRow(linkedMapOf("Segment name" to (text(o, "name") ?: s?.let { text(it, "name") } ?: "Segment ${i + 1}"), "Distance" to (number(o, "distance") ?: s?.let { number(it, "distance") }), "Time" to number(o, "elapsed_time"), "Moving time" to number(o, "moving_time"), "Avg HR" to number(o, "average_heartrate"), "Max HR" to number(o, "max_heartrate"), "Avg Power" to number(o, "average_watts"), "Average grade" to (number(o, "average_grade") ?: s?.let { number(it, "average_grade") }), "Maximum grade" to (number(o, "maximum_grade") ?: s?.let { number(it, "maximum_grade") }), "Achievement rank" to number(o, "achievement_rank")?.toInt()).filterValues { it != null }) }
    private fun rows(a: JSONArray?, mapper: (JSONObject, Int) -> ActivityRow) = if (a == null) emptyList() else (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { mapper(it, i) } }
    private fun streamValue(a: JSONArray, i: Int, factor: Double): Any? = if (i >= a.length() || a.isNull(i)) null else (a.opt(i) as? Number)?.toDouble()?.times(factor) ?: a.opt(i)
    private fun number(o: JSONObject, k: String) = if (!o.has(k) || o.isNull(k)) null else o.optDouble(k, Double.NaN).takeUnless(Double::isNaN)
    private fun text(o: JSONObject, k: String) = o.optString(k).trim().takeIf { it.isNotEmpty() && !it.equals("null", true) }
    private fun gear(o: JSONObject?) = o?.let { listOfNotNull(text(it, "name"), text(it, "brand_name"), text(it, "model_name")).distinct().joinToString(" ").ifBlank { null } }
    private fun canonicalSport(s: String) = when { s.contains("run", true) -> "running"; s.contains("ride", true) || s.contains("cycl", true) -> "cycling"; s.contains("walk", true) -> "walking"; s.contains("hike", true) -> "hiking"; s.contains("swim", true) -> "swimming"; else -> "other" }
    private fun availability(summary: List<CanonicalMetric>, power: List<CanonicalMetric>, dynamics: List<CanonicalMetric>, training: List<CanonicalMetric>, series: List<ActivityRow>) = linkedMapOf("Heart rate" to if (summary.any { "heart rate" in it.label.lowercase() }) "available" else "unavailable", "Power" to if (power.isNotEmpty()) "available" else "unavailable", "Running dynamics" to if (dynamics.isNotEmpty()) "available" else "unavailable", "Training Effect" to "unavailable from this source", "Relative Effort" to if (training.isNotEmpty()) "available from Strava" else "unavailable from this source", "Time series" to if (series.isNotEmpty()) "available" else "unavailable", "GPS coordinates" to "excluded by privacy policy")
}
