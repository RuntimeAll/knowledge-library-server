package com.ruoyi.knowledge.service.impl;

import com.ruoyi.knowledge.domain.KbOutline;
import com.ruoyi.knowledge.domain.KbOutlineNode;
import com.ruoyi.knowledge.dto.KbOutlineNodeKp;
import com.ruoyi.knowledge.mapper.KbKpMapper;
import com.ruoyi.knowledge.mapper.KbOutlineMapper;
import com.ruoyi.knowledge.mapper.KbOutlineNodeMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class KbOutlineBatchingTest {
    @Test
    void outlineLoadsNodeRelationsOnceWithoutChangingNodeOrderOrFields() {
        for (int size : List.of(10, 20)) {
            var mapper = mock(KbOutlineMapper.class);
            var nodes = mock(KbOutlineNodeMapper.class);
            var points = mock(KbKpMapper.class);
            var dictionary = mock(KnowledgeDictionary.class);
            var service = new KbOutlineServiceImpl(mapper, nodes, points, dictionary);
            var outline = new KbOutline(); outline.setId(36L); outline.setRowVersion(8L);
            outline.setTitle("教材"); outline.setStatus("CONFIRMED");
            // SQL node order is sort_order,id, not necessarily id order.
            List<KbOutlineNode> chapters = IntStream.range(0, size).mapToObj(index -> {
                var chapter = new KbOutlineNode(); chapter.setId(100L - index); chapter.setOutlineId(36L);
                chapter.setTitle("章节 " + index); chapter.setSortOrder(index); chapter.setRowVersion(4L);
                chapter.setParentId(index == 0 ? null : 100L);
                return chapter;
            }).toList();
            List<Long> ids = chapters.stream().map(KbOutlineNode::getId).toList();
            when(mapper.selectById(36L)).thenReturn(outline);
            when(nodes.selectList(argThat(query -> query.getOutlineId().equals(36L)))).thenReturn(chapters);
            when(nodes.selectKpsForNodes(ids)).thenReturn(List.of(link(99, 7), link(100, 2), link(100, 5)));

            assertSame(outline, service.get(36L));
            assertSame(chapters, outline.getNodes());
            assertEquals(ids, outline.getNodes().stream().map(KbOutlineNode::getId).toList());
            assertEquals(List.of(2L, 5L), chapters.get(0).getKpIds());
            assertEquals(List.of(7L), chapters.get(1).getKpIds());
            assertEquals(List.of(), chapters.get(2).getKpIds());
            assertNull(chapters.get(0).getParentId());
            assertEquals(100L, chapters.get(1).getParentId());
            assertEquals(4L, chapters.get(0).getRowVersion());
            assertEquals("章节 0", chapters.get(0).getTitle());
            assertEquals(8L, outline.getRowVersion());
            assertEquals("CONFIRMED", outline.getStatus());
            verify(mapper).selectById(36L);
            verify(nodes).selectList(argThat(query -> query.getOutlineId().equals(36L)));
            verify(nodes).selectKpsForNodes(ids);
            verifyNoMoreInteractions(mapper, nodes);
            verifyNoInteractions(points, dictionary);
        }
    }

    @Test
    void outlineWithoutNodesDoesNotIssueEmptyOrUnboundedRelationQuery() {
        var mapper = mock(KbOutlineMapper.class);
        var nodes = mock(KbOutlineNodeMapper.class);
        var outline = new KbOutline(); outline.setId(36L);
        when(mapper.selectById(36L)).thenReturn(outline);
        when(nodes.selectList(any())).thenReturn(List.of());
        var service = new KbOutlineServiceImpl(mapper, nodes, mock(KbKpMapper.class), mock(KnowledgeDictionary.class));
        assertEquals(List.of(), service.get(36L).getNodes());
        verify(mapper).selectById(36L); verify(nodes).selectList(any());
        verifyNoMoreInteractions(mapper, nodes);
    }

    @Test
    void batchSqlRestrictsNodeIdsAndKeepsKnowledgePointOrder() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/knowledge/KbOutlineNodeMapper.xml";
        try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input);
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        var mapped = configuration.getMappedStatement("com.ruoyi.knowledge.mapper.KbOutlineNodeMapper.selectKpsForNodes");
        var bound = mapped.getBoundSql(Map.of("ids", List.of(100L, 99L)));
        String sql = bound.getSql().replaceAll("\\s+", " ").trim();
        assertTrue(sql.contains("WHERE node_id IN ( ? , ? )"));
        assertTrue(sql.endsWith("ORDER BY node_id,kp_id"));
        assertEquals(2, bound.getParameterMappings().size());
        assertEquals(KbOutlineNodeKp.class, mapped.getResultMaps().get(0).getType());
    }

    private static KbOutlineNodeKp link(long nodeId, long kpId) {
        var link = new KbOutlineNodeKp(); link.setNodeId(nodeId); link.setKpId(kpId); return link;
    }
}
