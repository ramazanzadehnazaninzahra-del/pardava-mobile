package ir.pardava.mobile.core

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import ir.pardava.mobile.data.dto.LatestVersionDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * In-app update pipeline — everything happens INSIDE the app:
 *   check(api) → LatestVersionDto?             (null = server has no release info)
 *   downloadAndInstall(...) → InstallResult    (in-app download with progress, then
 *                                               the system installer opens automatically)
 *
 * The APK is served by pardava.ir itself (/static/mobile/…) so updates reach
 * Iranian users without any Play Store dependency. Version source of truth is
 * data/mobile_version.json on the server.
 */
object UpdateManager {

    /** Result of [downloadAndInstall]. */
    sealed class InstallResult {
        /** Installer intent fired — the user now sees the system install prompt. */
        data object Started : InstallResult()

        /** Android 8+ needs «install unknown apps» granted once; settings screen was opened. */
        data object NeedPermission : InstallResult()

        /** Download or installer launch failed. */
        data class Failed(val message: String?) : InstallResult()
    }

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /** Returns the latest release info, or null when none published. */
    suspend fun check(api: ApiClient): LatestVersionDto? = withContext(Dispatchers.IO) {
        try {
            api.call { api.api.mobileVersion() }.takeIf { it.ok != false }?.latest
        } catch (_: Exception) {
            null
        }
    }

    fun isNewer(latest: LatestVersionDto, currentCode: Int, currentName: String): Boolean {
        val code = latest.versionCode ?: 0
        if (code <= currentCode) return false
        // Same code but a different versionName (rebuild of the same release) also updates.
        if (code == currentCode) {
            return latest.versionName?.isNotBlank() == true && latest.versionName != currentName
        }
        return true
    }

    fun canInstall(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        return context.packageManager.canRequestPackageInstalls()
    }

    /** Opens the system screen that grants «install unknown apps» for Pardava. */
    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData(Uri.parse("package:" + context.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /**
     * Downloads the APK into the app's private external dir with progress reporting
     * (0..100, or -1 when the total size is unknown) and then fires the installer.
     * An already-complete download of the same version is reused (tap again after
     * granting the install permission → no second download).
     */
    suspend fun downloadAndInstall(
        context: Context,
        api: ApiClient,
        apkUrl: String,
        versionName: String?,
        onProgress: (Int) -> Unit = {},
    ): InstallResult = withContext(Dispatchers.IO) {
        if (!canInstall(context)) {
            openInstallPermissionSettings(context)
            return@withContext InstallResult.NeedPermission
        }
        val file = try {
            downloadApk(context, api, apkUrl, versionName, onProgress)
        } catch (e: Exception) {
            return@withContext InstallResult.Failed(e.message)
        }
        if (install(context, file)) InstallResult.Started else InstallResult.Failed(null)
    }

    /** Streams the APK to a file; resolves relative server paths against the site root. */
    private fun downloadApk(
        context: Context,
        api: ApiClient,
        apkUrl: String,
        versionName: String?,
        onProgress: (Int) -> Unit,
    ): File {
        val url = api.absoluteUrl(apkUrl) ?: throw IllegalStateException("نشانی نسخهٔ جدید نامعتبر است.")
        val safeName = "pardava-" + (versionName ?: "update").replace(Regex("[^A-Za-z0-9._-]"), "_") + ".apk"
        val dir = context.getExternalFilesDir("update") ?: context.filesDir
        val outFile = File(dir, safeName)
        if (outFile.exists() && outFile.length() > 0L) return outFile

        val request = Request.Builder().url(url).header("User-Agent", "pardava-android").build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("دانلود نسخهٔ جدید ناموفق بود (${response.code}).")
            }
            val body = response.body ?: throw IllegalStateException("پاسخ خالی از سرور.")
            val total = body.contentLength()
            val tmp = File(dir, "$safeName.part")
            tmp.outputStream().use { sink ->
                body.byteStream().use { src ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    var done = 0L
                    while (src.read(buffer).also { read = it } != -1) {
                        sink.write(buffer, 0, read)
                        done += read
                        if (total > 0) onProgress(((done * 100) / total).toInt().coerceIn(0, 100))
                    }
                }
            }
            if (tmp.length() == 0L) {
                tmp.delete()
                throw IllegalStateException("فایل نسخهٔ جدید خالی بود.")
            }
            if (!tmp.renameTo(outFile)) {
                tmp.copyTo(outFile, overwrite = true)
                tmp.delete()
            }
            return outFile
        }
    }

    /** Opens a downloaded APK with the system package installer. */
    fun install(context: Context, file: File): Boolean {
        return try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }
}
