package com.github.anrimian.musicplayer.data.utils

import android.content.Context
import android.net.Uri

fun Uri.hasPersistedReadPermission(context: Context): Boolean {
    return try {
        val persistedPermissions = context.contentResolver.persistedUriPermissions
        val hasPermission = persistedPermissions.any { it.uri == this && it.isReadPermission }
        hasPermission
    } catch (e: Exception) {
        false
    }
}