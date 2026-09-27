package com.garminaiexporter

import java.net.URI

object StravaShareParser {
    private val urlPattern = Regex("https://(?:www\\.)?(?:strava\\.com/activities/|strava\\.app\\.link/)[^\\s<>]+", RegexOption.IGNORE_CASE)
    private val activityPattern = Regex("^/activities/(\\d+)(?:/.*)?$")

    fun extractUrl(text: String?): String? = text?.let { urlPattern.find(it)?.value?.trimEnd('.', ',', ')', ']', '}') }

    fun activityId(url: String?): Long? = runCatching {
        val uri = URI(requireNotNull(url))
        if (uri.host?.lowercase() !in setOf("strava.com", "www.strava.com")) return null
        activityPattern.matchEntire(uri.path ?: "")?.groupValues?.get(1)?.toLongOrNull()
    }.getOrNull()
}
