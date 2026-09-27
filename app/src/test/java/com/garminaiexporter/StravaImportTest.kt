package com.garminaiexporter

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class StravaImportTest {
    @Test fun extractsAppLinkFromShareSentence() {
        assertEquals("https://strava.app.link/FzUeElsKW5b", StravaShareParser.extractUrl("Check out this Flyover of my ride on Strava: https://strava.app.link/FzUeElsKW5b"))
    }

    @Test fun parsesDirectActivityUrlsAndLargeIds() {
        assertEquals(123456789012345L, StravaShareParser.activityId("https://www.strava.com/activities/123456789012345"))
        assertEquals(42L, StravaShareParser.activityId("https://strava.com/activities/42?foo=bar"))
    }

    @Test fun malformedAndMissingTextAreRejected() {
        assertNull(StravaShareParser.extractUrl("look at this activity"))
        assertNull(StravaShareParser.extractUrl(null))
        assertNull(StravaShareParser.activityId("https://evil.example/activities/123"))
    }

    @Test fun tokenPolicyRefreshesOnlyExpiredOrNearExpiryTokens() {
        assertTrue(StravaTokenPolicy.shouldRefresh(1_200, 1_000, 300))
        assertFalse(StravaTokenPolicy.shouldRefresh(1_301, 1_000, 300))
    }

    @Test fun optionalFieldsMapWithoutFailureAndExportMarkdown() {
        val activity = JSONObject("""{"id":123,"name":"Morning Ride","sport_type":"Ride","start_date_local":"2026-08-27T08:15:00Z","distance":25420,"moving_time":3600,"average_heartrate":null}""")
        val normalized = StravaActivityMapper.map(activity, JSONObject())
        val document = MarkdownExporter.create(normalized)
        assertTrue(document.fileName.startsWith("2026-08-27_08-15_cycling_25.42km"))
        assertTrue(document.markdown.contains("Source: Strava"))
        assertTrue(document.markdown.contains("Activity ID: `123`"))
        assertFalse(document.markdown.contains("Average heart rate"))
    }

    @Test fun stravaParserDoesNotClaimGarminShares() {
        assertNull(StravaShareParser.extractUrl("https://connect.garmin.com/modern/activity/23504968322?share_unique_id=abc"))
    }
}
