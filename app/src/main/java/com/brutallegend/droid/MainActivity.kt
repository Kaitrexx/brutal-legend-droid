package com.brutallegend.droid

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import org.json.JSONObject
import java.io.File

data class ProfileItem(val id: String, val label: String)

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var fpsText: TextView
    private lateinit var logText: TextView
    private lateinit var profileSpinner: Spinner
    private lateinit var gamePathEdit: EditText
    private lateinit var runtime: RuntimeManager

    private val handler = Handler(Looper.getMainLooper())
    private var profiles: List<ProfileItem> = emptyList()
    private var ticker: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runtime = RuntimeManager(this)
        setContentView(buildUi())

        profiles = loadProfiles()
        profileSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            profiles.map { it.label }
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        runtime.cleanupPartial()
        gamePathEdit.setText("/sdcard/Download/BrutalLegend")
        statusText.text = if (runtime.isInstalled()) {
            "Runtime hazırdır. Profil seçib oyun qovluğunu göstərin."
        } else {
            "Runtime yoxdur — əvvəlcə 'Runtime yüklə' düyməsinə basın."
        }

        requestAllFilesAccessIfNeeded()
        startTicker()
    }

    override fun onDestroy() {
        ticker?.let { handler.removeCallbacks(it) }
        super.onDestroy()
    }

    private fun buildUi(): ScrollView {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 48)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        root.addView(title("Brutal Legend Droid — Phase 1 iskelet"))
        statusText = TextView(this).apply {
            textSize = 14f
            setPadding(0, 8, 0, 16)
        }
        root.addView(statusText)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            visibility = View.GONE
            max = 100
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        root.addView(progressBar)
        progressText = TextView(this).apply { textSize = 12f; setPadding(0, 4, 0, 8) }
        root.addView(progressText)

        fpsText = TextView(this).apply {
            text = "FPS: —"
            textSize = 22f
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 8)
        }
        root.addView(fpsText)

        root.addView(sectionLabel("Profil"))
        profileSpinner = Spinner(this)
        root.addView(profileSpinner)

        root.addView(sectionLabel("Oyun qovluğu (GOG / itch / digər nüsxə)"))
        gamePathEdit = EditText(this).apply {
            hint = "/sdcard/Download/BrutalLegend"
            isSingleLine = true
        }
        root.addView(gamePathEdit)

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 8)
        }
        val runtimeBtn = Button(this).apply { text = "Runtime yüklə" }
        val startBtn = Button(this).apply { text = "Başlat" }
        val stopBtn = Button(this).apply { text = "Dayandır" }
        buttons.addView(runtimeBtn, linearParam(1f))
        buttons.addView(startBtn, linearParam(1f))
        buttons.addView(stopBtn, linearParam(1f))
        root.addView(buttons)

        runtimeBtn.setOnClickListener {
            statusText.text = "Runtime yoxlanılır…"
            progressBar.visibility = View.VISIBLE
            progressBar.isIndeterminate = true
            progressText.text = "Hazırlanır…"
            runtime.ensureRuntime(
                { ok, msg ->
                    handler.post {
                        statusText.text = if (ok) msg else "XƏTA: $msg"
                        progressBar.visibility = View.GONE
                        progressText.text = ""
                    }
                },
                { stage, done, total ->
                    handler.post {
                        if (total > 0) {
                            progressBar.isIndeterminate = false
                            val pct = ((done * 100) / total).toInt().coerceIn(0, 100)
                            progressBar.progress = pct
                            progressText.text = "$stage: ${done / 1048576} / ${total / 1048576} MB ($pct%)"
                        } else {
                            progressBar.isIndeterminate = true
                            progressText.text = stage
                        }
                    }
                }
            )
        }

        startBtn.setOnClickListener { startEmulator() }
        stopBtn.setOnClickListener {
            stopService(Intent(this, EmulatorService::class.java))
            statusText.text = "Proses dayandırıldı."
        }

        root.addView(sectionLabel("Log"))
        logText = TextView(this).apply {
            textSize = 11f
            setTextIsSelectable(true)
        }
        root.addView(logText)

        scroll.addView(root)
        return scroll
    }

    private fun startEmulator() {
        if (!runtime.isInstalled()) {
            statusText.text = "Əvvəlcə runtime yükləyin."
            return
        }
        val gameDir = gamePathEdit.text.toString().trim()
        if (gameDir.isBlank()) {
            statusText.text = "Oyun qovluğunu göstərin."
            return
        }
        val profile = profiles.getOrNull(profileSpinner.selectedItemPosition)
            ?: run {
                statusText.text = "Profil seçilməyib."
                return
            }
        val intent = Intent(this, EmulatorService::class.java).apply {
            putExtra(EmulatorService.EXTRA_PROFILE, profile.id)
            putExtra(EmulatorService.EXTRA_GAME_DIR, gameDir)
        }
        startForegroundService(intent)
        statusText.text = "Başladılır: ${profile.label}"
    }

    private fun loadProfiles(): List<ProfileItem> {
        val result = mutableListOf<ProfileItem>()
        val names = assets.list("profiles")?.filter { it.endsWith(".json") }.orEmpty()
        for (name in names.sorted()) {
            try {
                val json = JSONObject(
                    assets.open("profiles/$name").bufferedReader().use { it.readText() }
                )
                result.add(ProfileItem(json.getString("id"), json.getString("label")))
            } catch (e: Exception) {
                result.add(ProfileItem("invalid-$name", "$name (oxunamadı)"))
            }
        }
        return result
    }

    private fun startTicker() {
        val tick = object : Runnable {
            override fun run() {
                try {
                    val fpsFile = File(filesDir, "fps.log")
                    fpsText.text = if (fpsFile.exists()) {
                        "FPS: ${fpsFile.readText().trim().takeLast(120)}"
                    } else {
                        "FPS: —"
                    }
                    val logFile = File(filesDir, "emulator.log")
                    logText.text = if (logFile.exists()) {
                        val all = logFile.readText()
                        if (all.length > 3000) all.takeLast(3000) else all
                    } else {
                        ""
                    }
                } catch (ignored: Exception) {
                    // fayl yazılarkən oxumaq — növbəti tick-də yenidən cəhd
                }
                handler.postDelayed(this, 1000)
            }
        }
        ticker = tick
        handler.postDelayed(tick, 1000)
    }

    private fun requestAllFilesAccessIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            !Environment.isExternalStorageManager()
        ) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } catch (ignored: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        }
    }

    private fun title(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 18f
        setPadding(0, 0, 0, 4)
    }

    private fun sectionLabel(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 12f
        setPadding(0, 12, 0, 4)
    }

    private fun linearParam(weight: Float): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight)
}
