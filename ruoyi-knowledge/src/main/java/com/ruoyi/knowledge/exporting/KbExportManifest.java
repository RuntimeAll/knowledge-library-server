package com.ruoyi.knowledge.exporting;

import java.util.List;
import com.ruoyi.knowledge.domain.KbDerivation;
import com.ruoyi.knowledge.domain.KbOccurrence;

public record KbExportManifest(String schemaVersion, String instanceId, String exportedAt,
                               String title, String materialId, boolean includeAnswers,
                               List<QuestionEntry> questions, List<AssetEntry> assets) {
    public record KnowledgePoint(String id, String name) { }
    public record QuestionEntry(String questionId, String rowVersion, String title, String path,
                                 String creationKind, String sourceNote, List<KnowledgePoint> knowledgePoints,
                                 List<KbOccurrence> directSources, List<KbDerivation> parents,
                                 List<KbOccurrence> ancestorSources) { }
    public record AssetEntry(String assetId, String path, String originalName, String contentType,
                              long fileSize, String sha256) { }
}
