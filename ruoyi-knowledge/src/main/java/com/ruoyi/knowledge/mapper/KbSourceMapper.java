package com.ruoyi.knowledge.mapper;

import com.ruoyi.knowledge.domain.KbSource;
import com.ruoyi.knowledge.domain.KbSourceAttachment;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface KbSourceMapper {
    KbSource selectById(Long id);

    KbSource lockById(Long id);

    List<KbSource> selectList(com.ruoyi.knowledge.catalog.KbCatalogQuery query);

    int insert(KbSource entity);

    int update(KbSource entity);

    int deleteById(Long id);

    KbSource selectExam(Long id);

    int saveExam(KbSource source);

    int deleteExam(Long id);

    List<KbSourceAttachment> selectAttachments(Long id);

    int insertAttachment(KbSourceAttachment attachment);

    int deleteAttachments(Long id);

    long countReferences(Long id);

    long countOccurrences(Long id);

    long countAssetEvidence(@Param("sourceId") Long sourceId, @Param("assetId") Long assetId);

    int advanceExtraction(@Param("id") Long id, @Param("actor") String actor);
}
