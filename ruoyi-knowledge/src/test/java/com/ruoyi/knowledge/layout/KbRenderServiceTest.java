package com.ruoyi.knowledge.layout;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.knowledge.catalog.KbOutlineScope;
import com.ruoyi.knowledge.domain.KbQuestion;
import com.ruoyi.knowledge.mapper.*;
import com.ruoyi.knowledge.service.*;
import com.ruoyi.knowledge.storage.IKbAssetService;

import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.*;

class KbRenderServiceTest {
    KbLayoutMapper mapper;
    IKbExperienceService templates;
    IKbQuestionService questions;
    KbQuestionMapper questionMapper;
    IKbCatalogService catalog;
    KbOccurrenceMapper occurrenceMapper;
    IKbSourceService sources;
    IKbAssetService assets;
    KbRenderServiceImpl service;
    KbQuestion question;
    KbRenderJob saved;
    List<KbRenderItem> items = new ArrayList<>();

    @BeforeEach
    void setup() {
        mapper = mock(KbLayoutMapper.class);
        templates = mock(IKbExperienceService.class);
        questions = mock(IKbQuestionService.class);
        questionMapper = mock(KbQuestionMapper.class);
        catalog = mock(IKbCatalogService.class);
        occurrenceMapper = mock(KbOccurrenceMapper.class);
        sources = mock(IKbSourceService.class);
        assets = mock(IKbAssetService.class);
        service =
                new KbRenderServiceImpl(
                        mapper,
                        templates,
                        questions,
                        questionMapper,
                        mock(KbMaterialMapper.class),
                        mock(IKbMaterialService.class),
                        occurrenceMapper,
                        catalog,
                        assets,
                        mock(KbKpMapper.class),
                        sources,
                        mock(KbProductMapper.class),
                        mock(IKbProductService.class), mock(com.ruoyi.knowledge.review.KbReviewService.class));
        var template = KbLayoutProtocolTest.template();
        KbLayoutProtocol.template(template);
        when(mapper.lockVersion(1L)).thenReturn(template);
        when(templates.usableVersion(1L)).thenReturn(template);
        question = new KbQuestion();
        question.setId(7L);
        question.setRowVersion(2L);
        question.setStatus("READY");
        question.setSubject("MATH");
        question.setTitle("冻结题");
        question.setStemHtml("<p>原题</p>");
        question.setAnswerHtml("<p>原答案</p>");
        question.setKpIds(List.of());
        question.setAssetIds(List.of());
        when(questionMapper.lockById(7L)).thenReturn(question);
        when(questions.get(7L)).thenReturn(question);
        when(questions.getMany(anyList())).thenReturn(List.of(question));
        when(occurrenceMapper.selectList(any())).thenReturn(List.of());
        SysUser user = new SysUser();
        user.setUserId(42L);
        user.setUserName("agent");
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                new LoginUser(42L, null, user, Set.of()), null, List.of()));
        when(mapper.insertJob(any()))
                .thenAnswer(
                        call -> {
                            saved = call.getArgument(0);
                            saved.setId(88L);
                            saved.setStatus("QUEUED");
                            return 1;
                        });
        when(mapper.job(88L)).thenAnswer(call -> saved);
        when(mapper.insertItem(any()))
                .thenAnswer(
                        call -> {
                            items.add(call.getArgument(0));
                            return 1;
                        });
        when(mapper.items(88L)).thenAnswer(call -> items);
        when(mapper.jobAssets(88L)).thenReturn(List.of());
        when(mapper.outputs(88L)).thenReturn(List.of());
        when(mapper.questionSourceIds(anyList())).thenReturn(List.of());
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    KbRenderRequest request() {
        var r = new KbRenderRequest();
        r.setIdempotencyKey("test:key");
        r.setTemplateVersionId(1L);
        r.setParameters(JSONObject.of("title", "新成品"));
        r.setOutputRoles(List.of("STUDENT", "INLINE_ANSWER"));
        var scope = new KbOutlineScope();
        scope.setOutlineId(9L);
        scope.setScopeKind("CATALOG_ONLY");
        r.setOutlineScopes(List.of(scope));
        var s = new KbRenderSection();
        s.setKey("day-1");
        s.setTitle("第一天");
        var q = new KbRenderQuestionRef();
        q.setQuestionId(7L);
        q.setRowVersion(2L);
        s.setQuestionRefs(List.of(q));
        r.setSections(List.of(s));
        return r;
    }

    @Test
    void exactKeyLookupUsesActualActorAndDoesNotRevalidateOrWriteFrozenJob() {
        var query = new KbRenderJob();query.setIdempotencyKey("test:key");query.setActorUserId(999L);
        var found = new KbRenderJob();found.setId(88L);found.setIdempotencyKey("test:key");found.setRequestHash("a".repeat(64));
        when(mapper.jobs(any())).thenReturn(List.of(found));
        assertEquals(List.of(found),service.list(query));
        assertEquals(42L,query.getActorUserId());
        verify(mapper).jobs(query);
        verifyNoMoreInteractions(mapper);
        verifyNoInteractions(questions,questionMapper,templates,catalog,sources,assets);
    }

    @Test
    void administratorExactKeyLookupCannotSubstituteAnotherActor() {
        var user = new SysUser();user.setUserId(1L);user.setUserName("admin");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new LoginUser(1L,null,user,Set.of()),null,List.of()));
        var query = new KbRenderJob();query.setIdempotencyKey("test:key");query.setActorUserId(42L);
        service.list(query);
        assertEquals(1L,query.getActorUserId());
        verify(mapper).jobs(query);
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void emptyExactKeyCannotFallBackToUnfilteredListing() {
        var query = new KbRenderJob();query.setIdempotencyKey(" ");
        assertThrows(ServiceException.class,()->service.list(query));
        verifyNoInteractions(mapper);
    }

    @Test
    void confirmedOutlineDoesNotWarnAboutUnverifiedTextbook() {
        doAnswer(
                        call -> {
                            var product =
                                    (com.ruoyi.knowledge.domain.KbProduct) call.getArgument(0);
                            product.getOutlineScopes()
                                    .forEach(scope -> scope.setOutlineStatus("CONFIRMED"));
                            return null;
                        })
                .when(catalog)
                .prepareProduct(any(), any());
        assertTrue(service.validate(request()).getWarnings().isEmpty());
    }

    @Test
    void draftOutlineWarnsWithoutTreatingGenerationAsConfirmation() {
        doAnswer(
                        call -> {
                            var product =
                                    (com.ruoyi.knowledge.domain.KbProduct) call.getArgument(0);
                            product.getOutlineScopes()
                                    .forEach(scope -> scope.setOutlineStatus("DRAFT"));
                            return null;
                        })
                .when(catalog)
                .prepareProduct(any(), any());
        assertEquals(List.of("教材大纲 9 仍待核实；生成不代表教材核实"), service.validate(request()).getWarnings());
    }

    @Test
    void submissionFreezesBodiesBeforeLaterQuestionUpdate() {
        var job = service.submit(request());
        assertEquals("<p>原题</p>", job.getItems().get(0).getSnapshot().getStemHtml());
        question.setStemHtml("<p>后来改题</p>");
        assertEquals(
                "<p>原题</p>",
                JSON.parseObject(items.get(0).getSnapshotJson(), KbRenderQuestionSnapshot.class)
                        .getQuestion()
                        .getStemHtml());
        assertEquals(2L, items.get(0).getQuestionRowVersion());
    }

    @Test
    void templateLayoutOptionsAreFrozenAndPassedToRenderer() {
        var template = templates.usableVersion(1L);
        template.getDefinition().getOrganization().setAnswerOnlyContinuous(true);
        template.getDefinition().getContent().setSubquestionLayout("MATCH_NUMBERED");
        var job = service.submit(request());

        template.getDefinition().getOrganization().setAnswerOnlyContinuous(false);
        template.getDefinition().getContent().setSubquestionLayout(null);
        service.hydrate(job);
        var input = new KbRendererInput(
                "kb-render-input/v1", job.getId().toString(), job.getTemplateSnapshot(),
                job.getConfigSnapshot(), job.getItems(), job.getAssets());
        var definition = JSON.parseObject(KbLayoutProtocol.json(input))
                .getJSONObject("template").getJSONObject("definition");
        assertTrue(definition.getJSONObject("organization").getBoolean("answerOnlyContinuous"));
        assertEquals("MATCH_NUMBERED", definition.getJSONObject("content").getString("subquestionLayout"));
    }

    @Test
    void questionLabelIsFrozenAsPrintConfigurationWithoutChangingQuestionBody() {
        var r = request();
        var section = tools.jackson.databind.json.JsonMapper.builder().build().readValue(
                """
                {"key":"source","questionLabel":" 【原题·教材第41页·第3题】 ",
                 "questionRefs":[{"questionId":"7","rowVersion":"2"}]}
                """, KbRenderSection.class);
        r.setSections(List.of(section));
        var job = service.submit(r);
        section.setQuestionLabel("后来修改的打印标签");
        service.hydrate(job);
        var input = new KbRendererInput(
                "kb-render-input/v1", job.getId().toString(), job.getTemplateSnapshot(),
                job.getConfigSnapshot(), job.getItems(), job.getAssets());
        var config = JSON.parseObject(KbLayoutProtocol.json(input)).getJSONObject("config");
        assertEquals("【原题·教材第41页·第3题】",
                config.getJSONArray("sections").getJSONObject(0).getString("questionLabel"));
        assertEquals("<p>原题</p>", question.getStemHtml());
        assertEquals("<p>原题</p>", job.getItems().get(0).getSnapshot().getStemHtml());
    }

    @Test
    void questionLabelRejectsHtmlControlCharactersAndAmbiguousMultipleQuestions() {
        for (String label : List.of("<b>原题</b>", "原题\n第3题", "题".repeat(201))) {
            var r = request();
            r.getSections().get(0).setQuestionLabel(label);
            assertEquals(400,
                    assertThrows(ServiceException.class, () -> service.submit(r)).getCode());
        }
        var r = request();
        var section = r.getSections().get(0);
        section.setQuestionLabel("【原题】");
        section.setQuestionRefs(List.of(section.getQuestionRefs().get(0), section.getQuestionRefs().get(0)));
        assertEquals(400,
                assertThrows(ServiceException.class, () -> service.submit(r)).getCode());
        verify(mapper, never()).insertJob(any());
    }

    @Test
    void blankQuestionLabelPreservesExistingMultipleQuestionSections() {
        var r = request();
        var section = r.getSections().get(0);
        section.setQuestionLabel("  ");
        section.setQuestionRefs(List.of(section.getQuestionRefs().get(0), section.getQuestionRefs().get(0)));
        var result = service.validate(r);
        assertEquals(2, result.getPositionCount());
        assertNull(result.getNormalizedRequest().getSections().get(0).getQuestionLabel());
    }

    @Test
    void staleVersionRejectedBeforeInsert() {
        question.setRowVersion(3L);
        assertEquals(
                409,
                assertThrows(ServiceException.class, () -> service.submit(request())).getCode());
        verify(mapper, never()).insertJob(any());
    }

    @Test
    void sameKeyReturnsOldTaskEvenAfterQuestionChange() {
        var r = request();
        var first = service.submit(r);
        when(mapper.ownKey(42L, "test:key")).thenReturn(saved);
        question.setRowVersion(99L);
        clearInvocations(questions);
        assertEquals(first.getId(), service.submit(r).getId());
        verifyNoInteractions(questions);
    }

    @Test
    void sameKeyDifferentConfigConflicts() {
        var r = request();
        service.submit(r);
        when(mapper.ownKey(42L, "test:key")).thenReturn(saved);
        r.setParameters(JSONObject.of("title", "别的成品"));
        assertEquals(409, assertThrows(ServiceException.class, () -> service.submit(r)).getCode());
    }

    @Test
    void repeatedQuestionInDifferentDayIsTwoPositions() {
        var r = request();
        var second =
                JSON.parseObject(
                        KbLayoutProtocol.json(r.getSections().get(0)), KbRenderSection.class);
        second.setKey("day-2");
        r.setSections(List.of(r.getSections().get(0), second));
        var valid = service.validate(r);
        assertEquals(2, valid.getPositionCount());
        assertEquals(1, valid.getUniqueQuestionCount());
    }

    @Test
    void missingImageRefIsRejected() {
        question.setStemHtml("<img src='local.png'>");
        assertThrows(ServiceException.class, () -> service.submit(request()));
        verify(mapper, never()).insertJob(any());
    }

    @Test
    void otherActorCannotReadTask() {
        service.submit(request());
        saved.setActorUserId(999L);
        assertEquals(403, assertThrows(ServiceException.class, () -> service.get(88L)).getCode());
    }

    @Test
    void referencedOrActiveTaskCannotBeRemoved() {
        service.submit(request());
        when(mapper.lockJob(88L)).thenReturn(saved);
        assertThrows(ServiceException.class, () -> service.delete(88L));
        verify(mapper, never()).deleteJob(any());
    }

    @Test
    void duplicateConcurrentSubmitUsesFreshLockingLookup() {
        var first = service.submit(request());
        clearInvocations(mapper);
        when(mapper.ownKey(42L, "test:key")).thenReturn(null);
        doThrow(new org.springframework.dao.DuplicateKeyException("concurrent winner"))
                .when(mapper)
                .insertJob(any());
        when(mapper.lockOwnKey(42L, "test:key")).thenReturn(saved);
        assertEquals(first.getId(), service.submit(request()).getId());
        verify(mapper).lockOwnKey(42L, "test:key");
        verify(mapper, never()).insertItem(any());
    }

    @Test
    void sourceLocksPrecedeQuestionLocksAndDiscoveryIsCheckedAgain() {
        when(mapper.questionSourceIds(List.of(7L))).thenReturn(List.of(3L, 5L));
        when(mapper.lockSource(anyLong())).thenAnswer(call -> call.getArgument(0));
        service.validate(request());
        var order = inOrder(mapper, questionMapper);
        order.verify(mapper).questionSourceIds(List.of(7L));
        order.verify(mapper).lockSource(3L);
        order.verify(mapper).lockSource(5L);
        order.verify(questionMapper).lockById(7L);
        order.verify(mapper).questionSourceIds(List.of(7L));
    }

    @Test
    void newlyCommittedSourceCausesRetryInsteadOfReverseLocking() {
        when(mapper.questionSourceIds(List.of(7L))).thenReturn(List.of(3L), List.of(3L, 5L));
        when(mapper.lockSource(3L)).thenReturn(3L);
        assertEquals(
                409,
                assertThrows(ServiceException.class, () -> service.submit(request())).getCode());
        verify(mapper, never()).lockSource(5L);
        verify(mapper, never()).insertJob(any());
        verify(catalog, never()).prepareProduct(any(), any());
    }

    @Test
    void frozenSourceAttachmentsKeepOccurrenceAndExamEvidenceReferenced() {
        when(mapper.questionSourceIds(List.of(7L))).thenReturn(List.of(3L));
        when(mapper.lockSource(3L)).thenReturn(3L);
        var occurrence = new com.ruoyi.knowledge.domain.KbOccurrence();
        occurrence.setId(31L);
        occurrence.setSourceId(3L);
        occurrence.setQuestionId(7L);
        occurrence.setEvidenceAssetId(77L);
        when(occurrenceMapper.forQuestions(anyList())).thenReturn(List.of(occurrence));
        var source = new com.ruoyi.knowledge.domain.KbSource();
        source.setId(3L);
        source.setTitle("原卷与核实附件");
        source.setAssetIds(List.of(77L, 78L));
        var exam = new com.ruoyi.knowledge.catalog.KbExamDetail();
        exam.setEvidenceAssetId(78L);
        source.setExamDetail(exam);
        when(sources.get(3L)).thenReturn(source);
        for (long id : List.of(77L, 78L)) {
            var asset = new com.ruoyi.knowledge.storage.domain.KbAsset();
            asset.setAssetId(id);
            asset.setContentType("application/pdf");
            asset.setSha256("fixture-sha-" + id);
            when(assets.requireReady(id)).thenReturn(asset);
        }
        service.submit(request());
        var captured = org.mockito.ArgumentCaptor.forClass(KbLayoutAsset.class);
        verify(mapper, times(2)).insertJobAsset(captured.capture());
        assertEquals(
                List.of(77L, 78L),
                captured.getAllValues().stream().map(KbLayoutAsset::getAssetId).sorted().toList());
        assertTrue(
                captured.getAllValues().stream()
                        .allMatch(a -> "SOURCE_ORIGINAL".equals(a.getAssetRole())));
        source.setAssetIds(
                List.of()); // Later attachment changes cannot remove the frozen job links.
        assertEquals(2, captured.getAllValues().size());
        var frozen =
                JSON.parseObject(items.get(0).getSnapshotJson(), KbRenderQuestionSnapshot.class);
        assertEquals(77L, frozen.getOccurrences().get(0).getEvidenceAssetId());
        assertEquals(78L, frozen.getSources().get(0).getExamDetail().getEvidenceAssetId());
    }
}
