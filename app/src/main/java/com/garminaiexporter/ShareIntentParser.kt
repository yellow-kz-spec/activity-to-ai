package com.garminaiexporter

import android.app.Activity
import android.content.Intent
import java.util.Locale

enum class ShareProvider { GARMIN, STRAVA, UNKNOWN }

data class SharedActivity(val provider: ShareProvider, val text: String?)

object ShareIntentParser {
    fun parse(activity: Activity, intent: Intent): SharedActivity {
        val text = if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain")
            runCatching { intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString() }.getOrNull()
        else null
        val strong = listOfNotNull(activity.callingPackage, activity.referrer?.host, intent.`package`)
            .joinToString(" ").lowercase(Locale.US)
        val provider = when {
            "strava" in strong -> ShareProvider.STRAVA
            "garmin" in strong -> ShareProvider.GARMIN
            GarminUrl.fromSharedText(text) != null -> ShareProvider.GARMIN
            StravaShareParser.extractUrl(text) != null -> ShareProvider.STRAVA
            else -> ShareProvider.UNKNOWN
        }
        return SharedActivity(provider, text)
    }
}
