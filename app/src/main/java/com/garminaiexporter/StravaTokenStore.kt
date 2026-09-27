package com.garminaiexporter

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class StravaTokenStore(context: Context) {
    private val preferences = context.getSharedPreferences("strava_secure_store", Context.MODE_PRIVATE)
    private val alias = "strava_oauth_tokens_v1"

    data class Tokens(
        val accessToken: String,
        val refreshToken: String,
        val expiresAt: Long,
        val athleteId: Long,
        val athleteName: String?,
        val scope: String,
    )

    fun save(tokens: Tokens) = preferences.edit()
        .putString("access", encrypt(tokens.accessToken))
        .putString("refresh", encrypt(tokens.refreshToken))
        .putString("expires", encrypt(tokens.expiresAt.toString()))
        .putString("athlete_id", encrypt(tokens.athleteId.toString()))
        .putString("athlete_name", tokens.athleteName?.let(::encrypt))
        .putString("scope", encrypt(tokens.scope))
        .apply()

    fun load(): Tokens? = runCatching {
        Tokens(
            accessToken = decrypt(requireNotNull(preferences.getString("access", null))),
            refreshToken = decrypt(requireNotNull(preferences.getString("refresh", null))),
            expiresAt = decrypt(requireNotNull(preferences.getString("expires", null))).toLong(),
            athleteId = decrypt(requireNotNull(preferences.getString("athlete_id", null))).toLong(),
            athleteName = preferences.getString("athlete_name", null)?.let(::decrypt),
            scope = preferences.getString("scope", null)?.let(::decrypt).orEmpty(),
        )
    }.getOrNull()

    fun clear() = preferences.edit().clear().apply()

    fun savePendingState(state: String) = preferences.edit().putString("pending_state", encrypt(state)).apply()

    fun consumePendingState(): String? {
        val value = preferences.getString("pending_state", null)?.let { runCatching { decrypt(it) }.getOrNull() }
        preferences.edit().remove("pending_state").apply()
        return value
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            generateKey()
        }
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val payload = cipher.iv + cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val payload = Base64.decode(value, Base64.NO_WRAP)
        require(payload.size > 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, payload.copyOfRange(0, 12)))
        }
        return String(cipher.doFinal(payload.copyOfRange(12, payload.size)), StandardCharsets.UTF_8)
    }
}
