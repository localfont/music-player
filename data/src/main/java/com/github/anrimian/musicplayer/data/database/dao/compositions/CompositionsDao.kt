package com.github.anrimian.musicplayer.data.database.dao.compositions

import android.annotation.SuppressLint
import androidx.room.Dao
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.util.appendPlaceholders
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.github.anrimian.musicplayer.data.database.entities.albums.AlbumEntity
import com.github.anrimian.musicplayer.data.database.entities.artist.ArtistEntity
import com.github.anrimian.musicplayer.data.database.entities.composition.CompositionEntity
import com.github.anrimian.musicplayer.data.models.composition.ExternalComposition
import com.github.anrimian.musicplayer.data.repositories.library.edit.models.CompositionMoveData
import com.github.anrimian.musicplayer.data.storage.providers.music.StorageComposition
import com.github.anrimian.musicplayer.domain.Constants
import com.github.anrimian.musicplayer.domain.models.composition.Composition
import com.github.anrimian.musicplayer.domain.models.composition.CorruptionType
import com.github.anrimian.musicplayer.domain.models.composition.DeletedComposition
import com.github.anrimian.musicplayer.domain.models.composition.FullComposition
import com.github.anrimian.musicplayer.domain.models.composition.InitialSource
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import java.util.Date

@Dao
interface CompositionsDao {
    
    @Query("""
        SELECT 
        (SELECT name FROM artists WHERE id = artistId) AS artist, 
        title AS title, 
        (SELECT name FROM albums WHERE id = albumId) AS album, 
        (SELECT name 
            FROM artists 
            WHERE id = (SELECT artistId FROM albums WHERE id = albumId)
        ) AS albumArtist, 
        (SELECT group_concat(name, '${Constants.GENRE_DIVIDER}') FROM (    
            SELECT name   
            FROM genres    
            JOIN genre_entries AS entries ON entries.compositionId = compositions.id   
            WHERE id = entries.genreId    
            ORDER BY entries.position)
        ) AS genres, 
        trackNumber AS trackNumber, 
        discNumber AS discNumber, 
        comment AS comment, 
        lyrics AS lyrics, 
        fileName AS fileName, 
        duration AS duration, 
        size AS size, 
        id AS id, 
        storageId AS storageId, 
        dateAdded AS dateAdded,     
        dateModified AS dateModified, 
        coverModifyTime AS coverModifyTime, 
        corruptionType AS corruptionType, 
        initialSource AS initialSource 
        FROM compositions 
        WHERE id = :id 
        LIMIT 1
    """)
    fun getFullCompositionObservable(id: Long): Observable<List<FullComposition>>

    @Query("SELECT IFNULL(lyrics, '') FROM compositions WHERE id = :compositionId LIMIT 1")
    fun getLyricsObservable(compositionId: Long): Observable<String>

    @Query("""
        SELECT 
        (SELECT name FROM artists WHERE id = artistId) AS artist, 
        title AS title, 
        (SELECT name FROM albums WHERE id = albumId) AS album, 
        (SELECT name 
            FROM artists 
            WHERE id = (SELECT artistId FROM albums WHERE id = albumId)
        ) AS albumArtist, 
        (SELECT group_concat(name, '${Constants.GENRE_DIVIDER}') FROM (    
            SELECT name   
            FROM genres    
            JOIN genre_entries AS entries ON entries.compositionId = compositions.id   
            WHERE id = entries.genreId    
            ORDER BY entries.position)
        ) AS genres, 
        trackNumber AS trackNumber, 
        discNumber AS discNumber, 
        comment AS comment, 
        lyrics AS lyrics, 
        fileName AS fileName, 
        duration AS duration, 
        size AS size, 
        id AS id, 
        storageId AS storageId, 
        dateAdded AS dateAdded,     
        dateModified AS dateModified, 
        coverModifyTime AS coverModifyTime, 
        corruptionType AS corruptionType, 
        initialSource AS initialSource 
        FROM compositions 
        WHERE id = :id 
        LIMIT 1
    """)
    fun getFullComposition(id: Long): FullComposition

