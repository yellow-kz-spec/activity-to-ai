package com.garminaiexporter

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.IdentityHashMap
import java.util.Locale

/** Temporary, isolated diagnostics for payloads received from Android's share sheet. */
object ShareIntentDebugger {
    const val LOG_TAG = "GarminToAI-ShareDebug"
    private const val MAX_LOG_CHUNK = 3_500

    data class Result(val dump: String, val file: File?)

    fun capture(activity: Activity, intent: Intent): Result {
        val dump = runCatching { buildDump(activity, intent) }.getOrElse {
            "SHARE INTENT DEBUG\n\nDiagnostic generation failed safely:\n${it.javaClass.name}: ${it.message}\n\nIntent fallback:\n${safe { intent.toString() }}"
        }
        logDump(dump)
        val file = runCatching {
            val directory = activity.getExternalFilesDir("share-intent-debug")
                ?: File(activity.filesDir, "share-intent-debug")
            check(directory.exists() || directory.mkdirs()) { "Cannot create diagnostic directory" }
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
            File(directory, "share_intent_debug_$timestamp.txt").also { it.writeText(dump) }
        }.onFailure { Log.w(LOG_TAG, "Could not save diagnostic dump", it) }.getOrNull()
        return Result(dump, file)
    }

    private fun buildDump(activity: Activity, intent: Intent): String = buildString {
        appendLine("SHARE INTENT DEBUG")
        appendLine()
        section("SOURCE / CALLER") {
            field("Detected source candidate", detectSource(intent))
            field("referrer", safe { activity.referrer })
            field("callingPackage", safe { activity.callingPackage })
            field("callingActivity", safe { activity.callingActivity })
        }
        section("ACTION") { appendLine(intent.action ?: "null") }
        section("TYPE") { appendLine(intent.type ?: "null") }
        section("DATA") {
            field("data", intent.data)
            field("dataString", intent.dataString)
            field("scheme", intent.scheme)
        }
        section("FLAGS") {
            appendLine("decimal: ${intent.flags}")
            appendLine("hex: 0x${intent.flags.toUInt().toString(16)}")
        }
        section("CATEGORIES") {
            val categories = intent.categories
            if (categories.isNullOrEmpty()) appendLine("none") else categories.sorted().forEach { appendLine(it) }
        }
        section("STANDARD EXTRAS") {
            standardExtra(intent, Intent.EXTRA_TEXT)
            standardExtra(intent, Intent.EXTRA_SUBJECT)
            standardExtra(intent, Intent.EXTRA_TITLE)
            standardExtra(intent, Intent.EXTRA_STREAM)
        }
        section("ALL EXTRAS") {
            val extras = runCatching { intent.extras }
            if (extras.isSuccess) appendBundle(extras.getOrNull(), "", IdentityHashMap())
            else appendLine("<unable to read extras: ${extras.exceptionOrNull()?.javaClass?.name}: ${extras.exceptionOrNull()?.message}>")
        }
        section("CLIP DATA") { appendClipData(intent.clipData) }
        section("REFERRER / CALLING PACKAGE") {
            field("Intent.EXTRA_REFERRER", safeExtra(intent, Intent.EXTRA_REFERRER))
            field("Intent.EXTRA_REFERRER_NAME", safeExtra(intent, Intent.EXTRA_REFERRER_NAME))
            field("Activity.referrer", safe { activity.referrer })
            field("Activity.callingPackage", safe { activity.callingPackage })
        }
        section("OTHER AVAILABLE INFORMATION") {
            field("component", intent.component)
            field("package", intent.`package`)
            field("identifier", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) intent.identifier else "<requires Android 10>")
            field("selector", intent.selector)
            field("sourceBounds", intent.sourceBounds)
            field("resolvedActivity", safe { intent.resolveActivity(activity.packageManager) })
            field("activityPackage", activity.packageName)
        }
    }

    private fun StringBuilder.section(title: String, body: StringBuilder.() -> Unit) {
        appendLine(title)
        body()
        appendLine()
    }

    private fun StringBuilder.field(name: String, value: Any?) {
        appendLine("$name:")
        appendLine("type: ${value?.javaClass?.name ?: "null"}")
        appendLine("value: ${safeValue(value, IdentityHashMap())}")
    }

    private fun StringBuilder.standardExtra(intent: Intent, key: String) {
        val value = safeExtra(intent, key)
        appendLine("$key:")
        appendLine("type: ${value?.javaClass?.name ?: "null"}")
        appendLine("value: ${safeValue(value, IdentityHashMap())}")
    }

    private fun StringBuilder.appendBundle(bundle: Bundle?, indent: String, seen: IdentityHashMap<Any, Boolean>) {
        if (bundle == null) {
            appendLine("${indent}none")
            return
        }
        if (seen.put(bundle, true) != null) {
            appendLine("${indent}<cycle: Bundle>")
            return
        }
        val keys = runCatching { bundle.keySet().sorted() }.getOrElse {
            appendLine("${indent}<unable to enumerate Bundle: ${it.javaClass.name}: ${it.message}>")
            return
        }
        if (keys.isEmpty()) appendLine("${indent}none")
        keys.forEach { key ->
            val value = runCatching { bundle.get(key) }.getOrElse { it }
            appendLine("${indent}key: $key")
            appendLine("${indent}type: ${value?.javaClass?.name ?: "null"}")
            appendLine("${indent}value: ${safeValue(value, seen)}")
        }
    }

    private fun StringBuilder.appendClipData(clipData: ClipData?) {
        if (clipData == null) {
            appendLine("none")
            return
        }
        field("description", clipData.description)
        appendLine("itemCount: ${clipData.itemCount}")
        for (index in 0 until clipData.itemCount) {
            appendLine("item[$index]:")
            val itemResult = runCatching { clipData.getItemAt(index) }
            if (itemResult.isFailure) {
                val error = itemResult.exceptionOrNull()
                appendLine("  error: ${error?.javaClass?.name}: ${error?.message}")
                continue
            }
            val item = itemResult.getOrThrow()
            field("  text", safe { item.text })
            field("  htmlText", safe { item.htmlText })
            field("  uri", safe { item.uri })
            field("  intent", safe { item.intent })
        }
    }

    private fun safeExtra(intent: Intent, key: String): Any? = runCatching {
        @Suppress("DEPRECATION")
        intent.extras?.get(key)
    }.getOrElse { "<unreadable ${it.javaClass.name}: ${it.message}>" }

    private fun safeValue(value: Any?, seen: IdentityHashMap<Any, Boolean>, depth: Int = 0): String {
        if (value == null) return "null"
        if (depth >= 8) return "<maximum nesting depth reached>"
        if (value is Bundle) return buildString { appendBundle(value, "  ", seen) }.trimEnd()
        if (value.javaClass.isArray) {
            val length = runCatching { java.lang.reflect.Array.getLength(value) }.getOrElse { return errorText(it) }
            return (0 until length).joinToString(prefix = "[", postfix = "]") { index ->
                safeValue(runCatching { java.lang.reflect.Array.get(value, index) }.getOrNull(), seen, depth + 1)
            }
        }
        if (value is Iterable<*>) {
            if (seen.put(value, true) != null) return "<cycle: ${value.javaClass.name}>"
            return value.joinToString(prefix = "[", postfix = "]") { safeValue(it, seen, depth + 1) }
        }
        return runCatching { value.toString() }.getOrElse(::errorText)
    }

    private fun errorText(error: Throwable) = "<toString failed: ${error.javaClass.name}: ${error.message}>"

    private inline fun safe(block: () -> Any?): Any? = runCatching(block).getOrElse {
        "<unavailable ${it.javaClass.name}: ${it.message}>"
    }

    private fun detectSource(intent: Intent): String {
        val candidates = buildList {
            add(intent.dataString.orEmpty())
            add(safeExtra(intent, Intent.EXTRA_TEXT)?.toString().orEmpty())
            val clip = intent.clipData
            if (clip != null) for (index in 0 until clip.itemCount) {
                val item = runCatching { clip.getItemAt(index) }.getOrNull() ?: continue
                add(item.text?.toString().orEmpty())
                add(item.uri?.toString().orEmpty())
            }
        }.joinToString("\n").lowercase(Locale.US)
        return if ("strava.app.link" in candidates || "strava.com" in candidates) "STRAVA" else "UNKNOWN"
    }

    private fun logDump(dump: String) {
        dump.chunked(MAX_LOG_CHUNK).forEachIndexed { index, chunk ->
            Log.i(LOG_TAG, "part ${index + 1}:\n$chunk")
        }
    }
}
