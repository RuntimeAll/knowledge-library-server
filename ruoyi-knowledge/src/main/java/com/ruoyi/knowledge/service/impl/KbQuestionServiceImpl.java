package com.ruoyi.knowledge.service.impl;

import static com.ruoyi.knowledge.service.impl.KnowledgeRules.changed;
import static com.ruoyi.knowledge.service.impl.KnowledgeRules.choice;
import static com.ruoyi.knowledge.service.impl.KnowledgeRules.found;
import static com.ruoyi.knowledge.service.impl.KnowledgeRules.ids;
import static com.ruoyi.knowledge.service.impl.KnowledgeRules.text;
import static com.ruoyi.knowledge.service.impl.KnowledgeRules.unreferenced;
import static com.ruoyi.knowledge.service.impl.KnowledgeRules.version;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.knowledge.domain.KbDerivation;
import com.ruoyi.knowledge.domain.KbQuestion;
import com.ruoyi.knowledge.dto.KbLineage;
import com.ruoyi.knowledge.dto.KbQuestionQuery;
import com.ruoyi.knowledge.mapper.KbKpMapper;
import com.ruoyi.knowledge.mapper.KbOccurrenceMapper;
import com.ruoyi.knowledge.mapper.KbQuestionMapper;
import com.ruoyi.knowledge.service.IKbKpService;
import com.ruoyi.knowledge.service.IKbQuestionService;
import com.ruoyi.knowledge.storage.IKbAssetService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 公共题目元数据和导入写入边界，正文只由导入服务调用。 */
@Service
public class KbQuestionServiceImpl implements IKbQuestionService {
    private final KbQuestionMapper mapper;
    private final KbKpMapper points;
    private final IKbKpService kpService;
    private final IKbAssetService assets;
    private final KbOccurrenceMapper occurrences;
    private final KnowledgeDictionary dictionary;

    public KbQuestionServiceImpl(
            KbQuestionMapper mapper,
            KbKpMapper points,
            IKbKpService kpService,
            IKbAssetService assets,
            KbOccurrenceMapper occurrences,
            KnowledgeDictionary dictionary) {
        this.mapper = mapper;
        this.points = points;
        this.kpService = kpService;
        this.assets = assets;
        this.occurrences = occurrences;
        this.dictionary = dictionary;
    }

    public void prepareQuery(KbQuestionQuery query) {
        query.setMatchMode(choice(query.getMatchMode(), "CONTAINS", "CONTAINS", "FULLTEXT"));
        if (query.getKpId() != null)
            query.setMatchedIds(
                    new ArrayList<>(
                            kpService.matchingQuestionIds(
                                    query.getKpId(), !Boolean.FALSE.equals(query.getExpand()))));
    }

    public List<KbQuestion> list(KbQuestionQuery query) {
        List<KbQuestion> page = mapper.selectList(query);
        for (KbQuestion question : page) {
            question.setKpIds(mapper.selectKpIds(question.getId()));
            question.setIsRealExam(mapper.isRealExam(question.getId()));
        }
        return page;
    }

    public KbQuestion get(Long id) {
        KbQuestion data = found(mapper.selectById(id));
        data.setKpIds(mapper.selectKpIds(id));
        data.setAssetIds(mapper.selectAssetIds(id));
        data.setIsRealExam(mapper.isRealExam(id));
        data.setReadinessReasons(readiness(data));
        return data;
    }

    @Transactional
    public KbQuestion updateMetadata(KbQuestion input) {
        KbQuestion data = found(mapper.lockById(input.getId()));
        version(input.getRowVersion(), data.getRowVersion());
        data.setKpIds(
                input.getKpIds() == null ? mapper.selectKpIds(data.getId()) : input.getKpIds());
        data.setAssetIds(mapper.selectAssetIds(data.getId()));
        if (input.getTitle() != null) data.setTitle(input.getTitle());
        if (input.getStatus() != null) data.setStatus(input.getStatus());
        if (input.getSubject() != null) data.setSubject(input.getSubject());
        if (input.getQuestionType() != null) data.setQuestionType(input.getQuestionType());
        if (input.getDifficulty() != null) data.setDifficulty(input.getDifficulty());
        if (input.getSourceNote() != null) data.setSourceNote(input.getSourceNote());
        if (input.getCreationKind() != null
                && !Objects.equals(input.getCreationKind(), data.getCreationKind())) {
            if ("DERIVED".equals(input.getCreationKind())
                    || "DERIVED".equals(data.getCreationKind()))
                throw new ServiceException("变式身份只能通过新建变式建立", 400);
            data.setCreationKind(input.getCreationKind());
        }
        data.setRemark(input.getRemark());
        data.setUpdateBy(input.getUpdateBy());
        validate(data);
        changed(mapper.update(data));
        relations(data);
        return get(data.getId());
    }

    @Transactional
    public KbQuestion createImportedQuestion(KbQuestion data) {
        if ("DERIVED".equals(data.getCreationKind())) throw new ServiceException("变式须通过母题导入", 400);
        return create(data, false);
    }

    private KbQuestion create(KbQuestion data, boolean derived) {
        data.setId(null);
        data.setCreationKind(
                derived
                        ? "DERIVED"
                        : choice(
                                data.getCreationKind(),
                                "EXTRACTED",
                                "UNKNOWN",
                                "EXTRACTED",
                                "AUTHORED"));
        data.setKpIds(ids(data.getKpIds()));
        data.setAssetIds(ids(data.getAssetIds()));
        validate(data);
        mapper.insert(data);
        relations(data);
        return get(data.getId());
    }

