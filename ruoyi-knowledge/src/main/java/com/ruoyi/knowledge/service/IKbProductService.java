package com.ruoyi.knowledge.service;

import com.ruoyi.knowledge.domain.KbProduct;

import java.util.List;

public interface IKbProductService {
    List<KbProduct> list(KbProduct query);

    KbProduct get(Long id);

    KbProduct create(KbProduct data);

    KbProduct update(KbProduct data);

    void delete(Long[] ids);

    List<KbProduct> versions(Long id);
}