    @Query("""
        SELECT (
            WITH RECURSIVE path(level, name, parentId) AS (                
                SELECT 0, name, parentId                 
                FROM folders                 
                WHERE id = compositions.folderId                 
                UNION ALL                 
                SELECT path.level + 1, folders.name, folders.parentId                 
                FROM folders                 
                JOIN path ON folders.id = path.parentId             
            ),             
            path_from_root AS (SELECT name FROM path ORDER BY level DESC)             
            SELECT IFNULL(group_concat(name, '/'), '') FROM path_from_root
        ) AS parentPath, 
        compositions.id AS id, 
        compositions.storageId AS storageId, 
        compositions.fileName AS fileName,
        compositions.pathModifyTime AS pathModifyTime
        FROM compositions 
        WHERE id = :id
    """)
    fun getCompositionMoveData(id: Long): CompositionMoveData

    @RawQuery(observedEntities = [CompositionEntity::class, ArtistEntity::class, AlbumEntity::class])
    fun getCompositionsObservable(query: SupportSQLiteQuery): Observable<List<Composition>>

    @RawQuery(observedEntities = [CompositionEntity::class, ArtistEntity::class, AlbumEntity::class])
    fun getCompositionsInFolderObservable(query: SupportSQLiteQuery): Observable<List<Composition>>

    @RawQuery
    fun executeQuery(sqlQuery: SimpleSQLiteQuery): List<Composition>

    @RawQuery
    fun executeQueryForMove(sqlQuery: SimpleSQLiteQuery): List<CompositionMoveData>

    @Query("""
        SELECT (
            WITH RECURSIVE path(level, name, parentId) AS (                
                SELECT 0, name, parentId                 
                FROM folders                 
                WHERE id = compositions.folderId                 
                UNION ALL                 
                SELECT path.level + 1, folders.name, folders.parentId                 
                FROM folders                 
                JOIN path ON folders.id = path.parentId             
            ),             
            path_from_root AS (SELECT name FROM path ORDER BY level DESC)             
            SELECT IFNULL(group_concat(name, '/'), '') FROM path_from_root
        ) AS parentPath, 
        (SELECT name FROM artists WHERE id = artistId) AS artist, 
        title AS title, 
        (SELECT name FROM albums WHERE id = albumId) AS album, 
        (SELECT name FROM artists WHERE id = (
            SELECT artistId FROM albums WHERE id = albumId
        )) AS albumArtist, 
        compositions.fileName AS fileName, 
        compositions.duration AS duration, 
        compositions.size AS size, 
        compositions.id AS id, 
        compositions.initialSource AS initialSource, 
        compositions.storageId AS storageId, 
        compositions.folderId AS folderId, 
        compositions.dateAdded AS dateAdded, 
        compositions.dateModified AS dateModified, 
        compositions.lastScanDate AS lastScanDate 
        FROM compositions 
        WHERE storageId NOTNULL 
        LIMIT :pageSize 
        OFFSET :pageIndex * :pageSize
    """)
    fun selectAllAsStorageCompositions(pageSize: Int, pageIndex: Int): List<StorageComposition>

    @Query("""
        INSERT INTO compositions (
            artistId,
            albumId,
            folderId,
            title,
            trackNumber,
            discNumber,
            comment,
            lyrics,
            fileName,
            duration,
            size,
            storageId,
            dateAdded,
            dateModified,
            lastScanDate,
            coverModifyTime,
            corruptionType,
            initialSource
        ) VALUES (
            :artistId,
            :albumId,
            :folderId,
            :title,
            :trackNumber,
            :discNumber,
            :comment,
            :lyrics,
            :fileName,  
            :duration,
            :size,
            :storageId,
            :dateAdded,
            :dateModified,
            :lastScanDate,
            :coverModifyTime,
            :corruptionType,
            :initialSource
        )
    """)
    fun insert(
        artistId: Long?,
        albumId: Long?,
        folderId: Long?,
        title: String?,
        trackNumber: Long?,
        discNumber: Long?,
        comment: String?,
        lyrics: String?,
        fileName: String?,
        duration: Long,
        size: Long,
        storageId: Long?,
        dateAdded: Date,
        dateModified: Date,
        lastScanDate: Date?,
        coverModifyTime: Date?,
        corruptionType: CorruptionType?,
        initialSource: InitialSource
    ): Long

