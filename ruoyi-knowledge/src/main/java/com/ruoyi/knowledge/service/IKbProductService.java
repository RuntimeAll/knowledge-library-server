package com.ruoyi.knowledge.service;

import com.ruoyi.knowledge.domain.KbProduct;

import java.util.List;

public interface IKbProductService {
    List<KbProduct> list(com.ruoyi.knowledge.catalog.KbCatalogQuery query);

    void prepareQuery(com.ruoyi.knowledge.catalog.KbCatalogQuery query);
    KbProduct catalog(Long id,com.ruoyi.knowledge.catalog.KbCatalogCommand command,String actor);
    KbProduct get(Long id);

    KbProduct create(KbProduct data);

    KbProduct update(KbProduct data);

    void delete(Long[] ids);

    List<KbProduct> versions(Long id);
}
