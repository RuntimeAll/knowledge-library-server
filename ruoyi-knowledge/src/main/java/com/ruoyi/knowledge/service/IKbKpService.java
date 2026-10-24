package com.ruoyi.knowledge.service;

import com.ruoyi.knowledge.domain.KbKp;
import com.ruoyi.knowledge.dto.KbKpName;

import java.util.List;
import java.util.Set;

public interface IKbKpService {
    List<KbKp> list(KbKp query);

    KbKp get(Long id);

    List<KbKpName> names(List<Long> ids);

    KbKp create(KbKp data);

    KbKp update(KbKp data);

    void delete(Long[] ids);

    Set<Long> matchingQuestionIds(Long id, boolean expand);
}
