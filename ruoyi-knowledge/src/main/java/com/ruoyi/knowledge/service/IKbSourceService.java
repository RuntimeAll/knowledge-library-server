package com.ruoyi.knowledge.service;

import com.ruoyi.knowledge.domain.KbOccurrence;
import com.ruoyi.knowledge.domain.KbSource;

import java.util.List;

public interface IKbSourceService {
    List<KbSource> list(com.ruoyi.knowledge.catalog.KbCatalogQuery query);

    void prepareQuery(com.ruoyi.knowledge.catalog.KbCatalogQuery query);
    KbSource catalog(Long id,com.ruoyi.knowledge.catalog.KbCatalogCommand command,String actor);
    KbSource get(Long id);

    KbSource create(KbSource data);

    KbSource update(KbSource data);

    void delete(Long[] ids);

    List<KbOccurrence> occurrences(Long questionId, Long sourceId);

    KbOccurrence createOccurrence(KbOccurrence data);

    KbOccurrence updateOccurrence(KbOccurrence data);

    void deleteOccurrence(Long id);
    KbSource verifyExam(Long id,com.ruoyi.knowledge.catalog.KbExamVerificationCommand command,String actor);
    List<KbOccurrence> occurrenceList(com.ruoyi.knowledge.catalog.KbOccurrenceQuery query);
    KbOccurrence collections(Long id,com.ruoyi.knowledge.catalog.KbCollectionsCommand command,String actor);
}
