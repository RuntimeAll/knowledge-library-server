package com.ruoyi.knowledge.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.knowledge.catalog.*;
import com.ruoyi.knowledge.domain.*;
import com.ruoyi.knowledge.mapper.*;
import com.ruoyi.knowledge.storage.IKbAssetService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

class KbCatalogServiceTest {
    KbCatalogMapper mapper;
    KbOutlineMapper outlines;
    KbOutlineNodeMapper nodes;
    KbKpMapper points;
    KbQuestionMapper questions;
    KbSourceMapper sources;
    IKbAssetService assets;
    KnowledgeDictionary dictionary;
    KbCatalogServiceImpl service;

    @BeforeEach
    void setup() {
        mapper = mock(KbCatalogMapper.class);
        outlines = mock(KbOutlineMapper.class);
        nodes = mock(KbOutlineNodeMapper.class);
        points = mock(KbKpMapper.class);
        questions = mock(KbQuestionMapper.class);
        sources = mock(KbSourceMapper.class);
        assets = mock(IKbAssetService.class);
        dictionary = mock(KnowledgeDictionary.class);
        service =
                new KbCatalogServiceImpl(
                        mapper, outlines, nodes, points, questions, sources, assets, dictionary);
    }

    KbSource source() {
        KbSource s = new KbSource();
        s.setId(1L);
        s.setRowVersion(3L);
        s.setTitle("数学练习");
        s.setSourceKind("HANDOUT");
        s.setSubject("MATH");
        s.setAssetIds(List.of(9L));
        s.setAttachments(List.of());
        s.setOutlineScopes(List.of());
        s.setDeclaredKpIds(List.of());
        s.setRegions(List.of());
        KbCatalogProfile p = new KbCatalogProfile();
        p.setSubject("MATH");
        p.setResourceType("HANDOUT");
        p.setCatalogStatus("DRAFT");
        p.setOriginKind("UNKNOWN");
        p.setProvenanceStatus("UNKNOWN");
        p.setPreparedYear(2024);
        s.setProfile(p);
        return s;
    }

    KbOutline outline(Long id, String subject) {
        KbOutline o = new KbOutline();
        o.setId(id);
        o.setSubject(subject);
        o.setCatalogKind("TEXTBOOK");
        o.setStatus("DRAFT");
        return o;
    }

    KbOutlineScope scope(Long id, String kind, Long... nodeIds) {
        KbOutlineScope s = new KbOutlineScope();
        s.setOutlineId(id);
        s.setScopeKind(kind);
        s.setNodeIds(Arrays.asList(nodeIds));
        return s;
    }

    KbOutlineNode node(Long id, Long outlineId, Long parent) {
        KbOutlineNode n = new KbOutlineNode();
        n.setId(id);
        n.setOutlineId(outlineId);
        n.setParentId(parent);
        return n;
    }

    @Test
    void omittedValuesRemainAndExplicitNullClearsNullableScalar() {
        KbSource old = source(), input = new KbSource();
        KbCatalogProfile patch = new KbCatalogProfile();
        patch.setDescription("补充简介");
        input.setProfile(patch);
        service.prepareSource(input, old);
        assertEquals(2024, input.getProfile().getPreparedYear());
        assertEquals("MATH", input.getProfile().getSubject());
        assertEquals("HANDOUT", input.getSourceKind());
        KbSource clear = new KbSource();
        KbCatalogProfile cp = new KbCatalogProfile();
        cp.setPreparedYear(null);
        clear.setProfile(cp);
        service.prepareSource(clear, old);
        assertNull(clear.getProfile().getPreparedYear());
    }

