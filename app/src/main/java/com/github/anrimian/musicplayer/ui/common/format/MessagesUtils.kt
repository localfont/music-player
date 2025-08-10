package com.github.anrimian.musicplayer.ui.common.format

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePaddingRelative
import com.github.anrimian.musicplayer.R
import com.github.anrimian.musicplayer.domain.models.playlist.PlayList
import com.github.anrimian.musicplayer.ui.common.snackbars.AppSnackbar
import com.github.anrimian.musicplayer.ui.utils.getDimensionPixelSize
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar

fun ViewGroup.showSnackbar(
    @StringRes text: Int,
    @BaseTransientBottomBar.Duration duration: Int = Snackbar.LENGTH_SHORT,
    anchorView: View? = null,
    actionText: String? = null,
    action: (() -> Unit)? = null,
): AppSnackbar {
    return showSnackbar(context.getString(text), duration, anchorView, actionText, action)
}

fun ViewGroup.showSnackbar(
    text: String,
    @BaseTransientBottomBar.Duration duration: Int = Snackbar.LENGTH_SHORT,
    anchorView: View? = null,
    actionText: String? = null,
    action: (() -> Unit)? = null,
): AppSnackbar {
    val snackbar = MessagesUtils.makeSnackbar(this, text, duration)
        .also { snackbar ->
            if (anchorView != null && anchorView.translationY == 0f) {
                if (measuredWidth < context.getDimensionPixelSize(R.dimen.snackbar_gravity_width_threshold)) {
                    snackbar.setAnchorView(anchorView)
                } else {
                    //if screen width is too large - place snackbar near fab
                    val snackbarView = snackbar.view
                    snackbarView.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                        gravity = Gravity.START or Gravity.BOTTOM
                    }
                    snackbarView.updatePaddingRelative(
                        start = context.getDimensionPixelSize(R.dimen.snackbar_large_margin_start),
                        end = context.getDimensionPixelSize(R.dimen.snackbar_large_margin_end),
                        bottom = context.getDimensionPixelSize(R.dimen.snackbar_large_margin_bottom),
                    )
                }
            }
            if (actionText != null) {
                snackbar.setAction(actionText, action)
            }
        }
    snackbar.show()
    return snackbar
}

fun getExportedPlaylistsMessage(context: Context, playlists: List<PlayList>): String {
    val size = playlists.size
    if (size == 1) {
        return context.getString(R.string.export_playlists_success, playlists[0].name)
    }
    return context.resources.getQuantityString(R.plurals.export_playlists_success, size, size)
}

fun getDeletedPlaylistsMessage(context: Context, playlists: Collection<PlayList>): String {
    val size = playlists.size
    if (size == 1) {
        return context.getString(R.string.play_list_deleted, playlists.first().name)
    }
    return context.resources.getQuantityString(R.plurals.delete_playlists_success, size, size)
}