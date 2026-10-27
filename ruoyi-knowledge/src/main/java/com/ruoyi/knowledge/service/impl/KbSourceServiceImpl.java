package com.ruoyi.knowledge.service.impl;

import static com.ruoyi.knowledge.service.impl.KnowledgeRules.changed;
import static com.ruoyi.knowledge.service.impl.KnowledgeRules.choice;
import static com.ruoyi.knowledge.service.impl.KnowledgeRules.found;
import static com.ruoyi.knowledge.service.impl.KnowledgeRules.ids;
import static com.ruoyi.knowledge.service.impl.KnowledgeRules.text;
import static com.ruoyi.knowledge.service.impl.KnowledgeRules.unreferenced;
import static com.ruoyi.knowledge.service.impl.KnowledgeRules.version;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.knowledge.catalog.*;
import com.ruoyi.knowledge.domain.KbOccurrence;
import com.ruoyi.knowledge.domain.KbQuestion;
import com.ruoyi.knowledge.domain.KbSource;
import com.ruoyi.knowledge.domain.KbSourceAttachment;
import com.ruoyi.knowledge.mapper.KbOccurrenceMapper;
import com.ruoyi.knowledge.mapper.KbQuestionMapper;
import com.ruoyi.knowledge.mapper.KbSourceMapper;
import com.ruoyi.knowledge.service.IKbCatalogService;
import com.ruoyi.knowledge.service.IKbSourceService;
import com.ruoyi.knowledge.storage.IKbAssetService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Objects;
import java.util.Set;

/** 来源身份、原件关联与实际出现事实各自维护。 */
@Service
public class KbSourceServiceImpl implements IKbSourceService {
    private final KbSourceMapper mapper;
    private final KbOccurrenceMapper occurrences;
    private final KbQuestionMapper questions;
    private final IKbAssetService assets;
    private final KnowledgeDictionary dictionary;
    private final IKbCatalogService catalog;

    public KbSourceServiceImpl(
            KbSourceMapper mapper,
            KbOccurrenceMapper occurrences,
            KbQuestionMapper questions,
            IKbAssetService assets,
            KnowledgeDictionary dictionary,
            IKbCatalogService catalog) {
        this.mapper = mapper;
        this.occurrences = occurrences;
        this.questions = questions;
        this.assets = assets;
        this.dictionary = dictionary;
        this.catalog = catalog;
    }

    public void prepareQuery(KbCatalogQuery query) {
        catalog.prepareQuery("SOURCE", query);
    }

    public List<KbSource> list(KbCatalogQuery query) {
        List<KbSource> page = mapper.selectList(query);
        if (page.isEmpty()) return page;
        Map<Long, KbSource> targets = new LinkedHashMap<>();
        page.forEach(row -> targets.put(row.getId(), row));
        Map<Long, List<KbSourceAttachment>> attachments = new HashMap<>();
        for (KbSourceAttachment item : mapper.selectAttachmentsBySources(new ArrayList<>(targets.keySet())))
            attachments.computeIfAbsent(item.getSourceId(), ignored -> new ArrayList<>()).add(item);
        page.forEach(row -> {
            row.setAttachments(attachments.getOrDefault(row.getId(), List.of()));
            row.setAssetIds(row.getAttachments().stream().map(KbSourceAttachment::getAssetId).distinct().toList());
        });
        catalog.enrichBatch("SOURCE", targets);
        page.forEach(row -> {
            if (row.getProfile() != null) row.setSubject(row.getProfile().getSubject());
            row.setMatchReasons(catalogMatches(query));
        });
        return page;
    }

    private List<String> catalogMatches(KbCatalogQuery query) {
        List<String> reasons = new ArrayList<>();
        if (query.getOutlineId() != null
                || query.getTextbookVersion() != null
                || query.getGrade() != null
                || query.getTerm() != null) reasons.add("命中资料直接声明的教材范围");
        if (query.getKpId() != null)
            reasons.add("ACTUAL".equals(query.getCoverageMode()) ? "命中已录入题目的实际考点" : "命中文件声明考点");
        if (query.getRegionCode() != null) reasons.add("命中指定角色区域");
        if (query.getYearField() != null) reasons.add("命中指定年份字段：" + query.getYearField());
        return reasons;
    }

