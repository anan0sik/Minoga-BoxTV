package com.minogatv.box.feature.player

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.app.UiModeManager
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector

/**
 * Handles audio routing recovery on smartphones (especially Samsung One UI + Car Bluetooth / Android Auto).
 *
 * Solves three issues when disconnecting from Car Bluetooth or turning off Bluetooth:
 * 1. Wireless Android Auto (`TYPE_REMOTE_SUBMIX` over Wi-Fi) continuing to hijack audio after Bluetooth is turned off;
 * 2. Stuck `MODE_IN_COMMUNICATION` / `BluetoothSco` / `CommunicationDevice` state from car hands-free profile (HFP);
 * 3. Dead `AudioTrack` sink in ExoPlayer after A2DP hardware offload disconnects.
 */
@OptIn(UnstableApi::class)
object AudioRouteRecovery {

    private const val TAG = "AudioRouteRecovery"
    private val mainHandler = Handler(Looper.getMainLooper())

    // Non-blocking focus listener: never ducks or pauses ExoPlayer
    private val noOpFocusListener = AudioManager.OnAudioFocusChangeListener { /* Never mute player */ }

    fun isTvDevice(context: Context): Boolean {
        val pm = context.packageManager
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        return pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }

    /**
     * Resets stuck communication modes, Bluetooth SCO, and stream mute state,
     * and requests non-blocking media audio focus so system volume buttons control STREAM_MUSIC.
     */
    fun recoverSystemAudioState(context: Context, forcePhoneSpeaker: Boolean = false) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            val mode = audioManager.mode
            val inRealPhoneCall = mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_RINGTONE
            val btEnabled = isBluetoothActuallyEnabled()