    @Query("""
        UPDATE compositions SET 
            title = :title, 
            fileName = :fileName, 
            duration = :duration, 
            size = :size, 
            dateModified = :dateModified 
        WHERE storageId = :storageId
    """)
    fun update(
        title: String?,
        fileName: String,
        duration: Long,
        size: Long,
        dateModified: Date,
        storageId: Long
    )

    @Query("""
        UPDATE compositions SET 
            title = :title, 
            trackNumber = :trackNumber, 
            discNumber = :discNumber, 
            comment = :comment, 
            lyrics = :lyrics, 
            duration = :duration, 
            size = :size, 
            dateModified = :dateModified 
        WHERE id = :id
    """)
    fun update(
        id: Long,
        title: String?,
        trackNumber: Long?,
        discNumber: Long?,
        comment: String?,
        lyrics: String?,
        duration: Long,
        size: Long,
        dateModified: Long
    )

    @Query("DELETE FROM compositions WHERE id = :id")
    fun delete(id: Long)

    @Query("DELETE FROM compositions WHERE id in (:ids)")
    fun delete(ids: Array<Long>)

    @Query("DELETE FROM compositions WHERE storageId IS NULL")
    fun deleteCompositionsWithoutStorageId()

    @Query("UPDATE compositions SET pathModifyTime = NULL")
    fun clearAllPathModifyTime()

    @Query("UPDATE compositions SET pathModifyTime = NULL WHERE id = :id")
    fun clearPathModifyTime(id: Long)

    @Query("SELECT pathModifyTime FROM compositions WHERE id = :id")
    fun getPathModifyTime(id: Long): Long?

    @Query("UPDATE compositions SET artistId = :artistId WHERE id = :id")
    fun updateArtist(id: Long, artistId: Long?)

    @Query("UPDATE compositions SET albumId = :albumId WHERE id = :id")
    fun updateAlbum(id: Long, albumId: Long?)

    @Query("UPDATE compositions SET title = :title WHERE id = :id")
    fun updateTitle(id: Long, title: String?)

    @Query("UPDATE compositions SET duration = :duration WHERE id = :id")
    fun updateDuration(id: Long, duration: Long)

    @Query("UPDATE compositions SET trackNumber = :trackNumber WHERE id = :id")
    fun updateTrackNumber(id: Long, trackNumber: Long?)

    @Query("UPDATE compositions SET discNumber = :discNumber WHERE id = :id")
    fun updateDiscNumber(id: Long, discNumber: Long?)

    @Query("UPDATE compositions SET comment = :comment WHERE id = :id")
    fun updateComment(id: Long, comment: String?)

    @Query("UPDATE compositions SET lyrics = :lyrics WHERE id = :id")
    fun updateLyrics(id: Long, lyrics: String?)

    @Query("UPDATE compositions SET size = :fileSize WHERE id = :id")
    fun updateFileSize(id: Long, fileSize: Long)

    @Query("UPDATE compositions SET fileName = :fileName WHERE id = :id")
    fun updateCompositionFileName(id: Long, fileName: String)

    @Query("UPDATE compositions SET folderId = :folderId WHERE id = :id")
    fun updateFolderId(id: Long, folderId: Long?)

    @Query("UPDATE compositions SET folderId = :folderId WHERE folderId = :fromFolderId")
    fun replaceFolderId(fromFolderId: Long, folderId: Long?)

    @Query("UPDATE compositions SET storageId = :storageId WHERE id = :id")
    fun updateStorageId(id: Long, storageId: Long?)

    @Query("SELECT id FROM compositions WHERE storageId = :storageId")
    fun selectIdByStorageId(storageId: Long): Long

