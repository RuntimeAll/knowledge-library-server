package com.ruoyi.knowledge.service;
import com.ruoyi.knowledge.domain.KbSource;
import com.ruoyi.knowledge.domain.KbProduct;
import com.ruoyi.knowledge.dto.KbAgentSourceRequest;
import com.ruoyi.knowledge.dto.KbAgentProductRequest;
public interface IKbAgentIngestionService {
    com.ruoyi.knowledge.domain.KbSource findSource(String key,String actor);
    KbSource saveSource(KbAgentSourceRequest request, String actor);
    KbProduct saveProduct(KbAgentProductRequest request, String actor);
    KbProduct findProduct(String key, String actor);
}
