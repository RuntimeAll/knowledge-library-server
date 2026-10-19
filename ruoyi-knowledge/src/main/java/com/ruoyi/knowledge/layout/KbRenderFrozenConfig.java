package com.ruoyi.knowledge.layout;

import com.alibaba.fastjson2.*;
import com.ruoyi.knowledge.catalog.*;
import com.ruoyi.knowledge.domain.*;

import java.util.*;

public class KbRenderFrozenConfig {
    private KbRenderRequest request;
    private KbProduct productCatalog;

    public KbRenderRequest getRequest() {
        return request;
    }

    public void setRequest(KbRenderRequest value) {
        request = value;
    }

    public KbProduct getProductCatalog() {
        return productCatalog;
    }

    public void setProductCatalog(KbProduct value) {
        productCatalog = value;
    }
}
