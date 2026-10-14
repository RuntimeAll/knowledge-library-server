package com.ruoyi.knowledge.service.impl;

import static com.ruoyi.knowledge.service.impl.KnowledgeRules.changed;
import static com.ruoyi.knowledge.service.impl.KnowledgeRules.choice;
import static com.ruoyi.knowledge.service.impl.KnowledgeRules.found;
import static com.ruoyi.knowledge.service.impl.KnowledgeRules.ids;
import static com.ruoyi.knowledge.service.impl.KnowledgeRules.text;
import static com.ruoyi.knowledge.service.impl.KnowledgeRules.unreferenced;
import static com.ruoyi.knowledge.service.impl.KnowledgeRules.version;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.knowledge.domain.KbOccurrence;
import com.ruoyi.knowledge.domain.KbQuestion;
import com.ruoyi.knowledge.domain.KbSource;
import com.ruoyi.knowledge.domain.KbSourceAttachment;
import com.ruoyi.knowledge.mapper.KbOccurrenceMapper;
import com.ruoyi.knowledge.mapper.KbQuestionMapper;
import com.ruoyi.knowledge.mapper.KbSourceMapper;
import com.ruoyi.knowledge.service.IKbSourceService;
import com.ruoyi.knowledge.storage.IKbAssetService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
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

    public KbSourceServiceImpl(
            KbSourceMapper mapper,
            KbOccurrenceMapper occurrences,
            KbQuestionMapper questions,
            IKbAssetService assets,
            KnowledgeDictionary dictionary) {
        this.mapper = mapper;
        this.occurrences = occurrences;
        this.questions = questions;
        this.assets = assets;
        this.dictionary = dictionary;
    }

    public List<KbSource> list(KbSource query) {
        return mapper.selectList(query);
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
        return data;
    }

    @Transactional
    public KbSource create(KbSource data) {
        validate(data);
        mapper.insert(data);
        saveRelations(data);
        return get(data.getId());
    }

    @Transactional
    public KbSource update(KbSource data) {
        KbSource old = found(mapper.lockById(data.getId()));
        version(data.getRowVersion(), old.getRowVersion());
        validate(data);
        if ("ORIGINAL_ONLY".equals(data.getExtractionStatus())
                && mapper.countOccurrences(data.getId()) > 0)
            throw new ServiceException("已有题目出处，不能改成仅有原件", 409);
        changed(mapper.update(data));
        saveRelations(data);
        return get(data.getId());
    }

    @Transactional
    public void delete(Long[] values) {
        for (Long id : values) {
            found(mapper.lockById(id));
            unreferenced(mapper.countReferences(id), "来源仍有题目出处或专项参考引用");
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

    @Transactional
    public void deleteOccurrence(Long id) {
        KbOccurrence data = found(occurrences.selectById(id));
        found(mapper.lockById(data.getSourceId()));
        occurrences.deleteById(id);
    }

    private void validate(KbSource data) {
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
        if (!"EXAM".equals(data.getSourceKind())) {
            if (hasText(data.getSubject())
                    || hasText(data.getGrade())
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
        if (data.getExamYear() != null && (data.getExamYear() < 1 || data.getExamYear() > 9999))
            throw new ServiceException("年份无效", 400);
        dictionary.validate("kb_subject", data.getSubject(), "学科");
        dictionary.validate("kb_exam_type", data.getExamType(), "考试类型");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void saveRelations(KbSource data) {
        if ("EXAM".equals(data.getSourceKind())) mapper.saveExam(data);
        else mapper.deleteExam(data.getId());
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
            assets.requireReady(link.getAssetId());
            link.setSourceId(data.getId());
            link.setRole(choice(link.getRole(), "ORIGINAL", "ORIGINAL", "EXCERPT", "EVIDENCE"));
            if (!keys.add(link.getAssetId() + ":" + link.getRole()))
                throw new ServiceException("附件关联重复", 400);
            wanted.add(link.getAssetId());
        }
        for (KbSourceAttachment old : mapper.selectAttachments(data.getId()))
            if (!wanted.contains(old.getAssetId()))
                unreferenced(
                        mapper.countAssetEvidence(data.getId(), old.getAssetId()), "附件仍被出处作为证据引用");
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
                "EXAM".equals(s.getSourceKind())
                        && matches
                        && "VERIFIED".equals(data.getMatchStatus())
                        && "VERIFIED".equals(s.getAuthenticityStatus()));
        return data;
    }
}