            if (!inRealPhoneCall) {
                if (mode != AudioManager.MODE_NORMAL) {
                    Log.d(TAG, "Resetting stuck AudioManager mode=$mode -> MODE_NORMAL")
                    try {
                        audioManager.mode = AudioManager.MODE_NORMAL
                    } catch (_: Exception) {}
                }

                if (!btEnabled || forcePhoneSpeaker) {
                    try {
                        if (audioManager.isBluetoothScoOn) {
                            audioManager.isBluetoothScoOn = false
                        }
                        @Suppress("DEPRECATION")
                        audioManager.stopBluetoothSco()
                    } catch (_: Exception) {}

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        try {
                            audioManager.clearCommunicationDevice()
                        } catch (_: Exception) {}
                    }
                }
            }

            // Ensure STREAM_MUSIC is not muted at the OS level
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (audioManager.isStreamMute(AudioManager.STREAM_MUSIC)) {
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_UNMUTE,
                        0,
                    )
                }
            }

            // Request non-blocking AudioFocus so Android routes media audio & volume keys to us
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build()
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attrs)
                    .setAcceptsDelayedFocusGain(true)
                    .setWillPauseWhenDucked(false)
                    .setOnAudioFocusChangeListener(noOpFocusListener)
                    .build()
                audioManager.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    noOpFocusListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN,
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "recoverSystemAudioState error", e)
        }
    }

    /**
     * Checks whether the system Bluetooth adapter is currently ON (not turning off or off).
     */
    fun isBluetoothActuallyEnabled(): Boolean {
        return try {
            @Suppress("DEPRECATION")
            val adapter = BluetoothAdapter.getDefaultAdapter()
            adapter != null && adapter.isEnabled && adapter.state == BluetoothAdapter.STATE_ON
        } catch (_: SecurityException) {
            true
        } catch (_: Exception) {
            true
        }
    }

    /**
     * Selects the appropriate hardware output device on ExoPlayer.
     *
     * When Bluetooth is OFF (or [forcePhoneSpeaker] is true) on a phone/tablet, explicitly binds
     * ExoPlayer to `TYPE_BUILTIN_SPEAKER` so Wireless Android Auto (`TYPE_REMOTE_SUBMIX` over Wi-Fi)
     * or a dead Bluetooth A2DP sink cannot swallow the audio.
     */
    fun applyOptimalPreferredDevice(
        context: Context,
        exoPlayer: ExoPlayer,
        forcePhoneSpeaker: Boolean = false,
    ): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return "Авто"
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return "Авто"

        try {
            if (isTvDevice(context)) {
                exoPlayer.setPreferredAudioDevice(null)
                return "ТВ"
            }

            val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val btEnabled = isBluetoothActuallyEnabled()

            val builtinSpeaker = outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }

            val wiredHeadset = outputs.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                    it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
            }

            val bluetoothOutput = if (btEnabled && !forcePhoneSpeaker) {
                outputs.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (
                            it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                                it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
                            )) ||
                        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            it.type == AudioDeviceInfo.TYPE_BLE_BROADCAST) ||
                        it.type == AudioDeviceInfo.TYPE_HEARING_AID
                }
            } else {
                null
            }

            return when {
                forcePhoneSpeaker && builtinSpeaker != null -> {
                    try {
                        @Suppress("DEPRECATION")
                        audioManager.isSpeakerphoneOn = true
                    } catch (_: Exception) {}
                    exoPlayer.setPreferredAudioDevice(builtinSpeaker)
                    Log.d(TAG, "Forced preferred audio device -> TYPE_BUILTIN_SPEAKER")
                    "Динамик"
                }
                bluetoothOutput != null -> {
                    exoPlayer.setPreferredAudioDevice(bluetoothOutput)
                    Log.d(TAG, "Preferred audio device -> Bluetooth (${bluetoothOutput.productName})")
                    "Bluetooth"
                }
                wiredHeadset != null -> {
                    exoPlayer.setPreferredAudioDevice(wiredHeadset)
                    Log.d(TAG, "Preferred audio device -> Wired/USB (${wiredHeadset.productName})")
                    "Наушники"
                }
                builtinSpeaker != null -> {
                    // Bluetooth is OFF or no BT/wired audio device is connected:
                    // Explicitly bind to TYPE_BUILTIN_SPEAKER to bypass Wireless Android Auto (TYPE_REMOTE_SUBMIX)
                    // and any stuck Bluetooth routing!
                    try {
                        @Suppress("DEPRECATION")
                        audioManager.isSpeakerphoneOn = true
                    } catch (_: Exception) {}
                    exoPlayer.setPreferredAudioDevice(builtinSpeaker)
                    Log.d(TAG, "Preferred audio device -> TYPE_BUILTIN_SPEAKER (BT off or disconnected)")
                    "Динамик"
                }
                else -> {
                    exoPlayer.setPreferredAudioDevice(null)
                    "Авто"
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "applyOptimalPreferredDevice failed", e)
            return "Авто"
        }
    }

    /**
     * Full recovery + hardware AudioTrack sink rebuild.
     * Disables and re-enables the audio renderer for 60ms so ExoPlayer closes any dead Bluetooth
     * `AudioTrack` and creates a fresh `AudioTrack` on the phone's speaker without interrupting video.
     */
    fun recoverAndRebuildAudioSink(
        context: Context,
        exoPlayer: ExoPlayer,
        trackSelector: DefaultTrackSelector,
        forcePhoneSpeaker: Boolean = false,
        rebuildSink: Boolean = true,
        onRouteUpdated: ((String) -> Unit)? = null,
    ) {
        recoverSystemAudioState(context, forcePhoneSpeaker)
        val label = applyOptimalPreferredDevice(context, exoPlayer, forcePhoneSpeaker)
        exoPlayer.volume = 1f
        onRouteUpdated?.invoke(label)

        if (rebuildSink) {
            try {
                trackSelector.setParameters(
                    trackSelector.buildUponParameters()
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true),
                )
                mainHandler.postDelayed({
                    try {
                        recoverSystemAudioState(context, forcePhoneSpeaker)
                        val updatedLabel = applyOptimalPreferredDevice(context, exoPlayer, forcePhoneSpeaker)
                        trackSelector.setParameters(
                            trackSelector.buildUponParameters()
                                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false),
                        )
                        exoPlayer.volume = 1f
                        if (exoPlayer.playbackState == Player.STATE_READY && exoPlayer.playWhenReady) {
                            exoPlayer.play()
                        }
                        onRouteUpdated?.invoke(updatedLabel)
                    } catch (_: Exception) {}
                }, 65L)
            } catch (e: Exception) {
                Log.w(TAG, "rebuildAudioSink error", e)
            }
        }
    }

    /**
     * Registers live listeners for Bluetooth state changes, ACTION_AUDIO_BECOMING_NOISY, and
     * AudioDeviceCallback so turning off Bluetooth immediately restores phone speaker audio.
     */
    fun registerLiveAudioRouteObserver(
        context: Context,
        exoPlayer: ExoPlayer,
        trackSelector: DefaultTrackSelector,
        isForceSpeakerProvider: () -> Boolean,
        onRouteChanged: (String, Boolean) -> Unit,
    ): () -> Unit {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        val triggerRecovery = { rebuildSink: Boolean, notifyHud: Boolean ->
            val forceSpeaker = isForceSpeakerProvider()
            recoverAndRebuildAudioSink(
                context = context,
                exoPlayer = exoPlayer,
                trackSelector = trackSelector,
                forcePhoneSpeaker = forceSpeaker,
                rebuildSink = rebuildSink,
            ) { label ->
                onRouteChanged(label, notifyHud)
            }
            // Follow-up pass after 650ms once Bluetooth/Android Auto teardown completes
            if (rebuildSink) {
                mainHandler.postDelayed({
                    try {
                        val forceNow = isForceSpeakerProvider()
                        recoverSystemAudioState(context, forceNow)
                        val finalLabel = applyOptimalPreferredDevice(context, exoPlayer, forceNow)
                        exoPlayer.volume = 1f
                        onRouteChanged(finalLabel, false)
                    } catch (_: Exception) {}
                }, 650L)
            }
        }

        val deviceCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioManager != null) {
            object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                    val hasRelevantChange = addedDevices?.any {
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                            it.type == AudioDeviceInfo.TYPE_USB_HEADSET
                    } == true
                    if (hasRelevantChange) {
                        Log.d(TAG, "External audio device added -> updating route")
                        triggerRecovery(true, true)
                    }
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                    val hasRelevantRemoval = removedDevices?.any {
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                            it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (
                                it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                                    it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
                                ))
                    } == true
                    if (hasRelevantRemoval) {
                        Log.d(TAG, "External audio device removed -> rebuilding audio sink on phone speaker")
                        triggerRecovery(true, true)
                    }
                }
            }.also { cb ->
                try {
                    audioManager.registerAudioDeviceCallback(cb, mainHandler)
                } catch (_: Exception) {}
            }
        } else {
            null
        }

        val broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                        Log.d(TAG, "ACTION_AUDIO_BECOMING_NOISY -> recovering speaker audio")
                        triggerRecovery(true, true)
                    }
                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
                        val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                        if (state == BluetoothAdapter.STATE_TURNING_OFF ||
                            state == BluetoothAdapter.STATE_OFF ||
                            state == BluetoothAdapter.STATE_ON
                        ) {
                            Log.d(TAG, "Bluetooth state changed ($state) -> recovering audio route")
                            triggerRecovery(true, state == BluetoothAdapter.STATE_OFF)
                        }
                    }
                    BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED -> {
                        val connState = intent.getIntExtra(
                            BluetoothAdapter.EXTRA_CONNECTION_STATE,
                            BluetoothAdapter.STATE_DISCONNECTED,
                        )
                        if (connState == BluetoothAdapter.STATE_DISCONNECTED ||
                            connState == BluetoothAdapter.STATE_CONNECTED
                        ) {
                            Log.d(TAG, "Bluetooth connection state changed ($connState) -> recovering audio route")
                            triggerRecovery(true, true)
                        }
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(broadcastReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(broadcastReceiver, filter)
            }
        } catch (_: Exception) {}

        return {
            mainHandler.removeCallbacksAndMessages(null)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && deviceCallback != null && audioManager != null) {
                try {
                    audioManager.unregisterAudioDeviceCallback(deviceCallback)
                } catch (_: Exception) {}
            }
            try {
                context.unregisterReceiver(broadcastReceiver)
            } catch (_: Exception) {}
        }
    }
}

