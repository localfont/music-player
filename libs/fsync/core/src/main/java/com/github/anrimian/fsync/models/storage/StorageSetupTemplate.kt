package com.github.anrimian.fsync.models.storage

class StorageSetupTemplate(
    val storageType: Int,
    val credentials: RemoteStorageCredentials,
    var remoteRootPath: String,
    var localRootPath: String
) {
    lateinit var accountInfo: StorageAccountInfo
    lateinit var spaceUsage: StorageSpaceUsage
}