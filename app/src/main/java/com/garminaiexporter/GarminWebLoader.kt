package com.garminaiexporter

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import android.util.Log
import java.util.concurrent.Executors

class GarminWebLoader(
    context: Context,
    private val activityId: String,
    private val callback: Callback
) {
    interface Callback {
        fun onLoaded(payload: JSONObject)
        fun onError(message: String)
    }

    private val handler = Handler(Looper.getMainLooper())
    private var completed = false
    private var injected = false
    private val weatherExecutor = Executors.newSingleThreadExecutor()
    val view = WebView(context)
    private val webView = view

    @SuppressLint("SetJavaScriptEnabled")
    fun start(url: String) {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = false
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        webView.addJavascriptInterface(Bridge(), "GarminExporter")
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return request.url.host?.equals("connect.garmin.com", ignoreCase = true) != true
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (injected) return
                injected = true
                handler.postDelayed({ if (!completed) view.evaluateJavascript(script(), null) }, 2_000)
            }
        }
        handler.postDelayed({ fail("Garmin did not return activity data within 45 seconds") }, 45_000)
        webView.loadUrl(url)
    }

    fun destroy() {
        handler.removeCallbacksAndMessages(null)
        webView.stopLoading()
        webView.removeJavascriptInterface("GarminExporter")
        webView.destroy()
        weatherExecutor.shutdownNow()
    }

    private fun script(): String = """
        (() => {
          const id = ${JSONObject.quote(activityId)};
          const csrf = document.querySelector('meta[name="csrf-token"]')?.content || '';
          const headers = {
            'accept': 'application/json',
            'connect-csrf-token': csrf,
            'nk': 'NT',
            'x-app-ver': '5.26.1.1',
            'x-lang': navigator.language || 'en-US',
            'x-requested-with': 'XMLHttpRequest'
          };
          const base = '/gc-api/activity-service/activity/' + id;
          const endpoints = {
            activity: base,
            details: base + '/details?maxChartSize=2000&maxPolylineSize=0&maxHeatMapSize=0',
            laps: base + '/laps',
            typedSplits: base + '/typedsplits',
            splitSummaries: base + '/split_summaries',
            hrZones: base + '/hrTimeInZones',
            powerZones: base + '/powerTimeInZones'
          };
          Promise.all(Object.entries(endpoints).map(async ([key, url]) => {
            const response = await fetch(url, {headers, credentials: 'same-origin'});
            if (!response.ok) throw new Error(key + ': HTTP ' + response.status);
            return [key, await response.json()];
          })).then(async items => {
            const payload = Object.fromEntries(items);
            try {
              const response = await fetch(base + '/weather', {headers, credentials: 'same-origin'});
              if (response.ok) payload.weather = await response.json();
            } catch (_) { /* Weather is optional and has a historical fallback. */ }
            GarminExporter.onPayload(JSON.stringify(payload));
          })
             .catch(error => GarminExporter.onError(String(error)));
        })();
    """.trimIndent()

    private fun fail(message: String) {
        if (completed) return
        completed = true
        callback.onError(message)
    }

    private inner class Bridge {
        @JavascriptInterface fun onPayload(json: String) = handler.post {
            if (completed) return@post
            runCatching {
                val raw = JSONObject(json)
                val returnedId = raw.optJSONObject("activity")?.opt("activityId")?.toString()
                require(returnedId == activityId) { "Activity ID does not match the shared link" }
                raw
            }.onSuccess { raw ->
                weatherExecutor.execute {
                    val resolved = WeatherResolver.resolve(raw, OpenMeteoWeatherClient())
                    val sanitized = GeoSanitizer.sanitize(resolved) as JSONObject
                    handler.post {
                        if (!completed) {
                            completed = true
                            callback.onLoaded(sanitized)
                        }
                    }
                }
            }
                .onFailure { fail("Garmin returned invalid data: ${it.message}") }
        }

        @JavascriptInterface fun onError(message: String) = handler.post { fail("Garmin data request failed: $message") }
    }
}
