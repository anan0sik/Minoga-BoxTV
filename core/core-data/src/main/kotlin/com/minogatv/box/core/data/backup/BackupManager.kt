package com.minogatv.box.core.data.backup

import android.content.Context
import android.os.Environment
import com.minogatv.box.core.data.repository.PlaylistRepository
import com.minogatv.box.core.data.sync.SyncScheduler
import com.minogatv.box.core.database.dao.ChannelDao
import com.minogatv.box.core.database.dao.FavoriteDao
import com.minogatv.box.core.database.dao.PlaylistDao
import com.minogatv.box.core.database.entity.FavoriteChannelEntity
import com.minogatv.box.core.database.entity.PlaylistEntity
import com.minogatv.box.core.model.enums.PlaylistType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result metrics for a completed backup restore operation.
 */
data class BackupRestoreStats(
    val playlistsRestored: Int,
    val favoritesRestored: Int,
    val settingsRestored: Int,
    val filePath: String,
)

/**
 * Manages backup export, import, auto-backup, and auto-restore across app reinstallations.
 *
 * Backups are stored in public storage (`Download/minoga_backup.json`) so they survive
 * app uninstallation and reinstallations on Android TV boxes.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playlistDao: PlaylistDao,
    private val channelDao: ChannelDao,
    private val favoriteDao: FavoriteDao,
    private val playlistRepository: PlaylistRepository,
    private val syncScheduler: SyncScheduler,
) {

    private val prefs by lazy {
        context.getSharedPreferences("minoga_tv_prefs", Context.MODE_PRIVATE)
    }

    /**
     * Standard candidate files where `minoga_backup.json` can be located.
     */
    fun getCandidateBackupFiles(): List<File> {
        val candidates = mutableListOf<File>()

        try {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadDir != null) {
                candidates.add(File(downloadDir, BACKUP_FILE_NAME))
            }
        } catch (_: Exception) {}

        try {
            val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            if (documentsDir != null) {
                candidates.add(File(documentsDir, BACKUP_FILE_NAME))
            }
        } catch (_: Exception) {}

        candidates.add(File("/storage/emulated/0/Download", BACKUP_FILE_NAME))
        candidates.add(File("/sdcard/Download", BACKUP_FILE_NAME))
        candidates.add(File("/storage/emulated/0", BACKUP_FILE_NAME))
        candidates.add(File("/sdcard", BACKUP_FILE_NAME))

        try {
            val extDir = context.getExternalFilesDir(null)
            if (extDir != null) {
                candidates.add(File(extDir, BACKUP_FILE_NAME))
            }
        } catch (_: Exception) {}

        // Check potential external USB drives on Android TV (/storage/XXXX-XXXX)
        try {
            val storageRoot = File("/storage")
            if (storageRoot.exists() && storageRoot.isDirectory) {
                storageRoot.listFiles()?.forEach { drive ->
                    if (drive.isDirectory && drive.name != "emulated" && drive.name != "self") {
                        candidates.add(File(drive, BACKUP_FILE_NAME))
                        candidates.add(File(File(drive, "Download"), BACKUP_FILE_NAME))
                    }
                }
            }
        } catch (_: Exception) {}

        return candidates.distinctBy { it.absolutePath }
    }

    /**
     * Finds the first existing backup file from candidate storage paths.
     */
    fun findExistingBackupFile(): File? {
        return getCandidateBackupFiles().firstOrNull { it.exists() && it.isFile && it.length() > 0 }
    }

    /**
     * Primary destination file for exporting.
     */
    fun getPrimaryExportFile(): File {
        return try {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadDir != null) {
                if (!downloadDir.exists()) downloadDir.mkdirs()
                File(downloadDir, BACKUP_FILE_NAME)
            } else {
                File("/sdcard/Download", BACKUP_FILE_NAME).also { it.parentFile?.mkdirs() }
            }
        } catch (_: Exception) {
            File(context.getExternalFilesDir(null) ?: context.filesDir, BACKUP_FILE_NAME)
        }
    }

    /**
     * Exports all playlists, favorite channels, and user settings into JSON.
     * Writes to public `Download/minoga_backup.json` and a secondary app-external copy.
     */
    suspend fun exportBackup(targetFile: File? = null): Result<File> = withContext(Dispatchers.IO) {
        try {
            val destination = targetFile ?: getPrimaryExportFile()
            destination.parentFile?.mkdirs()

            val rootJson = JSONObject()
            rootJson.put("app", "MinogaTVBox")
            rootJson.put("version", 1)
            rootJson.put("timestamp", System.currentTimeMillis())

            // 1. Playlists
            val playlists = playlistDao.getAll()
            val playlistsArray = JSONArray()
            for (p in playlists) {
                val plObj = JSONObject().apply {
                    put("name", p.name)
                    put("url", p.url)
                    put("epgUrl", p.epgUrl)
                    put("type", p.type.name)
                    put("userAgent", p.userAgent)
                    put("sortOrder", p.sortOrder)
                    put("epgTimeShiftHours", p.epgTimeShiftHours.toDouble())
                    put("isEnabled", p.isEnabled)
                }
                playlistsArray.put(plObj)
            }
            rootJson.put("playlists", playlistsArray)

            // 2. Favorites
            val favIds = favoriteDao.getFavoriteIds(1L).toSet()
            val allChannels = channelDao.getAll()
            val favChannels = allChannels.filter { it.id in favIds }
            val favArray = JSONArray()
            for (fav in favChannels) {
                val favObj = JSONObject().apply {
                    put("name", fav.name)
                    put("streamUrl", fav.streamUrl)
                    put("epgChannelId", fav.epgChannelId)
                    put("groupTitle", fav.groupTitle)
                }
                favArray.put(favObj)
            }
            rootJson.put("favorites", favArray)

            // 3. Settings / Preferences
            val allPrefs = prefs.all
            val prefsObj = JSONObject()
            for ((key, value) in allPrefs) {
                if (value != null) {
                    prefsObj.put(key, value)
                }
            }
            rootJson.put("preferences", prefsObj)

            // Write formatted JSON to destination
            val jsonString = rootJson.toString(2)
            destination.writeText(jsonString, Charsets.UTF_8)

            // Also make a secondary fallback copy
            try {
                val secondary = File(context.getExternalFilesDir(null) ?: context.filesDir, BACKUP_FILE_NAME)
                if (secondary.absolutePath != destination.absolutePath) {
                    secondary.writeText(jsonString, Charsets.UTF_8)
                }
            } catch (_: Exception) {}

            Result.success(destination)
        } catch (e: Exception) {
            Result.failure(IOException("Ошибка экспорта резервной копии: ${e.localizedMessage ?: e.message}", e))
        }
    }

    /**
     * Imports playlists, favorites, and preferences from [sourceFile] (or auto-detected backup).
     */
    suspend fun importBackup(sourceFile: File? = null): Result<BackupRestoreStats> = withContext(Dispatchers.IO) {
        try {
            val fileToRead = sourceFile ?: findExistingBackupFile()
                ?: return@withContext Result.failure(
                    FileNotFoundException(
                        "Файл $BACKUP_FILE_NAME не найден. Поместите его в папку Download устройства или подключите USB-накопитель."
                    )
                )

            val content = fileToRead.readText(Charsets.UTF_8)
            val root = JSONObject(content)

            var playlistsRestored = 0
            var favoritesRestored = 0
            var settingsRestored = 0

            // 1. Restore Preferences
            val prefsObj = root.optJSONObject("preferences")
            if (prefsObj != null) {
                val editor = prefs.edit()
                val keys = prefsObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val v = prefsObj.get(key)
                    when (v) {
                        is Boolean -> editor.putBoolean(key, v)
                        is Int     -> editor.putInt(key, v)
                        is Long    -> editor.putLong(key, v)
                        is Float   -> editor.putFloat(key, v)
                        is Double  -> editor.putFloat(key, v.toFloat())
                        is String  -> editor.putString(key, v)
                    }
                    settingsRestored++
                }
                editor.apply()
            }

            // 2. Restore Playlists
            val playlistsArray = root.optJSONArray("playlists")
            if (playlistsArray != null) {
                val existingPlaylists = playlistDao.getAll()
                for (i in 0 until playlistsArray.length()) {
                    val plObj = playlistsArray.getJSONObject(i)
                    val url = plObj.optString("url", "").trim()
                    if (url.isEmpty()) continue

                    val name = plObj.optString("name", "Плейлист").ifBlank { "Плейлист" }
                    val epgUrl = plObj.optString("epgUrl", "").takeIf { it.isNotBlank() }
                    val typeStr = plObj.optString("type", "M3U")
                    val type = runCatching { PlaylistType.valueOf(typeStr) }.getOrDefault(PlaylistType.M3U)
                    val userAgent = plObj.optString("userAgent", "MinogaTVBox/1.0 (Android)")
                    val sortOrder = plObj.optInt("sortOrder", i)
                    val epgTimeShift = plObj.optDouble("epgTimeShiftHours", 0.0).toFloat()
                    val isEnabled = plObj.optBoolean("isEnabled", true)

                    val existing = existingPlaylists.firstOrNull { it.url.equals(url, ignoreCase = true) }
                    val entityId = if (existing == null) {
                        playlistDao.insert(
                            PlaylistEntity(
                                profileId = 1L,
                                name = name,
                                url = url,
                                epgUrl = epgUrl,
                                type = type,
                                userAgent = userAgent,
                                sortOrder = sortOrder,
                                epgTimeShiftHours = epgTimeShift,
                                isEnabled = isEnabled,
                            )
                        )
                    } else {
                        existing.id
                    }

                    playlistsRestored++
                    // Trigger sync for the playlist
                    syncScheduler.scheduleM3uSync(entityId)
                    syncScheduler.triggerManualM3uSync(entityId)
                }
            }

            // 3. Restore Favorites (match by stream URL or channel name)
            val favArray = root.optJSONArray("favorites")
            if (favArray != null && favArray.length() > 0) {
                val allChannels = channelDao.getAll()
                for (i in 0 until favArray.length()) {
                    val favObj = favArray.getJSONObject(i)
                    val streamUrl = favObj.optString("streamUrl", "")
                    val name = favObj.optString("name", "")
                    val matchedChannel = allChannels.firstOrNull { ch ->
                        (streamUrl.isNotEmpty() && ch.streamUrl == streamUrl) ||
                        (name.isNotEmpty() && ch.name.equals(name, ignoreCase = true))
                    }
                    if (matchedChannel != null) {
                        favoriteDao.addFavorite(
                            FavoriteChannelEntity(
                                profileId = 1L,
                                channelId = matchedChannel.id,
                                sortOrder = i,
                            )
                        )
                        channelDao.setFavoriteFlag(matchedChannel.id, true)
                        favoritesRestored++
                    }
                }
            }

            Result.success(
                BackupRestoreStats(
                    playlistsRestored = playlistsRestored,
                    favoritesRestored = favoritesRestored,
                    settingsRestored = settingsRestored,
                    filePath = fileToRead.absolutePath,
                )
            )
        } catch (e: Exception) {
            Result.failure(IOException("Ошибка восстановления из резервной копии: ${e.localizedMessage ?: e.message}", e))
        }
    }

    /**
     * Checks if the app has 0 playlists (e.g. cold start after fresh install / reinstall)
     * and automatically restores from an existing `minoga_backup.json` in Download.
     */
    suspend fun checkAndAutoRestoreOnFirstLaunch(): Boolean = withContext(Dispatchers.IO) {
        try {
            val count = playlistDao.getAll().size
            if (count > 0) return@withContext false

            val existingFile = findExistingBackupFile() ?: return@withContext false
            val result = importBackup(existingFile)
            result.isSuccess && (result.getOrNull()?.playlistsRestored ?: 0) > 0
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Silently creates/updates the auto-backup file in public Download folder.
     */
    suspend fun autoBackup() = withContext(Dispatchers.IO) {
        try {
            exportBackup()
        } catch (_: Exception) {}
    }

    companion object {
        const val BACKUP_FILE_NAME = "minoga_backup.json"
    }
}
