package com.example.englishcantoneselearning.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "material_playback_progress",
    foreignKeys = @ForeignKey(
        entity = MaterialEntity.class,
        parentColumns = "id",
        childColumns = "materialId",
        onDelete = ForeignKey.CASCADE
    )
)
public class MaterialPlaybackProgressEntity {
    @PrimaryKey
    @NonNull public String materialId;
    public int resumeSentenceIndex;
    @NonNull public String completedSentenceIndicesJson;
    public boolean completed;
    public long updatedAt;

    public MaterialPlaybackProgressEntity() {
        materialId = "";
        completedSentenceIndicesJson = "[]";
    }
}
