package com.ruoyi.knowledge.service.impl;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.knowledge.domain.*;
import com.ruoyi.knowledge.dto.KbAgentProductRequest;
import com.ruoyi.knowledge.mapper.KbAgentSubmissionMapper;
import com.ruoyi.knowledge.service.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KbAgentIngestionTest {
    @Test
    void sameKeyReturnsOriginalVersionButChangedPayloadIsRejected() {
        var mapper = mock(KbAgentSubmissionMapper.class);
        var products = mock(IKbProductService.class);
        var service = new KbAgentIngestionServiceImpl(mapper, mock(IKbSourceService.class), products);
        var claim = new KbAgentSubmission();
        when(mapper.claim(eq("agent"), eq("PRODUCT"), eq("book-v1"), anyString())).thenAnswer(call -> {
            if (claim.getRequestHash() == null) claim.setRequestHash(call.getArgument(3));
            return 1;
        });
        when(mapper.lock("agent", "PRODUCT", "book-v1")).thenReturn(claim);
        when(mapper.complete("agent", "PRODUCT", "book-v1", 10L)).thenAnswer(call -> {
            claim.setResourceId(10L); return 1;
        });
        KbProduct stored = new KbProduct(); stored.setId(10L);
        when(products.create(any())).thenReturn(stored);
        when(products.get(10L)).thenReturn(stored);
        assertSame(stored, service.saveProduct(request("原版"), "agent"));
        assertSame(stored, service.saveProduct(request("原版"), "agent"));
        assertThrows(ServiceException.class, () -> service.saveProduct(request("不同内容"), "agent"));
        verify(products, times(1)).create(any());
        verify(mapper, times(3)).lock("agent", "PRODUCT", "book-v1");
    }

    @Test
    void lookupIsScopedToCurrentActor() {
        var mapper = mock(KbAgentSubmissionMapper.class);
        var products = mock(IKbProductService.class);
        var service = new KbAgentIngestionServiceImpl(mapper, mock(IKbSourceService.class), products);
        when(mapper.findResource("reader", "PRODUCT", "book-v1")).thenReturn(null);
        assertNull(service.findProduct("book-v1", "reader"));
        verify(mapper).findResource("reader", "PRODUCT", "book-v1");
        verifyNoInteractions(products);
    }

    private KbAgentProductRequest request(String title) {
        KbProduct product = new KbProduct(); product.setTitle(title); product.setAssetId(5L);
        return new KbAgentProductRequest("book-v1", product);
    }
}
