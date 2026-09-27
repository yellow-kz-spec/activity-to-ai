package com.garminaiexporter

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.concurrent.Executors

class StravaAuthManager(context: Context) {
    private val appContext = context.applicationContext
    private val store = StravaTokenStore(appContext)
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun connection(): StravaConnection {
        val tokens = store.load() ?: return StravaConnection(StravaAuthState.DISCONNECTED)
        return StravaConnection(
            StravaAuthState.CONNECTED, tokens.athleteId, tokens.athleteName,
            tokens.expiresAt, tokens.refreshToken.isNotBlank()
        )
    }

    fun isStravaConnected(): Boolean = store.load() != null

    fun authorizationIntent(): Intent {
        requireConfigured()
        val bytes = ByteArray(32).also(SecureRandom()::nextBytes)
        val state = android.util.Base64.encodeToString(bytes, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
        store.savePendingState(state)
        Log.d(TAG, "OAuth flow started")
        val uri = Uri.parse("https://www.strava.com/oauth/mobile/authorize").buildUpon()
            .appendQueryParameter("client_id", BuildConfig.STRAVA_CLIENT_ID)
            .appendQueryParameter("redirect_uri", BuildConfig.STRAVA_REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("approval_prompt", "auto")
            .appendQueryParameter("scope", REQUIRED_SCOPE)
            .appendQueryParameter("state", state)
            .build()
        return Intent(Intent.ACTION_VIEW, uri)
    }

    fun exchangeCallback(uri: Uri, callback: (Result<StravaConnection>) -> Unit) {
        Log.d(TAG, "OAuth callback received: scheme=${uri.scheme}, host=${uri.host}, path=${uri.path}")
        if (!isExpectedCallback(uri)) {
            Log.w(TAG, "OAuth callback failed: unexpected callback")
            return callback(Result.failure(AuthException("OAuth callback error: unexpected redirect URI.")))
        }
        val expectedState = store.consumePendingState()
        val returnedState = uri.getQueryParameter("state")
        if (expectedState == null || expectedState != returnedState) {
            Log.w(TAG, "OAuth callback failed: state validation")
            return callback(Result.failure(AuthException("OAuth state validation failed. Please try connecting again.")))
        }
        uri.getQueryParameter("error")?.let { error ->
            val message = when (error) {
                "access_denied" -> "OAuth error (access_denied): Strava authorization was cancelled."
                "invalid_redirect_uri" -> "OAuth error (invalid_redirect_uri): Strava rejected the redirect URI."
                else -> "OAuth error (unknown_error): Strava authorization failed."
            }
            Log.w(TAG, "OAuth callback failed: authorization denied or rejected")
            return callback(Result.failure(AuthException(message)))
        }
        val code = uri.getQueryParameter("code")
            ?: return callback(Result.failure(AuthException("OAuth callback error: Strava did not return an authorization code."))).also {
                Log.w(TAG, "OAuth callback failed: missing authorization code")
            }
        val granted = uri.getQueryParameter("scope").orEmpty().replace(',', ' ').split(' ').filter(String::isNotBlank)
        if (REQUIRED_SCOPE !in granted) return callback(Result.failure(AuthException("The required activity permission was not granted.")))
        runAsync(callback) {
            try {
                exchange(code, granted.joinToString(" ")).also { Log.d(TAG, "OAuth flow completed successfully") }
            } catch (error: AuthException) {
                Log.w(TAG, "OAuth token exchange failed: category=${error.statusCode ?: "network_or_response"}")
                throw AuthException("Token exchange failed: ${error.message}", error.statusCode)
            }
        }
    }

    fun getValidAccessToken(callback: (Result<String>) -> Unit) {
        val tokens = store.load() ?: return callback(Result.failure(AuthException("Strava is not connected.")))
        if (!StravaTokenPolicy.shouldRefresh(tokens.expiresAt, System.currentTimeMillis() / 1000)) return callback(Result.success(tokens.accessToken))
        if (BuildConfig.DEBUG) Log.d(TAG, "Refreshing Strava access token")
        runAsync(callback) { refresh(tokens) }
    }

    fun disconnect() = store.clear()

    private fun exchange(code: String, grantedScope: String): StravaConnection {
        requireConfigured()
        val json = postToken(mapOf("client_id" to BuildConfig.STRAVA_CLIENT_ID, "client_secret" to BuildConfig.STRAVA_CLIENT_SECRET, "code" to code, "grant_type" to "authorization_code"))
        val athlete = json.optJSONObject("athlete") ?: throw AuthException("Strava returned an incomplete athlete profile.")
        val first = athlete.optString("firstname").trim()
        val last = athlete.optString("lastname").trim()
        val username = athlete.optString("username").trim()
        val name = listOf(first, last).filter(String::isNotBlank).joinToString(" ").ifBlank { username }.ifBlank { null }
        val tokens = parseTokens(json, athlete.getLong("id"), name, json.optString("scope", grantedScope))
        store.save(tokens)
        return connection()
    }

    private fun refresh(old: StravaTokenStore.Tokens): String {
        requireConfigured()
        try {
            val json = postToken(mapOf("client_id" to BuildConfig.STRAVA_CLIENT_ID, "client_secret" to BuildConfig.STRAVA_CLIENT_SECRET, "grant_type" to "refresh_token", "refresh_token" to old.refreshToken))
            val updated = parseTokens(json, old.athleteId, old.athleteName, old.scope)
            store.save(updated)
            return updated.accessToken
        } catch (error: AuthException) {
            if (error.statusCode == 400 || error.statusCode == 401) store.clear()
            throw error
        }
    }

    private fun parseTokens(json: JSONObject, athleteId: Long, athleteName: String?, scope: String) = StravaTokenStore.Tokens(
        json.getString("access_token"), json.getString("refresh_token"), json.getLong("expires_at"), athleteId, athleteName, scope
    )

    private fun postToken(parameters: Map<String, String>): JSONObject {
        val body = parameters.entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" }.toByteArray()
        val connection = URL("https://www.strava.com/oauth/token").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.outputStream.use { it.write(body) }
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw AuthException("Strava rejected the authentication request (HTTP $status).", status)
            return runCatching { JSONObject(response) }.getOrElse { throw AuthException("Strava returned an unreadable response.") }
        } catch (error: java.net.SocketTimeoutException) {
            throw AuthException("Strava did not respond in time. Check your connection and try again.")
        } catch (error: IOException) {
            throw AuthException("Could not reach Strava. Check your internet connection and try again.")
        } finally { connection.disconnect() }
    }

    private fun requireConfigured() {
        if (BuildConfig.STRAVA_CLIENT_ID.isBlank() || BuildConfig.STRAVA_CLIENT_SECRET.isBlank()) throw AuthException("Strava development credentials are not configured.")
        if (BuildConfig.STRAVA_REDIRECT_URI.isBlank()) throw AuthException("Strava callback is not configured for this build.")
    }

    private fun isExpectedCallback(uri: Uri): Boolean {
        val expected = Uri.parse(BuildConfig.STRAVA_REDIRECT_URI)
        return uri.scheme == expected.scheme &&
            uri.host == expected.host &&
            uri.path == expected.path
    }

    private fun <T> runAsync(callback: (Result<T>) -> Unit, work: () -> T) = executor.execute {
        val result = runCatching(work)
        main.post { callback(result) }
    }

    private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name())

    class AuthException(message: String, val statusCode: Int? = null) : Exception(message)

    companion object {
        private const val TAG = "StravaOAuth"
        const val REQUIRED_SCOPE = "activity:read_all"
        const val REFRESH_MARGIN_SECONDS = 300L
    }
}

object StravaTokenPolicy {
    fun shouldRefresh(expiresAt: Long, now: Long, marginSeconds: Long = StravaAuthManager.REFRESH_MARGIN_SECONDS) =
        expiresAt <= now + marginSeconds
}