    public KbSource get(Long id) {
        KbSource data = found(mapper.selectById(id));
        KbSource exam = mapper.selectExam(id);
        if ("EXAM".equals(data.getSourceKind()) && exam != null) {
            data.setSubject(exam.getSubject());
            data.setGrade(exam.getGrade());
            data.setExamType(exam.getExamType());
            data.setExamYear(exam.getExamYear());
            data.setSchoolName(exam.getSchoolName());
            data.setScopeNote(exam.getScopeNote());
            data.setAuthenticityStatus(exam.getAuthenticityStatus());
            data.setVerificationNote(exam.getVerificationNote());
        }
        data.setAttachments(mapper.selectAttachments(id));
        data.setAssetIds(
                data.getAttachments().stream()
                        .map(KbSourceAttachment::getAssetId)
                        .distinct()
                        .toList());
        catalog.enrich("SOURCE", id, data);
        if (data.getProfile() != null) data.setSubject(data.getProfile().getSubject());
        return data;
    }

    @com.ruoyi.knowledge.review.KbQualityWrite(dependency = "NONE")
    @Transactional
    public KbSource create(KbSource data) {
        catalog.prepareSource(data, null);
        validate(data, null);
        mapper.insert(data);
        saveRelations(data);
        catalog.persist("SOURCE", data.getId(), data, data.getCreateBy(), true);
        return get(data.getId());
    }

    @com.ruoyi.knowledge.review.KbQualityWrite(dependency = "SOURCE")
    @Transactional
    public KbSource update(KbSource data) {
        found(mapper.lockById(data.getId()));
        KbSource old = get(data.getId());
        version(data.getRowVersion(), old.getRowVersion());
        if (data.getTitle() == null) data.setTitle(old.getTitle());
        if (data.getExtractionStatus() == null) data.setExtractionStatus(old.getExtractionStatus());
        if (data.getOriginUrl() == null) data.setOriginUrl(old.getOriginUrl());
        if (data.getOriginNote() == null) data.setOriginNote(old.getOriginNote());
        if (data.getAttachments() == null && data.getAssetIds() == null) {
            data.setAttachments(old.getAttachments());
            data.setAssetIds(old.getAssetIds());
        }
        catalog.prepareSource(data, old);
        validate(data, old);
        if ("ORIGINAL_ONLY".equals(data.getExtractionStatus())
                && mapper.countOccurrences(data.getId()) > 0)
            throw new ServiceException("已有题目出处，不能改成仅有原件", 409);
        changed(mapper.update(data));
        saveRelations(data);
        catalog.persist("SOURCE", data.getId(), data, data.getUpdateBy(), false);
        return get(data.getId());
    }

    @com.ruoyi.knowledge.review.KbQualityWrite(dependency = "NONE")
    @Transactional
    public void delete(Long[] values) {
        for (Long id : values) {
            found(mapper.lockById(id));
            unreferenced(mapper.countReferences(id), "来源仍有题目出处或专项参考引用");
            catalog.delete("SOURCE", id);
            mapper.deleteAttachments(id);
            mapper.deleteExam(id);
            mapper.deleteById(id);
        }
    }

    public List<KbOccurrence> occurrences(Long questionId, Long sourceId) {
        KbOccurrence query = new KbOccurrence();
        query.setQuestionId(questionId);
        query.setSourceId(sourceId);
        List<KbOccurrence> values = occurrences.selectList(query);
        values.forEach(this::flags);
        return values;
    }

    @com.ruoyi.knowledge.review.KbQualityWrite(dependency = "SOURCE")
    @Transactional
    public KbOccurrence createOccurrence(KbOccurrence data) {
        // A caller-supplied ID must not exclude an existing position from the
        // uniqueness check performed before the generated INSERT ID is assigned.
        data.setId(null);
        found(mapper.lockById(data.getSourceId()));
        KbQuestion q = found(questions.lockById(data.getQuestionId()));
        validateOccurrence(data, q);
        occurrences.insert(data);
        mapper.advanceExtraction(data.getSourceId(), data.getCreateBy());
        return flags(found(occurrences.selectById(data.getId())));
    }

