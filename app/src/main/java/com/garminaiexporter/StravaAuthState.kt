package com.garminaiexporter

enum class StravaAuthState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

data class StravaConnection(
    val state: StravaAuthState,
    val athleteId: Long? = null,
    val athleteName: String? = null,
    val expiresAt: Long? = null,
    val refreshTokenStored: Boolean = false,
    val error: String? = null,
)
