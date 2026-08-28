package com.example.englishcantoneselearning.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "material_generation_drafts")
public class MaterialDraftEntity {
    @PrimaryKey
    @NonNull public String id;
    @NonNull public String requestJson;
    @NonNull public String stateJson;
    @NonNull public String status;
    public int resumeFailureCount;
    public long updatedAt;

    public MaterialDraftEntity() {
        id = "";
        requestJson = "{}";
        stateJson = "{}";
        status = "ACTIVE";
    }
}
