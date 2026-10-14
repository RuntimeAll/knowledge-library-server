package com.ruoyi.knowledge.mapper;

import com.ruoyi.knowledge.domain.KbOccurrence;

import java.util.List;

public interface KbOccurrenceMapper {
    KbOccurrence selectById(Long id);

    KbOccurrence lockById(Long id);

    List<KbOccurrence> selectList(KbOccurrence query);

    int insert(KbOccurrence entity);

    int update(KbOccurrence entity);

    int deleteById(Long id);

    long countPosition(KbOccurrence occurrence);

    List<KbOccurrence> selectAncestorSources(Long id);
}
