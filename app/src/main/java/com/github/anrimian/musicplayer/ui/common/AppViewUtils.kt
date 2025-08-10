package com.github.anrimian.musicplayer.ui.common

import android.annotation.SuppressLint
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.FragmentActivity
import com.github.anrimian.musicplayer.ui.common.toolbar.AdvancedToolbar
import io.reactivex.rxjava3.core.Observable

@SuppressLint("CheckResult")
fun AdvancedToolbar.attachBackPressedCallback(activity: FragmentActivity) {
    val onBackPressedCallback = object : OnBackPressedCallback(isInSearchMode() || isInActiveSearchMode()) {
        override fun handleOnBackPressed() {
            if (isInActionMode()) {
                invokeActionModeExit()
                return
            }
            if (isInActiveSearchMode()) {
                invokeSearchModeExit()
                return
            }
        }
    }
    activity.onBackPressedDispatcher.addCallback(onBackPressedCallback)
    Observable.combineLatest(
        getActiveSearchModeObservable(),
        getSelectionModeObservable()
    ) { isSearchActive, isActionModeActive ->
        isSearchActive || isActionModeActive
    }.subscribe { isAnyModeActive -> onBackPressedCallback.isEnabled = isAnyModeActive }
}