    @Test
    void descriptionPatchPreservesUnsubmittedScopeIdentityAndAuditRows() {
        KbSource old = source();
        KbOutlineScope existing = scope(16L, "CATALOG_ONLY");
        existing.setId(81L);
        old.setOutlineScopes(List.of(existing));
        when(outlines.lockById(16L)).thenReturn(outline(16L, "MATH"));
        when(mapper.scopes("SOURCE", 1L)).thenReturn(List.of(existing));
        KbSource patch = new KbSource();
        KbCatalogProfile profile = new KbCatalogProfile();
        profile.setDescription("只补充资料简介");
        patch.setProfile(profile);

        service.prepareSource(patch, old);
        service.persist("SOURCE", 1L, patch, "editor", false);

        assertEquals(81L, patch.getOutlineScopes().get(0).getId());
        assertEquals("只补充资料简介", patch.getProfile().getDescription());
        verify(mapper).saveProfile("SOURCE", 1L, patch.getProfile(), "editor");
        verify(mapper, never()).deleteScopeNodes(anyString(), anyLong());
        verify(mapper, never()).deleteScopes(anyString(), anyLong());
        verify(mapper, never()).insertScope(anyString(), anyLong(), any(), anyString());
        verify(mapper, never()).insertScopeNode(anyString(), anyLong(), anyLong());
    }

    @ParameterizedTest
    @ValueSource(strings = {"SOURCE", "PRODUCT"})
    void reorderedScopesAndNodesRetainPersistedIdsForEitherOwner(String kind) {
        KbOutlineScope existing16 = scope(16L, "CHAPTERS", 21L, 20L);
        existing16.setId(81L);
        existing16.setNote("第一单元");
        KbOutlineScope existing17 = scope(17L, "WHOLE_BOOK");
        existing17.setId(82L);
        when(mapper.scopes(kind, 1L)).thenReturn(List.of(existing16, existing17));
        KbOutlineScope input16 = scope(16L, "CHAPTERS", 20L, 21L);
        input16.setNote("第一单元");
        input16.setId(999L);
        input16.setOutlineTitle("教材标题更新不改变范围");
        KbOutlineScope input17 = scope(17L, "WHOLE_BOOK");
        KbCatalogDocument input = new KbCatalogDocument();
        input.setProfile(source().getProfile());
        input.setOutlineScopes(List.of(input17, input16));

        service.persist(kind, 1L, input, "editor", false);

        assertEquals(81L, input16.getId());
        assertEquals(82L, input17.getId());
        verify(mapper, never()).deleteScopeNodes(anyString(), anyLong());
        verify(mapper, never()).deleteScopes(anyString(), anyLong());
        verify(mapper, never()).insertScope(anyString(), anyLong(), any(), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"outline", "kind", "note", "nodes", "clear"})
    void actualScopeChangesReplaceScopeRows(String change) {
        KbOutlineScope existing = scope(16L, "CHAPTERS", 20L);
        existing.setId(81L);
        existing.setNote("原范围说明");
        when(mapper.scopes("SOURCE", 1L)).thenReturn(List.of(existing));
        KbOutlineScope replacement = scope(16L, "CHAPTERS", 20L);
        replacement.setNote("原范围说明");
        switch (change) {
            case "outline" -> replacement.setOutlineId(17L);
            case "kind" -> {
                replacement.setScopeKind("WHOLE_BOOK");
                replacement.setNodeIds(List.of());
            }
            case "note" -> replacement.setNote("已核对的新范围说明");
            case "nodes" -> replacement.setNodeIds(List.of(21L));
        }
        KbSource input = source();
        input.setOutlineScopes("clear".equals(change) ? List.of() : List.of(replacement));

        service.persist("SOURCE", 1L, input, "editor", false);

        var order = inOrder(mapper);
        order.verify(mapper).deleteScopeNodes("SOURCE", 1L);
        order.verify(mapper).deleteScopes("SOURCE", 1L);
        if ("clear".equals(change))
            verify(mapper, never()).insertScope(anyString(), anyLong(), any(), anyString());
        else order.verify(mapper).insertScope("SOURCE", 1L, replacement, "editor");
    }

    @Test
    void readyRequiresAnExplicitTextbookScopeBeforeAnyWrite() {
        KbSource input = source();
        input.getProfile().setCatalogStatus("READY");
        service.prepareSource(input, null);
        ServiceException error =
                assertThrows(
                        ServiceException.class,
                        () -> service.persist("SOURCE", 1L, input, "editor", true));
        assertTrue(error.getMessage().contains("outlineScopes"));
        verify(mapper, never()).saveProfile(anyString(), anyLong(), any(), anyString());
    }

