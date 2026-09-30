package ru.studynotes.sfu.updates

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ru.studynotes.sfu.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val notes: String,
    val required: Boolean = false,
    val channel: String = "stable"
)

object AppUpdateManager {
    private const val REPO = "Probnik67/StudyNotesSFU-Updates"

    private fun manifestUrls(channel: String): List<String> {
        val c = if (channel.equals("test", true)) "test" else "stable"
        val nonce = System.currentTimeMillis()
        return listOf(
            "https://raw.githubusercontent.com/$REPO/main/$c.json?ts=$nonce",
            "https://cdn.jsdelivr.net/gh/$REPO@main/$c.json?ts=$nonce"
        )
    }

    // MANIFEST_FALLBACK_V0833
    fun check(channel: String): AppUpdateInfo? {
        var newest: AppUpdateInfo? = null
        var last: Throwable? = null
        var anySuccessfulManifest = false

        for (url in manifestUrls(channel)) {
            try {
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    requestMethod = "GET"
                    useCaches = false
                    setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0")
                    setRequestProperty("Pragma", "no-cache")
                    setRequestProperty("User-Agent", "StudyNotesSFU/${BuildConfig.VERSION_NAME}")
                }
                try {
                    if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
                    val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                    anySuccessfulManifest = true
                    val code = json.optInt("versionCode", 0)
                    if (code > BuildConfig.VERSION_CODE && (newest == null || code > newest!!.versionCode)) {
                        val name = json.optString("versionName", code.toString()).trim()
                        val apk = json.optString("apkUrl", "").trim().ifBlank {
                            "https://raw.githubusercontent.com/$REPO/main/apk/StudyNotesSFU-v$name-release.apk"
                        }
                        newest = AppUpdateInfo(
                            versionCode = code,
                            versionName = name,
                            apkUrl = apk,
                            notes = json.optString("notes", "Доступна новая версия StudyNotesSFU."),
                            required = json.optBoolean("required", false),
                            channel = if (channel.equals("test", true)) "test" else "stable"
                        )
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (t: Throwable) {
                last = t
            }
        }

        if (newest != null) return newest
        if (anySuccessfulManifest) return null
        throw IllegalStateException("Источники обновления недоступны: ${last?.message ?: "ошибка сети"}", last)
    }

    fun canInstallPackages(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun apkUrls(update: AppUpdateInfo): List<String> {
        val file = "StudyNotesSFU-v${update.versionName}-release.apk"
        return listOf(
            "https://cdn.jsdelivr.net/gh/$REPO@main/apk/$file",
            update.apkUrl,
            "https://raw.githubusercontent.com/$REPO/main/apk/$file"
        ).filter { it.startsWith("https://") }.distinct()
    }

    private fun download(url: String, apk: File, onProgress: (Int) -> Unit) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000
            readTimeout = 45000
            requestMethod = "GET"
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("User-Agent", "StudyNotesSFU/${BuildConfig.VERSION_NAME}")
        }
        try {
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
            val total = connection.contentLengthLong
            var done = 0L
            var lastProgress = -1
            onProgress(0)
            apk.outputStream().use { output ->
                connection.inputStream.use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        done += count
                        if (total > 0) {
                            val p = ((done * 100L) / total).toInt().coerceIn(0, 100)
                            if (p != lastProgress) {
                                lastProgress = p
                                onProgress(p)
                            }
                        }
                    }
                }
            }
            if (apk.length() < 1024 * 1024) error("Некорректный APK")
            onProgress(100)
        } finally {
            connection.disconnect()
        }
    }

    suspend fun downloadAndStartInstall(
        context: Context,
        update: AppUpdateInfo,
        onProgress: (Int) -> Unit = {}
    ) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val apk = File(dir, "StudyNotesSFU-${update.versionName}.apk")
        var last: Throwable? = null
        var ok = false
        for (url in apkUrls(update)) {
            try {
                if (apk.exists()) apk.delete()
                download(url, apk, onProgress)
                ok = true
                break
            } catch (t: Throwable) {
                last = t
            }
        }
        if (!ok) throw IllegalStateException("Не удалось скачать обновление: ${last?.message}", last)

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        withContext(Dispatchers.Main) {
            val install = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = uri
                clipData = ClipData.newRawUri("StudyNotesSFU update", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            }
            val fallback = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                clipData = ClipData.newRawUri("StudyNotesSFU update", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(install)
            } catch (first: Throwable) {
                try {
                    context.startActivity(fallback)
                } catch (second: Throwable) {
                    second.addSuppressed(first)
                    throw second
                }
            }
        }
    }
}
