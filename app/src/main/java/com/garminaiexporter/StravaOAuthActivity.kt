package com.garminaiexporter

import android.content.Intent
import android.os.Bundle
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.LinearLayout
import android.view.Gravity
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity

class StravaOAuthActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(ProgressBar(this@StravaOAuthActivity))
            addView(TextView(this@StravaOAuthActivity).apply { text = "Connecting Strava…" }, ViewGroup.LayoutParams(-2, -2))
        })
        val uri = intent?.data ?: return finishToHome("Missing Strava callback.")
        StravaAuthManager(this).exchangeCallback(uri) { result ->
            finishToHome(result.exceptionOrNull()?.message)
        }
    }

    private fun finishToHome(error: String?) {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            error?.let { putExtra(MainActivity.EXTRA_STRAVA_ERROR, it) }
        })
        finish()
    }
}
