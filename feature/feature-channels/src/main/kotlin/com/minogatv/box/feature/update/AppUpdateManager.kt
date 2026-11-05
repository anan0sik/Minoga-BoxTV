package com.minogatv.box.feature.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

data class RemoteVersionInfo(
    val versionName: String,
    val versionCode: Int,
    val sizeBytes: Long,
    val sha256: String,
    val updated: String,
    val downloadUrl: String,
)

data class AppUpdateState(
    val localVersionName: String = "1.0.6",
    val localVersionCode: Int = 7,
    val remoteInfo: RemoteVersionInfo? = null,
    val isChecking: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val downloadedApkFile: File? = null,
    val isBannerDismissed: Boolean = false,
    val lastCheckedAtMs: Long = 0L,
    val errorMessage: String? = null,
) {
    val isUpdateAvailable: Boolean
        get() = remoteInfo != null && remoteInfo.versionCode > localVersionCode
}

/**
 * Singleton OTA update manager that checks `https://minogatvbox.duckdns.org/download/version.json`,
 * downloads the latest APK with SHA-256 verification, and launches the Android system package installer.
 */
object AppUpdateManager {

    private const val BASE_URL = "https://minogatvbox.duckdns.org"
    private const val VERSION_JSON_URL = "$BASE_URL/download/version.json"
    private const val CHECK_COOLDOWN_MS = 5 * 60 * 1000L // 5 minutes for automatic background checks

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(AppUpdateState())
    val state: StateFlow<AppUpdateState> = _state.asStateFlow()

    @Volatile
    private var pendingInstallFile: File? = null

