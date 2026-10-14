package com.ruoyi.knowledge.mapper;

import com.ruoyi.knowledge.domain.KbComparisonRow;
import com.ruoyi.knowledge.domain.KbKp;
import com.ruoyi.knowledge.domain.KbMaterial;
import com.ruoyi.knowledge.domain.KbMaterialReference;
import com.ruoyi.knowledge.dto.KbComparisonQuery;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface KbMaterialMapper {
    KbMaterial selectById(Long id);

    KbMaterial lockById(Long id);

    List<KbMaterial> selectList(KbMaterial query);

    int insert(KbMaterial entity);

    int update(KbMaterial entity);

    int deleteById(Long id);

    List<Long> selectQuestionIds(Long id);

    List<KbMaterialReference> selectReferences(Long id);

    int deleteItems(Long id);

    int deleteReferences(Long id);

    int insertItem(
            @Param("id") Long id, @Param("questionId") Long questionId, @Param("seq") int seq);

    int insertReference(@Param("id") Long id, @Param("ref") KbMaterialReference ref);

    long countProducts(Long id);

    boolean realInSources(
            @Param("questionId") Long questionId, @Param("sourceIds") List<Long> sourceIds);

    Long selectVerifiedOccurrenceIdForUpdate(
            @Param("questionId") Long questionId, @Param("sourceIds") List<Long> sourceIds);

    List<KbComparisonRow> selectComparisonMatches(KbComparisonQuery query);

    long countComparisonMatches(KbComparisonQuery query);

    List<KbComparisonRow> selectComparisonCoverage(KbComparisonQuery query);

    long countComparisonCoverage(KbComparisonQuery query);

    List<KbKp> selectComparisonPoints(KbComparisonQuery query);
}
