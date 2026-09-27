package com.garminaiexporter

import android.util.Log
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

class StravaLinkResolver {
    fun resolve(url: String): String {
        var current = url
        repeat(MAX_REDIRECTS + 1) { hop ->
            if (StravaShareParser.activityId(current) != null) return current
            val uri = URI(current)
            require(uri.scheme == "https" && uri.host?.lowercase() in ALLOWED_HOSTS) { "Unsupported Strava link" }
            val connection = URL(current).openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.setRequestProperty("User-Agent", "Activity-to-MD/${BuildConfig.VERSION_NAME}")
                val status = connection.responseCode
                if (BuildConfig.DEBUG) Log.d(TAG, "Redirect hop $hop: ${uri.host}${uri.path} -> HTTP $status")
                if (status !in REDIRECT_CODES) return current
                val location = connection.getHeaderField("Location") ?: error("Redirect missing destination")
                val next = uri.resolve(location)
                require(next.scheme == "https" && next.host?.lowercase() in ALLOWED_HOSTS) { "Unsafe redirect destination" }
                current = next.toString()
            } finally { connection.disconnect() }
        }
        error("Too many Strava redirects")
    }

    companion object {
        private const val TAG = "StravaImport"
        private const val MAX_REDIRECTS = 8
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        private val ALLOWED_HOSTS = setOf("strava.app.link", "strava.com", "www.strava.com")
    }
}
