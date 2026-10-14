package com.ruoyi.knowledge.service;

import com.ruoyi.knowledge.domain.KbQuestion;
import com.ruoyi.knowledge.dto.KbLineage;
import com.ruoyi.knowledge.dto.KbQuestionQuery;

import java.util.List;

public interface IKbQuestionService {
    List<KbQuestion> list(KbQuestionQuery query);

    void prepareQuery(KbQuestionQuery query);

    KbQuestion get(Long id);

    KbQuestion updateMetadata(KbQuestion data);

    KbQuestion createImportedQuestion(KbQuestion data);

    KbQuestion updateImportedQuestion(KbQuestion data);

    KbQuestion deriveImportedQuestion(
            Long parentId,
            KbQuestion data,
            List<Long> auxiliaryParentIds,
            String operation,
            String changeNote,
            String preservedNote);

    void delete(Long[] ids);

    KbLineage lineage(Long id);
}