    @Test
    void draftCatalogMayBeUsedButScopeCannotCrossSubject() {
        KbSource input = source();
        input.getProfile().setCatalogStatus("READY");
        input.setOutlineScopes(List.of(scope(16L, "CATALOG_ONLY")));
        when(outlines.lockById(16L)).thenReturn(outline(16L, "MATH"));
        service.prepareSource(input, null);
        assertEquals("DRAFT", input.getOutlineScopes().get(0).getOutlineStatus());
        assertDoesNotThrow(() -> service.persist("SOURCE", 1L, input, "editor", true));
        KbSource invalid = source();
        invalid.setOutlineScopes(List.of(scope(17L, "CATALOG_ONLY")));
        when(outlines.lockById(17L)).thenReturn(outline(17L, "SCIENCE"));
        assertThrows(ServiceException.class, () -> service.prepareSource(invalid, null));
    }

    @Test
    void parentChapterCollapsesRedundantChildButForeignChapterFails() {
        when(outlines.lockById(16L)).thenReturn(outline(16L, "MATH"));
        when(nodes.lockById(20L)).thenReturn(node(20L, 16L, 0L));
        when(nodes.lockById(21L)).thenReturn(node(21L, 16L, 20L));
        KbSource input = source();
        input.setOutlineScopes(List.of(scope(16L, "CHAPTERS", 20L, 21L)));
        service.prepareSource(input, null);
        assertEquals(List.of(20L), input.getOutlineScopes().get(0).getNodeIds());
        when(nodes.lockById(99L)).thenReturn(node(99L, 17L, 0L));
        KbSource bad = source();
        bad.setOutlineScopes(List.of(scope(16L, "CHAPTERS", 99L)));
        assertThrows(ServiceException.class, () -> service.prepareSource(bad, null));
    }

    @Test
    void unsubmittedDisabledClassificationIsNotRevalidatedOnDescriptionEdit() {
        KbSource old = source();
        old.getProfile().setResourceType("HISTORIC_KIND");
        KbSource patch = new KbSource();
        KbCatalogProfile profile = new KbCatalogProfile();
        profile.setDescription("改说明");
        patch.setProfile(profile);
        service.prepareSource(patch, old);
        verify(dictionary, never()).validate(eq("kb_resource_type"), anyString(), anyString());
        assertEquals("HISTORIC_KIND", patch.getProfile().getResourceType());
    }

    @Test
    void regionsAreRoleSpecificAndNationwideRequiresExplicitApplicableRole() {
        KbCatalogQuery query = new KbCatalogQuery();
        query.setRegionRole("ORIGIN");
        query.setRegionCode("CN");
        query.setIncludeNationwide(true);
        KbRegion cn = new KbRegion();
        cn.setRegionCode("CN");
        when(mapper.region("CN")).thenReturn(cn);
        assertThrows(ServiceException.class, () -> service.prepareQuery("SOURCE", query));
        query.setRegionRole("APPLICABLE");
        assertDoesNotThrow(() -> service.prepareQuery("SOURCE", query));
        assertEquals(List.of("CN"), query.getRegionCodes());
    }

