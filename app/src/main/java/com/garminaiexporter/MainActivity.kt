package com.garminaiexporter

import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.View
import android.widget.LinearLayout
import android.widget.Button
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

class MainActivity : AppCompatActivity(), GarminWebLoader.Callback {
    private lateinit var status: TextView
    private lateinit var root: LinearLayout
    private lateinit var storage: ExportStorage
    private var pending: GarminUrl? = null
    private var pendingStravaText: String? = null
    private var pendingProvider = ShareProvider.UNKNOWN
    private var loader: GarminWebLoader? = null
    private var choosingFolderForHome = false
    private var stravaImporter: StravaImporter? = null

    private val chooseFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult finishWithError("Export folder was not selected")
        runCatching { storage.rememberTree(uri) }
            .onSuccess { if (choosingFolderForHome) showHome() else startImport() }
            .onFailure { finishWithError("Cannot retain access to the selected folder: ${it.message}") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        storage = ExportStorage(this)
        buildStatusView()
        if (intent?.action != Intent.ACTION_SEND && intent?.action != Intent.ACTION_SEND_MULTIPLE && intent?.action != Intent.ACTION_VIEW) {
            intent?.getStringExtra(EXTRA_STRAVA_ERROR)?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
            showHome()
            return
        }
        val diagnostic = if (BuildConfig.DEBUG && (
            intent?.action == Intent.ACTION_SEND ||
            intent?.action == Intent.ACTION_SEND_MULTIPLE ||
            intent?.action == Intent.ACTION_VIEW
        )) {
            ShareIntentDebugger.capture(this, intent)
        } else null
        val shared = ShareIntentParser.parse(this, intent)
        pendingProvider = shared.provider
        pending = if (shared.provider == ShareProvider.GARMIN) GarminUrl.fromSharedText(shared.text) else null
        pendingStravaText = if (shared.provider == ShareProvider.STRAVA) shared.text else null
        if (pending == null && pendingStravaText == null) {
            if (diagnostic != null) showDiagnostic(diagnostic) else finishWithError("No supported activity share link was found")
            return
        }
        if (storage.savedTree() == null) {
            status.text = "Choose a permanent folder for activity exports"
            chooseFolder.launch(null)
        } else startImport()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_STRAVA_ERROR)?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
        showHome()
    }

    private fun showHome() {
        val auth = StravaAuthManager(this)
        val connection = auth.connection()
        root.removeAllViews()
        root.gravity = Gravity.NO_GRAVITY
        root.setPadding(40, 48, 40, 40)
        fun label(value: String, large: Boolean = false) = TextView(this).apply {
            text = value
            textSize = if (large) 24f else 16f
            setPadding(0, 8, 0, 8)
        }
        root.addView(label(getString(R.string.app_name), true))
        root.addView(label("Version ${BuildConfig.VERSION_NAME}"))
        root.addView(label("\nGarmin\nNo connection required."))
        root.addView(label("Export folder: ${storage.savedTree()?.toString() ?: "Not selected"}"))
        root.addView(Button(this).apply {
            text = if (storage.savedTree() == null) "Choose export folder" else "Change export folder"
            setOnClickListener {
                choosingFolderForHome = true
                chooseFolder.launch(storage.savedTree())
            }
        })
        root.addView(label("\nStrava"))
        if (connection.state == StravaAuthState.CONNECTED) {
            root.addView(label("Connected${connection.athleteName?.let { " as $it" } ?: ""}"))
            root.addView(label("Athlete ID: ${connection.athleteId}\nToken expires: ${connection.expiresAt?.let { DateFormat.getDateTimeInstance().format(Date(it * 1000)) }}\nRefresh token stored: ${if (connection.refreshTokenStored) "YES" else "NO"}"))
            root.addView(Button(this).apply {
                text = "Disconnect Strava"
                setOnClickListener {
                    auth.disconnect()
                    Toast.makeText(this@MainActivity, "Strava disconnected locally", Toast.LENGTH_SHORT).show()
                    showHome()
                }
            })
        } else {
            root.addView(label("Disconnected"))
            root.addView(Button(this).apply {
                text = "Connect Strava"
                setOnClickListener {
                    runCatching { startActivity(auth.authorizationIntent()) }
                        .onFailure { Toast.makeText(this@MainActivity, it.message ?: "Could not start Strava authorization", Toast.LENGTH_LONG).show() }
                }
            })
        }
    }

    private fun showDiagnostic(diagnostic: ShareIntentDebugger.Result) {
        val copy = Button(this).apply {
            text = "Copy diagnostic"
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Garmin to AI share diagnostic", diagnostic.dump))
                Toast.makeText(this@MainActivity, "Diagnostic copied", Toast.LENGTH_SHORT).show()
            }
        }
        val output = TextView(this).apply {
            text = buildString {
                diagnostic.file?.let { appendLine("Saved to: ${it.absolutePath}\n") }
                append(diagnostic.dump)
            }
            setTextIsSelectable(true)
            setPadding(32, 16, 32, 32)
        }
        root.removeAllViews()
        root.gravity = Gravity.NO_GRAVITY
        root.addView(copy, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(ScrollView(this).apply { addView(output) }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))
    }

    private fun buildStatusView() {
        status = TextView(this).apply {
            text = "Preparing export…"
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(ProgressBar(this@MainActivity))
            addView(status, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        setContentView(root)
    }

    private fun startImport() {
        if (pendingProvider == ShareProvider.STRAVA) return startStravaImport()
        val request = pending ?: return
        status.text = "Loading Garmin activity…"
        loader = GarminWebLoader(this, request.activityId, this).also {
            it.view.visibility = View.INVISIBLE
            root.addView(it.view, 1, 1)
            it.start(request.url)
        }
    }

    private fun startStravaImport() {
        status.text = "Importing Strava activity…"
        val importer = StravaImporter(this).also { stravaImporter = it }
        importer.import(pendingStravaText) { result ->
            runOnUiThread {
                result.onSuccess(::saveStravaDocument).onFailure {
                    if ((it as? StravaImporter.ImportException)?.canChooseRecent == true) showRecentStravaActivities(importer)
                    else finishWithError(it.message ?: "Could not download activity from Strava.")
                }
            }
        }
    }

    private fun showRecentStravaActivities(importer: StravaImporter) {
        status.text = "Loading recent Strava activities…"
        importer.recentActivities { result -> runOnUiThread {
            result.onFailure { finishWithError("Could not identify the Strava activity.") }.onSuccess { candidates ->
                if (candidates.isEmpty()) return@onSuccess finishWithError("Could not identify the Strava activity.")
                root.removeAllViews()
                root.gravity = Gravity.NO_GRAVITY
                root.setPadding(32, 32, 32, 32)
                root.addView(TextView(this).apply { text = "Select the shared Strava activity"; textSize = 20f; setPadding(0, 0, 0, 16) })
                val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                candidates.forEach { candidate ->
                    list.addView(Button(this).apply {
                        text = "${candidate.name}\n${candidate.sport}  ${candidate.start.take(16).replace('T', ' ')}"
                        isAllCaps = false
                        setOnClickListener {
                            buildStatusView()
                            status.text = "Importing ${candidate.name}…"
                            importer.importActivity(candidate.id) { imported ->
                                runOnUiThread {
                                    imported.onSuccess(::saveStravaDocument).onFailure {
                                        finishWithError(it.message ?: "Could not download activity from Strava.")
                                    }
                                }
                            }
                        }
                    })
                }
                root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            }
        } }
    }

    private fun saveStravaDocument(document: ExportDocument) {
        status.text = "Saving file…"
        runCatching { storage.write(requireNotNull(storage.savedTree()), document.fileName, document.markdown) }
            .onSuccess { name -> Toast.makeText(this, "Activity exported successfully: $name", Toast.LENGTH_LONG).show(); finish() }
            .onFailure { finishWithError("Could not save the activity file.") }
    }

    override fun onLoaded(payload: JSONObject) {
        val request = pending ?: return
        status.text = "Creating Markdown…"
        runCatching {
            val document = MarkdownExporter.create(request.activityId, payload)
            val tree = requireNotNull(storage.savedTree())
            storage.write(tree, document.fileName, document.markdown)
        }.onSuccess { name ->
            Toast.makeText(this, "File created: $name", Toast.LENGTH_LONG).show()
            finish()
        }.onFailure { finishWithError("Export failed: ${it.message}") }
    }

    override fun onError(message: String) = finishWithError(message)

    private fun finishWithError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }

    override fun onDestroy() {
        loader?.destroy()
        loader = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_STRAVA_ERROR = "strava_error"
    }
}
