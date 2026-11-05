package com.ruoyi.knowledge.publication;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.ruoyi.knowledge.domain.KbAgentSubmission;
import com.ruoyi.knowledge.mapper.KbAgentSubmissionMapper;
import com.ruoyi.knowledge.mapper.KbQuestionMapper;
import com.ruoyi.knowledge.mapper.PublicationMapper;
import com.ruoyi.knowledge.publication.PublicationModels.*;
import com.ruoyi.knowledge.publication.PublicationRows.*;
import com.ruoyi.knowledge.review.KbPublicationReviewBridge;
import com.ruoyi.knowledge.review.KbReviewGate;
import com.ruoyi.knowledge.review.KbReviewService;
import com.ruoyi.knowledge.storage.IKbAssetService;
import com.ruoyi.knowledge.storage.domain.KbAsset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

class PublicationTopicsTest {
    private static final String LEGACY_INPUT = "{\"baiduShares\":[],\"images\":[],\"listingDescription\":null,\"postBody\":\"正文\",\"postTitle\":\"标题\",\"products\":[],\"referenceSources\":[],\"remark\":null,\"title\":\"资料\"}";

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void emptyKeywordsKeepHistoricalInputSnapshotAndDependencyHashes() {
        Fixture f = new Fixture(); Input input = input();
        assertEquals(LEGACY_INPUT, f.support.encode(input));
        input.topicKeywords = List.of();
        assertEquals(LEGACY_INPUT, f.support.encode(input));
        assertEquals(PublicationSupport.digest(LEGACY_INPUT), f.support.hash(input));
        String snapshot = "{\"check\":null,\"deliveryMode\":null,\"originalCommand\":null,\"publication\":" + LEGACY_INPUT + "}";
        assertEquals(snapshot, f.support.encode(new Snapshot(input, null)));
        assertEquals(snapshot, f.support.encode(f.support.decode(snapshot, Snapshot.class)));
        var checks = new PublicationCheckService(f.service, mock(KbPublicationReviewBridge.class),
                mock(KbQuestionMapper.class), mock(KbReviewService.class));
        assertEquals(PublicationSupport.digest("[" + LEGACY_INPUT + ",[],[],[],[]]"), checks.dependency(1L, input, List.of()));
        input.topicKeywords = List.of("科学课件");
        assertNotEquals(PublicationSupport.digest(LEGACY_INPUT), f.support.hash(input));
    }

    @Test void createAndUpdatePersistOrderedKeywordsAndOldClientsPreserveThem() {
        Fixture f = new Fixture(); Command create = command("create");
        assertEquals(List.of(), f.service.create(create).topicKeywords);
        assertEquals("[]", f.row.get().topicKeywordsJson);
        Command set = command("set"); set.publication.topicKeywords = List.of("科学课件", "课堂互动");
        assertEquals(List.of("科学课件", "课堂互动"), f.service.update(1L, set).topicKeywords);
        String stored = f.row.get().topicKeywordsJson;
        Command omitted = command("omitted"); omitted.contentVersion = f.row.get().contentVersion;
        assertEquals(List.of("科学课件", "课堂互动"), f.service.update(1L, omitted).topicKeywords);
        assertEquals(stored, f.row.get().topicKeywordsJson);
        Command clear = command("clear"); clear.contentVersion = f.row.get().contentVersion; clear.publication.topicKeywords = List.of();
        assertEquals(List.of(), f.service.update(1L, clear).topicKeywords);
        assertEquals("[]", f.row.get().topicKeywordsJson);
        Summary summary = new Summary(); summary.topicKeywordsJson = stored;
        when(f.mapper.list(any())).thenReturn(List.of(summary)); when(f.mapper.count(any())).thenReturn(1L);
        assertEquals(List.of("科学课件", "课堂互动"), f.service.list(new ListQuery()).rows().get(0).topicKeywords);
        assertFalse(f.support.encode(summary).contains("topicKeywordsJson"));
        Detail empty = new Detail();
        assertTrue(f.support.encode(empty).contains("\"topicKeywords\":[]"));
    }

    @Test void omittedAndExplicitClearCannotReplayOneAnotherWithTheSameUpdateKey() {
        Fixture f = new Fixture(); f.service.create(command("create"));
        Command omitted = command("same-key");
        f.service.update(1L, omitted);
        assertTrue(f.service.update(1L, omitted).registration.replayed());
        omitted.publication.topicKeywords = List.of();
        assertEquals("IDEMPOTENCY_CONFLICT", assertThrows(PublicationException.class,
                () -> f.service.update(1L, omitted)).getDetail().errorCode());
    }