    @com.ruoyi.knowledge.review.KbQualityWrite(dependency = "SOURCE")
    @Transactional
    public KbOccurrence updateOccurrence(KbOccurrence data) {
        found(mapper.lockById(data.getSourceId()));
        KbQuestion q = found(questions.lockById(data.getQuestionId()));
        KbOccurrence previous = found(occurrences.lockById(data.getId()));
        if (!Objects.equals(previous.getSourceId(), data.getSourceId())
                || !Objects.equals(previous.getQuestionId(), data.getQuestionId()))
            throw new ServiceException("出处不能改挂到另一来源或题目", 400);
        version(data.getRowVersion(), previous.getRowVersion());
        validateOccurrence(data, q);
        changed(occurrences.update(data));
        return flags(found(occurrences.selectById(data.getId())));
    }

    @com.ruoyi.knowledge.review.KbQualityWrite(dependency = "OCCURRENCE")
    @Transactional
    public void deleteOccurrence(Long id) {
        KbOccurrence data = found(occurrences.selectById(id));
        found(mapper.lockById(data.getSourceId()));
        if (!catalog.collections(id).isEmpty()) throw new ServiceException("出处仍有收录用途，请先取消收录", 409);
        occurrences.deleteById(id);
    }

    private void validate(KbSource data, KbSource old) {
        data.setTitle(text(data.getTitle(), "来源标题", 300));
        data.setSourceKind(
                choice(
                        data.getSourceKind(),
                        "HANDOUT",
                        "EXAM",
                        "HANDOUT",
                        "WORKBOOK",
                        "AUTHORED",
                        "OTHER"));
        data.setExtractionStatus(
                choice(
                        data.getExtractionStatus(),
                        "ORIGINAL_ONLY",
                        "ORIGINAL_ONLY",
                        "PARTIAL",
                        "EXTRACTED"));
        if (old == null || !Objects.equals(old.getSourceKind(), data.getSourceKind()))
            dictionary.validate("kb_source_kind", data.getSourceKind(), "来源类型");
        if (old == null || !Objects.equals(old.getExtractionStatus(), data.getExtractionStatus()))
            dictionary.validate("kb_extraction_status", data.getExtractionStatus(), "抽题进度");
        if (!"EXAM".equals(data.getSourceKind())) {
            if (hasText(data.getGrade())
                    || hasText(data.getExamType())
                    || data.getExamYear() != null
                    || hasText(data.getSchoolName())
                    || hasText(data.getScopeNote())
                    || hasText(data.getVerificationNote())
                    || (hasText(data.getAuthenticityStatus())
                            && !"UNVERIFIED".equals(data.getAuthenticityStatus()))) {
                throw new ServiceException("考试属性只能填写在考试来源中", 400);
            }
            data.setAuthenticityStatus(null);
            return;
        }
        data.setAuthenticityStatus(
                choice(
                        data.getAuthenticityStatus(),
                        "UNVERIFIED",
                        "UNVERIFIED",
                        "VERIFIED",
                        "REJECTED"));
        if (old == null
                || !Objects.equals(old.getAuthenticityStatus(), data.getAuthenticityStatus()))
            dictionary.validate("kb_verification_status", data.getAuthenticityStatus(), "来源核实状态");
        if (data.getExamYear() != null && (data.getExamYear() < 1 || data.getExamYear() > 9999))
            throw new ServiceException("年份无效", 400);
        if (old == null || !Objects.equals(old.getSubject(), data.getSubject()))
            dictionary.validate("kb_subject", data.getSubject(), "学科");
        if (old == null || !Objects.equals(old.getExamType(), data.getExamType()))
            dictionary.validate("kb_exam_type", data.getExamType(), "考试类型");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void saveRelations(KbSource data) {
        if ("EXAM".equals(data.getSourceKind())) mapper.saveExam(data);
        else catalog.clearExam(data.getId());
        if (data.getAssetIds() == null && data.getAttachments() == null) return;
        List<KbSourceAttachment> links = new ArrayList<>();
        if (data.getAttachments() != null) links.addAll(data.getAttachments());
        else
            for (Long id : ids(data.getAssetIds())) {
                KbSourceAttachment link = new KbSourceAttachment();
                link.setAssetId(id);
                link.setRole("ORIGINAL");
                links.add(link);
            }
        Set<Long> wanted = new HashSet<>();
        Set<String> keys = new HashSet<>();
        for (KbSourceAttachment link : links) {
            assets.requireOriginal(link.getAssetId());
            link.setSourceId(data.getId());
            link.setRole(choice(link.getRole(), "ORIGINAL", "ORIGINAL", "EXCERPT", "EVIDENCE"));
            dictionary.validate("kb_attachment_role", link.getRole(), "附件角色");
            if (!keys.add(link.getAssetId() + ":" + link.getRole()))
                throw new ServiceException("附件关联重复", 400);
            wanted.add(link.getAssetId());
        }
        for (KbSourceAttachment old : mapper.selectAttachments(data.getId()))
            if (!wanted.contains(old.getAssetId()))
                unreferenced(
                        mapper.countAssetEvidence(data.getId(), old.getAssetId())
                                + catalog.countSourceEvidence(data.getId(), old.getAssetId()),
                        "附件仍被出处作为证据引用");
        mapper.deleteAttachments(data.getId());
        links.forEach(mapper::insertAttachment);
    }

    private void validateOccurrence(KbOccurrence data, KbQuestion question) {
        if (data.getPageFrom() != null && data.getPageFrom() < 1
                || data.getPageTo() != null
                        && (data.getPageFrom() == null || data.getPageTo() < data.getPageFrom()))
            throw new ServiceException("页码范围无效", 400);
        if (data.getScore() != null
                && (data.getScore().signum() < 0
                        || data.getScore().compareTo(new java.math.BigDecimal("9999.9")) > 0
                        || data.getScore().stripTrailingZeros().scale() > 1))
            throw new ServiceException("分值须为0至9999.9，最多一位小数", 400);
        if (data.getQuestionNo() != null && data.getPageFrom() != null)
            unreferenced(occurrences.countPosition(data), "该页码题号已有出处，请编辑原记录");
        data.setMatchStatus(
                choice(data.getMatchStatus(), "UNVERIFIED", "UNVERIFIED", "VERIFIED", "REJECTED"));
        dictionary.validate("kb_verification_status", data.getMatchStatus(), "出处核实状态");
        if (data.getEvidenceAssetId() != null) {
            boolean linked =
                    mapper.selectAttachments(data.getSourceId()).stream()
                            .anyMatch(a -> data.getEvidenceAssetId().equals(a.getAssetId()));
            if (!linked) throw new ServiceException("证据必须属于同一来源", 400);
            assets.requireReady(data.getEvidenceAssetId());
        }
        data.setMatchedStemHash(
                "VERIFIED".equals(data.getMatchStatus()) ? question.getStemHash() : null);
        data.setVerifiedAt("VERIFIED".equals(data.getMatchStatus()) ? new Date() : null);
    }

    private KbOccurrence flags(KbOccurrence data) {
        KbQuestion q = questions.selectById(data.getQuestionId());
        KbSource s = get(data.getSourceId());
        data.setQuestionTitle(q == null ? null : q.getTitle());
        data.setSourceTitle(s.getTitle());
        boolean matches =
                q != null
                        && Objects.equals(data.getMatchedStemHash(), q.getStemHash())
                        && data.getMatchedStemHash() != null;
        data.setStemMatches(matches);
        data.setIsRealExam(
                catalog.realExam(s)
                        && q != null
                        && !"KNOWLEDGE".equals(q.getContentKind())
                        && matches
                        && "VERIFIED".equals(data.getMatchStatus())
                        && "VERIFIED".equals(s.getAuthenticityStatus()));
        data.setCollections(catalog.collections(data.getId()));
        return data;
    }

    @com.ruoyi.knowledge.review.KbQualityWrite(dependency = "SOURCE")
    @Transactional
    public KbSource catalog(Long id, KbCatalogCommand command, String actor) {
        KbSource input = new KbSource();
        input.setId(id);
        input.setRowVersion(command.getRowVersion());
        input.setUpdateBy(actor);
        copyCatalog(command, input);
        return update(input);
    }

    private void copyCatalog(KbCatalogDocument from, KbCatalogDocument to) {
        to.setProfile(from.getProfile());
        to.setOutlineScopes(from.getOutlineScopes());
        to.setDeclaredKpIds(from.getDeclaredKpIds());
        to.setRegions(from.getRegions());
        to.setExamDetail(from.getExamDetail());
    }

    @com.ruoyi.knowledge.review.KbQualityWrite(dependency = "SOURCE")
    @Transactional
    public KbSource verifyExam(Long id, KbExamVerificationCommand command, String actor) {
        found(mapper.lockById(id));
        KbSource data = get(id);
        version(command.getRowVersion(), data.getRowVersion());
        catalog.verify(data, command, actor);
        data.setUpdateBy(actor);
        changed(mapper.update(data));
        mapper.saveExam(data);
        catalog.persist("SOURCE", id, data, actor, false);
        return get(id);
    }

    public List<KbOccurrence> occurrenceList(KbOccurrenceQuery query) {
        List<KbOccurrence> page = occurrences.selectCatalogList(query);
        if (page.isEmpty()) return page;
        List<Long> ids = page.stream().map(KbOccurrence::getId).toList();
        Map<Long, KbOccurrenceQuestionState> current = new HashMap<>();
        for (KbOccurrenceQuestionState state : occurrences.questionStates(ids))
            current.put(state.getOccurrenceId(), state);
        Map<Long, KbSource> sourceRows = new LinkedHashMap<>();
        for (KbSource row : mapper.selectByIds(page.stream().map(KbOccurrence::getSourceId).distinct().toList()))
            sourceRows.put(row.getId(), row);
        catalog.enrichExamSources(sourceRows);
        Map<Long, Boolean> realSources = new HashMap<>();
        sourceRows.forEach((id, source) -> realSources.put(id, catalog.realExam(source)));
        Map<Long, List<KbOccurrenceCollection>> collections = catalog.collectionsBatch(ids);
        page.forEach(row -> {
            KbSource source = found(sourceRows.get(row.getSourceId()));
            KbOccurrenceQuestionState question = current.get(row.getId());
            boolean exists = question != null && question.getQuestionId() != null;
            boolean matches = exists && row.getMatchedStemHash() != null
                    && Objects.equals(row.getMatchedStemHash(), question.getStemHash());
            row.setQuestionTitle(exists ? question.getQuestionTitle() : null);
            row.setSourceTitle(source.getTitle());
            row.setStemMatches(matches);
            row.setIsRealExam(Boolean.TRUE.equals(realSources.get(source.getId())) && exists
                    && !"KNOWLEDGE".equals(question.getContentKind()) && matches
                    && "VERIFIED".equals(row.getMatchStatus())
                    && "VERIFIED".equals(source.getAuthenticityStatus()));
            row.setCollections(collections.getOrDefault(row.getId(), List.of()));
        });
        return page;
    }

    @com.ruoyi.knowledge.review.KbQualityWrite(dependency = "OCCURRENCE")
    @Transactional
    public KbOccurrence collections(Long id, KbCollectionsCommand command, String actor) {
        KbOccurrence before = found(occurrences.selectById(id));
        found(mapper.lockById(before.getSourceId()));
        KbOccurrence data = found(occurrences.lockById(id));
        version(command.getRowVersion(), data.getRowVersion());
        catalog.saveCollections(data, command, actor);
        data.setUpdateBy(actor);
        changed(occurrences.update(data));
        return flags(found(occurrences.selectById(id)));
    }
}
