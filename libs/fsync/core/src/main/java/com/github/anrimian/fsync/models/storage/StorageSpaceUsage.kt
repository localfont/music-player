package com.github.anrimian.fsync.models.storage

class StorageSpaceUsage(val used: Long, val total: Long)
fun unknownSpaceUsage() = StorageSpaceUsage(-1L, -1L)
fun unlimitedSpaceUsage() = StorageSpaceUsage(-1L, Long.MIN_VALUE)