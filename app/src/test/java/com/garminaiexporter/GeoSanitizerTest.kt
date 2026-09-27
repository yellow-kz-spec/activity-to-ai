package com.garminaiexporter

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoSanitizerTest {
    @Test fun removesNestedGeographicData() {
        val source = JSONObject("""{
          "summaryDTO":{"distance":9030,"startLatitude":43.0,"endLongitude":-79.0},
          "details":{"geoPolylineDTO":{"polyline":"secret"},"directHeartRate":145},
          "weather":{"weatherStationDTO":{"latitude":1,"name":"station"}}
        }""")
        val result = GeoSanitizer.sanitize(source).toString()
        assertTrue(result.contains("distance"))
        assertTrue(result.contains("directHeartRate"))
        assertFalse(result.contains("Latitude", ignoreCase = true))
        assertFalse(result.contains("Longitude", ignoreCase = true))
        assertFalse(result.contains("Polyline", ignoreCase = true))
    }
}
