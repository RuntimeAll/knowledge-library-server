package com.ruoyi.knowledge.service;

import com.ruoyi.knowledge.domain.KbKp;

import java.util.List;
import java.util.Set;

public interface IKbKpService {
    List<KbKp> list(KbKp query);

    KbKp get(Long id);

    KbKp create(KbKp data);

    KbKp update(KbKp data);

    void delete(Long[] ids);

    Set<Long> matchingQuestionIds(Long id, boolean expand);
}
