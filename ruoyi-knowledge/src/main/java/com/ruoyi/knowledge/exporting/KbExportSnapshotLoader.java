package com.ruoyi.knowledge.exporting;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.knowledge.domain.KbQuestion;
import com.ruoyi.knowledge.domain.KbMaterial;
import com.ruoyi.knowledge.domain.KbKp;
import com.ruoyi.knowledge.domain.KbDerivation;
import com.ruoyi.knowledge.domain.KbOccurrence;
import com.ruoyi.knowledge.mapper.KbQuestionMapper;
import com.ruoyi.knowledge.mapper.KbOccurrenceMapper;
import com.ruoyi.knowledge.service.IKbQuestionService;
import com.ruoyi.knowledge.service.IKbMaterialService;
import com.ruoyi.knowledge.service.IKbKpService;
import com.ruoyi.knowledge.review.KbQualityRead;
import com.ruoyi.knowledge.review.KbReviewService;
import com.ruoyi.knowledge.storage.domain.KbAsset;
import com.ruoyi.knowledge.storage.mapper.KbAssetMapper;
import com.ruoyi.knowledge.importing.mapper.KbImportMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.HashSet;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Freezes all export metadata in one short snapshot, before downloading any asset. */
@Service
public class KbExportSnapshotLoader {
    private final IKbQuestionService questions;
    private final IKbMaterialService materials;
    private final IKbKpService points;
    private final KbQuestionMapper questionMapper;
    private final KbOccurrenceMapper occurrences;
    private final KbAssetMapper assets;
    private final KbImportMapper imports;
    private final KbReviewService reviews;
    public KbExportSnapshotLoader(IKbQuestionService questions, IKbMaterialService materials, IKbKpService points,
            KbQuestionMapper questionMapper, KbOccurrenceMapper occurrences, KbAssetMapper assets,
            KbImportMapper imports, KbReviewService reviews) {
        this.questions=questions; this.materials=materials; this.points=points; this.questionMapper=questionMapper;
        this.occurrences=occurrences; this.assets=assets; this.imports=imports; this.reviews=reviews;
    }
    public record Snapshot(String title, String origin, KbMaterial material, List<KbQuestion> questions,
            Map<Long,KbKp> points, Map<Long,List<KbDerivation>> parents, Map<Long,List<KbOccurrence>> occurrences,
            Map<Long,List<KbOccurrence>> ancestors, Map<Long,KbAsset> assets) { }

    @KbQualityRead
    @Transactional(readOnly = true)
    public Snapshot load(KbExportRequest request) {
        if (request == null || (request.materialId() == null) == (request.questionIds() == null || request.questionIds().isEmpty()))
            { throw new ServiceException("请提供题目ID列表或专项ID，且只能提供一种",400); }
        String purpose=request.purpose()==null ? "NORMAL" : request.purpose();
        if (!List.of("NORMAL","MAINTENANCE").contains(purpose)) { throw new ServiceException("导出用途无效",400); }
        if ("MAINTENANCE".equals(purpose) && !SecurityUtils.hasPermi("knowledge:question:query")) { throw new ServiceException("维护导出需要题目查看权限",403); }
        KbMaterial material=request.materialId()==null ? null : materials.get(request.materialId());
        List<Long> ids=material==null ? request.questionIds() : material.getQuestionIds();
        if (ids==null || ids.isEmpty() || ids.size()>200 || ids.stream().anyMatch(Objects::isNull) || new HashSet<>(ids).size()!=ids.size())
            { throw new ServiceException("导出请选择1至200个不重复题目",400); }
        if ("NORMAL".equals(purpose)) {
            reviews.assertUsable(ids);
            if (material!=null && !Boolean.TRUE.equals(material.getSelectionValid())) { throw new ServiceException("专项引用已失效",409); }
        }
        List<KbQuestion> selected=questions.getMany(ids);
        if (selected.size()!=ids.size()) { throw new ServiceException("部分题目不存在",404); }
        List<Long> kpIds=selected.stream().flatMap(q -> q.getKpIds().stream()).distinct().toList();
        Map<Long,KbKp> kp=points.getMany(kpIds).stream().collect(Collectors.toMap(KbKp::getId,q->q));
        Map<Long,List<KbDerivation>> parents=questionMapper.parentsForQuestions(ids).stream().collect(Collectors.groupingBy(KbDerivation::getChildQuestionId));
        Map<Long,List<KbOccurrence>> own=occurrences.forQuestions(ids).stream().collect(Collectors.groupingBy(KbOccurrence::getQuestionId));
        Map<Long,List<KbOccurrence>> ancestor=occurrences.ancestorsForQuestions(ids).stream().collect(Collectors.groupingBy(KbExportAncestor::getDescendantQuestionId,Collectors.mapping(v -> (KbOccurrence)v,Collectors.toList())));
        List<Long> assetIds=selected.stream().flatMap(q->q.getAssetIds().stream()).distinct().toList();
        Map<Long,KbAsset> files=assetIds.isEmpty() ? Map.of() : assets.selectByIds(assetIds).stream().collect(Collectors.toMap(KbAsset::getAssetId,v->v));
        if (files.size()!=assetIds.size() || files.values().stream().anyMatch(a->!"READY".equals(a.getStatus()))) { throw new ServiceException("导出资产不完整或尚未就绪",409); }
        return new Snapshot(material==null ? "知识库选题导出" : material.getTitle(), imports.selectInstanceId(), material, selected,kp,parents,own,ancestor,files);
    }
}
