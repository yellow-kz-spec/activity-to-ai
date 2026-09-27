package com.garminaiexporter

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FormatV1Test {
    @Test fun stravaRunningNormalizesCadenceTimePrivacyAndJsonObjects() {
        val raw = JSONObject("""{
          "id":1,"name":"Run","sport_type":"Run","start_date_local":"2026-08-25T07:32:34Z",
          "timezone":"(GMT-05:00) America/New_York","distance":6690,"average_cadence":92.3,"suffer_score":28,
          "laps":[{"lap_index":1,"distance":1000,"elapsed_time":350,"resource_state":2}],
          "splits_metric":[{"split":1,"distance":1000,"elapsed_time":350,"average_speed":2.86}],
          "segment_efforts":[{"name":"Hill","elapsed_time":90,"start_index":4,"segment":{"name":"Hill","average_grade":2.2,"start_latlng":[1,2]}}],
          "start_latlng":[42,-71],"end_latlng":[42,-71]
        }""")
        val streams = JSONObject().put("heartrate", JSONObject().put("data", JSONArray("[150,152]"))).put("cadence", JSONObject().put("data", JSONArray("[92.3,93]")))
        val activity = StravaActivityMapper.map(raw, streams)
        val md = MarkdownExporter.create(activity).markdown
        assertEquals(184.6, activity.dynamics.first { it.label == "Average cadence" }.value, .001)
        assertTrue(md.contains("2026-08-25T07:32:34-04:00"))
        assertTrue(md.contains("cadence_spm")); assertTrue(md.contains("Relative Effort")); assertTrue(md.contains("## Segments"))
        assertFalse(md.contains("resource_state")); assertFalse(md.contains("start_index")); assertFalse(md.contains("latlng", true)); assertFalse(md.contains("```json"))
        assertTrue(md.contains("## Data Availability")); assertTrue(md.contains("## Instruction for AI"))
    }

    @Test fun cyclingCadenceIsNotDoubledAndEmptySectionsAreOmitted() {
        val activity = StravaActivityMapper.map(JSONObject("""{"id":2,"name":"Ride","sport_type":"Ride","average_cadence":92.3}"""), JSONObject())
        assertEquals(92.3, activity.dynamics.single().value, .001)
        val md = MarkdownExporter.create(activity).markdown
        assertTrue(md.contains("92.3 rpm")); assertFalse(md.contains("## Power\n")); assertFalse(md.contains("## Segments\n")); assertFalse(md.contains("null"))
    }

    @Test fun samplingIsDisclosedPreservesEndpointsAndCanonicalColumns() {
        val points = (0..2209).map { ActivityRow(mapOf("elapsed_s" to it, "heart_rate_bpm" to 140.00000001)) }
        val a = NormalizedActivity("Test", "3", "Long run", "running", null, null, timeSeries = points, timeSeriesColumns = listOf("elapsed_s", "heart_rate_bpm"), availability = mapOf("GPS coordinates" to "excluded by privacy policy"))
        val md = MarkdownExporter.create(a).markdown
        assertTrue(md.contains("Sampling: reduced")); assertTrue(md.contains("Original points: 2210")); assertTrue(md.contains("| 0 | 140 |")); assertTrue(md.contains("| 2209 | 140 |"))
        assertFalse(md.contains("140.00000001"))
    }

    @Test fun garminProprietaryMetricsAndDynamicsSurviveCanonicalMapping() {
        val payload = JSONObject("""{"activity":{"activityName":"Run","activityTypeDTO":{"typeKey":"running"},"summaryDTO":{"distance":6690,"trainingEffect":3.2,"anaerobicTrainingEffect":0,"activityTrainingLoad":99.32,"beginPotentialStamina":99,"endPotentialStamina":81,"averageRunCadence":185.02,"groundContactTime":250}},"hrZones":[10,20],"powerZones":[5,6]}""")
        val md = MarkdownExporter.create("4", payload).markdown
        assertTrue(md.contains("Aerobic Training Effect")); assertTrue(md.contains("Anaerobic Training Effect | 0")); assertTrue(md.contains("Starting Stamina")); assertTrue(md.contains("## Running Dynamics")); assertTrue(md.contains("## Heart Rate Zones")); assertTrue(md.contains("## Power Zones"))
    }
}
