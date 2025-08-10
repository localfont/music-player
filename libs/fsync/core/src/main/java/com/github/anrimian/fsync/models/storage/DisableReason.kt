package com.github.anrimian.fsync.models.storage

enum class DisableReason {
    MANUAL,
    LOGOUT,
    REMOTE_VERSION_IS_TOO_HIGH,
    SPACE_IS_FULL,
    WRONG_FILE_PATH,
}