package com.ruoyi.knowledge.service.impl;

import com.github.pagehelper.Page;
import com.ruoyi.knowledge.catalog.*;
import com.ruoyi.knowledge.domain.*;
import com.ruoyi.knowledge.mapper.*;
import com.ruoyi.knowledge.service.*;
import com.ruoyi.knowledge.storage.IKbAssetService;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KbCatalogListBatchingTest {
    @Test
    void sourcePagesKeepOrderTotalAndAttachmentRolesWithoutPerRowReads() {
        for (int size : List.of(10, 20)) {
            KbSourceMapper mapper = mock(KbSourceMapper.class);
            IKbCatalogService catalog = mock(IKbCatalogService.class);
            KbSourceServiceImpl service = sourceService(mapper, mock(KbOccurrenceMapper.class), catalog);
            Page<KbSource> page = new Page<>(2, size); page.setTotal(78);
            List<Long> ids = IntStream.range(0, size).mapToObj(i -> 100L - i).toList();
            for (Long id : ids) { KbSource row = new KbSource(); row.setId(id); row.setTitle("source " + id); page.add(row); }
            KbCatalogQuery query = new KbCatalogQuery(); query.setKpId(5L); query.setCoverageMode("ACTUAL");
            when(mapper.selectList(query)).thenReturn(page);
            KbSourceAttachment a = attachment(100L, 8L, "EVIDENCE"), b = attachment(100L, 8L, "ORIGINAL");
            when(mapper.selectAttachmentsBySources(ids)).thenReturn(List.of(a, b));
            assertSame(page, service.list(query));
            assertEquals(78, page.getTotal());
            assertEquals(ids, page.stream().map(KbSource::getId).toList());
            assertEquals(List.of(a, b), page.get(0).getAttachments());
            assertEquals(List.of(8L), page.get(0).getAssetIds());
            assertEquals(List.of(), page.get(1).getAttachments());
            assertEquals(List.of("命中已录入题目的实际考点"), page.get(0).getMatchReasons());
            verify(mapper).selectList(query); verify(mapper).selectAttachmentsBySources(ids); verifyNoMoreInteractions(mapper);
            verify(catalog).enrichBatch(eq("SOURCE"), anyMap()); verifyNoMoreInteractions(catalog);
        }
    }

    @Test
    void productPageRetainsImmutableRenderSnapshotAndMatchedVersion() {
        KbProductMapper mapper = mock(KbProductMapper.class);
        IKbCatalogService catalog = mock(IKbCatalogService.class);
        KbProductServiceImpl service = new KbProductServiceImpl(mapper, mock(KbMaterialMapper.class),
                mock(IKbAssetService.class), mock(IKbMaterialService.class), catalog);
        KbProduct product = new KbProduct(); product.setId(45L); product.setRenderJobId(7L);
        product.setSnapshotJson("{\"declaredKpIds\":[11],\"questionRefs\":[{\"questionId\":12,\"seq\":1}],\"referenceSources\":[{\"sourceId\":13}]}");
        Page<KbProduct> page = new Page<>(2, 10); page.setTotal(23); page.add(product);
        KbCatalogQuery query = new KbCatalogQuery(); when(mapper.selectList(query)).thenReturn(page);
        assertSame(page, service.list(query));
        assertEquals(23, page.getTotal()); assertEquals(45L, product.getMatchedProductId());
        assertEquals(List.of(11L), product.getActualKpIds());
        assertEquals(12L, product.getQuestionRefs().get(0).getQuestionId());
        assertEquals(13L, product.getReferenceSources().get(0).getSourceId());
        verify(mapper).selectList(query); verifyNoMoreInteractions(mapper);
        verify(catalog).enrichBatch(eq("PRODUCT"), anyMap()); verifyNoMoreInteractions(catalog);
    }

    @Test
    void occurrencesKeepCurrentStemAndKnowledgeExclusionAndReadSharedSourceOnce() {
        KbSourceMapper sources = mock(KbSourceMapper.class);
        KbOccurrenceMapper mapper = mock(KbOccurrenceMapper.class);
        IKbCatalogService catalog = mock(IKbCatalogService.class);
        KbSourceServiceImpl service = sourceService(sources, mapper, catalog);
        KbSource source = new KbSource(); source.setId(9L); source.setTitle("exam"); source.setAuthenticityStatus("VERIFIED");
        Page<KbOccurrence> page = new Page<>(2, 10); page.setTotal(37);
        List<KbOccurrenceQuestionState> states = new ArrayList<>();
        for (long id = 1; id <= 4; id++) {
            KbOccurrence row = new KbOccurrence(); row.setId(id); row.setQuestionId(id + 10); row.setSourceId(9L);
            row.setMatchedStemHash("old"); row.setMatchStatus("VERIFIED"); page.add(row);
            KbOccurrenceQuestionState state = new KbOccurrenceQuestionState(); state.setOccurrenceId(id);
            state.setQuestionId(id == 4 ? null : id + 10); state.setQuestionTitle("question " + id);
            state.setStemHash(id == 2 ? "changed" : "old"); state.setContentKind(id == 3 ? "KNOWLEDGE" : "EXERCISE"); states.add(state);
        }
        KbOccurrenceCollection collection = new KbOccurrenceCollection(); collection.setCollectionKind("WRONG_QUESTION");
        KbOccurrenceQuery query = new KbOccurrenceQuery(); List<Long> ids = List.of(1L, 2L, 3L, 4L);
        when(mapper.selectCatalogList(query)).thenReturn(page); when(mapper.questionStates(ids)).thenReturn(states);
        when(sources.selectByIds(List.of(9L))).thenReturn(List.of(source)); when(catalog.realExam(source)).thenReturn(true);
        when(catalog.collectionsBatch(ids)).thenReturn(Map.of(1L, List.of(collection)));
        assertSame(page, service.occurrenceList(query)); assertEquals(37, page.getTotal());
        assertEquals(List.of(true, false, false, false), page.stream().map(KbOccurrence::getIsRealExam).toList());
        assertEquals(List.of(true, false, true, false), page.stream().map(KbOccurrence::getStemMatches).toList());
        assertEquals(List.of(collection), page.get(0).getCollections()); assertEquals(List.of(), page.get(1).getCollections());
        assertNull(page.get(3).getQuestionTitle()); assertEquals("exam", page.get(0).getSourceTitle());
        verify(sources).selectByIds(List.of(9L)); verifyNoMoreInteractions(sources);
        verify(mapper).selectCatalogList(query); verify(mapper).questionStates(ids); verifyNoMoreInteractions(mapper);
        verify(catalog).enrichExamSources(anyMap()); verify(catalog).realExam(source); verify(catalog).collectionsBatch(ids);
        verifyNoMoreInteractions(catalog);
    }

    private KbSourceServiceImpl sourceService(KbSourceMapper sources, KbOccurrenceMapper occurrences, IKbCatalogService catalog) {
        return new KbSourceServiceImpl(sources, occurrences, mock(KbQuestionMapper.class), mock(IKbAssetService.class), mock(KnowledgeDictionary.class), catalog);
    }
    private KbSourceAttachment attachment(Long sourceId, Long assetId, String role) {
        KbSourceAttachment row = new KbSourceAttachment(); row.setSourceId(sourceId); row.setAssetId(assetId); row.setRole(role); return row;
    }
}
