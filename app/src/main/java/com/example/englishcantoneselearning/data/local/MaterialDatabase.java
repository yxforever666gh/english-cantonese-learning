package com.example.englishcantoneselearning.data.local;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(
    entities = {
        MaterialEntity.class,
        MaterialPlaybackProgressEntity.class,
        MaterialDraftEntity.class
    },
    version = 4,
    exportSchema = true
)
public abstract class MaterialDatabase extends RoomDatabase {
    public abstract MaterialDao materialDao();
}
