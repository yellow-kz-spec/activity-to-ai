package com.garminaiexporter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StravaOAuthConfigTest {
    @Test fun callbackIsSingleExpectedAppUri() {
        assertEquals("activitytomd://strava-auth.garminaiexporter.com/callback", BuildConfig.STRAVA_REDIRECT_URI)
    }

    @Test fun mobileAuthorizationContractContainsCallback() {
        val encoded = java.net.URLEncoder.encode(BuildConfig.STRAVA_REDIRECT_URI, Charsets.UTF_8.name())
        val url = "https://www.strava.com/oauth/mobile/authorize?client_id=123&redirect_uri=$encoded&response_type=code&approval_prompt=auto&scope=activity%3Aread_all"
        assertTrue(url.startsWith("https://www.strava.com/oauth/mobile/authorize"))
        assertTrue(url.contains("redirect_uri=$encoded"))
        assertTrue(url.contains("response_type=code"))
    }
}
