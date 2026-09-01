package com.example.englishcantoneselearning.data.local;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "practice_materials")
public class MaterialEntity {
    @PrimaryKey
    @NonNull
    public String id;
    @NonNull public String batchId;
    public int batchPosition;
    @NonNull public String language;
    @NonNull public String difficulty;
    @NonNull public String topic;
    @NonNull public String title;
    @NonNull public String targetText;
    @NonNull public String sentencesJson;
    @NonNull public String sourcesJson;
    public long createdAt;
    @NonNull public String promptVersion;
    @NonNull public String providerName;
    @NonNull public String model;
    @NonNull public String responseId;
    public int inputTokens;
    public int outputTokens;
    @NonNull public String requestFingerprint;
    @NonNull public String origin;
    @NonNull public String sectionsJson;
    @Nullable public Float listeningBand;

    public MaterialEntity() {
        id = "";
        batchId = "";
        language = "";
        difficulty = "";
        topic = "";
        title = "";
        targetText = "";
        sentencesJson = "[]";
        sourcesJson = "[]";
        promptVersion = "";
        providerName = "";
        model = "";
        responseId = "";
        requestFingerprint = "";
        origin = "AI_GENERATED";
        sectionsJson = "[]";
    }
}
