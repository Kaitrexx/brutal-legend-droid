package com.brutallegend.droid

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import org.json.JSONObject
import java.io.File

/**
 * Oyunu proot konteynerində başladan və log/FPS fayllarını idarə edən foreground service.
 * Profillər assets/profiles qovluğundakı JSON fayllarından oxunur; komanda şablonundakı
 * {RUNTIME_DIR}, {GAME_DIR}, {LOG_FILE}, {FPS_LOG} yerlərinə dəyərlər qoyulur.
 */
class EmulatorService : Service() {

    companion object {
        const val EXTRA_PROFILE = "profile"
        const val EXTRA_GAME_DIR = "game_dir"
        private const val CHANNEL_ID = "brutal_legend_emulator"
        private const val NOTIFICATION_ID = 1
    }

    private val ui = Handler(Looper.getMainLooper())
    private var process: Process? = null
    private var readerThread: Thread? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val profileId = intent?.getStringExtra(EXTRA_PROFILE) ?: "linux-32"
        val gameDir = intent?.getStringExtra(EXTRA_GAME_DIR).orEmpty()

        startForeground(NOTIFICATION_ID, buildNotification("Hazırlanır: $profileId"))

        if (process != null) {
            appendLog("Artıq işləyən proses var. Əvvəlcə dayandırın.\n")
            return START_STICKY
        }

        Thread({
            try {
                launch(profileId, gameDir)
            } catch (e: Exception) {
                appendLog("XƏTA: ${e.stackTraceToString()}\n")
                ui.post { updateNotification("Xəta — log-a baxın") }
            }
        }, "emulator-launch").start()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        process?.destroy()
        process = null
        readerThread?.interrupt()
        readerThread = null
        super.onDestroy()
    }

    private fun loadProfile(id: String): JSONObject {
        val name = "profiles/$id.json"
        val text = assets.open(name).bufferedReader().use { it.readText() }
        return JSONObject(text)
    }

    private fun launch(profileId: String, gameDir: String) {
        val profile = loadProfile(profileId)
        val runtimeDir = RuntimeManager(this).runtimePath()
        val logFile = File(filesDir, "emulator.log")
        val fpsFile = File(filesDir, "fps.log")

        logFile.writeText("")
        fpsFile.writeText("")

        val envVars = HashMap<String, String>()
        profile.optJSONObject("env")?.let { json ->
            json.keys().forEach { key -> envVars[key] = json.getString(key) }
        }

        val command = profile.getString("command")
            .replace("{RUNTIME_DIR}", runtimeDir)
            .replace("{GAME_DIR}", gameDir)
            .replace("{LOG_FILE}", logFile.absolutePath)
            .replace("{FPS_LOG}", fpsFile.absolutePath)

        appendLog("Profil: $profileId\nOyun qovluğu: $gameDir\nKomanda: $command\n\n")
        ui.post { updateNotification("İşləyir: $profileId") }

        val builder = ProcessBuilder("/system/bin/sh", "-c", command)
        builder.directory(File(runtimeDir))
        builder.environment().putAll(envVars)
        builder.redirectErrorStream(true)

        val started = builder.start()
        process = started

        readerThread = Thread({
            try {
                started.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line -> appendLog("$line\n") }
                }
            } catch (e: Exception) {
                appendLog("Log oxuma xətası: $e\n")
            }
            ui.post { updateNotification("Proses bitdi") }
        }, "emulator-reader").also { it.start() }

        val exitCode = started.waitFor()
        appendLog("\nÇıxış kodu: $exitCode\n")
        process = null
    }

    @Synchronized
    private fun appendLog(text: String) {
        File(filesDir, "emulator.log").appendText(text)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Brütal Legend emulator",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val openApp = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openApp,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Brutal Legend Droid")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }
}
