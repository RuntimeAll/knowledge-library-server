package com.ruoyi.knowledge.service.impl;

import static com.ruoyi.knowledge.service.impl.KnowledgeRules.changed;
import static com.ruoyi.knowledge.service.impl.KnowledgeRules.choice;
import static com.ruoyi.knowledge.service.impl.KnowledgeRules.found;
import static com.ruoyi.knowledge.service.impl.KnowledgeRules.ids;
import static com.ruoyi.knowledge.service.impl.KnowledgeRules.text;
import static com.ruoyi.knowledge.service.impl.KnowledgeRules.unreferenced;
import static com.ruoyi.knowledge.service.impl.KnowledgeRules.version;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.knowledge.domain.KbComparisonRow;
import com.ruoyi.knowledge.domain.KbMaterial;
import com.ruoyi.knowledge.domain.KbMaterialReference;
import com.ruoyi.knowledge.dto.KbComparison;
import com.ruoyi.knowledge.dto.KbComparisonQuery;
import com.ruoyi.knowledge.dto.KbPointMatch;
import com.ruoyi.knowledge.dto.KbQuestionQuery;
import com.ruoyi.knowledge.mapper.KbMaterialMapper;
import com.ruoyi.knowledge.mapper.KbQuestionMapper;
import com.ruoyi.knowledge.mapper.KbSourceMapper;
import com.ruoyi.knowledge.service.IKbKpService;
import com.ruoyi.knowledge.service.IKbMaterialService;
import com.ruoyi.knowledge.service.IKbQuestionService;
import com.ruoyi.knowledge.service.IKbSourceService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 专项只保存题目引用与顺序，成品始终独立冻结。 */
@Service
public class KbMaterialServiceImpl implements IKbMaterialService {
    private final KbMaterialMapper mapper;
    private final KbQuestionMapper questionMapper;
    private final IKbQuestionService questions;
    private final KbSourceMapper sources;
    private final IKbSourceService sourceService;
    private final IKbKpService points;

    public KbMaterialServiceImpl(
            KbMaterialMapper mapper,
            KbQuestionMapper questionMapper,
            IKbQuestionService questions,
            KbSourceMapper sources,
            IKbSourceService sourceService,
            IKbKpService points) {
        this.mapper = mapper;
        this.questionMapper = questionMapper;
        this.questions = questions;
        this.sources = sources;
        this.sourceService = sourceService;
        this.points = points;
    }

    public List<KbMaterial> list(KbMaterial query) {
        return mapper.selectList(query);
    }

    public KbMaterial get(Long id) {
        KbMaterial data = found(mapper.selectById(id));
        data.setQuestionIds(mapper.selectQuestionIds(id));
        data.setReferenceSources(mapper.selectReferences(id));
        data.setQuestions(data.getQuestionIds().stream().map(questions::get).toList());
        data.setQuestionCount(data.getQuestionIds().size());
        data.setSelectionValid(valid(data));
        return data;
    }

    @Transactional
    public KbMaterial create(KbMaterial data) {
        validate(data);
        mapper.insert(data);
        relations(data);
        return get(data.getId());
    }

    @Transactional
    public KbMaterial update(KbMaterial data) {
        KbMaterial old = found(mapper.lockById(data.getId()));
        version(data.getRowVersion(), old.getRowVersion());
        if (data.getQuestionIds() == null)
            data.setQuestionIds(mapper.selectQuestionIds(data.getId()));
        if (data.getReferenceSources() == null)
            data.setReferenceSources(mapper.selectReferences(data.getId()));
        validate(data);
        relations(data);
        changed(mapper.update(data));
        return get(data.getId());
    }

    @Transactional
    public void delete(Long[] values) {
        for (Long id : values) {
            found(mapper.lockById(id));
            unreferenced(mapper.countProducts(id), "专项已有成品引用，不能删除");
            mapper.deleteItems(id);
            mapper.deleteReferences(id);
            mapper.deleteById(id);
        }
    }

    public void assertSelectionValid(Long id) {
        if (!get(id).getSelectionValid()) throw new ServiceException("仅真题选题已失效，请复核出处或调整专项", 409);
    }

    private boolean valid(KbMaterial data) {
        if (!"EXAM_ONLY".equals(data.getSelectionMode())) return true;
        List<Long> ids =
                data.getReferenceSources().stream().map(KbMaterialReference::getSourceId).toList();
        return data.getQuestionIds().stream()
                .allMatch(q -> !ids.isEmpty() && mapper.realInSources(q, ids));
    }

    private void validate(KbMaterial data) {
        data.setTitle(text(data.getTitle(), "专项标题", 300));
        data.setSelectionMode(choice(data.getSelectionMode(), "MIXED", "MIXED", "EXAM_ONLY"));
        data.setQuestionIds(ids(data.getQuestionIds()));
        if (data.getQuestionIds().size() > 500) {
            throw new ServiceException("单份专项最多保存500道题目", 400);
        }
        if (data.getKpId() != null) points.get(data.getKpId());
        if (data.getReferenceSources() == null) data.setReferenceSources(List.of());
    }

