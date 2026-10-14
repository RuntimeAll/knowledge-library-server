package com.ruoyi.knowledge.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ruoyi.knowledge.domain.KbOutline;
import com.ruoyi.knowledge.domain.KbOutlineNode;
import com.ruoyi.knowledge.mapper.KbKpMapper;
import com.ruoyi.knowledge.mapper.KbOutlineMapper;
import com.ruoyi.knowledge.mapper.KbOutlineNodeMapper;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class KbOutlineConcurrencyTest {
    @Test
    void treeLockPrecedesReadsUsedToValidateMove() {
        KbOutlineMapper outlines = mock(KbOutlineMapper.class);
        KbOutlineNodeMapper nodes = mock(KbOutlineNodeMapper.class);
        KbOutlineServiceImpl service =
                new KbOutlineServiceImpl(outlines, nodes, mock(KbKpMapper.class));
        KbOutlineNode current = new KbOutlineNode();
        current.setId(2L);
        current.setOutlineId(1L);
        current.setRowVersion(0L);
        current.setTitle("章节");
        KbOutlineNode parent = new KbOutlineNode();
        parent.setId(3L);
        parent.setOutlineId(1L);
        when(outlines.lockById(1L)).thenReturn(new KbOutline());
        when(nodes.lockById(2L)).thenReturn(current);
        when(nodes.selectById(3L)).thenReturn(parent);
        when(nodes.selectById(2L)).thenReturn(current);
        when(nodes.update(any())).thenReturn(1);
        KbOutlineNode input = new KbOutlineNode();
        input.setId(2L);
        input.setOutlineId(1L);
        input.setRowVersion(0L);
        input.setTitle("章节");
        input.setParentId(3L);
        service.updateNode(input);
        InOrder order = inOrder(outlines, nodes);
        order.verify(outlines).lockById(1L);
        order.verify(nodes).lockById(2L);
        order.verify(nodes).selectById(3L);
        order.verify(nodes).update(input);
    }
}
