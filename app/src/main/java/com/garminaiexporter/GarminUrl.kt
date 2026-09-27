package com.garminaiexporter

import android.net.Uri

data class GarminUrl(val url: String, val activityId: String) {
    companion object {
        private val candidate = Regex("https?://[^\\s<>]+", RegexOption.IGNORE_CASE)
        private val path = Regex("^/(?:app|modern)/activity/(\\d+)/?$", RegexOption.IGNORE_CASE)

        fun fromSharedText(text: String?): GarminUrl? {
            if (text.isNullOrBlank()) return null
            return candidate.findAll(text).mapNotNull { match ->
                val cleaned = match.value.trimEnd('.', ',', ';', ')', ']', '}')
                val uri = runCatching { Uri.parse(cleaned) }.getOrNull() ?: return@mapNotNull null
                if (!uri.scheme.equals("https", ignoreCase = true)) return@mapNotNull null
                if (!uri.host.equals("connect.garmin.com", ignoreCase = true)) return@mapNotNull null
                val id = path.matchEntire(uri.path.orEmpty())?.groupValues?.get(1)
                    ?: return@mapNotNull null
                if (uri.getQueryParameter("share_unique_id").isNullOrBlank()) return@mapNotNull null
                GarminUrl(cleaned, id)
            }.firstOrNull()
        }
    }
}