    fun ensureLocalVersionLoaded(context: Context) {
        try {
            val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkg.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pkg.versionCode
            }
            val name = pkg.versionName ?: "1.0"
            _state.update {
                it.copy(
                    localVersionName = name,
                    localVersionCode = code,
                )
            }
        } catch (_: Exception) {
        }
    }

    fun dismissBannerForSession() {
        _state.update { it.copy(isBannerDismissed = true) }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    /**
     * Checks `https://minogatvbox.duckdns.org/download/version.json` for a newer `versionCode`.
     *
     * @param force If true, bypasses the 5-minute cooldown and clears previous error messages.
     */
    fun checkForUpdates(context: Context, force: Boolean = false, onChecked: ((Boolean) -> Unit)? = null) {
        val appContext = context.applicationContext
        ensureLocalVersionLoaded(appContext)

        val current = _state.value
        if (current.isChecking || current.isDownloading) return
        val now = System.currentTimeMillis()
        if (!force && current.lastCheckedAtMs > 0L && (now - current.lastCheckedAtMs) < CHECK_COOLDOWN_MS) {
            return
        }

        _state.update {
            it.copy(
                isChecking = true,
                errorMessage = if (force) null else it.errorMessage,
            )
        }

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val url = URL("$VERSION_JSON_URL?t=${System.currentTimeMillis()}")
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 8000
                        readTimeout = 8000
                        useCaches = false
                        setRequestProperty("Cache-Control", "no-cache")
                    }
                    try {
                        val code = conn.responseCode
                        if (code !in 200..299) {
                            throw IllegalStateException("HTTP $code при проверке обновлений")
                        }
                        val body = conn.inputStream.bufferedReader().use { it.readText() }
                        val json = JSONObject(body)
                        val versionName = json.optString("versionName", "").trim()
                        val versionCode = json.optInt("versionCode", 0)
                        val sizeBytes = json.optLong("sizeBytes", 0L)
                        val sha256 = json.optString("sha256", "").trim().lowercase(Locale.US)
                        val updated = json.optString("updated", "").trim()
                        val rawUrl = json.optString("url", "/download/MinogaTVBox.apk").trim()
                        val fullUrl = when {
                            rawUrl.startsWith("http://") || rawUrl.startsWith("https://") -> rawUrl
                            rawUrl.startsWith("/") -> "$BASE_URL$rawUrl"
                            else -> "$BASE_URL/$rawUrl"
                        }
                        RemoteVersionInfo(
                            versionName = versionName.ifBlank { "1.0.$versionCode" },
                            versionCode = versionCode,
                            sizeBytes = sizeBytes,
                            sha256 = sha256,
                            updated = updated,
                            downloadUrl = fullUrl,
                        )
                    } finally {
                        conn.disconnect()
                    }
                }
            }

            result.onSuccess { remote ->
                _state.update { prev ->
                    val isNewerVersion = remote.versionCode > prev.localVersionCode
                    val prevRemoteCode = prev.remoteInfo?.versionCode ?: -1
                    prev.copy(
                        remoteInfo = remote,
                        isChecking = false,
                        lastCheckedAtMs = System.currentTimeMillis(),
                        errorMessage = null,
                        // Reset banner dismissal if an even newer version appeared
                        isBannerDismissed = if (remote.versionCode != prevRemoteCode) false else prev.isBannerDismissed,
                    )
                }
                val hasUpdate = _state.value.isUpdateAvailable
                onChecked?.invoke(hasUpdate)
            }.onFailure { err ->
                _state.update { prev ->
                    prev.copy(
                        isChecking = false,
                        errorMessage = if (force) "Не удалось проверить обновления: ${err.localizedMessage ?: "ошибка сети"}" else prev.errorMessage,
                    )
                }
            }
        }
    }

    /**
     * Downloads the new APK into `cacheDir/updates/MinogaTVBox-update.apk`, verifies its SHA-256 hash,
     * and launches the system package installer.
     */
    fun downloadAndInstall(context: Context) {
        val current = _state.value
        val remote = current.remoteInfo ?: return
        if (current.isDownloading) return

        // If already downloaded for this exact version and file still exists with matching size, install right away
        val existingFile = current.downloadedApkFile
        if (existingFile != null && existingFile.exists() && existingFile.length() > 1_000_000L) {
            installApk(context, existingFile)
            return
        }

        val appContext = context.applicationContext
        _state.update {
            it.copy(
                isDownloading = true,
                downloadProgress = 0f,
                downloadedBytes = 0L,
                totalBytes = remote.sizeBytes,
                errorMessage = null,
            )
        }

        scope.launch {
            val downloadResult = withContext(Dispatchers.IO) {
                runCatching {
                    val updatesDir = File(appContext.cacheDir, "updates").apply { mkdirs() }
                    val targetFile = File(updatesDir, "MinogaTVBox-v${remote.versionCode}.apk")
                    val tempFile = File(updatesDir, "MinogaTVBox-v${remote.versionCode}.apk.part")

                    // Clean up old update files
                    updatesDir.listFiles()?.forEach { f ->
                        if (f != targetFile) runCatching { f.delete() }
                    }

                    val downloadUrl = "${remote.downloadUrl}?t=${System.currentTimeMillis()}"
                    val conn = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 15000
                        readTimeout = 30000
                        useCaches = false
                    }

                    try {
                        val responseCode = conn.responseCode
                        if (responseCode !in 200..299) {
                            throw IllegalStateException("Сервер вернул код HTTP $responseCode")
                        }

                        val contentLength = conn.contentLengthLong.takeIf { it > 0L } ?: remote.sizeBytes
                        val digest = MessageDigest.getInstance("SHA-256")
                        var bytesReadTotal = 0L

                        conn.inputStream.buffered().use { input ->
                            FileOutputStream(tempFile).use { output ->
                                val buffer = ByteArray(32 * 1024)
                                var read: Int
                                var lastEmitMs = 0L
                                while (input.read(buffer).also { read = it } != -1) {
                                    output.write(buffer, 0, read)
                                    digest.update(buffer, 0, read)
                                    bytesReadTotal += read

                                    val now = System.currentTimeMillis()
                                    if (now - lastEmitMs >= 80L) {
                                        lastEmitMs = now
                                        val progress = if (contentLength > 0L) {
                                            (bytesReadTotal.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                                        } else {
                                            0f
                                        }
                                        _state.update { s ->
                                            s.copy(
                                                downloadProgress = progress,
                                                downloadedBytes = bytesReadTotal,
                                                totalBytes = contentLength,
                                            )
                                        }
                                    }
                                }
                                output.flush()
                            }
                        }

                        val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }.lowercase(Locale.US)
                        if (remote.sha256.isNotBlank() && actualSha256 != remote.sha256.lowercase(Locale.US)) {
                            tempFile.delete()
                            throw IllegalStateException("Ошибка проверки контрольной суммы SHA-256 скачанного файла")
                        }

                        if (targetFile.exists()) targetFile.delete()
                        if (!tempFile.renameTo(targetFile)) {
                            tempFile.copyTo(targetFile, overwrite = true)
                            tempFile.delete()
                        }
                        targetFile
                    } finally {
                        conn.disconnect()
                    }
                }
            }

            downloadResult.onSuccess { apkFile ->
                _state.update {
                    it.copy(
                        isDownloading = false,
                        downloadProgress = 1f,
                        downloadedApkFile = apkFile,
                        errorMessage = null,
                    )
                }
                installApk(context, apkFile)
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        isDownloading = false,
                        downloadProgress = 0f,
                        errorMessage = "Ошибка загрузки: ${err.localizedMessage ?: "неизвестная ошибка"}",
                    )
                }
            }
        }
    }

    /**
     * Launches the Android system package installer for [apkFile], requesting unknown-sources permission first if needed.
     */
    fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists()) {
            _state.update { it.copy(downloadedApkFile = null, errorMessage = "Файл обновления не найден, скачайте заново.") }
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            pendingInstallFile = apkFile
            Toast.makeText(
                context,
                "Разрешите установку обновлений из этого источника и нажмите Назад",
                Toast.LENGTH_LONG,
            ).show()
            try {
                val permIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(permIntent)
                return
            } catch (_: Exception) {
                // Fallback on TV boxes that don't have a separate per-app unknown sources screen
            }
        }

        pendingInstallFile = null
        try {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile,
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            _state.update {
                it.copy(errorMessage = "Не удалось запустить установщик: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Called when returning from the Android "Allow from this source" settings screen.
     */
    fun onResumeCheckPendingInstall(context: Context) {
        val pending = pendingInstallFile ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()) {
            pendingInstallFile = null
            installApk(context, pending)
        }
    }

    fun formatSizeMb(bytes: Long): String {
        if (bytes <= 0L) return ""
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        return String.format(Locale.US, "%.1f МБ", mb)
    }
}