    @Test
    void productCannotUseExamFiltersAndChapterMustBelongToSelectedOutline() {
        KbCatalogQuery product = new KbCatalogQuery();
        product.setExamYear(2024);
        assertThrows(ServiceException.class, () -> service.prepareQuery("PRODUCT", product));
        KbCatalogQuery source = new KbCatalogQuery();
        source.setOutlineId(16L);
        source.setOutlineNodeId(99L);
        when(nodes.selectById(99L)).thenReturn(node(99L, 17L, 0L));
        assertThrows(ServiceException.class, () -> service.prepareQuery("SOURCE", source));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L})
    void chapterQueryAcceptsNullOrZeroRootAndExpandsOnlyItsSubtree(Long rootParent) {
        KbOutlineNode root = node(14L, 16L, rootParent);
        KbOutlineNode child = node(15L, 16L, 14L);
        KbOutlineNode unrelated = node(99L, 16L, null);
        when(nodes.selectById(14L)).thenReturn(root);
        when(nodes.selectById(15L)).thenReturn(child);
        when(nodes.selectList(any())).thenReturn(List.of(child, unrelated, root));
        KbCatalogQuery rootQuery = new KbCatalogQuery();
        rootQuery.setOutlineNodeId(14L);
        rootQuery.setChapterMatch("SUBTREE");

        service.prepareQuery("SOURCE", rootQuery);

        assertEquals(16L, rootQuery.getOutlineId());
        assertEquals(List.of(14L), rootQuery.getChapterAncestorIds());
        assertEquals(Set.of(14L, 15L), new HashSet<>(rootQuery.getChapterTargetIds()));
        KbCatalogQuery childQuery = new KbCatalogQuery();
        childQuery.setOutlineNodeId(15L);
        service.prepareQuery("PRODUCT", childQuery);
        assertEquals(List.of(15L, 14L), childQuery.getChapterAncestorIds());
        assertEquals(List.of(15L), childQuery.getChapterTargetIds());
    }

    @ParameterizedTest
    @ValueSource(strings = {"missingNode", "missingParent", "selfCycle", "ancestorCycle"})
    void chapterQueryRejectsIncompleteOrCyclicAncestorChains(String defect) {
        KbOutlineNode selected = node(15L, 16L, 14L);
        KbOutlineNode parent = node(14L, 16L, null);
        List<KbOutlineNode> tree = List.of(selected, parent);
        switch (defect) {
            case "missingNode" -> tree = List.of(parent);
            case "missingParent" -> tree = List.of(selected);
            case "selfCycle" -> selected.setParentId(15L);
            case "ancestorCycle" -> parent.setParentId(15L);
        }
        when(nodes.selectById(15L)).thenReturn(selected);
        when(nodes.selectList(any())).thenReturn(tree);
        KbCatalogQuery query = new KbCatalogQuery();
        query.setOutlineNodeId(15L);
        ServiceException error =
                assertThrows(ServiceException.class, () -> service.prepareQuery("SOURCE", query));
        assertEquals(409, error.getCode());
        assertNull(query.getChapterAncestorIds());
        assertNull(query.getChapterTargetIds());
    }

    @Test
    void actualMockExamRequiresEvidenceAndIndependentVerification() {
        KbSource s = source();
        s.setSourceKind("EXAM");
        s.getProfile().setResourceType("EXAM_PAPER");
        KbExamDetail e = new KbExamDetail();
        e.setPaperNature("ACTUAL_ADMINISTERED");
        e.setExamPurpose("MOCK");
        e.setExamYear(2024);
        e.setOrganizerName("某校");
        e.setAuthenticityStatus("UNVERIFIED");
        s.setExamDetail(e);
        KbExamVerificationCommand command = new KbExamVerificationCommand();
        command.setAuthenticityStatus("VERIFIED");
        command.setVerificationNote("原件考试日期、组织者和举办事实已人工核对");
        assertThrows(ServiceException.class, () -> service.verify(s, command, "reviewer"));
        command.setEvidenceAssetId(9L);
        service.verify(s, command, "reviewer");
        assertTrue(service.realExam(s));
        assertNotNull(e.getIdentityFingerprint());
        assertEquals("reviewer", e.getVerifiedBy());
        e.setPaperNature("SIMULATED");
        assertFalse(service.realExam(s));
    }

    @Test
    void actualSchoolExamCanBeVerifiedWithUnknownYearAndStillNeedsEvidence() {
        KbSource s = source();
        s.setSourceKind("EXAM");
        s.getProfile().setResourceType("EXAM_PAPER");
        KbExamDetail e = new KbExamDetail();
        e.setPaperNature("ACTUAL_ADMINISTERED");
        e.setExamPurpose("UNIT");
        e.setSchoolName("原卷标明的学校");
        s.setExamDetail(e);
        KbExamVerificationCommand command = new KbExamVerificationCommand();
        command.setAuthenticityStatus("VERIFIED");
        command.setVerificationNote("提供者确认学生实际考试；原卷及批改答题卡已核对，年份待补");
        assertThrows(ServiceException.class, () -> service.verify(s, command, "reviewer"));
        command.setEvidenceAssetId(9L);
        service.verify(s, command, "reviewer");
        assertNull(e.getExamYear());
        assertTrue(service.realExam(s));
        e.setExamYear(2026);
        assertFalse(service.realExam(s), "后补身份事实仍须重新核实，不绕过指纹校验");
        e.setExamYear(null);
        e.setPaperNature("SIMULATED");
        assertThrows(ServiceException.class, () -> service.verify(s, command, "reviewer"));
    }

    @Test
    void identityEditInvalidatesVerifiedSourceButDescriptionDoesNot() {
        KbSource old = source();
        old.setSourceKind("EXAM");
        old.getProfile().setResourceType("EXAM_PAPER");
        KbExamDetail e = new KbExamDetail();
        e.setPaperNature("ACTUAL_ADMINISTERED");
        e.setExamPurpose("FINAL");
        e.setExamYear(2024);
        e.setSchoolName("某校");
        old.setExamDetail(e);
        KbExamVerificationCommand command = new KbExamVerificationCommand();
        command.setAuthenticityStatus("VERIFIED");
        command.setEvidenceAssetId(9L);
        command.setVerificationNote("已核实");
        service.verify(old, command, "reviewer");
        KbSource description = new KbSource();
        KbCatalogProfile profile = new KbCatalogProfile();
        profile.setDescription("仅补充教学用途");
        description.setProfile(profile);
        service.prepareSource(description, old);
        assertEquals("VERIFIED", description.getAuthenticityStatus());
        KbSource changed = new KbSource();
        KbExamDetail patch = new KbExamDetail();
        patch.setExamYear(2025);
        changed.setExamDetail(patch);
        service.prepareSource(changed, old);
        assertEquals("UNVERIFIED", changed.getAuthenticityStatus());
        assertNull(changed.getExamDetail().getIdentityFingerprint());
    }

    @Test
    void ordinaryCreateCannotForgeVerifiedIdentity() {
        KbSource input = source();
        input.getProfile().setResourceType("EXAM_PAPER");
        input.setAuthenticityStatus("VERIFIED");
        assertThrows(ServiceException.class, () -> service.prepareSource(input, null));
    }

    @Test
    void productQuestionVersionConflictDoesNotCreateAssociations() {
        KbProduct input = new KbProduct();
        input.setAssetId(9L);
        input.setProfile(source().getProfile());
        KbProductQuestionRef ref = new KbProductQuestionRef();
        ref.setSeq(1);
        ref.setQuestionId(5L);
        ref.setQuestionRowVersion(2L);
        input.setQuestionRefs(List.of(ref));
        KbQuestion q = new KbQuestion();
        q.setId(5L);
        q.setRowVersion(3L);
        q.setSubject("MATH");
        when(questions.lockById(5L)).thenReturn(q);
        assertThrows(ServiceException.class, () -> service.prepareProduct(input, null));
        verify(mapper, never()).insertProductQuestion(anyLong(), any());
    }

    @Test
    void wrongCollectionEvidenceMustBeAttachedToItsOwnSource() {
        KbOccurrence occurrence = new KbOccurrence();
        occurrence.setId(7L);
        occurrence.setSourceId(1L);
        occurrence.setRowVersion(2L);
        KbOccurrenceCollection item = new KbOccurrenceCollection();
        item.setCollectionKind("WRONG_QUESTION");
        item.setEvidenceAssetId(9L);
        KbCollectionsCommand command = new KbCollectionsCommand();
        command.setCollections(List.of(item));
        when(sources.selectAttachments(1L)).thenReturn(List.of());
        assertThrows(
                ServiceException.class,
                () -> service.saveCollections(occurrence, command, "editor"));
        verify(mapper, never()).deleteCollections(anyLong());
    }
}