    @Query("SELECT storageId FROM compositions WHERE id = :id")
    fun selectStorageId(id: Long): Long

    @Query("SELECT storageId FROM compositions WHERE id = :id")
    fun getStorageId(id: Long): Long?

    @Query("SELECT corruptionType FROM compositions WHERE id = :id")
    fun selectCorruptionType(id: Long): CorruptionType?

    @Query("UPDATE compositions SET corruptionType = :corruptionType WHERE id = :id")
    fun setCorruptionType(corruptionType: CorruptionType?, id: Long)

    @Query("SELECT albumId FROM compositions WHERE id = :compositionId")
    fun getAlbumId(compositionId: Long): Long?

    @Query("UPDATE compositions SET albumId = :newAlbumId WHERE id = :compositionId")
    fun setAlbumId(compositionId: Long, newAlbumId: Long)

    @Query("SELECT artistId FROM compositions WHERE id = :id")
    fun getArtistId(id: Long): Long?

    @Query("UPDATE compositions SET dateModified = :date WHERE id = :id")
    fun setUpdateTime(id: Long, date: Date)

    @Query("UPDATE compositions SET pathModifyTime = :time WHERE id = :id")
    fun setPathModifyTime(id: Long, time: Long?)

    @Query("""
        UPDATE compositions SET 
            coverModifyTime = :date, 
            dateModified = :date, 
            size = :size 
        WHERE id = :id
    """)
    fun setCoverModifyTimeAndSize(id: Long, size: Long, date: Date)

    @Query("UPDATE compositions SET coverModifyTime = :time WHERE id = :id")
    fun setCoverModifyTime(id: Long, time: Long)

    @Query("SELECT count() FROM compositions")
    fun getCompositionsCount(): Long

    @Query("""
        SELECT 
            (SELECT name FROM artists WHERE id = artistId) AS artist, 
            title AS title, 
            (SELECT name FROM albums WHERE id = albumId) AS album, 
            (SELECT name FROM artists WHERE id = (
                SELECT artistId FROM albums WHERE id = albumId
            )) AS albumArtist, 
            (SELECT group_concat(name, '${Constants.GENRE_DIVIDER}') FROM (    
                SELECT name   
                FROM genres    
                JOIN genre_entries AS entries ON entries.compositionId = compositions.id   
                WHERE id = entries.genreId    
                ORDER BY entries.position
            )) AS genres, 
            trackNumber AS trackNumber, 
            discNumber AS discNumber, 
            comment AS comment, 
            lyrics AS lyrics, 
            fileName AS fileName, 
            duration AS duration, 
            size AS size, 
            id AS id, 
            storageId AS storageId, 
            dateAdded AS dateAdded, 
            dateModified AS dateModified, 
            coverModifyTime AS coverModifyTime, 
            corruptionType AS corruptionType, 
            initialSource AS initialSource 
        FROM compositions 
        WHERE (lastScanDate < dateModified OR lastScanDate < :lastCompleteScanTime) 
            AND storageId IS NOT NULL 
        ORDER BY dateModified DESC 
        LIMIT :filesCount
    """)
    fun selectNextCompositionsToScan(
        lastCompleteScanTime: Long,
        filesCount: Int
    ): Single<List<FullComposition>>

    @Query("UPDATE compositions SET lastScanDate = :time WHERE id = :id")
    fun setCompositionLastFileScanTime(id: Long, time: Date)

    @Query("UPDATE compositions SET lastScanDate = 0")
    fun cleanLastFileScanTime()

    @Query("SELECT folderId FROM compositions WHERE id = :compositionId")
    fun getFolderId(compositionId: Long): Long?

