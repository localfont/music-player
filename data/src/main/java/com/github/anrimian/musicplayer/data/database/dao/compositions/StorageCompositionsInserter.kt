package com.github.anrimian.musicplayer.data.database.dao.compositions

import com.github.anrimian.musicplayer.data.database.LibraryDatabase
import com.github.anrimian.musicplayer.data.database.dao.albums.AlbumsDao
import com.github.anrimian.musicplayer.data.database.dao.albums.AlbumsDaoWrapper
import com.github.anrimian.musicplayer.data.database.dao.artist.ArtistsDao
import com.github.anrimian.musicplayer.data.database.dao.artist.ArtistsDaoWrapper
import com.github.anrimian.musicplayer.data.database.dao.folders.FoldersDaoWrapper
import com.github.anrimian.musicplayer.data.database.dao.genre.GenreDao
import com.github.anrimian.musicplayer.data.database.mappers.CompositionCorruptionDetector
import com.github.anrimian.musicplayer.data.models.changes.Change
import com.github.anrimian.musicplayer.data.storage.providers.music.StorageComposition
import com.github.anrimian.musicplayer.data.storage.providers.music.StorageFullComposition
import com.github.anrimian.musicplayer.domain.models.composition.CorruptionType
import com.github.anrimian.musicplayer.domain.models.composition.InitialSource
import java.util.Date

class StorageCompositionsInserter(
    private val libraryDatabase: LibraryDatabase,
    private val compositionsDao: CompositionsDao,
    private val compositionsDaoWrapper: CompositionsDaoWrapper,
    private val foldersDaoWrapper: FoldersDaoWrapper,
    private val artistsDao: ArtistsDao,
    private val artistsDaoWrapper: ArtistsDaoWrapper,
    private val albumsDao: AlbumsDao,
    private val albumsDaoWrapper: AlbumsDaoWrapper,
    private val genresDao: GenreDao
) {

    fun applyChanges(
        addedCompositions: List<StorageFullComposition>,
        deletedCompositions: List<StorageComposition>,
        changedCompositions: List<Change<StorageComposition, StorageFullComposition>>,
    ) {
        val previousCount = compositionsDao.getCompositionsCount()

        libraryDatabase.runInTransaction {
            applyCompositionChanges(addedCompositions, deletedCompositions, changedCompositions)
        }

        if (previousCount == 0L) {
            //on first app launch room invalidation tracker can be not launched so call update manually
            compositionsDaoWrapper.launchManualUpdate()
        }
    }

    private fun applyCompositionChanges(
        compositionsToAdd: List<StorageFullComposition>,
        deletedCompositions: List<StorageComposition>,
        changedCompositions: List<Change<StorageComposition, StorageFullComposition>>,
    ) {
        insertCompositions(compositionsToAdd)
        for (composition in deletedCompositions) {
            compositionsDao.delete(composition.id)
        }
        for (change in changedCompositions) {
            handleCompositionUpdate(change)
        }
        albumsDao.deleteEmptyAlbums()
        artistsDao.deleteEmptyArtists()
        foldersDaoWrapper.deleteEmptyFolders()
        genresDao.deleteEmptyGenres()
    }

    private fun insertCompositions(compositionsToAdd: List<StorageFullComposition>) {
        //optimization with cache, ~33% faster
        val artistsCache = HashMap<String, Long>()
        val albumsCache = HashMap<String, Long>()
        val foldersCache = HashMap<String, Long>()

        for (composition in compositionsToAdd) {
            val artist = composition.artist
            val artistId = artistsDaoWrapper.getOrInsertArtist(artist, artistsCache)

            var albumId: Long? = null
            val storageAlbum = composition.storageAlbum
            if (storageAlbum != null) {
                albumId = albumsDaoWrapper.getOrInsertAlbum(
                    storageAlbum.album,
                    storageAlbum.artist,
                    artistsCache,
                    albumsCache
                )
            }

            val folderId = foldersDaoWrapper.getOrCreateFolder(composition.relativePath, foldersCache)

            //if we have not found composition - just remove not_found mark
            val id = compositionsDao.findCompositionByFileName(composition.fileName, folderId)
            if (id != null) {
                val storageId = compositionsDao.selectStorageId(id)
                val actualStorageId = composition.storageId
                if (storageId != actualStorageId) {
                    compositionsDao.updateStorageId(id, actualStorageId)
                    if (storageId == 0L) {
                        val corruptionType = compositionsDao.selectCorruptionType(id)
                        if (corruptionType == CorruptionType.NOT_FOUND || corruptionType == CorruptionType.SOURCE_NOT_FOUND) {
                            compositionsDao.setCorruptionType(null, id)
                        }
                    }
                    continue
                }
            }

            compositionsDao.insert(
                artistId,
                albumId,
                folderId,
                composition.title,
                null,
                null,
                null,
                null,
                composition.fileName,
                composition.duration,
                composition.size,
                composition.storageId,
                composition.dateAdded,
                composition.dateModified,
                Date(0),
                Date(0),
                CompositionCorruptionDetector.getCorruptionType(composition),
                InitialSource.LOCAL
            )
        }
    }

    private fun handleCompositionUpdate(change: Change<StorageComposition, StorageFullComposition>) {
        val composition = change.obj
        val oldComposition = change.old
        val compositionId = oldComposition.id

        var newAlbumName: String? = null
        var newAlbumArtist: String? = null
        val newAlbum = composition.storageAlbum
        if (newAlbum != null) {
            newAlbumName = newAlbum.album
            newAlbumArtist = newAlbum.artist
        }
        if (newAlbumName != oldComposition.album) {
            compositionsDaoWrapper.updateAlbum(compositionId, newAlbumName)
        }
        if (newAlbumArtist != oldComposition.albumArtist) {
            compositionsDaoWrapper.updateAlbumArtist(compositionId, newAlbumArtist)
        }
        val newArtist = composition.artist
        if (newArtist != oldComposition.artist) {
            compositionsDaoWrapper.updateArtist(compositionId, newArtist)
        }
        val relativePath = composition.relativePath
        if (relativePath != oldComposition.parentPath) {
            val folderId = foldersDaoWrapper.getOrCreateFolder(relativePath, HashMap())
            compositionsDao.updateFolderId(compositionId, folderId)
        }

        compositionsDao.update(
            composition.title,
            composition.fileName,
            composition.duration,
            composition.size,
            composition.dateModified,
            composition.storageId,
        )
    }

}