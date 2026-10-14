package com.ruoyi.knowledge.mapper;

import com.ruoyi.knowledge.domain.KbProduct;

import java.util.List;

public interface KbProductMapper {
    KbProduct selectById(Long id);

    KbProduct lockById(Long id);

    List<KbProduct> selectList(KbProduct query);

    int insert(KbProduct entity);

    int update(KbProduct entity);

    int deleteById(Long id);

    int initializeRoot(Long id);

    int nextVersion(Long rootId);

    long countNewerVersions(Long id);
}
