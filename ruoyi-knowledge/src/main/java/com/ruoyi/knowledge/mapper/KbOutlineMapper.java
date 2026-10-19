package com.ruoyi.knowledge.mapper;

import com.ruoyi.knowledge.domain.KbOutline;

import java.util.List;

public interface KbOutlineMapper {
    KbOutline selectById(Long id);

    KbOutline lockById(Long id);

    List<KbOutline> selectList(KbOutline query);

    int insert(KbOutline entity);

    int update(KbOutline entity);

    int deleteById(Long id);

    long countNodes(Long id);
    List<String> selectTextbookVersions();
    long countCatalogReferences(Long id);
    long countNodeCatalogReferences(Long id);
}
