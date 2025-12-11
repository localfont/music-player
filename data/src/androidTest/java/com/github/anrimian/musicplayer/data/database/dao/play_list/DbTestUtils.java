package com.github.anrimian.musicplayer.data.database.dao.play_list;

import com.github.anrimian.musicplayer.data.database.dao.compositions.CompositionsDao;
import com.github.anrimian.musicplayer.domain.models.composition.InitialSource;

import java.util.Date;

public class DbTestUtils {

    public static long insert(CompositionsDao compositionsDao,
                        Long artistId,
                        Long albumId,
                        String title) {
        return insert(compositionsDao, artistId, albumId, title, null);
    }

    public static long insert(CompositionsDao compositionsDao,
                        Long artistId,
                        Long albumId,
                        String title,
                        Long folderId) {
        return compositionsDao.insert(
                artistId,
                albumId,
                folderId,
                title,
                null,
                null,
                null,
                null,
                "test file path",
                100L,
                100L,
                null,
                new Date(),
                new Date(),
                new Date(),
                new Date(),
                null,
                InitialSource.LOCAL
        );
    }

}
