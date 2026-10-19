package com.ruoyi.knowledge.service;

import com.ruoyi.knowledge.catalog.*;
import com.ruoyi.knowledge.domain.*;

import java.util.List;

public interface IKbCatalogService {
    void enrich(String kind, Long id, KbCatalogDocument target);

    void prepareSource(KbSource target, KbSource old);

    void prepareProduct(KbProduct target, KbProduct old);

    void persist(String kind, Long id, KbCatalogDocument target, String actor, boolean create);

    void delete(String kind, Long id);

    void clearExam(Long sourceId);

    void prepareQuery(String kind, KbCatalogQuery query);

    List<KbRegion> regions(String parentCode, String keyword);

    KbRegion region(String code);

    KbExamDetail verify(KbSource source, KbExamVerificationCommand command, String actor);

    List<KbOccurrenceCollection> collections(Long occurrenceId);

    void saveCollections(KbOccurrence occurrence, KbCollectionsCommand command, String actor);

    long countSourceEvidence(Long sourceId, Long assetId);

    boolean realExam(KbSource source);
}
