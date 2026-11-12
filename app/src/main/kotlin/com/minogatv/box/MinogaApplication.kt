package com.minogatv.box

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application class for Minoga TV Box.
 *
 * ## Hilt
 * Annotated with [@HiltAndroidApp] which triggers Hilt's code generation and
 * installs the global component hierarchy.
 *
 * ## WorkManager
 * Implements [Configuration.Provider] so WorkManager uses [HiltWorkerFactory]
 * instead of the default factory. This allows [M3uSyncWorker] and [EpgSyncWorker]
 * to receive their Hilt-injected dependencies via `@AssistedInject`.
 *
 * The default WorkManager initializer is disabled in AndroidManifest.xml:
 * ```xml
 * <meta-data android:name="androidx.work.WorkManagerInitializer" tools:node="remove"/>
 * ```
 */
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.minogatv.box.core.network.SmartDohDns
import com.minogatv.box.core.network.di.BaseOkHttpClient
import okhttp3.OkHttpClient

@HiltAndroidApp
class MinogaApplication : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    @BaseOkHttpClient
    lateinit var baseOkHttpClient: OkHttpClient

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        SmartDohDns.init(this)
        // Safeguard against random system/decoder exceptions crashing the process on TV boxes
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("MinogaApp", "Uncaught exception on thread ${thread.name}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient {
                if (::baseOkHttpClient.isInitialized) baseOkHttpClient else OkHttpClient()
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(filesDir.resolve("picons_cache"))
                    .maxSizeBytes(150L * 1024 * 1024) // 150MB persistent disk cache for channel logos
                    .build()
            }
            .respectCacheHeaders(false)
            .crossfade(false) // Disable crossfade overhead on TV boxes
            .build()
    }
}
