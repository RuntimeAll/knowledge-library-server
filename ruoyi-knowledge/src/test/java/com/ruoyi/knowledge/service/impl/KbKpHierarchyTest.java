package com.ruoyi.knowledge.service.impl;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.knowledge.domain.KbKp;
import com.ruoyi.knowledge.domain.KbKpEdge;
import com.ruoyi.knowledge.domain.KbQuestionKp;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class KbKpHierarchyTest {
    @Test void depthFollowsNestingButNotComposition() {
        var positions = KbKpHierarchy.positions(List.of(point(1), point(2), point(3), point(4)),
                List.of(edge(1, 2, "NEST"), edge(2, 3, "NEST"), edge(4, 3, "COMBINE")));
        assertEquals(1, positions.get(1L).depth());
        assertEquals(3, positions.get(3L).depth());
        assertEquals(1L, positions.get(3L).rootId());
        assertEquals(2L, positions.get(3L).parentId());
        assertEquals(1, positions.get(4L).depth());
    }

    @Test void aPointCannotHaveTwoNestingParents() {
        assertThrows(ServiceException.class, () -> KbKpHierarchy.parents(
                List.of(edge(1, 3, "NEST"), edge(2, 3, "NEST"))));
    }

    @Test void cycleIncludingCombinationIsRejected() {
        assertThrows(ServiceException.class, () -> KbKpHierarchy.topological(
                List.of(edge(1, 2, "NEST"), edge(2, 3, "NEST"), edge(3, 1, "COMBINE"))));
    }

    @Test void deepHierarchyHasNoRecursionStackLimitAndExpandsQuestions() {
        int count = 12000;
        List<KbKp> points = new ArrayList<>();
        List<KbKpEdge> edges = new ArrayList<>();
        for (long id = 1; id <= count; id++) {
            points.add(point(id));
            if (id > 1) edges.add(edge(id - 1, id, "NEST"));
        }
        var positions = KbKpHierarchy.positions(points, edges);
        assertEquals(count, positions.get((long) count).depth());
        KbQuestionKp link = new KbQuestionKp(); link.setQuestionId(99L); link.setKpId((long) count);
        assertEquals(Set.of(99L), KbKpServiceImpl.match(1L, true, edges, List.of(link)));
    }

    private KbKp point(long id) { KbKp point = new KbKp(); point.setId(id); point.setName("point-" + id); return point; }
    private KbKpEdge edge(long from, long to, String kind) {
        KbKpEdge edge = new KbKpEdge(); edge.setFromKpId(from); edge.setToKpId(to); edge.setKind(kind); return edge;
    }
}
