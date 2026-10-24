package com.ruoyi.knowledge.service.impl;

import static com.ruoyi.knowledge.service.impl.KnowledgeRules.*;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.knowledge.catalog.*;
import com.ruoyi.knowledge.domain.*;
import com.ruoyi.knowledge.mapper.*;
import com.ruoyi.knowledge.service.IKbCatalogService;
import com.ruoyi.knowledge.storage.IKbAssetService;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

/** Catalog invariants shared by the two typed aggregates; caller owns the root transaction. */
@Service
public class KbCatalogServiceImpl implements IKbCatalogService {
    private final KbCatalogMapper mapper;
    private final KbOutlineMapper outlines;
    private final KbOutlineNodeMapper nodes;
    private final KbKpMapper points;
    private final KbQuestionMapper questions;
    private final KbSourceMapper sources;
    private final IKbAssetService assets;
    private final KnowledgeDictionary dictionary;

    public KbCatalogServiceImpl(
            KbCatalogMapper mapper,
            KbOutlineMapper outlines,
            KbOutlineNodeMapper nodes,
            KbKpMapper points,
            KbQuestionMapper questions,
            KbSourceMapper sources,
            IKbAssetService assets,
            KnowledgeDictionary dictionary) {
        this.mapper = mapper;
        this.outlines = outlines;
        this.nodes = nodes;
        this.points = points;
        this.questions = questions;
        this.sources = sources;
        this.assets = assets;
        this.dictionary = dictionary;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static void limit(String s, int max, String field) {
        if (s != null && s.length() > max) throw new ServiceException(field + "过长", 400);
    }

    private static void year(Integer n) {
        if (n != null && (n < 1 || n > 9999)) throw new ServiceException("年份须为1至9999", 400);
    }

    private static <T> List<T> list(List<T> value) {
        return value == null ? List.of() : value;
    }

    private void enumChange(String type, String value, String old, boolean ready) {
        if (!Objects.equals(value, old) || ready) dictionary.validate(type, value, type);
    }

    private KbCatalogProfile legacy(String kind, KbCatalogDocument doc) {
        KbCatalogProfile p = new KbCatalogProfile();
        p.setCatalogStatus("DRAFT");
        p.setResourceType("UNKNOWN");
        p.setOriginKind("UNKNOWN");
        p.setProvenanceStatus("UNKNOWN");
        p.setLegacy(true);
        if (doc instanceof KbSource s) {
            p.setSubject(s.getSubject());
            p.setResourceType(legacyType(s.getSourceKind()));
            if ("AUTHORED".equals(s.getSourceKind())) p.setOriginKind("AUTHORED");
        }
        return p;
    }

    private String legacyType(String kind) {
        return switch (kind == null ? "" : kind) {
            case "EXAM" -> "EXAM_PAPER";
            case "HANDOUT" -> "HANDOUT";
            case "WORKBOOK" -> "WORKBOOK";
            default -> "UNKNOWN";
        };
    }

    private String sourceKind(KbCatalogProfile p) {
        return switch (p.getResourceType() == null ? "UNKNOWN" : p.getResourceType()) {
            case "EXAM_PAPER" -> "EXAM";
            case "HANDOUT" -> "HANDOUT";
            case "WORKBOOK" -> "WORKBOOK";
            default -> "OTHER";
        };
    }

    public void enrich(String kind, Long id, KbCatalogDocument target) {
        KbCatalogProfile p = mapper.profile(kind, id);
        if (p == null) p = legacy(kind, target);
        else p.setLegacy(false);
        target.setProfile(p);
        target.setOutlineScopes(mapper.scopes(kind, id));
        target.setDeclaredKpIds(mapper.declaredKps(kind, id));
        target.setActualKpIds(mapper.actualKps(kind, id));
        target.setRegions(mapper.regions(kind, id));
        if ("SOURCE".equals(kind)) {
            target.setExamDetail(mapper.exam(id));
            if (target.getExamDetail() != null
                    && "VERIFIED".equals(target.getExamDetail().getAuthenticityStatus())
                    && target.getExamDetail().getIdentityFingerprint() == null)
                target.getExamDetail().setLegacyVerification(true);
        } else {
            target.setQuestionRefs(mapper.productQuestions(id));
            target.setReferenceSources(mapper.productSources(id));
        }
        target.setReadinessReasons(readiness(kind, target));
        target.setMatchReasons(List.of());
    }

    /** Load relationships once for the current page, retaining each existing document instance. */
    public void enrichBatch(String kind, Map<Long, ? extends KbCatalogDocument> targets) {
        if (targets.isEmpty()) return;
        List<Long> ids = new ArrayList<>(targets.keySet());
        Map<Long, KbCatalogBatch> profiles = indexed(mapper.batchProfiles(kind, ids));
        Map<Long, KbCatalogBatch> scopes = indexed(mapper.batchScopes(kind, ids));
        Map<Long, KbCatalogBatch> kps = indexed(mapper.batchKps(kind, ids));
        Map<Long, KbCatalogBatch> regions = indexed(mapper.batchRegions(kind, ids));
        Map<Long, KbCatalogBatch> exams = "SOURCE".equals(kind)
                ? indexed(mapper.batchExams(ids)) : Map.of();
        Map<Long, KbCatalogBatch> questions = "PRODUCT".equals(kind)
                ? indexed(mapper.batchProductQuestions(ids)) : Map.of();
        Map<Long, KbCatalogBatch> sources = "PRODUCT".equals(kind)
                ? indexed(mapper.batchProductSources(ids)) : Map.of();
        targets.forEach((id, target) -> {
            applyProfile(kind, target, profiles.get(id));
            target.setOutlineScopes(scopes.containsKey(id) ? list(scopes.get(id).getOutlineScopes()) : List.of());
            target.setDeclaredKpIds(kps.containsKey(id) ? list(kps.get(id).getDeclaredKpIds()) : List.of());
            target.setActualKpIds(kps.containsKey(id) ? list(kps.get(id).getActualKpIds()) : List.of());
            target.setRegions(regions.containsKey(id) ? list(regions.get(id).getRegions()) : List.of());
            if ("SOURCE".equals(kind)) applyExam(target, exams.get(id));
            else {
                target.setQuestionRefs(questions.containsKey(id) ? list(questions.get(id).getQuestionRefs()) : List.of());
                target.setReferenceSources(sources.containsKey(id) ? list(sources.get(id).getReferenceSources()) : List.of());
            }
            target.setReadinessReasons(readiness(kind, target));
            target.setMatchReasons(List.of());
        });
    }

    /** Occurrence flags need exact exam identity, not unrelated scopes, attachments or question lists. */
    public void enrichExamSources(Map<Long, KbSource> targets) {
        if (targets.isEmpty()) return;
        List<Long> ids = new ArrayList<>(targets.keySet());
        Map<Long, KbCatalogBatch> profiles = indexed(mapper.batchProfiles("SOURCE", ids));
        Map<Long, KbCatalogBatch> regions = indexed(mapper.batchRegions("SOURCE", ids));
        Map<Long, KbCatalogBatch> exams = indexed(mapper.batchExams(ids));
        targets.forEach((id, target) -> {
            applyProfile("SOURCE", target, profiles.get(id));
            target.setRegions(regions.containsKey(id) ? list(regions.get(id).getRegions()) : List.of());
            applyExam(target, exams.get(id));
            target.setSubject(target.getProfile().getSubject());
        });
    }

    public Map<Long, List<KbOccurrenceCollection>> collectionsBatch(List<Long> ids) {
        Map<Long, List<KbOccurrenceCollection>> result = new HashMap<>();
        if (!ids.isEmpty()) {
            for (KbCatalogBatch row : mapper.batchCollections(ids))
                result.put(row.getOwnerId(), list(row.getCollections()));
        }
        return result;
    }

    private Map<Long, KbCatalogBatch> indexed(List<KbCatalogBatch> rows) {
        Map<Long, KbCatalogBatch> result = new HashMap<>();
        for (KbCatalogBatch row : rows) result.put(row.getOwnerId(), row);
        return result;
    }

    private void applyProfile(String kind, KbCatalogDocument target, KbCatalogBatch row) {
        KbCatalogProfile profile = row == null ? null : row.getProfile();
        if (profile == null) profile = legacy(kind, target);
        else profile.setLegacy(false);
        target.setProfile(profile);
    }

    private void applyExam(KbCatalogDocument target, KbCatalogBatch row) {
        KbExamDetail exam = row == null ? null : row.getExamDetail();
        if (exam != null && "VERIFIED".equals(exam.getAuthenticityStatus())
                && exam.getIdentityFingerprint() == null) exam.setLegacyVerification(true);
        target.setExamDetail(exam);
    }

    private void merge(String kind, KbCatalogDocument data, KbCatalogDocument old) {
        KbCatalogProfile prior = old == null ? legacy(kind, data) : old.getProfile();
        KbCatalogProfile profile = new KbCatalogProfile();
        profile.apply(prior);
        if (data.getProfile() != null) profile.apply(data.getProfile());
        if (profile.getCatalogStatus() == null)
            throw new ServiceException("catalogStatus不能清空", 400);
        if (profile.getResourceType() == null) profile.setResourceType("UNKNOWN");
        if (profile.getOriginKind() == null) profile.setOriginKind("UNKNOWN");
        if (profile.getProvenanceStatus() == null) profile.setProvenanceStatus("UNKNOWN");
        boolean ready =
                "READY".equals(profile.getCatalogStatus())
                        && !"READY".equals(prior.getCatalogStatus());
        enumChange("kb_subject", profile.getSubject(), prior.getSubject(), ready);
        enumChange("kb_resource_type", profile.getResourceType(), prior.getResourceType(), ready);
        enumChange("kb_origin_kind", profile.getOriginKind(), prior.getOriginKind(), ready);
        enumChange(
                "kb_catalog_status",
                profile.getCatalogStatus(),
                prior.getCatalogStatus(),
                old == null || ready);
        enumChange("kb_audience_role", profile.getAudienceRole(), prior.getAudienceRole(), ready);
        enumChange(
                "kb_provenance_status",
                profile.getProvenanceStatus(),
                prior.getProvenanceStatus(),
                ready);
        choice(profile.getCatalogStatus(), "DRAFT", "DRAFT", "READY", "RETIRED");
        choice(profile.getProvenanceStatus(), "UNKNOWN", "UNKNOWN", "DECLARED", "TRACEABLE");
        limit(profile.getSubject(), 40, "subject");
        limit(profile.getResourceType(), 40, "resourceType");
        limit(profile.getOriginKind(), 40, "originKind");
        limit(profile.getAudienceRole(), 30, "audienceRole");
        limit(profile.getDescription(), 10000, "description");
        limit(profile.getAuthorName(), 200, "authorName");
        limit(profile.getPublisherName(), 200, "publisherName");
        year(profile.getPublishedYear());
        year(profile.getPreparedYear());
        if ("OTHER".equals(profile.getResourceType()) && blank(profile.getDescription()))
            throw new ServiceException("其他资料类型必须填写description", 400);
        if ("SOURCE".equals(kind) && profile.getAudienceRole() != null)
            throw new ServiceException("audienceRole仅适用于成品", 400);
        if ("PRODUCT".equals(kind) && profile.getAcquiredAt() != null)
            throw new ServiceException("acquiredAt仅适用于来源", 400);
        data.setProfile(profile);
        if (data.getOutlineScopes() == null)
            data.setOutlineScopes(old == null ? List.of() : old.getOutlineScopes());
        if (data.getDeclaredKpIds() == null)
            data.setDeclaredKpIds(old == null ? List.of() : old.getDeclaredKpIds());
        if (data.getRegions() == null) data.setRegions(old == null ? List.of() : old.getRegions());
        List<Long> wanted = ids(data.getDeclaredKpIds());
        for (Long id : wanted.stream().sorted().toList()) {
            KbKp kp = found(points.lockById(id));
            if (!Objects.equals(kp.getSubject(), profile.getSubject()))
                throw new ServiceException("资料和声明考点学科必须一致", 400);
        }
        data.setDeclaredKpIds(wanted);
        validateScopes(data, old, ready);
        validateRegions(kind, data, old, ready);
    }

    private void validateScopes(KbCatalogDocument data, KbCatalogDocument old, boolean ready) {
        Set<Long> used = new HashSet<>();
        List<KbOutlineScope> ordered = new ArrayList<>(list(data.getOutlineScopes()));
        ordered.sort(
                Comparator.comparing(
                        KbOutlineScope::getOutlineId,
                        Comparator.nullsFirst(Comparator.naturalOrder())));
        for (KbOutlineScope scope : ordered) {
            if (scope.getOutlineId() == null || !used.add(scope.getOutlineId()))
                throw new ServiceException("教材范围缺少outlineId或重复", 400);
            KbOutline outline = found(outlines.lockById(scope.getOutlineId()));
            if (!"TEXTBOOK".equals(outline.getCatalogKind())
                    || !Objects.equals(data.getProfile().getSubject(), outline.getSubject()))
                throw new ServiceException("资料必须挂同学科TEXTBOOK大纲", 400);
            String previous =
                    old == null
                            ? null
                            : list(old.getOutlineScopes()).stream()
                                    .filter(
                                            x ->
                                                    Objects.equals(
                                                            x.getOutlineId(), scope.getOutlineId()))
                                    .map(KbOutlineScope::getScopeKind)
                                    .findFirst()
                                    .orElse(null);
            scope.setScopeKind(
                    choice(
                            scope.getScopeKind(),
                            "CATALOG_ONLY",
                            "CATALOG_ONLY",
                            "WHOLE_BOOK",
                            "CHAPTERS"));
            enumChange("kb_scope_kind", scope.getScopeKind(), previous, ready);
            limit(scope.getNote(), 2000, "范围说明");
            List<Long> wanted = ids(scope.getNodeIds());
            if ("CHAPTERS".equals(scope.getScopeKind()) == wanted.isEmpty())
                throw new ServiceException("CHAPTERS须选章节，其他范围不能提交nodeIds", 400);
            Set<Long> selected = new HashSet<>(wanted);
            List<Long> normalized = new ArrayList<>();
            for (Long id : wanted.stream().sorted().toList()) {
                KbOutlineNode node = found(nodes.lockById(id));
                if (!Objects.equals(node.getOutlineId(), outline.getId()))
                    throw new ServiceException("章节不属于指定教材", 400);
                boolean covered = false;
                Set<Long> seen = new HashSet<>();
                Long parent = node.getParentId();
                while (parent != null && parent != 0) {
                    if (!seen.add(parent)) throw new ServiceException("章节关系存在循环", 409);
                    if (selected.contains(parent)) {
                        covered = true;
                        break;
                    }
                    parent = found(nodes.selectById(parent)).getParentId();
                }
                if (!covered) normalized.add(id);
            }
            scope.setNodeIds(normalized);
            scope.setOutlineTitle(outline.getTitle());
            scope.setSubject(outline.getSubject());
            scope.setTextbookVersion(outline.getTextbookVersion());
            scope.setGrade(outline.getGrade());
            scope.setTerm(outline.getTerm());
            scope.setOutlineStatus(outline.getStatus());
        }
        data.setOutlineScopes(ordered);
    }

    private void validateRegions(
            String kind, KbCatalogDocument data, KbCatalogDocument old, boolean ready) {
        Set<String> seen = new HashSet<>();
        for (KbRegionLink link : list(data.getRegions())) {
            link.setRegionCode(canonicalRegionCode(link.getRegionCode()));
            String role = choice(link.getRole(), null, "ORIGIN", "APPLICABLE", "EXAM_SCOPE");
            if ("PRODUCT".equals(kind) && !"APPLICABLE".equals(role))
                throw new ServiceException("成品只允许APPLICABLE区域", 400);
            if ("EXAM_SCOPE".equals(role)
                    && !"EXAM_PAPER".equals(data.getProfile().getResourceType()))
                throw new ServiceException("非考试来源不能登记考试区域", 400);
            if (blank(link.getRegionCode()) || !seen.add(role + ":" + link.getRegionCode()))
                throw new ServiceException("区域缺少代码或重复", 400);
            KbRegion region = found(mapper.region(link.getRegionCode()));
            boolean unchanged =
                    old != null
                            && list(old.getRegions()).stream()
                                    .anyMatch(
                                            x ->
                                                    Objects.equals(x.getRole(), role)
                                                            && Objects.equals(
                                                                    x.getRegionCode(),
                                                                    link.getRegionCode()));
            if (!unchanged || ready) {
                dictionary.validate("kb_region_role", role, "区域角色");
                if (!"0".equals(region.getStatus())) throw new ServiceException("区域已停用", 400);
            }
            link.setName(region.getName());
            link.setFullName(region.getFullName());
        }
    }

    public void prepareSource(KbSource data, KbSource old) {
        if (data.getProfile() == null
                && data.getSourceKind() != null
                && (old == null || !Objects.equals(data.getSourceKind(), old.getSourceKind()))) {
            KbCatalogProfile p = new KbCatalogProfile();
            p.setResourceType(legacyType(data.getSourceKind()));
            if ("AUTHORED".equals(data.getSourceKind())) p.setOriginKind("AUTHORED");
            data.setProfile(p);
        }
        if (data.getSubject() != null
                && (old == null || !Objects.equals(data.getSubject(), old.getSubject()))) {
            if (data.getProfile() == null) data.setProfile(new KbCatalogProfile());
            if (data.getProfile().has("subject")
                    && !Objects.equals(data.getProfile().getSubject(), data.getSubject()))
                throw new ServiceException("subject与profile.subject不一致", 400);
            data.getProfile().setSubject(data.getSubject());
        }
        merge("SOURCE", data, old);
        data.setSourceKind(sourceKind(data.getProfile()));
        data.setSubject(data.getProfile().getSubject());
        KbExamDetail prior = old == null ? null : old.getExamDetail();
        KbExamDetail patch = data.getExamDetail();
        if (!"EXAM".equals(data.getSourceKind())) {
            if (patch != null) throw new ServiceException("examDetail只适用于试卷来源", 400);
            data.setExamDetail(null);
            data.setAuthenticityStatus(null);
            return;
        }
        KbExamDetail exam = new KbExamDetail();
        if (prior != null) exam.apply(prior);
        if (patch != null) exam.apply(patch);
        if (data.getExamType() != null && (patch == null || !patch.has("examPurpose")))
            exam.setExamPurpose(data.getExamType());
        if (data.getExamYear() != null && (patch == null || !patch.has("examYear")))
            exam.setExamYear(data.getExamYear());
        if (data.getGrade() != null && (patch == null || !patch.has("grade")))
            exam.setGrade(data.getGrade());
        if (data.getSchoolName() != null && (patch == null || !patch.has("schoolName")))
            exam.setSchoolName(data.getSchoolName());
        if (data.getScopeNote() != null && (patch == null || !patch.has("scopeNote")))
            exam.setScopeNote(data.getScopeNote());
        String oldStatus = old == null ? null : old.getAuthenticityStatus();
        if (data.getAuthenticityStatus() != null
                        && !Objects.equals(data.getAuthenticityStatus(), oldStatus)
                        && !(oldStatus == null && "UNVERIFIED".equals(data.getAuthenticityStatus()))
                || patch != null
                        && patch.getAuthenticityStatus() != null
                        && !Objects.equals(patch.getAuthenticityStatus(), oldStatus)
                        && !(oldStatus == null
                                && "UNVERIFIED".equals(patch.getAuthenticityStatus())))
            throw new ServiceException("请使用独立来源核实命令", 403);
        exam.setAuthenticityStatus(oldStatus == null ? "UNVERIFIED" : oldStatus);
        if (prior != null) {
            exam.setVerifiedAt(prior.getVerifiedAt());
            exam.setVerifiedBy(prior.getVerifiedBy());
            exam.setIdentityFingerprint(prior.getIdentityFingerprint());
        }
        exam.setPaperNature(
                choice(
                        exam.getPaperNature(),
                        "UNKNOWN",
                        "ACTUAL_ADMINISTERED",
                        "SIMULATED",
                        "RECONSTRUCTED",
                        "COMPILED",
                        "UNKNOWN"));
        enumChange(
                "kb_exam_type",
                exam.getExamPurpose(),
                prior == null ? null : prior.getExamPurpose(),
                false);
        enumChange(
                "kb_entrance_stage",
                exam.getEntranceStage(),
                prior == null ? null : prior.getEntranceStage(),
                false);
        enumChange(
                "kb_organizer_level",
                exam.getOrganizerLevel(),
                prior == null ? null : prior.getOrganizerLevel(),
                false);
        enumChange(
                "kb_paper_nature",
                exam.getPaperNature(),
                prior == null ? null : prior.getPaperNature(),
                false);
        if (exam.getEntranceStage() != null && !"ENTRANCE".equals(exam.getExamPurpose()))
            throw new ServiceException("升学阶段仅适用于ENTRANCE", 400);
        if (exam.getTerm() != null) choice(exam.getTerm(), null, "FIRST", "SECOND");
        year(exam.getExamYear());
        year(exam.getAcademicYearStart());
        if (exam.getExamDate() != null
                && exam.getExamYear() != null
                && exam.getExamDate().getYear() != exam.getExamYear())
            throw new ServiceException("考试年份和日期不一致", 400);
        limit(exam.getGrade(), 20, "考试年级");
        limit(exam.getSchoolName(), 200, "学校");
        limit(exam.getScopeNote(), 2000, "考试范围");
        limit(exam.getSessionLabel(), 100, "场次");
        limit(exam.getPaperCode(), 100, "卷别");
        limit(exam.getOrganizerName(), 200, "组织者");
        limit(exam.getVerificationNote(), 10000, "核实说明");
        data.setExamDetail(exam);
        if (prior != null && !Objects.equals(identity(data), identity(old))) {
            exam.setAuthenticityStatus("UNVERIFIED");
            exam.setIdentityFingerprint(null);
        }
        mirrorExam(data);
    }

    private void mirrorExam(KbSource data) {
        KbExamDetail e = data.getExamDetail();
        if (e == null) return;
        data.setExamType(e.getExamPurpose());
        data.setExamYear(e.getExamYear());
        data.setGrade(e.getGrade());
        data.setSchoolName(e.getSchoolName());
        data.setScopeNote(e.getScopeNote());
        data.setAuthenticityStatus(e.getAuthenticityStatus());
        data.setVerificationNote(e.getVerificationNote());
    }

    public void prepareProduct(KbProduct data, KbProduct old) {
        Map<Long, KbQuestion> locked = new HashMap<>();
        if (old == null) {
            for (Long id :
                    list(data.getReferenceSources()).stream()
                            .map(KbProductReferenceSource::getSourceId)
                            .filter(Objects::nonNull)
                            .distinct()
                            .sorted()
                            .toList()) found(sources.lockById(id));
            for (Long id :
                    list(data.getQuestionRefs()).stream()
                            .map(KbProductQuestionRef::getQuestionId)
                            .filter(Objects::nonNull)
                            .distinct()
                            .sorted()
                            .toList()) locked.put(id, found(questions.lockById(id)));
        }
        merge("PRODUCT", data, old);
        if (data.getExamDetail() != null) throw new ServiceException("成品不能携带examDetail", 400);
        if (old != null) {
            if (data.getQuestionRefs() != null
                            && !sameRefs(data.getQuestionRefs(), old.getQuestionRefs())
                    || data.getReferenceSources() != null
                            && !sameSources(data.getReferenceSources(), old.getReferenceSources()))
                throw new ServiceException("成品题单和参考来源已冻结，请另存新版本", 409);
            data.setQuestionRefs(old.getQuestionRefs());
            data.setReferenceSources(old.getReferenceSources());
            if (!"UNKNOWN".equals(data.getProfile().getProvenanceStatus())
                    && list(old.getQuestionRefs()).isEmpty())
                throw new ServiceException("没有题单不能声明可追溯", 400);
            return;
        }
        List<KbProductQuestionRef> refs = list(data.getQuestionRefs());
        Set<Integer> seqs = new HashSet<>();
        for (KbProductQuestionRef ref : refs) {
            KbQuestion q = locked.get(ref.getQuestionId());
            if (q == null || ref.getSeq() == null || ref.getSeq() < 1 || !seqs.add(ref.getSeq()))
                throw new ServiceException("题单缺少题目或题序重复", 400);
            version(ref.getQuestionRowVersion(), q.getRowVersion());
            if (ref.getStemHash() != null && !Objects.equals(ref.getStemHash(), q.getStemHash()))
                throw new ServiceException("成品题目指纹已变化", 409);
            if (data.getProfile().getSubject() != null
                    && !Objects.equals(data.getProfile().getSubject(), q.getSubject()))
                throw new ServiceException("成品与题单学科不一致", 400);
            ref.setStemHash(q.getStemHash());
            limit(ref.getItemLocator(), 500, "题单定位");
        }
        if (!refs.isEmpty()) {
            for (int i = 1; i <= refs.size(); i++)
                if (!seqs.contains(i)) throw new ServiceException("题序必须从1连续排列", 400);
        }
        List<KbProductReferenceSource> references = list(data.getReferenceSources());
        Set<Long> sourceIds = new HashSet<>();
        for (KbProductReferenceSource ref :
                references.stream()
                        .sorted(
                                Comparator.comparing(
                                        KbProductReferenceSource::getSourceId,
                                        Comparator.nullsFirst(Comparator.naturalOrder())))
                        .toList()) {
            if (ref.getSourceId() == null || !sourceIds.add(ref.getSourceId()))
                throw new ServiceException("参考来源缺少ID或重复", 400);
            limit(ref.getNote(), 2000, "引用说明");
        }
        if (!"UNKNOWN".equals(data.getProfile().getProvenanceStatus()) && refs.isEmpty())
            throw new ServiceException("声明或可追溯题单不能为空", 400);
        data.setQuestionRefs(refs);
        data.setReferenceSources(references);
    }

    private boolean sameRefs(List<KbProductQuestionRef> a, List<KbProductQuestionRef> b) {
        if (a.size() != list(b).size()) return false;
        for (int i = 0; i < a.size(); i++) {
            var x = a.get(i);
            var y = b.get(i);
            if (!Objects.equals(x.getSeq(), y.getSeq())
                    || !Objects.equals(x.getQuestionId(), y.getQuestionId())
                    || !Objects.equals(x.getQuestionRowVersion(), y.getQuestionRowVersion())
                    || !Objects.equals(x.getStemHash(), y.getStemHash())
                    || !Objects.equals(x.getItemLocator(), y.getItemLocator())) return false;
        }
        return true;
    }

    private boolean sameSources(
            List<KbProductReferenceSource> a, List<KbProductReferenceSource> b) {
        if (a.size() != list(b).size()) return false;
        return a.stream()
                .allMatch(
                        x ->
                                b.stream()
                                        .anyMatch(
                                                y ->
                                                        Objects.equals(
                                                                        x.getSourceId(),
                                                                        y.getSourceId())
                                                                && Objects.equals(
                                                                        x.getNote(), y.getNote())));
    }

    private List<String> readiness(String kind, KbCatalogDocument data) {
        List<String> reasons = new ArrayList<>();
        KbCatalogProfile p = data.getProfile();
        if (blank(p.getSubject())) reasons.add("profile.subject: 缺少学科");
        if (blank(p.getResourceType()) || "UNKNOWN".equals(p.getResourceType()))
            reasons.add("profile.resourceType: 待分类");
        if (list(data.getOutlineScopes()).isEmpty()) reasons.add("outlineScopes: 尚未挂载教材大纲");
        if (data instanceof KbSource s
                && list(s.getAssetIds()).isEmpty()
                && list(s.getAttachments()).isEmpty()) reasons.add("attachments: 缺少来源原件");
        if (data instanceof KbProduct pdt && pdt.getAssetId() == null)
            reasons.add("assetId: 缺少成品文件");
        if ("PRODUCT".equals(kind) && blank(p.getAudienceRole()))
            reasons.add("profile.audienceRole: 缺少成品使用角色");
        return reasons;
    }

    private record ScopeState(Long outlineId, String scopeKind, String note, List<Long> nodeIds) {}

    private List<ScopeState> scopeStates(List<KbOutlineScope> scopes) {
        return list(scopes).stream()
                .map(
                        scope ->
                                new ScopeState(
                                        scope.getOutlineId(),
                                        scope.getScopeKind(),
                                        scope.getNote(),
                                        list(scope.getNodeIds()).stream().sorted().toList()))
                .sorted(Comparator.comparing(ScopeState::outlineId))
                .toList();
    }

    private void persistScopes(
            String kind, Long id, KbCatalogDocument data, String actor, boolean create) {
        List<KbOutlineScope> existing = create ? List.of() : list(mapper.scopes(kind, id));
        if (!create && scopeStates(existing).equals(scopeStates(data.getOutlineScopes()))) {
            for (KbOutlineScope scope : list(data.getOutlineScopes())) {
                existing.stream()
                        .filter(old -> Objects.equals(old.getOutlineId(), scope.getOutlineId()))
                        .findFirst()
                        .ifPresent(old -> scope.setId(old.getId()));
            }
            return;
        }
        mapper.deleteScopeNodes(kind, id);
        mapper.deleteScopes(kind, id);
        for (KbOutlineScope scope : list(data.getOutlineScopes())) {
            mapper.insertScope(kind, id, scope, actor);
            for (Long node : list(scope.getNodeIds()))
                mapper.insertScopeNode(kind, scope.getId(), node);
        }
    }

    public void persist(
            String kind, Long id, KbCatalogDocument data, String actor, boolean create) {
        List<String> reasons = readiness(kind, data);
        if ("READY".equals(data.getProfile().getCatalogStatus()) && !reasons.isEmpty())
            throw new ServiceException("资料不能设为READY: " + String.join("；", reasons), 400);
        mapper.saveProfile(kind, id, data.getProfile(), actor);
        persistScopes(kind, id, data, actor, create);
        mapper.deleteKps(kind, id);
        for (Long kp : list(data.getDeclaredKpIds())) mapper.insertKp(kind, id, kp);
        mapper.deleteRegions(kind, id);
        for (KbRegionLink region : list(data.getRegions())) mapper.insertRegion(kind, id, region);
        if (data instanceof KbSource source) {
            if (source.getExamDetail() != null) {
                validateEvidence(source, source.getExamDetail().getEvidenceAssetId());
                mapper.saveExamDetail(id, source.getExamDetail(), actor);
            } else mapper.deleteExamDetail(id);
        }
        if ("PRODUCT".equals(kind) && create) {
            for (KbProductQuestionRef ref : list(data.getQuestionRefs()))
                mapper.insertProductQuestion(id, ref);
            for (KbProductReferenceSource ref : list(data.getReferenceSources()))
                mapper.insertProductSource(id, ref);
        }
    }

    public void clearExam(Long sourceId) {
        mapper.deleteExamDetail(sourceId);
        sources.deleteExam(sourceId);
    }

    public void delete(String kind, Long id) {
        mapper.deleteScopeNodes(kind, id);
        mapper.deleteScopes(kind, id);
        mapper.deleteKps(kind, id);
        mapper.deleteRegions(kind, id);
        mapper.deleteProfile(kind, id);
        if ("SOURCE".equals(kind)) mapper.deleteExamDetail(id);
        else {
            mapper.deleteProductQuestions(id);
            mapper.deleteProductSources(id);
        }
    }

    private void validateEvidence(KbSource source, Long id) {
        if (id == null) return;
        if (list(source.getAttachments()).stream()
                        .noneMatch(a -> Objects.equals(id, a.getAssetId()))
                && !list(source.getAssetIds()).contains(id))
            throw new ServiceException("核实证据必须属于该来源附件", 400);
        assets.requireReady(id);
    }

    public KbExamDetail verify(KbSource source, KbExamVerificationCommand command, String actor) {
        if (!"EXAM".equals(source.getSourceKind()) || source.getExamDetail() == null)
            throw new ServiceException("仅考试来源可核实", 400);
        KbExamDetail e = source.getExamDetail();
        String status =
                choice(command.getAuthenticityStatus(), null, "VERIFIED", "REJECTED", "UNVERIFIED");
        dictionary.validate("kb_verification_status", status, "核实状态");
        e.setEvidenceAssetId(command.getEvidenceAssetId());
        e.setVerificationNote(command.getVerificationNote());
        validateEvidence(source, e.getEvidenceAssetId());
        if ("VERIFIED".equals(status)) {
            if (!"ACTUAL_ADMINISTERED".equals(e.getPaperNature())
                    || blank(source.getProfile().getSubject())
                    || blank(e.getExamPurpose())
                    || blank(e.getOrganizerName()) && blank(e.getSchoolName())
                    || e.getEvidenceAssetId() == null
                    || blank(e.getVerificationNote()))
                throw new ServiceException("核实须具备实际考试原卷、学科、考试用途、组织者或学校、附件证据及核实说明；未知年份可待补", 400);
            e.setVerifiedAt(LocalDateTime.now());
            e.setVerifiedBy(actor);
            e.setIdentityFingerprint(identity(source));
        } else e.setIdentityFingerprint(null);
        e.setAuthenticityStatus(status);
        mirrorExam(source);
        return e;
    }

    private String identity(KbSource s) {
        KbExamDetail e = s.getExamDetail();
        if (e == null) return "";
        List<Object> identity =
                Arrays.asList(
                        s.getProfile().getSubject(),
                        e.getExamPurpose(),
                        e.getExamYear(),
                        e.getGrade(),
                        e.getSchoolName(),
                        e.getScopeNote(),
                        e.getPaperNature() == null ? "UNKNOWN" : e.getPaperNature(),
                        e.getEntranceStage(),
                        e.getOrganizerLevel(),
                        e.getOrganizerName(),
                        e.getExamDate(),
                        e.getAcademicYearStart(),
                        e.getTerm(),
                        e.getSessionLabel(),
                        e.getPaperCode(),
                        e.getEvidenceAssetId(),
                        list(s.getRegions()).stream()
                                .filter(r -> "EXAM_SCOPE".equals(r.getRole()))
                                .map(KbRegionLink::getRegionCode)
                                .sorted()
                                .toList());
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(
                                            com.alibaba
                                                    .fastjson2
                                                    .JSON
                                                    .toJSONString(identity)
                                                    .getBytes(StandardCharsets.UTF_8)));
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public boolean realExam(KbSource s) {
        KbExamDetail e = s.getExamDetail();
        return "EXAM".equals(s.getSourceKind())
                && e != null
                && "ACTUAL_ADMINISTERED".equals(e.getPaperNature())
                && "VERIFIED".equals(s.getAuthenticityStatus())
                && e.getEvidenceAssetId() != null
                && e.getVerifiedAt() != null
                && Objects.equals(e.getIdentityFingerprint(), identity(s));
    }

    public List<KbRegion> regions(String parentCode, String keyword) {
        // Resolve the legacy CN alias in SQL so PageHelper applies to the list query.
        return mapper.listRegions(parentCode, keyword);
    }

    public KbRegion region(String code) {
        return found(mapper.region(canonicalRegionCode(code)));
    }

    private String canonicalRegionCode(String code) {
        // Keep a pre-import installation usable and old clients compatible.
        if ("CN".equals(code) && mapper.region("00000000") != null) return "00000000";
        return code;
    }

    public List<KbOccurrenceCollection> collections(Long id) {
        return mapper.collections(id);
    }

    public void saveCollections(
            KbOccurrence occurrence, KbCollectionsCommand command, String actor) {
        if (command.getCollections() == null)
            throw new ServiceException("必须提交collections数组，清空使用[]", 400);
        Set<String> kinds = new HashSet<>();
        for (KbOccurrenceCollection item : command.getCollections()) {
            item.setCollectionKind(text(item.getCollectionKind(), "收录用途", 40));
            dictionary.validate("kb_collection_kind", item.getCollectionKind(), "收录用途");
            if (!kinds.add(item.getCollectionKind())) throw new ServiceException("收录用途重复", 400);
            limit(item.getNote(), 2000, "收录说明");
            if (item.getEvidenceAssetId() != null) {
                if (sources.selectAttachments(occurrence.getSourceId()).stream()
                        .noneMatch(a -> Objects.equals(a.getAssetId(), item.getEvidenceAssetId())))
                    throw new ServiceException("错题证据必须属于原来源附件", 400);
                assets.requireReady(item.getEvidenceAssetId());
            }
        }
        mapper.deleteCollections(occurrence.getId());
        for (KbOccurrenceCollection item : command.getCollections())
            mapper.insertCollection(
                    occurrence.getId(), item, actor, occurrence.getRowVersion() + 1);
    }

    public long countSourceEvidence(Long sourceId, Long assetId) {
        return mapper.countSourceEvidence(sourceId, assetId);
    }

    public void prepareQuery(String kind, KbCatalogQuery q) {
        q.setChapterMatch(choice(q.getChapterMatch(), "EXACT", "EXACT", "SUBTREE"));
        q.setCoverageMode(choice(q.getCoverageMode(), "DECLARED", "DECLARED", "ACTUAL"));
        q.setHistoryMode(choice(q.getHistoryMode(), "ANY", "ANY", "LATEST"));
        if (q.getYearField() == null && (q.getYearFrom() != null || q.getYearTo() != null))
            throw new ServiceException("年份范围需要yearField", 400);
        if (q.getYearField() != null) {
            choice(
                    q.getYearField(),
                    null,
                    "publishedYear",
                    "preparedYear",
                    "examYear",
                    "academicYearStart");
            if (q.getYearFrom() == null && q.getYearTo() == null)
                throw new ServiceException("yearField需要年份上下界", 400);
        }
        year(q.getYearFrom());
        year(q.getYearTo());
        if (q.getYearFrom() != null && q.getYearTo() != null && q.getYearFrom() > q.getYearTo())
            throw new ServiceException("年份范围顺序无效", 400);
        if ("PRODUCT".equals(kind)
                && (q.getExamPurpose() != null
                        || q.getOrganizerLevel() != null
                        || q.getPaperNature() != null
                        || q.getExamYear() != null
                        || q.getAcademicYearStart() != null
                        || q.getAuthenticityStatus() != null
                        || "examYear".equals(q.getYearField())
                        || "academicYearStart".equals(q.getYearField())))
            throw new ServiceException("成品不支持考试条件", 400);
        if ("SOURCE".equals(kind)
                && (q.getAudienceRole() != null
                        || q.getProvenanceStatus() != null
                        || q.getMaterialId() != null
                        || q.getRootProductId() != null
                        || "LATEST".equals(q.getHistoryMode())))
            throw new ServiceException("来源不支持成品条件", 400);
        if (q.getRegionCode() != null) {
            q.setRegionCode(canonicalRegionCode(q.getRegionCode()));
            q.setRegionRole(choice(q.getRegionRole(), null, "ORIGIN", "APPLICABLE", "EXAM_SCOPE"));
            if ("PRODUCT".equals(kind) && !"APPLICABLE".equals(q.getRegionRole()))
                throw new ServiceException("成品仅支持适用地区筛选", 400);
            found(mapper.region(q.getRegionCode()));
            List<String> codes =
                    new ArrayList<>(
                            Boolean.TRUE.equals(q.getRegionDescendants())
                                    ? mapper.descendantRegions(q.getRegionCode())
                                    : List.of(q.getRegionCode()));
            if (Boolean.TRUE.equals(q.getIncludeNationwide())) {
                if (!"APPLICABLE".equals(q.getRegionRole()))
                    throw new ServiceException("全国扩展仅适用于APPLICABLE", 400);
                if (!codes.contains("CN")) codes.add("CN");
                if (mapper.region("00000000") != null && !codes.contains("00000000"))
                    codes.add("00000000");
            }
            if (codes.contains("00000000") && !codes.contains("CN")) codes.add("CN");
            q.setRegionCodes(codes);
        } else if (q.getRegionRole() != null
                || Boolean.TRUE.equals(q.getRegionDescendants())
                || Boolean.TRUE.equals(q.getIncludeNationwide()))
            throw new ServiceException("区域筛选需要regionCode", 400);
        if (q.getOutlineNodeId() != null) {
            KbOutlineNode node = found(nodes.selectById(q.getOutlineNodeId()));
            if (q.getOutlineId() != null && !Objects.equals(q.getOutlineId(), node.getOutlineId()))
                throw new ServiceException("筛选章节不属于指定教材", 400);
            q.setOutlineId(node.getOutlineId());
            KbOutlineNode filter = new KbOutlineNode();
            filter.setOutlineId(node.getOutlineId());
            List<KbOutlineNode> all = nodes.selectList(filter);
            Map<Long, KbOutlineNode> byId = new HashMap<>();
            for (KbOutlineNode item : all) byId.put(item.getId(), item);
            List<Long> ancestors = new ArrayList<>();
            Long p = node.getId();
            Set<Long> seen = new HashSet<>();
            while (p != null && p != 0) {
                if (!seen.add(p)) throw new ServiceException("章节存在循环", 409);
                KbOutlineNode current = byId.get(p);
                if (current == null) throw new ServiceException("章节父链不完整，请刷新后重试", 409);
                ancestors.add(p);
                p = current.getParentId();
            }
            q.setChapterAncestorIds(ancestors);
            Set<Long> targets = new HashSet<>();
            targets.add(node.getId());
            if ("SUBTREE".equals(q.getChapterMatch())) {
                boolean changed;
                do {
                    changed = false;
                    for (KbOutlineNode n : all)
                        if (targets.contains(n.getParentId())) changed |= targets.add(n.getId());
                } while (changed);
            }
            q.setChapterTargetIds(new ArrayList<>(targets));
        }
    }
}
