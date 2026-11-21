package com.brutallegend.droid

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * Runtime bundle-in (proot, FEX/Box86/Box64, Mesa, Xvfb, PulseAudio, minimal rootfs)
 * endirilməsi, SHA256 yoxlanılması və açılmasından məsuldur.
 *
 * Bundle bir ZIP-dir və GitHub Releases üzərindən paylanır. Oyun faylları heç vaxt
 * bu yolla endirilmir — istifadəçi öz qanuni nüsxəsini göstərir.
 */
class RuntimeManager(private val ctx: Context) {

    private val ui = Handler(Looper.getMainLooper())
    private val runtimeDir = File(ctx.filesDir, "runtime")
    private val marker = File(runtimeDir, ".installed")

    fun isInstalled(): Boolean = marker.exists()

    fun runtimePath(): String = runtimeDir.absolutePath

    fun ensureRuntime(callback: (Boolean, String) -> Unit) {
        if (isInstalled()) {
            callback(true, "Runtime hazırdır: ${marker.readText().take(16)}…")
            return
        }
        Thread({
            try {
                val manifestText = ctx.assets.open("runtime-manifest.json")
                    .bufferedReader().use { it.readText() }
                val bundle = JSONObject(manifestText).getJSONObject("runtime_bundle")
                val url = bundle.getString("url")
                val expected = bundle.getString("sha256")

                if (expected.isBlank() || expected.startsWith("REPLACE", ignoreCase = true)) {
                    throw IllegalStateException(
                        "runtime bundle SHA256 hələ manifest-də doldurulmayıb. " +
                            "Əvvəlcə runtime bundle yayımlayın."
                    )
                }

                val zipFile = File(ctx.cacheDir, "runtime-bundle.zip")
                download(url, zipFile)
                val actual = sha256(zipFile)
                if (!actual.equals(expected, ignoreCase = true)) {
                    zipFile.delete()
                    throw IllegalStateException(
                        "SHA256 uyuşmur.\nGözlənən: $expected\nAlınan:   $actual"
                    )
                }

                unzip(zipFile, runtimeDir)
                zipFile.delete()
                marker.writeText(actual)
                ui.post { callback(true, "Runtime quraşdırıldı: ${actual.take(16)}…") }
            } catch (e: Exception) {
                val msg = e.message ?: e.toString()
                ui.post { callback(false, msg) }
            }
        }, "runtime-download").start()
    }

    private fun download(urlStr: String, out: File) {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = true
        try {
            if (conn.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${conn.responseCode}: $urlStr")
            }
            conn.inputStream.use { input ->
                FileOutputStream(out).use { output -> input.copyTo(output, 64 * 1024) }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val sink = object : OutputStream() {
            override fun write(b: Int) = digest.update(b.toByte())
            override fun write(b: ByteArray, off: Int, len: Int) = digest.update(b, off, len)
        }
        FileInputStream(file).use { it.copyTo(sink, 64 * 1024) }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun unzip(zipFile: File, target: File) {
        target.mkdirs()
        val canonicalTarget = target.canonicalPath
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val out = File(target, entry.name)
                if (!out.canonicalPath.startsWith(canonicalTarget)) {
                    throw SecurityException("ZIP içində qeyri-qanuni yol: ${entry.name}")
                }
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    FileOutputStream(out).use { zis.copyTo(it, 64 * 1024) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
