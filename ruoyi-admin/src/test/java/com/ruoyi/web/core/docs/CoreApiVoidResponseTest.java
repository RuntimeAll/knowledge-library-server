package com.ruoyi.web.core.docs;

import static org.junit.jupiter.api.Assertions.*;
import com.ruoyi.common.core.domain.AjaxResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class CoreApiVoidResponseTest {
    static class Examples {
        @CoreApi(value="example.boxedVoid",response=Void.class,published=false)
        public void boxed() { }
        @CoreApi(value="example.primitiveVoid",response=void.class,published=false)
        public void primitive() { }
        @CoreApi(value="example.data",response=String.class,published=false)
        public void data() { }
    }
    @Test void voidDataEnvelopeMatchesActualSuccessWithoutInventingData() throws Exception {
        for(String method:List.of("boxed","primitive")) {
            var schema=new CoreApiSchemas(true).response(Examples.class.getMethod(method).getAnnotation(CoreApi.class));
            assertEquals(List.of("code","msg"),schema.getRequired());
            assertFalse(schema.getProperties().containsKey("data"));
            assertFalse(AjaxResult.success().containsKey("data"));
        }
    }
    @Test void aRealPayloadStillRequiresItsDataField() throws Exception {
        var schema=new CoreApiSchemas(true).response(Examples.class.getMethod("data").getAnnotation(CoreApi.class));
        assertTrue(schema.getProperties().containsKey("data"));assertTrue(schema.getRequired().contains("data"));
    }
}
