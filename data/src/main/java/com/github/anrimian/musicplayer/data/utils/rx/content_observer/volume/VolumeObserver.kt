package com.github.anrimian.musicplayer.data.utils.rx.content_observer.volume

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import com.github.anrimian.musicplayer.data.utils.rx.receivers.RxReceivers
import com.github.anrimian.musicplayer.domain.Constants
import com.github.anrimian.musicplayer.domain.models.volume.VolumeState
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.subjects.PublishSubject

object VolumeObserver {
    /**
     * Emits volume in absolute values. Has no start event
     */
    fun getVolumeObservable(context: Context, audioManager: AudioManager): Observable<Int> {
        return RxReceivers.from("android.media.VOLUME_CHANGED_ACTION", context)
            .flatMapSingle { safeGetStreamVolumeSingle(audioManager) }
            .distinctUntilChanged()
    }

    /**
     * Emits volume in state model. Has start event
     */
    fun getVolumeStateObservable(
        context: Context,
        audioManager: AudioManager,
    ): Observable<VolumeState> {
        val outputChangeSubject = PublishSubject.create<Any>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioManager.registerAudioDeviceCallback(object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
                    super.onAudioDevicesAdded(addedDevices)
                    outputChangeSubject.onNext(Constants.TRIGGER)
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
                    super.onAudioDevicesRemoved(removedDevices)
                    outputChangeSubject.onNext(Constants.TRIGGER)
                }
            }, null)
        }
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return outputChangeSubject.startWithItem(Constants.TRIGGER)
            .switchMap {
                RxReceivers.from("android.media.VOLUME_CHANGED_ACTION", context)
                    .flatMapSingle { safeGetStreamVolumeSingle(audioManager) }
                    .startWith(safeGetStreamVolumeSingle(audioManager))
                    .map { volume -> VolumeState.from(volume, maxVolume) }
            }
    }

    fun safeGetStreamVolume(audioManager: AudioManager): Int {
        return try {
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        } catch (ignored: Exception) {
            0
        }
    }

    private fun safeGetStreamVolumeSingle(audioManager: AudioManager): Single<Int> {
        return Single.create { emitter ->
            try {
                emitter.onSuccess(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
            } catch (ignored: Exception) {}
        }
    }

}
