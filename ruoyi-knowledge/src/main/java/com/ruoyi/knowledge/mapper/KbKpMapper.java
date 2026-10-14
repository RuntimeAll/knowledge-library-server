package com.ruoyi.knowledge.mapper;

import com.ruoyi.knowledge.domain.KbKp;
import com.ruoyi.knowledge.domain.KbKpEdge;
import com.ruoyi.knowledge.domain.KbQuestionKp;

import java.util.List;

public interface KbKpMapper {
    KbKp selectById(Long id);

    KbKp lockById(Long id);

    List<KbKp> selectList(KbKp query);

    int insert(KbKp entity);

    int update(KbKp entity);

    int deleteById(Long id);

    List<KbKp> lockGraph();

    List<KbKpEdge> selectEdges();

    List<KbQuestionKp> selectQuestionPoints();

    int deleteEdges(Long id);

    int insertEdge(KbKpEdge edge);

    long countReferences(Long id);
}
