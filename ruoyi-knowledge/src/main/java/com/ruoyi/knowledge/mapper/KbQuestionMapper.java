package com.ruoyi.knowledge.mapper;

import com.ruoyi.knowledge.domain.KbDerivation;
import com.ruoyi.knowledge.domain.KbQuestion;
import com.ruoyi.knowledge.dto.KbQuestionQuery;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface KbQuestionMapper {
    KbQuestion selectById(Long id);

    KbQuestion lockById(Long id);

    List<KbQuestion> selectList(KbQuestionQuery query);

    int insert(KbQuestion entity);

    int update(KbQuestion entity);

    int deleteById(Long id);

    List<Long> selectKpIds(Long id);

    List<Long> selectAssetIds(Long id);

    int deleteKps(Long id);

    int deleteAssets(Long id);

    int insertKp(@Param("id") Long id, @Param("kpId") Long kpId);

    int insertAsset(@Param("id") Long id, @Param("assetId") Long assetId);

    long countReferences(Long id);

    boolean isRealExam(Long id);

    int deleteIncomingDerivations(Long id);

    int insertDerivation(KbDerivation derivation);

    List<KbDerivation> selectParents(Long id);

    List<KbDerivation> selectChildren(Long id);
}
