package com.ruoyi.knowledge.service;

import com.ruoyi.knowledge.domain.KbMaterial;
import com.ruoyi.knowledge.dto.KbComparison;
import com.ruoyi.knowledge.dto.KbQuestionQuery;

import java.util.List;

public interface IKbMaterialService {
    List<KbMaterial> list(KbMaterial query);

    KbMaterial get(Long id);

    KbMaterial create(KbMaterial data);

    KbMaterial update(KbMaterial data);

    void delete(Long[] ids);

    void prepareCandidates(Long id, String scope, KbQuestionQuery query);

    void assertSelectionValid(Long id);

    KbComparison comparison(
            Long id,
            String scope,
            boolean expand,
            int matchPageNum,
            int matchPageSize,
            int coveragePageNum,
            int coveragePageSize,
            int pointPageNum,
            int pointPageSize);
}
