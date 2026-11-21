package com.brutallegend.droid

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.StatFs
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
 * Runtime bundle-in (proot, Wine, Box64, Mesa, X11) endirilməsi, SHA256 yoxlanılması
 * və açılması. Endirmə zamanı proqres verir, çatışmayan yaddaşı əvvəlcədən yoxlayır
 * və uğursuzluqda yarımçıq faylları silir.
 *
 * Oyun faylları BURA DAHIL EDILMIR.
 */
class RuntimeManager(private val ctx: Context) {

    private val ui = Handler(Looper.getMainLooper())
    private val runtimeDir = File(ctx.filesDir, "runtime")
    private val marker = File(runtimeDir, ".installed")
    private val zipFile = File(ctx.cacheDir, "runtime-bundle.zip")

    fun isInstalled(): Boolean = marker.exists()

    fun runtimePath(): String = runtimeDir.absolutePath

    fun availableBytes(): Long = StatFs(ctx.filesDir.path).availableBytes

    /** Uğursuz quraşdırmadan qalan yarımçıq faylları silir. */
    fun cleanupPartial() {
        if (isInstalled()) return
        if (runtimeDir.exists()) runtimeDir.deleteRecursively()
        if (zipFile.exists()) zipFile.delete()
    }

    fun ensureRuntime(
        onDone: (Boolean, String) -> Unit,
        onProgress: (stage: String, done: Long, total: Long) -> Unit = { _, _, _ -> }
    ) {
        if (isInstalled()) {
            onDone(true, "Runtime hazırdır: ${marker.readText().take(16)}…")
            return
        }
        Thread({
            var success = false
            try {
                cleanupPartial()

                val manifestText = ctx.assets.open("runtime-manifest.json")
                    .bufferedReader().use { it.readText() }
                val bundle = JSONObject(manifestText).getJSONObject("runtime_bundle")
                val url = bundle.getString("url")
                val expected = bundle.getString("sha256")
                if (expected.isBlank() || expected.startsWith("REPLACE", ignoreCase = true)) {
                    throw IllegalStateException("runtime bundle SHA256 manifest-də doldurulmayıb")
                }

                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout = 60_000
                conn.instanceFollowRedirects = true
                try {
                    if (conn.responseCode !in 200..299) {
                        throw IllegalStateException("HTTP ${conn.responseCode}: $url")
                    }
                    val total = conn.contentLength.toLong().coerceAtLeast(0)
                    val need = if (total > 0) total * 3 else 1_500_000_000L
                    val free = availableBytes()
                    if (free < need) {
                        throw IllegalStateException(
                            "Boş yaddaş çatışmır: ${free / 1048576} MB mövcuddur, " +
                                "təxminən ${need / 1048576} MB lazımdır. " +
                                "Digər tətbiqlərin cache-ini təmizləyin."
                        )
                    }

                    var downloaded = 0L
                    var lastReport = 0L
                    conn.inputStream.use { input ->
                        FileOutputStream(zipFile).use { output ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                output.write(buf, 0, n)
                                downloaded += n
                                if (downloaded - lastReport >= 256 * 1024 || downloaded == total) {
                                    lastReport = downloaded
                                    ui.post { onProgress("Endirilir", downloaded, total) }
                                }
                            }
                        }
                    }
                    conn.disconnect()

                    ui.post { onProgress("SHA256 yoxlanılır", 0, -1) }
                    val actual = sha256(zipFile)
                    if (!actual.equals(expected, ignoreCase = true)) {
                        throw IllegalStateException(
                            "SHA256 uyuşmur.\nGözlənən: $expected\nAlınan:   $actual"
                        )
                    }

                    ui.post { onProgress("Açılır", 0, -1) }
                    unzip(zipFile, runtimeDir)
                    marker.writeText(actual)
                    zipFile.delete()
                    success = true
                    ui.post { onDone(true, "Runtime quraşdırıldı: ${actual.take(16)}…") }
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                val msg = e.message ?: e.toString()
                ui.post { onDone(false, msg) }
            } finally {
                if (!success) cleanupPartial()
            }
        }, "runtime-download").start()
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