    @Transactional
    public KbQuestion updateImportedQuestion(KbQuestion input) {
        KbQuestion old = found(mapper.lockById(input.getId()));
        version(input.getRowVersion(), old.getRowVersion());
        if (input.getTitle() == null) input.setTitle(old.getTitle());
        if (input.getSubject() == null) input.setSubject(old.getSubject());
        if (input.getQuestionType() == null) input.setQuestionType(old.getQuestionType());
        if (input.getDifficulty() == null) input.setDifficulty(old.getDifficulty());
        if (input.getSourceNote() == null) input.setSourceNote(old.getSourceNote());
        if (input.getStatus() == null) input.setStatus(old.getStatus());
        if (input.getKpIds() == null) input.setKpIds(mapper.selectKpIds(old.getId()));
        if (input.getAssetIds() == null) input.setAssetIds(mapper.selectAssetIds(old.getId()));
        input.setCreationKind(old.getCreationKind());
        validate(input);
        changed(mapper.update(input));
        relations(input);
        return get(input.getId());
    }

    @Transactional
    public KbQuestion deriveImportedQuestion(
            Long parentId,
            KbQuestion data,
            List<Long> auxiliary,
            String operation,
            String change,
            String preserved) {
        text(change, "改动说明", 20000);
        text(preserved, "保留说明", 20000);
        dictionary.validate("kb_derivation_operation", operation, "变式操作");
        List<Long> parents = new ArrayList<>(ids(auxiliary));
        if (parents.contains(parentId)) throw new ServiceException("主母题不能重复作为辅助题", 400);
        parents.add(parentId);
        parents.sort(Long::compareTo);
        for (Long id : parents) found(mapper.lockById(id));
        KbQuestion child = create(data, true);
        for (Long id : parents) {
            KbDerivation edge = new KbDerivation();
            edge.setParentQuestionId(id);
            edge.setChildQuestionId(child.getId());
            edge.setIsPrimary(id.equals(parentId));
            edge.setOperation(operation);
            edge.setChangeNote(change);
            edge.setPreservedNote(preserved);
            edge.setCreateBy(data.getCreateBy());
            mapper.insertDerivation(edge);
        }
        return child;
    }

    @Transactional
    public void delete(Long[] values) {
        for (Long id : values) {
            found(mapper.lockById(id));
            unreferenced(mapper.countReferences(id), "题目仍被专项、出处或变式引用，请先解除关系");
            mapper.deleteIncomingDerivations(id);
            mapper.deleteKps(id);
            mapper.deleteAssets(id);
            mapper.deleteById(id);
        }
    }

    public KbLineage lineage(Long id) {
        get(id);
        KbLineage result = new KbLineage();
        result.setParents(mapper.selectParents(id));
        result.setChildren(mapper.selectChildren(id));
        result.setAncestorSources(occurrences.selectAncestorSources(id));
        result.setNotice("祖先出处仅用于追溯，不表示当前题在这些来源中出现。");
        return result;
    }

    private void validate(KbQuestion data) {
        data.setTitle(text(data.getTitle(), "题目标题", 300));
        dictionary.validate("kb_subject", data.getSubject(), "学科");
        dictionary.validate("kb_question_type", data.getQuestionType(), "题型");
        dictionary.validate("kb_difficulty", data.getDifficulty(), "难度");
        if (data.getStemHtml() == null || data.getStemHtml().isBlank())
            throw new ServiceException("题干不能为空", 400);
        data.setCreationKind(
                choice(
                        data.getCreationKind(),
                        "UNKNOWN",
                        "UNKNOWN",
                        "EXTRACTED",
                        "AUTHORED",
                        "DERIVED"));
        data.setKpIds(ids(data.getKpIds()));
        data.setAssetIds(ids(data.getAssetIds()));
        for (Long id : data.getKpIds().stream().sorted().toList()) found(points.lockById(id));
        for (Long id : data.getAssetIds().stream().sorted().toList()) assets.requireReady(id);
        List<String> reasons = readiness(data);
        data.setStatus(
                choice(
                        data.getStatus(),
                        reasons.isEmpty() ? "READY" : "DRAFT",
                        "DRAFT",
                        "READY",
                        "RETIRED"));
        if ("READY".equals(data.getStatus()) && !reasons.isEmpty())
            throw new ServiceException(String.join("；", reasons), 400);
        if (data.getAnswerHtml() == null) data.setAnswerHtml("");
        if (data.getAnalysisHtml() == null) data.setAnalysisHtml("");
        if (data.getSourceNote() == null) data.setSourceNote("");
    }

    private List<String> readiness(KbQuestion data) {
        List<String> reasons = new ArrayList<>();
        if (data.getKpIds() == null || data.getKpIds().isEmpty()) reasons.add("缺少有效考点");
        if (!QuestionContentRules.hasContent(data.getStemHtml())) reasons.add("题干缺少可见文字、数学表达或图形内容");
        return reasons;
    }

    private void relations(KbQuestion data) {
        mapper.deleteKps(data.getId());
        for (Long id : data.getKpIds()) mapper.insertKp(data.getId(), id);
        mapper.deleteAssets(data.getId());
        for (Long id : data.getAssetIds()) mapper.insertAsset(data.getId(), id);
    }
}
