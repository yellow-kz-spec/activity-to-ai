package com.garminaiexporter

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ExportDocument(val fileName: String, val markdown: String)

object MarkdownExporter {
    private const val MAX_ROWS = 1000
    fun create(activityId: String, payload: JSONObject) = create(GarminActivityMapper.map(activityId, payload))

    fun create(a: NormalizedActivity): ExportDocument {
        val out = StringBuilder("# ${escape(a.title)}\n")
        section(out, "Metadata", listOf("Source" to a.source, "Activity ID" to "`${a.id}`", "Activity type" to "`${a.sportType}`", "Start time" to a.startTime, "Timezone" to a.timezone, "Device" to a.device, "Gear" to a.gear, "Location" to a.location, "Coordinates included" to "false"))
        metrics(out, "Summary", a.summary)
        metrics(out, "Training Metrics", a.trainingMetrics, true)
        metrics(out, when (a.sportType) { "running" -> "Running Dynamics"; "cycling" -> "Cycling Dynamics"; else -> "Sport-specific Dynamics" }, a.dynamics)
        metrics(out, "Power", a.power)
        weather(out, a.weather)
        zones(out, "Heart Rate Zones", a.heartRateZones)
        zones(out, "Power Zones", a.powerZones)
        table(out, "Laps", a.laps)
        table(out, "Splits", a.splits)
        table(out, "Segments", a.segments)
        timeSeries(out, a)
        if (a.availability.isNotEmpty()) { out.append("\n## Data Availability\n\n"); a.availability.forEach { (k, v) -> out.append("- ${escape(k)}: ${escape(v)}\n") } }
        if (a.sourceNotes.isNotEmpty()) { out.append("\n## Source Notes\n\n"); a.sourceNotes.forEach { out.append("- ${escape(it)}\n") } }
        out.append("\n## Instruction for AI\n\nAnalyze the workout using the supplied measurements.\n\nDistinguish:\n- directly observed measurements;\n- provider-derived metrics;\n- Activity to MD normalized values;\n- inference.\n\nDo not interpret missing values as zero.\n\nExplicitly note missing context when it materially affects the analysis.\n\nPrefer trends and relationships between measurements over isolated values when time-series data is available.\n")
        val date = a.startTime?.take(16)?.replace(':', '-')?.replace('T', '_').orEmpty().ifBlank { SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date()) }
        val distance = a.distanceMeters?.let { "_${number(it / 1000, 2)}km" }.orEmpty()
        return ExportDocument("${date}_${safe(a.sportType)}$distance.md", out.toString())
    }

    private fun section(out: StringBuilder, title: String, entries: List<Pair<String, String?>>) { val useful = entries.filter { !it.second.isNullOrBlank() }; if (useful.isEmpty()) return; out.append("\n## $title\n\n"); useful.forEach { out.append("- ${it.first}: ${escape(it.second!!)}\n") } }
    private fun metrics(out: StringBuilder, title: String, values: List<CanonicalMetric>, source: Boolean = false) { if (values.isEmpty()) return; out.append("\n## $title\n\n| Metric | Value |"); if (source) out.append(" Source |"); out.append("\n|---|---:|"); if (source) out.append("---|"); out.append('\n'); values.forEach { m -> out.append("| ${escape(m.label)} | ${metricValue(m)} |"); if (source) out.append(" ${m.source.orEmpty()} |"); out.append('\n') } }
    private fun metricValue(m: CanonicalMetric): String = when { m.unit == "s" && ("duration" in m.label.lowercase()) -> duration(m.value); m.unit == "s/km" -> pace(m.value); else -> number(m.value, precision(m.unit)) + if (m.unit.isBlank()) "" else " ${m.unit}" }
    private fun weather(out: StringBuilder, w: WeatherData?) { if (w == null) return; val values = listOf("Condition" to w.condition, "Temperature" to w.temperature?.let { "${number(it, 1)} °F" }, "Feels like" to w.feelsLike?.let { "${number(it, 1)} °F" }, "Dew point" to w.dewPoint?.let { "${number(it, 1)} °F" }, "Humidity" to w.humidity?.let { "${number(it, 1)} %" }, "Wind direction" to listOfNotNull(w.windDirection, w.windDirectionDegrees?.let { "${number(it, 0)}°" }).joinToString(" ").ifBlank { null }, "Wind speed" to w.windSpeed?.let { "${number(it, 1)} mph" }, "Wind gust" to w.windGust?.let { "${number(it, 1)} mph" }, "Precipitation" to w.precipitation?.let { "${number(it, 2)} in" }, "Weather station" to w.station, "Source" to w.source); section(out, "Weather", values) }
    private fun zones(out: StringBuilder, title: String, values: List<Double>) { if (values.isEmpty()) return; out.append("\n## $title\n\n| Zone | Time |\n|---:|---:|\n"); values.forEachIndexed { i, s -> out.append("| ${i + 1} | ${duration(s)} |\n") } }
    private fun table(out: StringBuilder, title: String, rows: List<ActivityRow>) { if (rows.isEmpty()) return; val columns = rows.flatMap { it.values.keys }.distinct().filter { c -> rows.any { it.values[c] != null } }; if (columns.isEmpty()) return; out.append("\n## $title\n\n| ${columns.joinToString(" | ")} |\n|${columns.joinToString("|") { "---:" }}|\n"); rows.forEach { row -> out.append("| ${columns.joinToString(" | ") { cell(row.values[it], it) }} |\n") } }
    private fun timeSeries(out: StringBuilder, a: NormalizedActivity) { if (a.timeSeries.isEmpty() || a.timeSeriesColumns.isEmpty()) return; val count = a.timeSeries.size; val stride = maxOf(1, (count + MAX_ROWS - 1) / MAX_ROWS); val indices = if (stride == 1) (0 until count).toList() else ((0 until count step stride).toMutableList().apply { if (lastOrNull() != count - 1) add(count - 1) }); out.append("\n## Time Series\n\n"); if (stride > 1) { val interval = elapsedInterval(a.timeSeries, indices); out.append("Sampling: reduced\nMethod: uniform\n"); interval?.let { out.append("Approximate interval: ${number(it, 1)} s\n") }; out.append("Original points: $count\nExported points: ${indices.size}\n\n") }; out.append("| ${a.timeSeriesColumns.joinToString(" | ")} |\n|${a.timeSeriesColumns.joinToString("|") { "---:" }}|\n"); indices.forEach { i -> out.append("| ${a.timeSeriesColumns.joinToString(" | ") { cell(a.timeSeries[i].values[it], it) }} |\n") } }
    private fun elapsedInterval(rows: List<ActivityRow>, indices: List<Int>): Double? { if (indices.size < 2) return null; val first = rows[indices.first()].values["elapsed_s"] as? Number ?: return null; val last = rows[indices.last()].values["elapsed_s"] as? Number ?: return null; return (last.toDouble() - first.toDouble()) / (indices.size - 1) }
    private fun cell(v: Any?, column: String) = when (v) { null, JSONObject.NULL -> ""; is Boolean -> v.toString(); is Number -> if (column in setOf("Time", "Moving time")) duration(v.toDouble()) else number(v.toDouble(), when { "heart_rate" in column.lowercase() || "power" in column.lowercase() || column in setOf("Lap", "Split") -> 0; "speed" in column.lowercase() -> 2; "pct" in column || "grade" in column.lowercase() -> 2; "cadence" in column.lowercase() -> 1; else -> 1 }); else -> escape(v.toString()) }
    private fun duration(seconds: Double): String { val total = seconds.toLong(); return if (total >= 3600) "%d:%02d:%02d".format(total / 3600, total / 60 % 60, total % 60) else "%02d:%02d".format(total / 60, total % 60) }
    private fun pace(seconds: Double) = "${seconds.toInt() / 60}:${(seconds.toInt() % 60).toString().padStart(2, '0')} /km"
    private fun precision(unit: String) = when (unit) { "km", "m/s" -> 2; "m", "cm", "%", "spm", "rpm", "breaths/min", "kJ" -> 1; "bpm", "W", "ms" -> 0; else -> 2 }
    private fun number(v: Double, digits: Int): String { val formatted = String.format(Locale.US, "%.${digits}f", v); return if (formatted.contains('.')) formatted.trimEnd('0').trimEnd('.') else formatted }
    private fun escape(v: String) = v.replace("|", "\\|").replace("\n", " ").replace("\r", " ")
    private fun safe(v: String) = v.lowercase(Locale.US).replace(Regex("[^a-z0-9_-]+"), "_").trim('_').ifBlank { "other" }
}
