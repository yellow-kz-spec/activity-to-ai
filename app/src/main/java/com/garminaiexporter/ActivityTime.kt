package com.garminaiexporter

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object ActivityTime {
    fun normalizedLocal(value: String?, timezone: String?): String? {
        val raw = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val local = runCatching { LocalDateTime.parse(raw.removeSuffix("Z"), DateTimeFormatter.ISO_LOCAL_DATE_TIME) }.getOrNull() ?: return raw
        val zone = timezone?.trim()?.substringAfterLast(' ')?.let { runCatching { ZoneId.of(it) }.getOrNull() }
        return zone?.let { local.atZone(it).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) }
            ?: if (raw.endsWith("Z")) local.toString() else raw
    }

    fun instant(value: String?, timezone: String?): Instant? {
        val normalized = normalizedLocal(value, timezone) ?: return null
        return runCatching { Instant.parse(normalized) }.getOrElse { runCatching { OffsetDateTime.parse(normalized).toInstant() }.getOrNull() }
    }
}
