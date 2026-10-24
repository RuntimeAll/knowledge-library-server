package com.ruoyi.knowledge.mapper;

import com.ruoyi.knowledge.domain.KbKp;
import com.ruoyi.knowledge.domain.KbKpEdge;
import com.ruoyi.knowledge.domain.KbQuestionKp;
import com.ruoyi.knowledge.dto.KbKpOutlineBinding;
import com.ruoyi.knowledge.dto.KbKpOutlineRow;
import com.ruoyi.knowledge.dto.KbKpName;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface KbKpMapper {
    KbKp selectById(Long id);

    List<KbKpName> selectNames(@Param("ids") List<Long> ids);

    KbKp lockById(Long id);

    List<KbKp> selectList(KbKp query);

    int insert(KbKp entity);

    int update(KbKp entity);

    int deleteById(Long id);

    List<KbKp> lockGraph();

    List<KbKpEdge> selectEdges();
    List<KbKp> selectHierarchyPoints();
    int deleteEdge(KbKpEdge edge);
    int touchRelations(@Param("ids") List<Long> ids, @Param("updateBy") String updateBy);

    List<KbQuestionKp> selectQuestionPoints();

    int deleteEdges(Long id);

    int insertEdge(KbKpEdge edge);

    long countReferences(Long id);

    List<Long> selectOutlineIds(Long id);
    List<KbKpOutlineBinding> selectOutlineBindings(Long id);
    List<KbKpOutlineRow> selectOutlineRowsForKps(@Param("ids") List<Long> ids);
    int insertOutline(@Param("kpId") Long kpId, @Param("outlineId") Long outlineId);
    int deleteOutline(@Param("kpId") Long kpId, @Param("outlineId") Long outlineId);
    int deleteOutlines(Long id);
    long countChapterLinks(@Param("kpId") Long kpId, @Param("outlineId") Long outlineId);
    long countOutlinePoints(Long outlineId);
    long countMismatchedQuestions(@Param("kpId") Long kpId, @Param("subject") String subject);
}
