package com.garminaiexporter

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownExporterTest {
    @Test fun exportsAvailableValuesWithoutCoordinates() {
        val payload = JSONObject("""{
          "activity":{"activityName":"Base run","activityTypeDTO":{"typeKey":"running"},"summaryDTO":{"startTimeLocal":"2026-07-06T19:46:00","distance":9030,"averageHR":145}},
          "laps":{"lapDTOs":[{"lapIndex":1,"distance":1000,"duration":360,"startLatitude":43}]}
        }""")
        val sanitized = GeoSanitizer.sanitize(payload) as JSONObject
        val result = MarkdownExporter.create("23504968322", sanitized)
        assertTrue(result.fileName.startsWith("2026-07-06_19-46_running_9.03km"))
        assertTrue(result.markdown.contains("Average heart rate"))
        assertFalse(result.markdown.contains("latitude", ignoreCase = true))
    }

    @Test fun embeddedGarminWeatherAppearsInMarkdownAndMetricsRemain() {
        val payload = JSONObject("""{
          "activity":{"activityId":1,"activityName":"Weather run","activityTypeDTO":{"typeKey":"running"},"summaryDTO":{"startTimeGMT":"2026-07-06T23:46:00Z","distance":9030,"averageHR":145}},
          "weather":{"temp":75,"apparentTemp":74,"relativeHumidity":83,"windSpeed":6,"windDirectionCompassPoint":"E","weatherTypeDTO":{"desc":"Mostly Cloudy"}}
        }""")
        val resolved = WeatherResolver.resolve(payload) { _, _, _ -> error("fallback must not run") }
        val result = MarkdownExporter.create("1", GeoSanitizer.sanitize(resolved) as JSONObject)
        assertTrue(result.markdown.contains("## Weather"))
        assertTrue(result.markdown.contains("Condition: Mostly Cloudy"))
        assertTrue(result.markdown.contains("Temperature: 75 °F"))
        assertTrue(result.markdown.contains("Average heart rate | 145 bpm"))
    }

    @Test fun missingGarminWeatherUsesHistoricalFallback() {
        val payload = activityWithLocation()
        val resolved = WeatherResolver.resolve(payload) { lat, lon, _ ->
            assertEquals(43.1, lat, 0.0)
            assertEquals(-78.9, lon, 0.0)
            JSONObject().put("condition", "Clear sky").put("temperature", 61).put("source", "test historical")
        }
        val result = MarkdownExporter.create("1", GeoSanitizer.sanitize(resolved) as JSONObject)
        assertTrue(result.markdown.contains("Condition: Clear sky"))
        assertFalse(result.markdown.contains("latitude", ignoreCase = true))
    }

    @Test fun missingLocationOrWeatherStillExports() {
        val payload = JSONObject("""{"activity":{"activityName":"Indoor","summaryDTO":{"distance":1000}}}""")
        val resolved = WeatherResolver.resolve(payload) { _, _, _ -> error("fallback must not run") }
        val result = MarkdownExporter.create("1", resolved)
        assertFalse(result.markdown.contains("## Weather"))
        assertTrue(result.markdown.contains("Distance | 1 km"))
    }

    @Test fun weatherApiFailureDoesNotPreventExport() {
        val resolved = WeatherResolver.resolve(activityWithLocation()) { _, _, _ -> throw java.io.IOException("offline") }
        val result = MarkdownExporter.create("1", GeoSanitizer.sanitize(resolved) as JSONObject)
        assertFalse(result.markdown.contains("## Weather"))
        assertTrue(result.markdown.contains("# Run"))
    }

    private fun activityWithLocation() = JSONObject("""{
      "activity":{"activityId":1,"activityName":"Run","summaryDTO":{"startTimeGMT":"2026-07-06T23:46:00Z","startLatitude":43.1,"startLongitude":-78.9,"distance":1000}}
    }""")
}
