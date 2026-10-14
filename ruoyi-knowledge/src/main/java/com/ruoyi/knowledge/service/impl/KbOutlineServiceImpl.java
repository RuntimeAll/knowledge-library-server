package com.ruoyi.knowledge.service.impl;

import static com.ruoyi.knowledge.service.impl.KnowledgeRules.changed;
import static com.ruoyi.knowledge.service.impl.KnowledgeRules.found;
import static com.ruoyi.knowledge.service.impl.KnowledgeRules.ids;
import static com.ruoyi.knowledge.service.impl.KnowledgeRules.text;
import static com.ruoyi.knowledge.service.impl.KnowledgeRules.unreferenced;
import static com.ruoyi.knowledge.service.impl.KnowledgeRules.version;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.knowledge.domain.KbOutline;
import com.ruoyi.knowledge.domain.KbOutlineNode;
import com.ruoyi.knowledge.mapper.KbKpMapper;
import com.ruoyi.knowledge.mapper.KbOutlineMapper;
import com.ruoyi.knowledge.mapper.KbOutlineNodeMapper;
import com.ruoyi.knowledge.service.IKbOutlineService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class KbOutlineServiceImpl implements IKbOutlineService {
    private final KbOutlineMapper mapper;
    private final KbOutlineNodeMapper nodes;
    private final KbKpMapper points;

    public KbOutlineServiceImpl(
            KbOutlineMapper mapper, KbOutlineNodeMapper nodes, KbKpMapper points) {
        this.mapper = mapper;
        this.nodes = nodes;
        this.points = points;
    }

    public List<KbOutline> list(KbOutline query) {
        return mapper.selectList(query);
    }

    public KbOutline get(Long id) {
        KbOutline data = found(mapper.selectById(id));
        KbOutlineNode query = new KbOutlineNode();
        query.setOutlineId(id);
        List<KbOutlineNode> values = nodes.selectList(query);
        values.forEach(n -> n.setKpIds(nodes.selectKpIds(n.getId())));
        data.setNodes(values);
        return data;
    }

    @Transactional
    public KbOutline create(KbOutline data) {
        data.setTitle(text(data.getTitle(), "大纲标题", 200));
        mapper.insert(data);
        return get(data.getId());
    }

    @Transactional
    public KbOutline update(KbOutline data) {
        version(data.getRowVersion(), found(mapper.lockById(data.getId())).getRowVersion());
        data.setTitle(text(data.getTitle(), "大纲标题", 200));
        changed(mapper.update(data));
        return get(data.getId());
    }

    @Transactional
    public void delete(Long[] values) {
        for (Long id : values) {
            found(mapper.lockById(id));
            unreferenced(mapper.countNodes(id), "大纲仍有章节，请先清空章节");
            mapper.deleteById(id);
        }
    }

    @Transactional
    public KbOutlineNode createNode(KbOutlineNode data) {
        found(mapper.lockById(data.getOutlineId()));
        validateNode(data);
        nodes.insert(data);
        saveKps(data);
        return node(data.getId());
    }

    @Transactional
    public KbOutlineNode updateNode(KbOutlineNode data) {
        // Serialize the tree before any consistent read creates an RR snapshot.
        found(mapper.lockById(data.getOutlineId()));
        KbOutlineNode old = found(nodes.lockById(data.getId()));
        if (!Objects.equals(old.getOutlineId(), data.getOutlineId()))
            throw new ServiceException("不能跨大纲移动章节", 400);
        version(data.getRowVersion(), old.getRowVersion());
        validateNode(data);
        changed(nodes.update(data));
        saveKps(data);
        return node(data.getId());
    }

    @Transactional
    public void deleteNode(Long id) {
        KbOutlineNode data = found(nodes.selectById(id));
        found(mapper.lockById(data.getOutlineId()));
        unreferenced(nodes.countChildren(id), "章节仍有子章节");
        if (!nodes.selectKpIds(id).isEmpty()) throw new ServiceException("章节仍挂有考点，请先解除", 409);
        nodes.deleteById(id);
    }

    private KbOutlineNode node(Long id) {
        KbOutlineNode data = found(nodes.selectById(id));
        data.setKpIds(nodes.selectKpIds(id));
        return data;
    }

    private void validateNode(KbOutlineNode data) {
        data.setTitle(text(data.getTitle(), "章节标题", 200));
        if (data.getParentId() != null && data.getParentId() == 0) data.setParentId(null);
        Long ancestor = data.getParentId();
        Set<Long> seen = new HashSet<>();
        while (ancestor != null) {
            if (ancestor.equals(data.getId()) || !seen.add(ancestor))
                throw new ServiceException("章节关系不能形成循环", 409);
            KbOutlineNode parent = found(nodes.selectById(ancestor));
            if (!Objects.equals(parent.getOutlineId(), data.getOutlineId()))
                throw new ServiceException("父章节须属于同一大纲", 400);
            ancestor = parent.getParentId();
        }
        if (data.getSortOrder() == null) data.setSortOrder(0);
    }

    private void saveKps(KbOutlineNode data) {
        if (data.getKpIds() == null) return;
        List<Long> wanted = ids(data.getKpIds());
        for (Long id : wanted) found(points.lockById(id));
        nodes.deleteKps(data.getId());
        for (Long id : wanted) nodes.insertKp(data.getId(), id);
    }
}
