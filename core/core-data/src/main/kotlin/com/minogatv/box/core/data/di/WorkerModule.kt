package com.minogatv.box.core.data.di

import androidx.hilt.work.HiltWorkerFactory
import androidx.work.WorkerFactory
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that provides the [HiltWorkerFactory] as the app's [WorkerFactory].
 *
 * This is required for `@HiltWorker`-annotated workers (like [M3uSyncWorker] and
 * [EpgSyncWorker]) to have their dependencies injected via Hilt.
 *
 * ## Configuration required in MinogaApplication
 * The app's `Application` class must implement [androidx.work.Configuration.Provider]
 * and return a [androidx.work.Configuration] that uses this factory:
 *
 * ```kotlin
 * @HiltAndroidApp
 * class MinogaApplication : Application(), Configuration.Provider {
 *     @Inject lateinit var workerFactory: HiltWorkerFactory
 *
 *     override val workManagerConfiguration: Configuration
 *         get() = Configuration.Builder()
 *             .setWorkerFactory(workerFactory)
 *             .build()
 * }
 * ```
 *
 * Also remove the default WorkManager initializer from AndroidManifest.xml:
 * ```xml
 * <provider
 *     android:name="androidx.startup.InitializationProvider"
 *     android:authorities="${applicationId}.androidx-startup"
 *     tools:node="remove" />
 * ```
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class WorkerModule {

    @Binds
    @Singleton
    abstract fun bindWorkerFactory(factory: HiltWorkerFactory): WorkerFactory
}
