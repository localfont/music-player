package com.github.anrimian.musicplayer.data.database.dao.compositions

import androidx.collection.LongSparseArray
import androidx.sqlite.db.SimpleSQLiteQuery
import com.github.anrimian.musicplayer.data.database.LibraryDatabase
import com.github.anrimian.musicplayer.data.database.dao.albums.AlbumsDao
import com.github.anrimian.musicplayer.data.database.dao.artist.ArtistsDao
import com.github.anrimian.musicplayer.data.database.dao.folders.FoldersDao
import com.github.anrimian.musicplayer.data.database.dao.genre.GenreDao
import com.github.anrimian.musicplayer.data.database.utils.DatabaseUtils
import com.github.anrimian.musicplayer.data.models.composition.ExternalComposition
import com.github.anrimian.musicplayer.data.models.exceptions.CompositionNotFoundException
import com.github.anrimian.musicplayer.data.repositories.library.edit.models.CompositionMoveData
import com.github.anrimian.musicplayer.data.repositories.scanner.storage.playlists.m3uparser.PlayListEntry
import com.github.anrimian.musicplayer.data.storage.providers.music.StorageComposition
import com.github.anrimian.musicplayer.data.utils.collections.AndroidCollectionUtils
import com.github.anrimian.musicplayer.domain.Constants
import com.github.anrimian.musicplayer.domain.models.composition.Composition
import com.github.anrimian.musicplayer.domain.models.composition.CorruptionType
import com.github.anrimian.musicplayer.domain.models.composition.DeletedComposition
import com.github.anrimian.musicplayer.domain.models.composition.FullComposition
import com.github.anrimian.musicplayer.domain.models.composition.InitialSource
import com.github.anrimian.musicplayer.domain.models.composition.tags.AudioFileInfo
import com.github.anrimian.musicplayer.domain.models.order.Order
import com.github.anrimian.musicplayer.domain.models.order.OrderType
import com.github.anrimian.musicplayer.domain.models.sync.FileKey
import com.github.anrimian.musicplayer.domain.models.utils.CompositionHelper
import com.github.anrimian.musicplayer.domain.utils.FileUtils
import com.github.anrimian.musicplayer.domain.utils.TextUtils
import com.github.anrimian.musicplayer.domain.utils.getOrPut
import com.github.anrimian.musicplayer.domain.utils.rx.firstListItemOrComplete
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.subjects.BehaviorSubject
import java.util.Date

