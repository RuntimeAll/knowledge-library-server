package com.ruoyi.knowledge.service.impl;

import com.github.pagehelper.Page;
import com.ruoyi.knowledge.domain.KbKp;
import com.ruoyi.knowledge.domain.KbKpEdge;
import com.ruoyi.knowledge.domain.KbOccurrence;
import com.ruoyi.knowledge.domain.KbQuestion;
import com.ruoyi.knowledge.domain.KbQuestionKp;
import com.ruoyi.knowledge.dto.KbKpOutlineBinding;
import com.ruoyi.knowledge.dto.KbKpOutlineRow;
import com.ruoyi.knowledge.dto.KbQuestionQuery;
import com.ruoyi.knowledge.mapper.KbKpMapper;
import com.ruoyi.knowledge.mapper.KbOccurrenceMapper;
import com.ruoyi.knowledge.mapper.KbOutlineMapper;
import com.ruoyi.knowledge.mapper.KbQuestionMapper;
import com.ruoyi.knowledge.service.IKbKpService;
import com.ruoyi.knowledge.storage.IKbAssetService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KbListBatchingTest {
    @Test
    void questionPageKeepsPaginationOrderAndScopedMatchesWithConstantQueryCount() {
        for (int pageSize : List.of(10, 20)) {
            KbQuestionMapper questions = mock(KbQuestionMapper.class);
            KbOccurrenceMapper occurrences = mock(KbOccurrenceMapper.class);
            KbQuestionServiceImpl service = new KbQuestionServiceImpl(questions, mock(KbKpMapper.class),
                    mock(IKbKpService.class), mock(IKbAssetService.class), occurrences, mock(KnowledgeDictionary.class));
            Page<KbQuestion> page = new Page<>(2, pageSize);
            page.setTotal(97);
            List<Long> ids = IntStream.range(0, pageSize).mapToObj(i -> 100L - i).toList();
            for (Long id : ids) {
                KbQuestion item = new KbQuestion();
                item.setId(id); item.setSubject("MATH"); item.setQuestionType("FILL");
                item.setStemHtml("<p>2 + 3 = ?</p>"); item.setRowVersion(4L);
                item.setIsRealExam(id.equals(100L));
                page.add(item);
            }
            KbQuestionQuery query = new KbQuestionQuery();
            query.setExamSourceId(42L); query.setWrongSourceId(43L);
            KbQuestionKp kpA = point(100L, 5L), kpB = point(100L, 9L);
            KbOccurrence exam = occurrence(99L, 42L, 1L);
            KbOccurrence wrongA = occurrence(100L, 43L, 2L), wrongB = occurrence(100L, 43L, 3L);
            when(questions.selectList(query)).thenReturn(page);
            when(questions.selectKpsForQuestions(ids)).thenReturn(List.of(kpA, kpB));
            when(occurrences.matchedExamForQuestions(ids, query)).thenReturn(List.of(exam));
            when(occurrences.matchedWrongForQuestions(ids, query)).thenReturn(List.of(wrongA, wrongB));

            List<KbQuestion> result = service.list(query);
            assertSame(page, result);
            assertEquals(97, page.getTotal());
            assertEquals(ids, result.stream().map(KbQuestion::getId).toList());
            assertEquals(List.of(5L, 9L), result.get(0).getKpIds());
            assertEquals(List.of(), result.get(1).getKpIds());
            assertTrue(result.get(0).getIsRealExam(), "Unscoped exam identity must not depend on scoped matches");
            assertEquals(List.of(), result.get(0).getMatchedExamOccurrences());
            assertEquals(List.of(exam), result.get(1).getMatchedExamOccurrences());
            assertEquals(List.of(wrongA, wrongB), result.get(0).getMatchedWrongOccurrences());
            assertEquals(List.of(), result.get(1).getMatchedWrongOccurrences());
            assertEquals("EXERCISE", result.get(0).getContentKind());
            assertEquals(4L, result.get(0).getRowVersion());
            assertTrue(result.get(1).getReadinessReasons().contains("缺少有效考点"));
            verify(questions).selectList(query);
            verify(questions).selectKpsForQuestions(ids);
            verifyNoMoreInteractions(questions);
            verify(occurrences).matchedExamForQuestions(ids, query);
            verify(occurrences).matchedWrongForQuestions(ids, query);
            verifyNoMoreInteractions(occurrences);
        }
    }

    @Test
    void kpPageKeepsMultipleChaptersUnlocatedBindingsAndEmptyRelationsWithoutNPlusOne() {
        for (int pageSize : List.of(10, 20)) {
            KbKpMapper mapper = mock(KbKpMapper.class);
            KbKpServiceImpl service = new KbKpServiceImpl(mapper, mock(KbOutlineMapper.class), mock(KnowledgeDictionary.class));
            Page<KbKp> page = new Page<>(2, pageSize);
            page.setTotal(45);
            List<Long> ids = IntStream.range(0, pageSize).mapToObj(i -> 100L - i).toList();
            for (Long id : ids) {
                KbKp item = new KbKp(); item.setId(id); item.setName("point " + id); item.setRowVersion(8L);
                page.add(item);
            }
            KbKp query = new KbKp();
            List<KbKpOutlineRow> rows = new ArrayList<>();
            rows.add(binding(100L, 16L, 31L)); rows.add(binding(100L, 16L, 32L));
            rows.add(binding(100L, 17L, null));
            KbKpOutlineRow orphan = new KbKpOutlineRow(); orphan.setKpId(99L); orphan.setOutlineId(18L);
            rows.add(orphan);
            KbKpEdge edge = new KbKpEdge(); edge.setFromKpId(100L); edge.setToKpId(99L); edge.setKind("NEST");
            when(mapper.selectList(query)).thenReturn(page);
            when(mapper.selectOutlineRowsForKps(ids)).thenReturn(rows);
            when(mapper.selectEdges()).thenReturn(List.of(edge));
            when(mapper.selectHierarchyPoints()).thenReturn(new ArrayList<>(page));

            assertSame(page, service.list(query));
            assertEquals(45, page.getTotal());
            assertEquals(ids, page.stream().map(KbKp::getId).toList());
            assertEquals(List.of(16L, 17L), page.get(0).getOutlineIds());
            assertEquals(3, page.get(0).getOutlineBindings().size());
            assertEquals(31L, page.get(0).getOutlineBindings().get(0).getNodeId());
            assertEquals(32L, page.get(0).getOutlineBindings().get(1).getNodeId());
            assertNull(page.get(0).getOutlineBindings().get(2).getNodeId());
            assertEquals(List.of(18L), page.get(1).getOutlineIds());
            assertEquals(List.of(), page.get(1).getOutlineBindings());
            assertEquals(List.of(), page.get(2).getOutlineIds());
            assertEquals(List.of(), page.get(2).getOutlineBindings());
            assertEquals(100L, page.get(1).getParentId());
            assertEquals(2, page.get(1).getDepth());
            assertEquals(8L, page.get(0).getRowVersion());
            verify(mapper).selectList(query);
            verify(mapper).selectOutlineRowsForKps(ids);
            verify(mapper).selectEdges();
            verify(mapper).selectHierarchyPoints();
            verifyNoMoreInteractions(mapper);
        }
    }

    @Test
    void emptyPagesKeepTheirTotalAndDoNotIssueUnboundedRelationQueries() {
        KbQuestionMapper questions = mock(KbQuestionMapper.class);
        KbOccurrenceMapper occurrences = mock(KbOccurrenceMapper.class);
        KbQuestionServiceImpl questionService = new KbQuestionServiceImpl(questions, mock(KbKpMapper.class),
                mock(IKbKpService.class), mock(IKbAssetService.class), occurrences, mock(KnowledgeDictionary.class));
        Page<KbQuestion> emptyQuestions = new Page<>(100, 10); emptyQuestions.setTotal(3);
        when(questions.selectList(any())).thenReturn(emptyQuestions);
        assertSame(emptyQuestions, questionService.list(new KbQuestionQuery()));
        assertEquals(3, emptyQuestions.getTotal());
        verify(questions).selectList(any()); verifyNoMoreInteractions(questions); verifyNoInteractions(occurrences);

        KbKpMapper points = mock(KbKpMapper.class);
        KbKpServiceImpl kpService = new KbKpServiceImpl(points, mock(KbOutlineMapper.class), mock(KnowledgeDictionary.class));
        Page<KbKp> emptyPoints = new Page<>(100, 10); emptyPoints.setTotal(4);
        when(points.selectList(any())).thenReturn(emptyPoints);
        assertSame(emptyPoints, kpService.list(new KbKp()));
        assertEquals(4, emptyPoints.getTotal());
        verify(points).selectList(any()); verifyNoMoreInteractions(points);
    }

    private KbQuestionKp point(long questionId, long kpId) {
        KbQuestionKp point = new KbQuestionKp(); point.setQuestionId(questionId); point.setKpId(kpId); return point;
    }

    private KbOccurrence occurrence(long questionId, long sourceId, long id) {
        KbOccurrence item = new KbOccurrence(); item.setQuestionId(questionId); item.setSourceId(sourceId); item.setId(id); return item;
    }

    private KbKpOutlineRow binding(long kpId, long outlineId, Long nodeId) {
        KbKpOutlineRow row = new KbKpOutlineRow(); row.setKpId(kpId); row.setOutlineId(outlineId); row.setNodeId(nodeId);
        KbKpOutlineBinding binding = new KbKpOutlineBinding(); binding.setOutlineId(outlineId); binding.setNodeId(nodeId);
        row.setBinding(binding); return row;
    }
}
