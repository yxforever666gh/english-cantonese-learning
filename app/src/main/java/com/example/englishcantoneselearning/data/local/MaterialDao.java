package com.example.englishcantoneselearning.data.local;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface MaterialDao {
    @Query("SELECT * FROM practice_materials ORDER BY createdAt DESC, batchPosition ASC")
    List<MaterialEntity> getAll();

    @Query("SELECT * FROM practice_materials WHERE id = :id LIMIT 1")
    MaterialEntity getById(String id);

    @Query("SELECT * FROM practice_materials WHERE requestFingerprint = :fingerprint ORDER BY batchPosition ASC")
    List<MaterialEntity> getByFingerprint(String fingerprint);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<MaterialEntity> materials);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(MaterialEntity material);

    @Delete
    void delete(MaterialEntity material);

    @Query("DELETE FROM practice_materials WHERE batchId = :batchId")
    void deleteBatch(String batchId);

    @Query("SELECT sourcesJson FROM practice_materials ORDER BY createdAt DESC LIMIT :limit")
    List<String> getRecentSourcesJson(int limit);

    @Query("SELECT * FROM material_playback_progress")
    List<MaterialPlaybackProgressEntity> getAllPlaybackProgress();

    @Query("SELECT * FROM material_playback_progress WHERE materialId = :materialId LIMIT 1")
    MaterialPlaybackProgressEntity getPlaybackProgress(String materialId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void savePlaybackProgress(MaterialPlaybackProgressEntity progress);

    @Query("DELETE FROM material_playback_progress WHERE materialId = :materialId")
    void deletePlaybackProgress(String materialId);

    @Query("SELECT * FROM material_generation_drafts ORDER BY updatedAt DESC LIMIT 1")
    MaterialDraftEntity getActiveDraft();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void saveDraft(MaterialDraftEntity draft);

    @Query("DELETE FROM material_generation_drafts WHERE id = :draftId")
    void deleteDraft(String draftId);

    @androidx.room.Transaction
    default void finalizeDraft(MaterialEntity material, String draftId) {
        insert(material);
        deleteDraft(draftId);
    }
}