    @Query("""
        WITH RECURSIVE allChildFolders(cfId, cfPId, name) AS (
            SELECT folders.id AS cfId, folders.parentId AS cfPId, folders.name AS name    
            FROM folders    
            WHERE parentId = :parentFolderId OR (parentId IS NULL AND :parentFolderId IS NULL)
            UNION 
            SELECT folders.id AS cfId, folders.parentId AS cfPId, folders.name AS name    
            FROM folders    
            INNER JOIN allChildFolders ON parentId = allChildFolders.cfId
        ), 
        entries(genreId, position) AS (SELECT genreId, position FROM genre_entries) 
        SELECT (
            WITH RECURSIVE path(level, name, parentId) AS (   
                SELECT 0, name, cfPId    
                FROM allChildFolders    
                WHERE cfId = compositions.folderId    
                UNION ALL    
                SELECT path.level + 1, allChildFolders.name, allChildFolders.cfPId    
                FROM allChildFolders    
                JOIN path ON allChildFolders.cfId = path.parentId 
            ), 
            path_from_root AS (SELECT name FROM path ORDER BY level DESC)    
            SELECT IFNULL(group_concat(name, '/'), '') FROM path_from_root
        ) AS parentPath, 
        fileName AS fileName, 
        title AS title, 
        (SELECT name FROM artists WHERE id = artistId) AS artist, 
        (SELECT name FROM albums WHERE id = albumId) AS album, 
        (SELECT name FROM artists WHERE id = (
            SELECT artistId FROM albums WHERE id = albumId
        )) AS albumArtist, 
        (SELECT group_concat(name, '${Constants.GENRE_DIVIDER}') FROM (    
            SELECT name  
            FROM genres    
            JOIN genre_entries AS entries ON entries.compositionId = compositions.id   
            WHERE id = entries.genreId    
            ORDER BY entries.position
        )) AS genres, 
        trackNumber AS trackNumber, 
        discNumber AS discNumber, 
        comment AS comment, 
        lyrics AS lyrics, 
        duration AS duration, 
        size AS size, 
        dateAdded AS dateAdded, 
        dateModified AS dateModified, 
        pathModifyTime AS pathModifyTime, 
        coverModifyTime AS coverModifyTime, 
        storageId IS NOT NULL AS isFileExists 
        FROM compositions 
        WHERE folderId IN (SELECT cfId FROM allChildFolders)   
            OR (folderId = :parentFolderId OR (folderId IS NULL AND :parentFolderId IS NULL))
    """)
    fun getAllAsExternalCompositions(parentFolderId: Long?): List<ExternalComposition>

    @Query("SELECT id FROM compositions WHERE fileName = :fileName ")
    fun findCompositionsByFileName(fileName: String): List<Long>

    @Query("""
        SELECT id 
        FROM compositions 
        WHERE fileName = :fileName 
            AND (folderId = :folderId OR (folderId IS NULL AND :folderId IS NULL))
    """)
    fun findCompositionByFileName(fileName: String, folderId: Long?): Long?

    @Query("""
        WITH RECURSIVE path(level, name, parentId) AS (    
            SELECT 0, name, parentId    
            FROM folders    
            WHERE id = (SELECT folderId FROM compositions WHERE id = :id)   
            UNION ALL    
            SELECT path.level + 1, folders.name, folders.parentId    
            FROM folders    
            JOIN path ON folders.id = path.parentId
        ), 
        path_from_root AS (    
            SELECT name    
            FROM path    
            ORDER BY level DESC
        ) 
        SELECT IFNULL(group_concat(name, '/'), '') 
        FROM path_from_root
    """)
    fun getCompositionParentPath(id: Long): String

    @Query("SELECT fileName FROM compositions WHERE id = :id")
    fun getCompositionFileName(id: Long): String?

    @Query("SELECT size FROM compositions WHERE id = :id")
    fun getCompositionSize(id: Long): Long

    @Query("""
        UPDATE compositions 
        SET initialSource = :initialSource 
        WHERE id = :compositionId AND initialSource = :updateFrom
    """)
    fun updateCompositionInitialSource(
        compositionId: Long,
        initialSource: InitialSource,
        updateFrom: InitialSource
    )

    @RawQuery
    fun selectDeletedComposition(query: SupportSQLiteQuery): List<DeletedComposition>

    @Query("SELECT exists(SELECT 1 FROM compositions WHERE id = :compositionId)")
    fun isCompositionExists(compositionId: Long): Boolean