class CompositionsDaoWrapper(
    private val libraryDatabase: LibraryDatabase,
    private val artistsDao: ArtistsDao,
    private val compositionsDao: CompositionsDao,
    private val albumsDao: AlbumsDao,
    private val genreDao: GenreDao,
    private val foldersDao: FoldersDao
) {

    private val updateSubject = BehaviorSubject.createDefault(Constants.TRIGGER)

    fun getCompositionObservable(id: Long, useFileName: Boolean): Observable<Composition> {
        val query = CompositionsDao.getCompositionQuery(useFileName)
        query.append(" WHERE id = ? LIMIT 1")
        val sqlQuery = SimpleSQLiteQuery(
            query.toString(),
            arrayOf(id.toString())
        )
        return compositionsDao.getCompositionsObservable(sqlQuery)
            .firstListItemOrComplete()
    }

    fun getFullCompositionObservable(id: Long): Observable<FullComposition> {
        return compositionsDao.getFullCompositionObservable(id)
            .firstListItemOrComplete()
    }

    fun getLyricsObservable(compositionId: Long): Observable<String> {
        return compositionsDao.getLyricsObservable(compositionId)
    }

    fun getFullComposition(id: Long): FullComposition {
        return compositionsDao.getFullComposition(id)
    }

    fun getCompositionMoveData(id: Long): CompositionMoveData {
        return compositionsDao.getCompositionMoveData(id)
    }

    fun getCompositionsMoveData(ids: List<Long>): List<CompositionMoveData> {
        return ids.map(compositionsDao::getCompositionMoveData)
    }

    fun getAllObservable(
        order: Order,
        useFileName: Boolean,
        searchText: String?
    ): Observable<List<Composition>> {
        val query = CompositionsDao.getCompositionQuery(useFileName)
        query.append(CompositionsDao.getSearchWhereQuery(useFileName))
        query.append(getOrderQuery(order))
        val sqlQuery = SimpleSQLiteQuery(
            query.toString(),
            DatabaseUtils.getSearchArgs(searchText, 3)
        )
        return updateSubject.switchMap { compositionsDao.getCompositionsObservable(sqlQuery) }
    }

    fun launchManualUpdate() {
        updateSubject.onNext(Constants.TRIGGER)
    }

    fun getCompositionsInFolderObservable(
        folderId: Long?,
        order: Order,
        useFileName: Boolean,
        searchText: String?
    ): Observable<List<Composition>> {
        val query = CompositionsDao.getCompositionQuery(useFileName)
        query.append(CompositionsDao.getSearchWhereQuery(useFileName))
        query.append(" AND (? IS NOT NULL OR ")
        query.append("(folderId = ")
        query.append(folderId)
        query.append(" OR (folderId IS NULL AND ")
        query.append(folderId)
        query.append(" IS NULL)))")
        query.append(getOrderQuery(order))
        val sqlQuery = SimpleSQLiteQuery(
            query.toString(),
            DatabaseUtils.getSearchArgs(searchText, 4)
        )
        return compositionsDao.getCompositionsInFolderObservable(sqlQuery)
    }

    fun getAllCompositionsInFolder(
        parentFolderId: Long?,
        useFileName: Boolean
    ): List<Composition> {
        var query = FoldersDao.getRecursiveFolderQuery(parentFolderId)
        query += CompositionsDao.getCompositionQuery(useFileName)
        query += " WHERE folderId IN (SELECT childFolderId FROM allChildFolders) "
        query += "OR folderId = "
        query += parentFolderId
        val sqlQuery = SimpleSQLiteQuery(query)
        return compositionsDao.executeQuery(sqlQuery)
    }

    fun getAllCompositionsInFolder(parentFolderId: Long?): List<CompositionMoveData> {
        var query = FoldersDao.getRecursiveFolderQuery(parentFolderId)
        query += CompositionsDao.getMoveCompositionQuery()
        query += " WHERE folderId IN (SELECT childFolderId FROM allChildFolders) "
        query += "OR folderId = "
        query += parentFolderId
        val sqlQuery = SimpleSQLiteQuery(query)
        return compositionsDao.executeQueryForMove(sqlQuery)
    }

    fun getCompositionsInFolder(
        parentFolderId: Long?,
        order: Order,
        useFileName: Boolean
    ): List<Composition> {
        val query = CompositionsDao.getCompositionQuery(useFileName)
        query.append(" WHERE folderId = ")
        query.append(parentFolderId)
        query.append(" OR (folderId IS NULL AND ")
        query.append(parentFolderId)
        query.append(" IS NULL)")
        query.append(getOrderQuery(order))
        val sqlQuery = SimpleSQLiteQuery(query.toString())
        return compositionsDao.executeQuery(sqlQuery)
    }

    fun selectAllAsStorageCompositions(): LongSparseArray<StorageComposition> {
        val result = LongSparseArray<StorageComposition>()
        val pageSize = 1000
        var index = 0
        var pageResult: LongSparseArray<StorageComposition>
        do {
            pageResult = AndroidCollectionUtils.mapToSparseArray(
                compositionsDao.selectAllAsStorageCompositions(pageSize, index),
                StorageComposition::getStorageId
            )
            result.putAll(pageResult)
            index++
        } while (pageResult.size() == pageSize)
        return result
    }

    fun getStorageId(compositionId: Long): Long {
        val storageId = compositionsDao.getStorageId(compositionId)
            ?: throw CompositionNotFoundException("composition not found")
        return storageId
    }

    fun selectStorageId(compositionId: Long): Maybe<Long> {
        return Maybe.fromCallable { compositionsDao.getStorageId(compositionId) }
    }

    fun delete(id: Long) {
        libraryDatabase.runInTransaction {
            compositionsDao.delete(id)
            albumsDao.deleteEmptyAlbums()
            artistsDao.deleteEmptyArtists()
            genreDao.deleteEmptyGenres()
            foldersDao.deleteFoldersWithoutContainment()
        }
    }

    fun deleteAll(ids: Array<Long>) {
        libraryDatabase.runInTransaction {
            compositionsDao.delete(ids)
            albumsDao.deleteEmptyAlbums()
            artistsDao.deleteEmptyArtists()
            genreDao.deleteEmptyGenres()
            foldersDao.deleteFoldersWithoutContainment()
        }
    }

    fun deleteCompositionsWithoutStorageId() {
        compositionsDao.deleteCompositionsWithoutStorageId()
    }

    fun clearAllPathModifyTime() {
        compositionsDao.clearAllPathModifyTime()
    }

    fun clearPathModifyTime(keys: Iterable<FileKey>) {
        libraryDatabase.runInTransaction {
            for (key in keys) {
                val id = findCompositionIdByFilePath(key.parentPath, key.name) ?: continue
                compositionsDao.clearPathModifyTime(id)
            }
        }
    }

    fun getPathModifyTime(key: FileKey): Long? {
        val id = findCompositionIdByFilePath(key.parentPath, key.name) ?: return null
        return compositionsDao.getPathModifyTime(id)
    }

    fun updateFolderId(id: Long, folderId: Long?) {
        compositionsDao.updateFolderId(id, folderId)
    }

    fun replaceFolderId(fromFolderId: Long, folderId: Long?) {
        compositionsDao.replaceFolderId(fromFolderId, folderId)
    }

    fun updateStorageId(id: Long, storageId: Long?) {
        compositionsDao.updateStorageId(id, storageId)
    }

    fun updateAlbum(compositionId: Long, albumName: String?) {
        libraryDatabase.runInTransaction {
            var artistId: Long? = null
            val existsAlbumId = compositionsDao.getAlbumId(compositionId)
            if (existsAlbumId != null) {
                artistId = albumsDao.getArtistId(existsAlbumId)
            }
            if (artistId == null) {
                artistId = compositionsDao.getArtistId(compositionId)
            }

            // find new album by artist and name from albums
            var albumId = albumsDao.findAlbum(artistId, albumName)

            // if album not exists - create album
            if (albumId == null && !TextUtils.isEmpty(albumName)) {
                albumId = albumsDao.insertAlbum(artistId, albumName)
            }

            // set new albumId
            val oldAlbumId = compositionsDao.getAlbumId(compositionId)
            compositionsDao.updateAlbum(compositionId, albumId)
            if (oldAlbumId != null) {
                albumsDao.deleteEmptyAlbum(oldAlbumId)
            }
        }
    }

    fun updateArtist(id: Long, authorName: String?) {
        libraryDatabase.runInTransaction {
            // 1) find new artist by name from artists
            var artistId = artistsDao.findArtistIdByName(authorName)

            // 2) if artist not exists - create artist
            if (artistId == null && !TextUtils.isEmpty(authorName)) {
                artistId = artistsDao.insertArtist(authorName)
            }
            // 3) set new artistId
            val oldArtistId = compositionsDao.getArtistId(id)
            compositionsDao.updateArtist(id, artistId)

            // 4) if OLD artist exists and has no references - delete him
            if (oldArtistId != null) {
                artistsDao.deleteEmptyArtist(oldArtistId)
            }
        }
    }

    fun updateAlbumArtist(id: Long, artistName: String?) {
        libraryDatabase.runInTransaction {
            //find album
            val albumId = compositionsDao.getAlbumId(id) ?: return@runInTransaction
            // 1) find new artist by name from artists
            var artistId = artistsDao.findArtistIdByName(artistName)

            // 2) if artist not exists - create artist
            if (artistId == null && !TextUtils.isEmpty(artistName)) {
                artistId = artistsDao.insertArtist(artistName)
            }

            val albumEntity = albumsDao.getAlbumEntity(albumId)
            val oldArtistId = albumEntity.artistId

            //find new album with author id and name
            var newAlbumId = albumsDao.findAlbum(artistId, albumEntity.name)

            //if not exists, create
            if (newAlbumId == null) {
                newAlbumId = albumsDao.insertAlbum(artistId, albumEntity.name)
            }
            //set new album to composition
            compositionsDao.setAlbumId(id, newAlbumId)

            //if album is empty, delete
            albumsDao.deleteEmptyAlbum(albumId)

            // 4) if OLD artist exists and has no references - delete him
            if (oldArtistId != null) {
                artistsDao.deleteEmptyArtist(oldArtistId)
            }
        }
    }

    fun setCompositionGenres(compositionId: Long, genres: Array<String>) {
        genreDao.removeCompositionGenres(compositionId)
        for (genre in genres) {
            var genreId = genreDao.findGenre(genre)
            if (genreId == null) {
                genreId = genreDao.insertGenre(genre)
            }
            genreDao.insertGenreEntry(compositionId, genreId)
        }
    }

    fun updateTitle(id: Long, title: String?) {
        libraryDatabase.runInTransaction {
            compositionsDao.updateTitle(id, title)
            compositionsDao.setUpdateTime(id, Date())
        }
    }

    fun updateDuration(id: Long, duration: Long) {
        libraryDatabase.runInTransaction {
            compositionsDao.updateDuration(id, duration)
            compositionsDao.setUpdateTime(id, Date())
        }
    }

    fun updateTrackNumber(id: Long, trackNumber: Long?) {
        libraryDatabase.runInTransaction {
            compositionsDao.updateTrackNumber(id, trackNumber)
            compositionsDao.setUpdateTime(id, Date())
        }
    }

    fun updateDiscNumber(id: Long, discNumber: Long?) {
        libraryDatabase.runInTransaction {
            compositionsDao.updateDiscNumber(id, discNumber)
            compositionsDao.setUpdateTime(id, Date())
        }
    }

    fun updateComment(id: Long, text: String?) {
        libraryDatabase.runInTransaction {
            compositionsDao.updateComment(id, text)
            compositionsDao.setUpdateTime(id, Date())
        }
    }

    fun updateLyrics(id: Long, text: String?) {
        libraryDatabase.runInTransaction {
            compositionsDao.updateLyrics(id, text)
            compositionsDao.setUpdateTime(id, Date())
        }
    }

    fun updateFileSize(id: Long, fileSize: Long) {
        libraryDatabase.runInTransaction {
            compositionsDao.updateFileSize(id, fileSize)
            compositionsDao.setUpdateTime(id, Date())
        }
    }

    fun setModifyTimeToCurrent(id: Long) {
        compositionsDao.setUpdateTime(id, Date())
    }

    fun setCompositionPathModifyTime(id: Long, time: Long?) {
        compositionsDao.setPathModifyTime(id, time)
    }

    fun updateCoverModifyTimeAndSize(id: Long, size: Long, date: Date) {
        compositionsDao.setCoverModifyTimeAndSize(id, size, date)
    }

    fun updateCoverModifyTime(id: Long, time: Long) {
        compositionsDao.setCoverModifyTime(id, time)
    }

    fun updateCompositionFileName(id: Long, fileName: String) {
        compositionsDao.updateCompositionFileName(id, fileName)
    }

    fun setCorruptionType(corruptionType: CorruptionType?, id: Long) {
        compositionsDao.setCorruptionType(corruptionType, id)
    }

    fun selectNextCompositionsToScan(
        lastCompleteScanTime: Long,
        filesCount: Int
    ): Single<List<FullComposition>> {
        return compositionsDao.selectNextCompositionsToScan(lastCompleteScanTime, filesCount)
    }

    fun setCompositionLastFileScanTime(composition: FullComposition, time: Date) {
        compositionsDao.setCompositionLastFileScanTime(composition.id, time)
    }

    fun cleanLastFileScanTime() {
        compositionsDao.cleanLastFileScanTime()
    }

    fun updateCompositionsByFileInfo(
        scannedCompositions: List<Pair<FullComposition, AudioFileInfo>>,
        allCompositions: List<FullComposition>
    ) {
        libraryDatabase.runInTransaction {
            for ((first, second) in scannedCompositions) {
                updateCompositionByFileInfo(first, second)
            }
            val currentDate = Date()
            for (composition in allCompositions) {
                setCompositionLastFileScanTime(composition, currentDate)
            }
        }
    }

    fun updateCompositionByFileInfo(
        composition: FullComposition,
        fileInfo: AudioFileInfo
    ): Boolean {
        return libraryDatabase.runInTransaction<Boolean> {
            val id = composition.id
            val tags = fileInfo.audioTags

            var wasChanges = false

            val tagTitle = tags.title
            if (!TextUtils.isEmpty(tagTitle) && composition.title != tagTitle) {
                compositionsDao.updateTitle(id, tagTitle)
                wasChanges = true
            }

            val tagArtist = tags.artist
            if (!TextUtils.isEmpty(tagArtist) && composition.artist != tagArtist) {
                updateArtist(id, tagArtist)
                wasChanges = true
            }

            val tagAlbum = tags.album
            if (!TextUtils.isEmpty(tagAlbum) && composition.album != tagAlbum) {
                updateAlbum(id, tagAlbum)
                wasChanges = true
            }

            val tagAlbumArtist = tags.albumArtist
            if (!TextUtils.isEmpty(tagAlbumArtist) && composition.albumArtist != tagAlbumArtist) {
                updateAlbumArtist(id, tagAlbumArtist)
                wasChanges = true
            }

            //if we just update duration, we'll lose milliseconds part. So just update 0 values
            val tagDuration = tags.durationSeconds
            val duration = composition.duration
            if (duration == 0L && tagDuration != 0) {
                val tagDurationMillis = tagDuration * 1000L
                compositionsDao.updateDuration(id, tagDurationMillis)
                if (compositionsDao.selectCorruptionType(id) == CorruptionType.UNKNOWN) {
                    compositionsDao.setCorruptionType(null, id)
                }
                wasChanges = true
            }

            val tagTrackNumber = tags.trackNumber
            if (composition.trackNumber != tagTrackNumber) {
                compositionsDao.updateTrackNumber(id, tagTrackNumber)
                wasChanges = true
            }

            val tagDiscNumber = tags.discNumber
            if (composition.discNumber != tagDiscNumber) {
                compositionsDao.updateDiscNumber(id, tagDiscNumber)
                wasChanges = true
            }

            val tagComment = tags.comment
            if (!TextUtils.isEmpty(tagComment) && composition.comment != tagComment) {
                compositionsDao.updateComment(id, tagComment)
                wasChanges = true
            }

            val tagLyrics = tags.lyrics
            if (!TextUtils.isEmpty(tagLyrics) && composition.lyrics != tagLyrics) {
                compositionsDao.updateLyrics(id, tagLyrics)
                wasChanges = true
            }
            val tagGenres = tags.genres
            val compositionGenres = CompositionHelper.splitGenres(composition.genres)
            if (!compositionGenres.contentEquals(tagGenres)) {
                setCompositionGenres(id, tagGenres)
                wasChanges = true
            }

            val fileSize = fileInfo.fileSize
            if (composition.size != fileSize) {
                compositionsDao.updateFileSize(id, fileSize)
                wasChanges = true
            }
            return@runInTransaction wasChanges
        }
    }

    fun getFolderId(compositionId: Long): Long? {
        return compositionsDao.getFolderId(compositionId)
    }

    fun getAllAsExternalCompositions(parentPath: String?): List<ExternalComposition> {
        val folderId: Long?
        if (TextUtils.isEmpty(parentPath)) {
            folderId = null
        } else {
            folderId = findFolderId(parentPath)
            if (folderId == null) {
                return emptyList()
            }
        }
        return compositionsDao.getAllAsExternalCompositions(folderId)
    }

    fun findCompositionIdByFilePath(parentPath: String?, fileName: String): Long? {
        val folderId = findFolderId(parentPath)
        return compositionsDao.findCompositionByFileName(fileName, folderId)
    }

    fun requireCompositionIdByFilePath(parentPath: String?, fileName: String): Long {
        val id = findCompositionIdByFilePath(parentPath, fileName)
            ?: throw CompositionNotFoundException("$fileName not found")
        return id
    }

    fun getCompositionNameAndPath(id: Long): FileKey {
        val fileName = compositionsDao.getCompositionFileName(id)
            ?: throw CompositionNotFoundException("composition not found")
        val parentPath = compositionsDao.getCompositionParentPath(id)
        return FileKey(fileName, parentPath)
    }

    fun getCompositionSize(id: Long): Long {
        return compositionsDao.getCompositionSize(id)
    }

    fun updateCompositionIdsInitialSource(
        compositionsIds: List<Long>,
        initialSource: InitialSource,
        updateFrom: InitialSource
    ) {
        libraryDatabase.runInTransaction {
            for (id in compositionsIds) {
                updateCompositionInitialSource(id, initialSource, updateFrom)
            }
        }
    }

    fun updateCompositionInitialSource(
        compositionId: Long,
        initialSource: InitialSource,
        updateFrom: InitialSource
    ) {
        compositionsDao.updateCompositionInitialSource(compositionId, initialSource, updateFrom)
    }

    fun selectDeletedComposition(
        ids: Array<Long>,
        useFileName: Boolean
    ): List<DeletedComposition> {
        val query = CompositionsDao.getDeletedCompositionQuery(useFileName, ids.size).toString()
        val sqlQuery = SimpleSQLiteQuery(query, ids)
        return compositionsDao.selectDeletedComposition(sqlQuery)
    }

    fun selectDeletedComposition(compositionId: Long, useFileName: Boolean): DeletedComposition {
        return selectDeletedComposition(arrayOf(compositionId), useFileName)[0]
    }

    fun getCompositionIds(
        fileEntries: List<PlayListEntry>,
        pathIdMapCache: HashMap<String, Long>
    ): List<Long> {
        return fileEntries.mapNotNull { entry ->
            val path = entry.filePath
            return@mapNotNull pathIdMapCache.getOrPut(path) {
                val parentPath = FileUtils.getParentDirPath(path)
                val fileName = FileUtils.getFileName(path)
                val nameIds = compositionsDao.findCompositionsByFileName(fileName)
                for (nameId in nameIds) {
                    val dbPath = compositionsDao.getCompositionParentPath(nameId)
                    if (parentPath.endsWith(dbPath)) {
                        return@getOrPut nameId
                    }
                }
                return@getOrPut null
            }
        }
    }

    fun getCompositionsInFolder(relativePath: String?): List<FileKey> {
        val folderId = findFolderId(relativePath) ?: return emptyList()
        return getCompositionsInFolder(folderId)
    }

    fun getCompositionsInFolder(folderId: Long?): List<FileKey> {
        val compositions = getAllCompositionsInFolder(folderId)
        return compositions.map { c -> FileKey(c.fileName, c.parentPath) }
    }

    fun findFolderId(filePath: String?): Long? {
        return findFolderId(filePath, null)
    }

    private fun findFolderId(filePath: String?, parentId: Long?): Long? {
        if (filePath.isNullOrEmpty()) {
            return parentId
        }

        val folderId: Long?
        val delimiterIndex = filePath.indexOf('/')
        if (delimiterIndex == -1) {
            folderId = foldersDao.getFolderByName(parentId, filePath)
        } else {
            val folderName = filePath.substring(0, delimiterIndex)
            val parentFolderId = foldersDao.getFolderByName(parentId, folderName) ?: return null
            val folderPath = filePath.substring(delimiterIndex + 1)
            folderId = findFolderId(folderPath, parentFolderId)
        }
        return folderId
    }

    private fun getOrderQuery(order: Order): String {
        val orderQuery = StringBuilder(" ORDER BY ")
        when (order.orderType) {
            OrderType.NAME -> {
                orderQuery.append("CASE WHEN title IS NULL OR title = '' THEN fileName ELSE title END")
            }
            OrderType.FILE_NAME -> orderQuery.append("fileName")
            OrderType.ADD_TIME -> orderQuery.append("dateAdded")
            OrderType.SIZE -> orderQuery.append("size")
            OrderType.DURATION -> orderQuery.append("duration")
            else -> throw IllegalStateException("unknown order type: $order")
        }
        orderQuery.append(" ")
        orderQuery.append(if (order.isReversed) "DESC" else "ASC")
        return orderQuery.toString()
    }

}
