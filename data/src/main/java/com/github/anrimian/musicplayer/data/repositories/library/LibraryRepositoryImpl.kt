package com.github.anrimian.musicplayer.data.repositories.library

import com.github.anrimian.musicplayer.data.database.dao.albums.AlbumsDaoWrapper
import com.github.anrimian.musicplayer.data.database.dao.artist.ArtistsDaoWrapper
import com.github.anrimian.musicplayer.data.database.dao.compositions.CompositionsDaoWrapper
import com.github.anrimian.musicplayer.data.database.dao.folders.FoldersDaoWrapper
import com.github.anrimian.musicplayer.data.database.dao.genre.GenresDaoWrapper
import com.github.anrimian.musicplayer.data.database.dao.ignoredfolders.IgnoredFoldersDao
import com.github.anrimian.musicplayer.data.storage.files.StorageFilesDataSource
import com.github.anrimian.musicplayer.domain.models.albums.Album
import com.github.anrimian.musicplayer.domain.models.albums.AlbumComposition
import com.github.anrimian.musicplayer.domain.models.artist.Artist
import com.github.anrimian.musicplayer.domain.models.composition.Composition
import com.github.anrimian.musicplayer.domain.models.composition.CorruptionType
import com.github.anrimian.musicplayer.domain.models.composition.DeletedComposition
import com.github.anrimian.musicplayer.domain.models.composition.FullComposition
import com.github.anrimian.musicplayer.domain.models.folders.FileSource
import com.github.anrimian.musicplayer.domain.models.folders.FolderFileSource
import com.github.anrimian.musicplayer.domain.models.folders.FolderInfo
import com.github.anrimian.musicplayer.domain.models.folders.IgnoredFolder
import com.github.anrimian.musicplayer.domain.models.genres.Genre
import com.github.anrimian.musicplayer.domain.models.sync.FileKey
import com.github.anrimian.musicplayer.domain.repositories.LibraryRepository
import com.github.anrimian.musicplayer.domain.repositories.MediaScannerRepository
import com.github.anrimian.musicplayer.domain.repositories.SettingsRepository
import com.github.anrimian.musicplayer.domain.utils.ListUtils
import com.github.anrimian.musicplayer.domain.utils.TextUtils
import com.github.anrimian.musicplayer.domain.utils.rx.collectIntoList
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.core.Single
import java.util.LinkedList

/**
 * Created on 24.10.2017.
 */