    private void relations(KbMaterial data) {
        Set<Long> seen = new HashSet<>();
        for (KbMaterialReference ref : data.getReferenceSources()) {
            if (ref.getSourceId() == null || !seen.add(ref.getSourceId()))
                throw new ServiceException("参考来源无效或重复", 400);
            if (ref.getNote() != null && ref.getNote().length() > 500)
                throw new ServiceException("参考说明过长", 400);
        }
        for (Long id : seen.stream().sorted().toList()) found(sources.lockById(id));
        for (Long id : data.getQuestionIds().stream().sorted().toList())
            found(questionMapper.lockById(id));
        if (!validForWrite(data)) throw new ServiceException("选题须自身在参考范围内有当前有效的已核实真题出处", 409);
        mapper.deleteItems(data.getId());
        mapper.deleteReferences(data.getId());
        for (int i = 0; i < data.getQuestionIds().size(); i++)
            mapper.insertItem(data.getId(), data.getQuestionIds().get(i), i + 1);
        for (KbMaterialReference ref : data.getReferenceSources())
            mapper.insertReference(data.getId(), ref);
    }

    private boolean validForWrite(KbMaterial data) {
        if (!"EXAM_ONLY".equals(data.getSelectionMode())) {
            return true;
        }
        List<Long> sourceIds =
                data.getReferenceSources().stream().map(KbMaterialReference::getSourceId).toList();
        // Metadata/dictionary reads may already have created an RR snapshot.
        // Eligibility after obtaining source/question locks must use current reads.
        return data.getQuestionIds().stream()
                .allMatch(
                        questionId ->
                                !sourceIds.isEmpty()
                                        && mapper.selectVerifiedOccurrenceIdForUpdate(
                                                        questionId, sourceIds)
                                                != null);
    }

    public void prepareCandidates(Long id, String scope, KbQuestionQuery query) {
        KbMaterial material = get(id);
        scope = choice(scope, "REFERENCE", "REFERENCE", "ALL");
        if ("EXAM_ONLY".equals(material.getSelectionMode()) && "ALL".equals(scope))
            throw new ServiceException("仅真题模式须使用已保存参考范围", 400);
        if ("REFERENCE".equals(scope))
            query.setSourceIds(
                    material.getReferenceSources().stream()
                            .map(KbMaterialReference::getSourceId)
                            .toList());
        query.setVerifiedOnly("EXAM_ONLY".equals(material.getSelectionMode()));
        questions.prepareQuery(query);
    }

    @Transactional(readOnly = true)
    public KbComparison comparison(
            Long id,
            String scope,
            boolean expand,
            int matchPageNum,
            int matchPageSize,
            int coveragePageNum,
            int coveragePageSize,
            int pointPageNum,
            int pointPageSize) {
        KbMaterial material = found(mapper.selectById(id));
        scope = choice(scope, "REFERENCE", "REFERENCE", "ALL");
        KbComparisonQuery query = new KbComparisonQuery();
        query.setSourceIds(
                "REFERENCE".equals(scope)
                        ? mapper.selectReferences(id).stream()
                                .map(KbMaterialReference::getSourceId)
                                .toList()
                        : null);
        Set<Long> basis = new LinkedHashSet<>();
        if (material.getKpId() != null) {
            basis.add(material.getKpId());
        }
        for (Long questionId : mapper.selectQuestionIds(id)) {
            basis.addAll(questionMapper.selectKpIds(questionId));
        }
        query.setPointIds(new ArrayList<>(basis));
        List<KbPointMatch> accepted = new ArrayList<>();
        for (Long pointId : basis) {
            Set<Long> questionIds = points.matchingQuestionIds(pointId, expand);
            if (!questionIds.isEmpty()) {
                accepted.add(
                        new KbPointMatch(
                                pointId,
                                points.get(pointId).getName(),
                                new ArrayList<>(questionIds)));
            }
        }
        query.setPointMatches(accepted);
        KbComparison result = new KbComparison();
        query.page(matchPageNum, matchPageSize);
        result.setMatchTotal(mapper.countComparisonMatches(query));
        result.setMatches(mapper.selectComparisonMatches(query));
        query.page(coveragePageNum, coveragePageSize);
        result.setCoverageTotal(mapper.countComparisonCoverage(query));
        List<KbComparisonRow> coverage = mapper.selectComparisonCoverage(query);
        coverage.forEach(row -> row.setNotice(coverageNotice(row.getComparisonStatus())));
        result.setCoverage(coverage);
        query.page(pointPageNum, pointPageSize);
        result.setPointTotal(basis.size());
        result.setPoints(mapper.selectComparisonPoints(query));
        result.setNotice("共同考点不等于同题、相同考法或质量结论；只呈现当前核实有效事实。");
        return result;
    }

    private String coverageNotice(String status) {
        return switch (status) {
            case "ORIGINAL_ONLY" -> "仅原件，未参与逐题比较";
            case "NOT_EXAM" -> "非考试来源";
            case "SOURCE_UNVERIFIED" -> "考试身份未核实或已否定";
            case "NO_KNOWLEDGE_POINTS" -> "尚无训练考点";
            case "NO_VERIFIED_QUESTIONS" -> "暂无当前有效核实题目，不能据此认定没有相关考点";
            case "COMPARED_PARTIAL" -> "只比较已整理且核实有效的部分";
            default -> "已拆题，仍只比较核实有效的题目";
        };
    }
}