    companion object {

        fun getCompositionQuery(useFileName: Boolean): StringBuilder {
            return StringBuilder().apply {
                append("SELECT ")
                append(getCompositionSelectionQuery(useFileName))
                append("FROM compositions")
            }
        }

        fun getMoveCompositionQuery(): StringBuilder {
            return StringBuilder("""
                SELECT (
                    WITH RECURSIVE path(level, name, parentId) AS (                
                        SELECT 0, name, parentId                 
                        FROM folders                 
                        WHERE id = compositions.folderId                 
                        UNION ALL                 
                        SELECT path.level + 1, folders.name, folders.parentId                 
                        FROM folders                 
                        JOIN path ON folders.id = path.parentId             
                    ),             
                    path_from_root AS (SELECT name FROM path ORDER BY level DESC)             
                    SELECT IFNULL(group_concat(name, '/'), '') FROM path_from_root
                ) AS parentPath, 
                compositions.id AS id, 
                compositions.storageId AS storageId, 
                compositions.fileName AS fileName,
                compositions.pathModifyTime AS pathModifyTime
                FROM compositions
            """)
        }

        @JvmStatic
        fun getCompositionSelectionQuery(useFileName: Boolean): String {
            return """
                compositions.id AS id, 
                compositions.storageId AS storageId, 
                (SELECT name FROM artists WHERE id = artistId) AS artist, 
                (SELECT name FROM albums WHERE id = albumId) AS album, 
                (${if (useFileName) "fileName" else "CASE WHEN title IS NULL OR title = '' THEN fileName ELSE title END"}) AS title, 
                compositions.duration AS duration, 
                compositions.size AS size, 
                compositions.comment AS comment, 
                compositions.dateAdded AS dateAdded, 
                compositions.dateModified AS dateModified, 
                compositions.coverModifyTime AS coverModifyTime, 
                storageId IS NOT NULL AS isFileExists, 
                initialSource AS initialSource, 
                compositions.corruptionType AS corruptionType 
            """
        }

        fun getSearchWhereQuery(useFileName: Boolean): StringBuilder {
            return StringBuilder().apply {
                append(" WHERE (? IS NULL OR ")
                if (useFileName) {
                    append("fileName")
                } else {
                    append("CASE WHEN title IS NULL OR title = '' THEN fileName ELSE title END")
                }
                append(" LIKE ? OR (artist NOTNULL AND artist LIKE ?))")
            }
        }

        @JvmStatic
        fun getSearchQuery(useFileName: Boolean): StringBuilder {
            return StringBuilder(" (? IS NULL OR ").apply {
                if (useFileName) {
                    append("fileName")
                } else {
                    append("CASE WHEN title IS NULL OR title = '' THEN fileName ELSE title END")
                }
                append(" LIKE ? OR (artist NOTNULL AND artist LIKE ?))")
            }
        }

        @SuppressLint("RestrictedApi")
        fun getDeletedCompositionQuery(
            useFileName: Boolean,
            compositionsCount: Int
        ): StringBuilder {
            return StringBuilder("""
                SELECT (
                    WITH RECURSIVE path(level, name, parentId) AS (                
                        SELECT 0, name, parentId                 
                        FROM folders                 
                        WHERE id = compositions.folderId                 
                        UNION ALL                 
                        SELECT path.level + 1, folders.name, folders.parentId                 
                        FROM folders                 
                        JOIN path ON folders.id = path.parentId             
                    ),             
                    path_from_root AS (SELECT name FROM path ORDER BY level DESC)             
                    SELECT IFNULL(group_concat(name, '/'), '') FROM path_from_root
                ) AS parentPath, 
                fileName AS fileName, 
                compositions.storageId AS storageId, 
            """
            ).apply {
                append("(")
                if (useFileName) {
                    append("fileName")
                } else {
                    append("CASE WHEN title IS NULL OR title = '' THEN fileName ELSE title END")
                }
                append(") AS title ")
                append("FROM compositions WHERE id IN(")
                appendPlaceholders(this, compositionsCount)
                append(")")
            }
        }
    }

}
