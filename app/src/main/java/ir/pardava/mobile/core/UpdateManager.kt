package ir.pardava.mobile.core

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import ir.pardava.mobile.core.ApiClient
import ir.pardava.mobile.data.dto.LatestVersionDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * In-app update pipeline:
 *   check(api) → LatestVersionDto?   (null = server has no release info)
 *   download(context, apkUrl) → id   (system DownloadManager, visible notification)
 *   install(context, id)             (opens the downloaded APK with the installer)
 *
 * The APK is served by pardava.ir itself (/static/mobile/…) so updates reach
 * Iranian users without any Play Store dependency. Version source of truth is
 * data/mobile_version.json on the server.
 */
object UpdateManager {

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

    /** Enqueue the APK download; the completed notification itself opens the installer. */
    fun download(context: Context, apkUrl: String): Long {
        val request = DownloadManager.Request(Uri.parse(apkUrl)).apply {
            setTitle("Pardava update")
            setDescription("Pardava-vX.Y.Z")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                "pardava-update.apk",
            )
            setMimeType("application/vnd.android.package-archive")
            allowScanningByMediaScanner()
        }
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return dm.enqueue(request)
    }

    /** Re-open the downloaded APK (used when the user taps “install” again). */
    fun install(context: Context, downloadId: Long): Boolean {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        var cursor: Cursor? = null
        try {
            cursor = dm.query(query)
            if (cursor == null || !cursor.moveToFirst()) return false
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            if (status != DownloadManager.STATUS_SUCCESSFUL) return false
            val uri = dm.getUriForDownloadedFile(downloadId) ?: return false
            return launchInstaller(context, uri)
        } catch (_: Exception) {
            return false
        } finally {
            cursor?.close()
        }
    }

    /** Opens any APK Uri (content/file) with the system package installer. */
    fun launchInstaller(context: Context, uri: Uri): Boolean {
        return try {
            val finalUri = if (uri.scheme == "file") {
                FileProvider.getUriForFile(
                    context,
                    context.packageName + ".fileprovider",
                    File(uri.path ?: return false),
                )
            } else {
                uri
            }
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(finalUri, "application/vnd.android.package-archive")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Poll download state — used to flip the settings UI into “install” mode. */
    fun statusOf(context: Context, downloadId: Long): Int {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val cursor = dm.query(DownloadManager.Query().setFilterById(downloadId)) ?: return -1
        return try {
            if (cursor.moveToFirst()) {
                cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            } else -1
        } finally {
            cursor.close()
        }
    }

    const val STATUS_SUCCESSFUL = DownloadManager.STATUS_SUCCESSFUL
    const val STATUS_RUNNING = DownloadManager.STATUS_RUNNING
    const val STATUS_FAILED = DownloadManager.STATUS_FAILED
}
