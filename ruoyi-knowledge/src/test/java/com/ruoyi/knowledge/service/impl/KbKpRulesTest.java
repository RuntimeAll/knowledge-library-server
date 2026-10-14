package com.ruoyi.knowledge.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.knowledge.domain.KbKpEdge;
import com.ruoyi.knowledge.domain.KbQuestionKp;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

class KbKpRulesTest {
    @Test
    void nestedPointsUnionButComponentsIntersectAndDirectTagsRemain() {
        List<KbKpEdge> edges =
                List.of(edge(1, 2, "NEST"), edge(1, 3, "COMBINE"), edge(1, 4, "COMBINE"));
        List<KbQuestionKp> links =
                List.of(link(10, 1), link(11, 2), link(12, 3), link(12, 4), link(13, 3));
        assertEquals(Set.of(10L, 11L, 12L), KbKpServiceImpl.match(1L, true, edges, links));
        assertEquals(Set.of(10L), KbKpServiceImpl.match(1L, false, edges, links));
    }

    @Test
    void emptyComponentDoesNotWidenIntersection() {
        assertEquals(
                Set.of(),
                KbKpServiceImpl.match(
                        1L,
                        true,
                        List.of(edge(1, 2, "COMBINE"), edge(1, 3, "COMBINE")),
                        List.of(link(10, 2))));
    }

    @Test
    void sharedDescendantIsNotACycleButMixedRelationLoopIs() {
        KbKpServiceImpl.ensureAcyclic(
                List.of(
                        edge(1, 2, "NEST"),
                        edge(1, 3, "NEST"),
                        edge(2, 4, "COMBINE"),
                        edge(3, 4, "NEST")));
        assertThrows(
                ServiceException.class,
                () ->
                        KbKpServiceImpl.ensureAcyclic(
                                List.of(
                                        edge(1, 2, "NEST"),
                                        edge(2, 3, "COMBINE"),
                                        edge(3, 1, "NEST"))));
    }

    private KbKpEdge edge(long from, long to, String kind) {
        KbKpEdge value = new KbKpEdge();
        value.setFromKpId(from);
        value.setToKpId(to);
        value.setKind(kind);
        return value;
    }

    private KbQuestionKp link(long question, long point) {
        KbQuestionKp value = new KbQuestionKp();
        value.setQuestionId(question);
        value.setKpId(point);
        return value;
    }
}
