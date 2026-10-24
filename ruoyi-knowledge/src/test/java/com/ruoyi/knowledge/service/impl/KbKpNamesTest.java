package com.ruoyi.knowledge.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.knowledge.dto.KbKpName;
import com.ruoyi.knowledge.mapper.KbKpMapper;
import com.ruoyi.knowledge.mapper.KbOutlineMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KbKpNamesTest {
    private final KbKpMapper mapper = mock(KbKpMapper.class);
    private final KbOutlineMapper outlines = mock(KbOutlineMapper.class);
    private final KnowledgeDictionary dictionary = mock(KnowledgeDictionary.class);
    private final KbKpServiceImpl service = new KbKpServiceImpl(mapper, outlines, dictionary);

    @Test
    void namesUseOneProjectionQueryAndKeepFirstRequestedOrderSkippingMissingIds() throws Exception {
        when(mapper.selectNames(List.of(3L, 1L, 99L))).thenReturn(List.of(name(1, "One"), name(3, "Three")));
        List<KbKpName> actual = service.names(List.of(3L, 1L, 3L, 99L));
        assertEquals(List.of(3L, 1L), actual.stream().map(KbKpName::getId).toList());
        assertEquals("[{\"id\":\"3\",\"name\":\"Three\"},{\"id\":\"1\",\"name\":\"One\"}]",
                new ObjectMapper().writeValueAsString(actual));
        verify(mapper).selectNames(List.of(3L, 1L, 99L));
        verifyNoMoreInteractions(mapper);
        verifyNoInteractions(outlines, dictionary);
    }

    @Test
    void largeIdsAreSerializedAsStringsWithoutJavaScriptPrecisionLoss() throws Exception {
        var json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(name(Long.MAX_VALUE, "Large")));
        assertTrue(json.get("id").isTextual());
        assertEquals("9223372036854775807", json.get("id").textValue());
    }

    @Test
    void exactlyTwoHundredIdsAreAcceptedInOneQuery() {
        List<Long> ids = LongStream.rangeClosed(1, 200).boxed().toList();
        when(mapper.selectNames(ids)).thenReturn(List.of());
        assertTrue(service.names(ids).isEmpty());
        verify(mapper).selectNames(ids);
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void emptyInvalidOrOverLimitInputNeverReachesDatabase() {
        assertInvalid(null);
        assertInvalid(List.of());
        assertInvalid(List.of(0L));
        assertInvalid(List.of(-1L));
        assertInvalid(Arrays.asList(1L, null));
        assertInvalid(LongStream.rangeClosed(1, 201).boxed().toList());
        // Limit raw input count before deduplication, including repeated IDs.
        assertInvalid(Collections.nCopies(201, 1L));
        verifyNoInteractions(mapper, outlines, dictionary);
    }

    private void assertInvalid(List<Long> ids) {
        ServiceException error = assertThrows(ServiceException.class, () -> service.names(ids));
        assertEquals(400, error.getCode());
    }

    private static KbKpName name(long id, String name) {
        KbKpName result = new KbKpName(); result.setId(id); result.setName(name); return result;
    }
}