    @Test void keywordsAreValidatedWithoutChangingOrderOrInventingTopicBindings() {
        Fixture f = new Fixture(); Input input = input();
        input.topicKeywords = List.of("科学课件", "课堂 互动", "SCIENCE", "science");
        assertDoesNotThrow(() -> f.graph.validate(input));
        List<List<String>> invalid = List.of(List.of(""), List.of(" "), List.of("#科学课件"), List.of("＃科学课件"),
                List.of("科学\n课件"), List.of("科学\r课件"), List.of("科学\u2028课件"), List.of(" 科学"),
                List.of("科学 "), List.of("科学", "科学"), List.of("字".repeat(81)), java.util.Collections.nCopies(21, "科学"));
        for (List<String> value : invalid) {
            input.topicKeywords = value;
            assertEquals(400, assertThrows(PublicationException.class, () -> f.graph.validate(input), value.toString()).getCode());
        }
        input.topicKeywords = java.util.Arrays.asList((String) null);
        assertThrows(PublicationException.class, () -> f.graph.validate(input));
        Publication row = new Publication(); row.id = 1L;
        assertEquals(List.of(), f.graph.read(row).topicKeywords);
        row.topicKeywordsJson = "[\"课堂互动\",\"科学课件\"]";
        assertEquals(List.of("课堂互动", "科学课件"), f.graph.read(row).topicKeywords);
        row.topicKeywordsJson = "[\"#不合法\"]";
        assertEquals(503, assertThrows(PublicationException.class, () -> f.graph.read(row)).getCode());
    }

    @Test void existingActiveShareKeepsItsLegacyDependencyAndPublicKeywordsStayIndependent() {
        var f = new PublicationPublicGateTest.Fixture();
        var support = new PublicationSupport(mock(PlatformTransactionManager.class), new KbReviewGate(), new PublicationProperties());
        Input current = input(); current.topicKeywords = List.of();
        String legacyDependency = PublicationSupport.digest("[" + LEGACY_INPUT + ",[],[],[],[]]");
        var frozen = new CheckResult(); frozen.status = "PASSED"; frozen.dependencyHash = legacyDependency;
        var sale = f.mapper.getOperation(1L, 3L); sale.snapshotJson = support.encode(new Snapshot(support.decode(LEGACY_INPUT, Input.class), frozen));
        when(f.checks.evaluate(eq(f.row), eq(sale))).thenAnswer(invocation -> {
            CheckResult result = new CheckResult(); result.status = "PASSED";
            result.dependencyHash = support.hash(List.of(current, List.of(), List.of(), List.of(), List.of())); return result;
        });
        f.row.shareAssetId = 8L; f.row.postBody = current.postBody; var html = new KbAsset(); html.setContentType("text/html");
        when(f.assets.requireReady(8L)).thenReturn(html); when(f.graph.read(f.row)).thenReturn(current);
        assertEquals(List.of(), f.publicApi.get(f.row.shareCode).topicKeywords());
        assertEquals("READY", f.row.shareState); verify(f.mapper, never()).updatePublication(any());
        current.topicKeywords = List.of("课堂互动", "科学课件");
        frozen.dependencyHash = support.hash(List.of(current, List.of(), List.of(), List.of(), List.of()));
        sale.snapshotJson = support.encode(new Snapshot(current, frozen));
        assertEquals(List.of("课堂互动", "科学课件"), f.publicApi.get(f.row.shareCode).topicKeywords());
        assertEquals("正文", f.publicApi.get(f.row.shareCode).postBody());
    }

    private static Input input() {
        Input input = new Input(); input.title = "资料"; input.postTitle = "标题"; input.postBody = "正文";
        input.products = List.of(); input.images = List.of(); input.referenceSources = List.of(); input.baiduShares = List.of();
        return input;
    }
    private static Command command(String key) {
        Command command = new Command(); command.operationKey = key; command.publication = input();
        command.expectedRowVersion = 0L; command.contentVersion = 1L; return command;
    }
    private static class Fixture {
        final PublicationMapper mapper = mock(PublicationMapper.class);
        final KbAgentSubmissionMapper submissions = mock(KbAgentSubmissionMapper.class);
        final AtomicReference<Publication> row = new AtomicReference<>();
        final PublicationSupport support;
        final PublicationGraph graph;
        final PublicationService service;
        Fixture() {
            PublicationSaleRecoveryTest.login("owner", Set.of("knowledge:publication:all", "knowledge:publication:add",
                    "knowledge:publication:edit", "knowledge:publication:list"));
            var properties = new PublicationProperties(); properties.setEnabled(true);
            var manager = mock(PlatformTransactionManager.class);
            when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
            support = new PublicationSupport(manager, new KbReviewGate(), properties);
            graph = new PublicationGraph(mapper, support, mock(IKbAssetService.class));
            service = new PublicationService(mapper, support, graph, submissions);
            when(mapper.insertPublication(any())).thenAnswer(invocation -> {
                Publication created = invocation.getArgument(0); created.id = 1L; row.set(created); return 1;
            });
            when(mapper.get(1L)).thenAnswer(invocation -> row.get());
            when(mapper.lock(1L)).thenAnswer(invocation -> row.get());
            when(mapper.updatePublication(any())).thenReturn(1);
            Map<String, KbAgentSubmission> ledger = new HashMap<>();
            when(submissions.claim(anyString(), anyString(), anyString(), anyString())).thenAnswer(invocation -> {
                String key = invocation.getArgument(2); var value = new KbAgentSubmission(); value.setRequestHash(invocation.getArgument(3));
                ledger.putIfAbsent(key, value); return 1;
            });
            when(submissions.lock(anyString(), anyString(), anyString())).thenAnswer(invocation -> ledger.get(invocation.getArgument(2)));
            when(submissions.complete(anyString(), anyString(), anyString(), anyLong())).thenAnswer(invocation -> {
                ledger.get(invocation.getArgument(2)).setResourceId(invocation.getArgument(3)); return 1;
            });
        }
    }
}