class LibraryRepositoryImpl(
    private val storageFilesDataSource: StorageFilesDataSource,
    private val compositionsDao: CompositionsDaoWrapper,
    private val artistsDao: ArtistsDaoWrapper,
    private val albumsDao: AlbumsDaoWrapper,
    private val genresDao: GenresDaoWrapper,
    private val foldersDao: FoldersDaoWrapper,
    private val ignoredFoldersDao: IgnoredFoldersDao,
    private val settingsPreferences: SettingsRepository,
    private val mediaScannerRepository: MediaScannerRepository,
    private val scheduler: Scheduler
) : LibraryRepository {

    override fun getAllCompositionsObservable(searchText: String?): Observable<List<Composition>> {
        return settingsPreferences.compositionsOrderObservable
            .switchMap { order ->
                settingsPreferences.displayFileNameObservable
                    .switchMap { useFileName ->
                        compositionsDao.getAllObservable(order, useFileName, searchText)
                    }
            }
    }

    override fun getCompositionObservable(id: Long): Observable<Composition> {
        return settingsPreferences.displayFileNameObservable
            .switchMap { useFileName -> compositionsDao.getCompositionObservable(id, useFileName) }
    }

    override fun getFullCompositionObservable(id: Long): Observable<FullComposition> {
        return compositionsDao.getFullCompositionObservable(id)
    }

    override fun getLyricsObservable(id: Long): Observable<String> {
        return compositionsDao.getLyricsObservable(id)
    }

    override fun writeErrorAboutComposition(
        corruptionType: CorruptionType?,
        composition: Composition
    ): Completable {
        return Completable.fromAction {
            compositionsDao.setCorruptionType(corruptionType, composition.id)
        }.subscribeOn(scheduler)
    }

    override fun deleteComposition(composition: Composition): Single<DeletedComposition> {
        return Single.fromCallable {
            val id = composition.id
            var deletedComposition = compositionsDao.selectDeletedComposition(
                id,
                settingsPreferences.isDisplayFileNameEnabled
            )
            deletedComposition = storageFilesDataSource.deleteCompositionFile(deletedComposition)
            compositionsDao.delete(id)
            return@fromCallable deletedComposition
        }.subscribeOn(scheduler)
    }

    override fun deleteCompositions(compositions: List<Composition>): Single<List<DeletedComposition>> {
        return Single.fromCallable {
            val ids = ListUtils.mapToLongArray(compositions, Composition::id)
            var deletedCompositions = compositionsDao.selectDeletedComposition(
                ids,
                settingsPreferences.isDisplayFileNameEnabled
            )
            deletedCompositions = storageFilesDataSource.deleteCompositionFiles(
                deletedCompositions,
                compositions
            )
            compositionsDao.deleteAll(ids)
            return@fromCallable deletedCompositions
        }.subscribeOn(scheduler)
    }

    override fun getFoldersInFolder(
        folderId: Long?,
        searchQuery: String?
    ): Observable<List<FileSource>> {
        return settingsPreferences.folderOrderObservable
            .switchMap { order ->
                settingsPreferences.displayFileNameObservable
                    .switchMap { useFileName ->
                        foldersDao.getFilesObservable(folderId, order, useFileName, searchQuery)
                    }
            }
    }

    override fun getFolderObservable(folderId: Long): Observable<FolderInfo> {
        return foldersDao.getFolderObservable(folderId)
    }

    override fun getAllCompositionsInFolder(folderId: Long?): Single<List<Composition>> {
        return Single.fromCallable { selectAllCompositionsInFolder(folderId) }
            .subscribeOn(scheduler)
    }

    override fun getAllCompositionsInFolders(fileSources: Iterable<FileSource>): Single<List<Composition>> {
        return foldersDao.extractAllCompositionsFromFiles(
            fileSources,
            settingsPreferences.folderOrder,
            settingsPreferences.isDisplayFileNameEnabled
        ).subscribeOn(scheduler)
    }

    override fun deleteFolder(folder: FolderFileSource): Single<List<DeletedComposition>> {
        return Single.fromCallable {
            val compositions = compositionsDao.getAllCompositionsInFolder(
                folder.id,
                settingsPreferences.isDisplayFileNameEnabled
            )
            val ids = ListUtils.mapToLongArray(compositions, Composition::id)
            var deletedCompositions = compositionsDao.selectDeletedComposition(
                ids,
                settingsPreferences.isDisplayFileNameEnabled
            )
            deletedCompositions = storageFilesDataSource.deleteCompositionFiles(
                deletedCompositions,
                folder
            )
            foldersDao.deleteFolder(folder.id, ids)
            return@fromCallable deletedCompositions
        }.subscribeOn(scheduler)
    }

    override fun deleteFolders(folders: List<FileSource>): Single<List<DeletedComposition>> {
        return foldersDao.extractAllCompositionsFromFiles(folders)
            .map { idList ->
                val ids = idList.toTypedArray()
                var deletedCompositions = compositionsDao.selectDeletedComposition(
                    ids,
                    settingsPreferences.isDisplayFileNameEnabled
                )
                deletedCompositions = storageFilesDataSource.deleteCompositionFiles(
                    deletedCompositions,
                    folders
                )
                foldersDao.deleteFolders(extractFolderIds(folders), ids)
                return@map deletedCompositions
            }.subscribeOn(scheduler)
    }

    override fun getAllParentFolders(folderId: Long?): Single<List<Long>> {
        return Single.fromCallable { foldersDao.getAllParentFoldersId(folderId) }
            .subscribeOn(scheduler)
    }

    override fun getAllParentFoldersForComposition(compositionId: Long): Single<List<Long>> {
        return Single.fromCallable{
            val folderId = compositionsDao.getFolderId(compositionId)
            foldersDao.getAllParentFoldersId(folderId)
        }.subscribeOn(scheduler)
    }

    override fun getFolderNamesInPath(path: String?): Single<List<String>> {
        return Single.fromCallable {
            val folderId: Long?
            if (TextUtils.isEmpty(path)) {
                folderId = null
            } else {
                folderId = compositionsDao.findFolderId(path)
                if (folderId == null) {
                    return@fromCallable emptyList<String>()
                }
            }
            return@fromCallable foldersDao.getFolderNamesInFolder(folderId)
        }.subscribeOn(scheduler)
    }

    override fun getArtistsObservable(searchText: String?): Observable<List<Artist>> {
        return settingsPreferences.artistsOrderObservable
            .switchMap { order -> artistsDao.getAllObservable(order, searchText) }
    }

    override fun getAllCompositionIdsByArtists(artistId: Long): Single<List<Long>> {
        return artistsDao.getAllCompositionIdsByArtist(artistId)
            .subscribeOn(scheduler)
    }

    override fun getAllCompositionIdsByArtists(artists: Iterable<Artist>): Single<List<Long>> {
        return Observable.fromIterable(artists)
            .flatMapSingle { artist -> artistsDao.getAllCompositionIdsByArtist(artist.id) }
            .collectIntoList(ArrayList<Long>::addAll)
            .subscribeOn(scheduler)
    }

    override fun getAllCompositionsByArtists(artists: Iterable<Artist>): Single<List<Composition>> {
        return Observable.fromIterable(artists)
            .map { artist ->
                artistsDao.getAllCompositionsByArtist(
                    artist.id,
                    settingsPreferences.isDisplayFileNameEnabled
                )
            }
            .collectIntoList(ArrayList<Composition>::addAll)
            .subscribeOn(scheduler)
    }

    override fun getAllCompositionsByArtistIds(artists: Iterable<Long>): Single<List<Composition>> {
        return Observable.fromIterable(artists)
            .map { artisId ->
                artistsDao.getAllCompositionsByArtist(
                    artisId,
                    settingsPreferences.isDisplayFileNameEnabled
                )
            }
            .collectIntoList(ArrayList<Composition>::addAll)
            .subscribeOn(scheduler)
    }

    override fun getCompositionsByArtist(artistId: Long): Observable<List<Composition>> {
        return settingsPreferences.displayFileNameObservable
            .switchMap { useFileName ->
                artistsDao.getCompositionsByArtistObservable(artistId, useFileName)
            }
    }

    override fun getArtistObservable(artistId: Long): Observable<Artist> {
        return artistsDao.getArtistObservable(artistId)
    }

    override fun getAllAlbumsForArtist(artistId: Long): Observable<List<Album>> {
        return albumsDao.getAllAlbumsForArtistObservable(artistId)
    }

    override fun getAuthorNames(): Single<Array<String>> {
        return Single.fromCallable { artistsDao.authorNames }
            .subscribeOn(scheduler)
    }

    override fun getAlbumsObservable(searchText: String?): Observable<List<Album>> {
        return settingsPreferences.albumsOrderObservable
            .switchMap { order -> albumsDao.getAllObservable(order, searchText) }
    }

    override fun getAlbumItemsObservable(albumId: Long): Observable<List<AlbumComposition>> {
        return settingsPreferences.displayFileNameObservable
            .switchMap { useFileName ->
                albumsDao.getCompositionsInAlbumObservable(albumId, useFileName)
            }
    }

    override fun getCompositionIdsInAlbum(albumId: Long): Single<List<Long>> {
        return albumsDao.getCompositionIdsInAlbum(albumId)
            .subscribeOn(scheduler)
    }

    override fun getCompositionIdsInAlbums(albums: Iterable<Album>): Single<List<Long>> {
        return Observable.fromIterable(albums)
            .flatMapSingle { album -> albumsDao.getCompositionIdsInAlbum(album.id) }
            .collectIntoList(ArrayList<Long>::addAll)
            .subscribeOn(scheduler)
    }

    override fun getCompositionsInAlbums(albums: Iterable<Album>): Single<List<Composition>> {
        return Observable.fromIterable(albums)
            .map { album ->
                albumsDao.getCompositionsInAlbum(
                    album.id,
                    settingsPreferences.isDisplayFileNameEnabled
                )
            }
            .collectIntoList(ArrayList<Composition>::addAll)
            .subscribeOn(scheduler)
    }

    override fun getCompositionsByAlbumIds(albumIds: Iterable<Long>): Single<List<Composition>> {
        return Observable.fromIterable(albumIds)
            .map { albumId ->
                albumsDao.getCompositionsInAlbum(
                    albumId,
                    settingsPreferences.isDisplayFileNameEnabled
                )
            }
            .collectIntoList(ArrayList<Composition>::addAll)
            .subscribeOn(scheduler)
    }

    override fun getAlbumObservable(albumId: Long): Observable<Album> {
        return albumsDao.getAlbumObservable(albumId)
    }

    override fun getAlbumNames(): Single<Array<String>> {
        return Single.fromCallable { albumsDao.albumNames }
            .subscribeOn(scheduler)
    }

    override fun getGenresObservable(searchText: String?): Observable<List<Genre>> {
        return settingsPreferences.genresOrderObservable
            .switchMap { order -> genresDao.getAllObservable(order, searchText) }
    }

    override fun getGenreItemsObservable(genreId: Long): Observable<List<Composition>> {
        return settingsPreferences.displayFileNameObservable
            .switchMap { useFileName ->
                genresDao.getCompositionsInGenreObservable(genreId, useFileName)
            }
    }

    override fun getCompositionIdsInGenres(genres: Iterable<Genre>): Single<List<Long>> {
        return Observable.fromIterable(genres)
            .flatMapSingle { playList -> genresDao.getAllCompositionIdsByGenre(playList.id) }
            .collectIntoList(ArrayList<Long>::addAll)
            .subscribeOn(scheduler)
    }

    override fun getCompositionsInGenres(genres: Iterable<Genre>): Single<List<Composition>> {
        return Observable.fromIterable(genres)
            .map { genre ->
                genresDao.getCompositionsInGenre(
                    genre.id,
                    settingsPreferences.isDisplayFileNameEnabled
                )
            }
            .collectIntoList(ArrayList<Composition>::addAll)
            .subscribeOn(scheduler)
    }

    override fun getCompositionsInGenresIds(genresIds: Iterable<Long>): Single<List<Composition>> {
        return Observable.fromIterable(genresIds)
            .map { genreId ->
                genresDao.getCompositionsInGenre(
                    genreId,
                    settingsPreferences.isDisplayFileNameEnabled
                )
            }
            .collectIntoList(ArrayList<Composition>::addAll)
            .subscribeOn(scheduler)
    }

    override fun getAllCompositionsByGenre(genreId: Long): Single<List<Long>> {
        return genresDao.getAllCompositionIdsByGenre(genreId)
            .subscribeOn(scheduler)
    }

    override fun getGenreNames(forCompositionId: Long): Single<Array<String>> {
        return Single.fromCallable { genresDao.getGenreNames(forCompositionId) }
            .subscribeOn(scheduler)
    }

    override fun getGenreObservable(genreId: Long): Observable<Genre> {
        return genresDao.getGenreObservable(genreId)
    }

    override fun addFolderToIgnore(folder: FolderFileSource): Single<Pair<IgnoredFolder, List<FileKey>>> {
        return Single.fromCallable {
            val folderPath = foldersDao.getFullFolderPath(folder.id)
            val compositions = compositionsDao.getCompositionsInFolder(folder.id)
            val ignoredFolder = ignoredFoldersDao.insertIgnoredFolder(folderPath)
            mediaScannerRepository.rescanStorage()
            return@fromCallable Pair(ignoredFolder, compositions)
        }.subscribeOn(scheduler)
    }

    override fun addFolderToIgnore(folder: IgnoredFolder): Single<List<FileKey>> {
        return Single.fromCallable {
            val compositions = compositionsDao.getCompositionsInFolder(folder.relativePath)
            ignoredFoldersDao.insert(folder.relativePath, folder.addDate)
            mediaScannerRepository.rescanStorage()
            return@fromCallable compositions
        }.subscribeOn(scheduler)
    }

    override fun getIgnoredFoldersObservable(): Observable<List<IgnoredFolder>> {
        return ignoredFoldersDao.getIgnoredFoldersObservable()
    }

    override fun deleteIgnoredFolder(folder: IgnoredFolder): Single<List<FileKey>> {
        return Single.fromCallable { deleteIgnoredFolder(folder.relativePath) }
            .subscribeOn(scheduler)
    }

    override fun deleteIgnoredFolder(folderRelativePath: String): List<FileKey> {
        val deletedRows = ignoredFoldersDao.deleteIgnoredFolder(folderRelativePath)
        if (deletedRows > 0) {
            mediaScannerRepository.rescanStorage()
            return compositionsDao.getCompositionsInFolder(folderRelativePath)
        }
        return emptyList()
    }

    private fun extractFolderIds(sources: List<FileSource>): List<Long> {
        val result = LinkedList<Long>()
        for (source in sources) {
            if (source is FolderFileSource) {
                result.add(source.id)
            }
        }
        return result
    }

    private fun selectAllCompositionsInFolder(folderId: Long?): List<Composition> {
        return foldersDao.getAllCompositionsInFolder(
            folderId,
            settingsPreferences.folderOrder,
            settingsPreferences.isDisplayFileNameEnabled
        )
    }
}
