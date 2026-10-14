package com.ruoyi.knowledge.service;

import com.ruoyi.knowledge.domain.KbOccurrence;
import com.ruoyi.knowledge.domain.KbSource;

import java.util.List;

public interface IKbSourceService {
    List<KbSource> list(KbSource query);

    KbSource get(Long id);

    KbSource create(KbSource data);

    KbSource update(KbSource data);

    void delete(Long[] ids);

    List<KbOccurrence> occurrences(Long questionId, Long sourceId);

    KbOccurrence createOccurrence(KbOccurrence data);

    KbOccurrence updateOccurrence(KbOccurrence data);

    void deleteOccurrence(Long id);
}
