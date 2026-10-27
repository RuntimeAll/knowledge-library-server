package com.ruoyi.knowledge.mapper;

import com.ruoyi.knowledge.domain.KbOccurrence;

import java.util.List;

public interface KbOccurrenceMapper {
    KbOccurrence selectById(Long id);

    KbOccurrence lockById(Long id);

    List<KbOccurrence> selectList(KbOccurrence query);
    List<KbOccurrence> forQuestions(@org.apache.ibatis.annotations.Param("ids") List<Long> ids);

    List<KbOccurrence> selectCatalogList(com.ruoyi.knowledge.catalog.KbOccurrenceQuery query);
    List<KbOccurrence> matchedExam(@org.apache.ibatis.annotations.Param("questionId") Long questionId,@org.apache.ibatis.annotations.Param("query") com.ruoyi.knowledge.dto.KbQuestionQuery query);
    List<KbOccurrence> matchedWrong(@org.apache.ibatis.annotations.Param("questionId") Long questionId,@org.apache.ibatis.annotations.Param("query") com.ruoyi.knowledge.dto.KbQuestionQuery query);
    List<KbOccurrence> matchedExamForQuestions(@org.apache.ibatis.annotations.Param("questionIds") List<Long> questionIds, @org.apache.ibatis.annotations.Param("query") com.ruoyi.knowledge.dto.KbQuestionQuery query);
    List<KbOccurrence> matchedWrongForQuestions(@org.apache.ibatis.annotations.Param("questionIds") List<Long> questionIds, @org.apache.ibatis.annotations.Param("query") com.ruoyi.knowledge.dto.KbQuestionQuery query);
    List<com.ruoyi.knowledge.catalog.KbOccurrenceQuestionState> questionStates(@org.apache.ibatis.annotations.Param("ids") List<Long> ids);

    int insert(KbOccurrence entity);

    int update(KbOccurrence entity);

    int deleteById(Long id);

    long countPosition(KbOccurrence occurrence);

    List<KbOccurrence> selectAncestorSources(Long id);
    List<com.ruoyi.knowledge.exporting.KbExportAncestor> ancestorsForQuestions(@org.apache.ibatis.annotations.Param("ids") List<Long> ids);
}
