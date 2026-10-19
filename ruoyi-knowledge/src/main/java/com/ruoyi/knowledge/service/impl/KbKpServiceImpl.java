package com.ruoyi.knowledge.service.impl;

import static com.ruoyi.knowledge.service.impl.KnowledgeRules.changed;
import static com.ruoyi.knowledge.service.impl.KnowledgeRules.found;
import static com.ruoyi.knowledge.service.impl.KnowledgeRules.ids;
import static com.ruoyi.knowledge.service.impl.KnowledgeRules.text;
import static com.ruoyi.knowledge.service.impl.KnowledgeRules.unreferenced;
import static com.ruoyi.knowledge.service.impl.KnowledgeRules.version;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.knowledge.domain.KbKp;
import com.ruoyi.knowledge.domain.KbKpEdge;
import com.ruoyi.knowledge.domain.KbQuestionKp;
import com.ruoyi.knowledge.domain.KbOutline;
import com.ruoyi.knowledge.mapper.KbKpMapper;
import com.ruoyi.knowledge.mapper.KbOutlineMapper;
import com.ruoyi.knowledge.service.IKbKpService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 考点维护及嵌套/组合查询语义。 */
@Service
public class KbKpServiceImpl implements IKbKpService {
    private final KbKpMapper mapper;
    private final KbOutlineMapper outlines;
    private final KnowledgeDictionary dictionary;

    public KbKpServiceImpl(KbKpMapper mapper, KbOutlineMapper outlines, KnowledgeDictionary dictionary) {
        this.mapper = mapper;
        this.outlines = outlines;
        this.dictionary = dictionary;
    }

    public List<KbKp> list(KbKp query) {
        List<KbKp> page = mapper.selectList(query);
        page.forEach(this::attachOutlines);
        attachHierarchy(page, mapper.selectEdges());
        return page;
    }

    public KbKp get(Long id) {
        KbKp item = found(mapper.selectById(id));
        attachOutlines(item);
        List<KbKpEdge> allEdges = mapper.selectEdges();
        attachHierarchy(List.of(item), allEdges);
        List<KbKpEdge> edges = allEdges.stream().filter(e -> id.equals(e.getFromKpId())).toList();
        item.setNestedIds(
                edges.stream()
                        .filter(e -> "NEST".equals(e.getKind()))
                        .map(KbKpEdge::getToKpId)
                        .toList());
        item.setComponentIds(
                edges.stream()
                        .filter(e -> "COMBINE".equals(e.getKind()))
                        .map(KbKpEdge::getToKpId)
                        .toList());
        return item;
    }

    @Transactional
    public KbKp create(KbKp data) {
        data.setId(null);
        mapper.lockGraph();
        validate(data);
        mapper.insert(data);
        saveOutlines(data);
        relations(data);
        return get(data.getId());
    }

    @Transactional
    public KbKp update(KbKp data) {
        mapper.lockGraph();
        KbKp current = found(mapper.lockById(data.getId()));
        version(data.getRowVersion(), current.getRowVersion());
        if (data.getSubject() == null) data.setSubject(current.getSubject());
        if (data.getOutlineIds() == null) data.setOutlineIds(mapper.selectOutlineIds(data.getId()));
        validate(data);
        changed(mapper.update(data));
        saveOutlines(data);
        relations(data);
        return get(data.getId());
    }

    @Transactional
    public void delete(Long[] values) {
        mapper.lockGraph();
        for (Long id : values) {
            found(mapper.lockById(id));
            unreferenced(mapper.countReferences(id), "考点仍被题目、大纲、专项或其他考点引用");
            mapper.deleteEdges(id);
            mapper.deleteOutlines(id);
            mapper.deleteById(id);
        }
    }

    private void validate(KbKp data) {
        data.setName(text(data.getName(), "考点名称", 200));
        if (data.getDescription() == null) data.setDescription("");
        data.setSubject(text(data.getSubject(), "科目", 30));
        dictionary.validate("kb_subject", data.getSubject(), "科目");
        data.setOutlineIds(ids(data.getOutlineIds()));
        if (data.getOutlineIds().isEmpty()) throw new ServiceException("考点必须挂载至少一个教材大纲", 400);
        for (Long id : data.getOutlineIds().stream().sorted().toList()) {
            KbOutline outline = found(outlines.lockById(id));
            if (!"TEXTBOOK".equals(outline.getCatalogKind()))
                throw new ServiceException("考点须挂教材大纲，资料覆盖目录不能替代教材", 400);
            if (!java.util.Objects.equals(outline.getSubject(), data.getSubject()))
                throw new ServiceException("考点与教材大纲的科目必须一致", 400);
        }
        if (data.getId() != null && mapper.countMismatchedQuestions(data.getId(), data.getSubject()) > 0)
            throw new ServiceException("已有不同科目的内容引用此考点，不能变更科目", 409);
        if (data.getId() != null) {
            for (KbKpEdge edge : mapper.selectEdges()) {
                Long other = data.getId().equals(edge.getFromKpId()) ? edge.getToKpId()
                        : data.getId().equals(edge.getToKpId()) ? edge.getFromKpId() : null;
                if (other != null && !java.util.Objects.equals(found(mapper.selectById(other)).getSubject(), data.getSubject()))
                    throw new ServiceException("已有嵌套或组合关系的考点必须保持同一科目", 409);
            }
        }
    }

    private void attachOutlines(KbKp data) {
        data.setOutlineIds(mapper.selectOutlineIds(data.getId()));
        data.setOutlineBindings(mapper.selectOutlineBindings(data.getId()));
    }

    private void attachHierarchy(List<KbKp> items, List<KbKpEdge> edges) {
        List<KbKp> points = new ArrayList<>(mapper.selectHierarchyPoints());
        // Include the requested records even in an isolated mapper test.
        Set<Long> known = new HashSet<>();
        points.forEach(p -> known.add(p.getId()));
        items.stream().filter(p -> known.add(p.getId())).forEach(points::add);
        Map<Long, KbKpHierarchy.Position> positions = KbKpHierarchy.positions(points, edges);
        for (KbKp item : items) {
            KbKpHierarchy.Position position = positions.get(item.getId());
            item.setParentId(position.parentId());
            item.setRootId(position.rootId());
            item.setRootName(position.rootName());
            item.setDepth(position.depth());
        }
    }

    private void saveOutlines(KbKp data) {
        for (Long old : mapper.selectOutlineIds(data.getId())) {
            if (!data.getOutlineIds().contains(old)) {
                if (mapper.countChapterLinks(data.getId(), old) > 0)
                    throw new ServiceException("考点仍挂在该教材章节下，请先解除章节关系", 409);
                mapper.deleteOutline(data.getId(), old);
            }
        }
        for (Long id : data.getOutlineIds()) mapper.insertOutline(data.getId(), id);
    }

    private void relations(KbKp data) {
        if (data.getNestedIds() == null && data.getComponentIds() == null && data.getParentId() == null) return;
        List<KbKpEdge> previous = mapper.selectEdges();
        List<KbKpEdge> outgoing = previous.stream().filter(e -> data.getId().equals(e.getFromKpId())).toList();
        List<Long> nested =
                ids(data.getNestedIds() == null ? outgoing.stream().filter(e -> "NEST".equals(e.getKind())).map(KbKpEdge::getToKpId).toList() : data.getNestedIds());
        List<Long> combined =
                ids(
                        data.getComponentIds() == null
                                ? outgoing.stream().filter(e -> "COMBINE".equals(e.getKind())).map(KbKpEdge::getToKpId).toList()
                                : data.getComponentIds());
        if (combined.size() == 1 || combined.size() > 32)
            throw new ServiceException("组合考点需要2至32个组成项", 400);
        List<KbKpEdge> edges =
                new ArrayList<>(
                        previous.stream()
                                .filter(e -> !data.getId().equals(e.getFromKpId()))
                                .toList());
        for (String kind : List.of("NEST", "COMBINE"))
            for (Long target : (kind.equals("NEST") ? nested : combined)) {
                KbKp targetPoint = found(mapper.selectById(target));
                if (!java.util.Objects.equals(targetPoint.getSubject(), data.getSubject()))
                    throw new ServiceException("嵌套或组合考点的科目必须一致", 400);
                if (target.equals(data.getId())) throw new ServiceException("考点不能关联自身", 400);
                KbKpEdge edge = new KbKpEdge();
                edge.setFromKpId(data.getId());
                edge.setToKpId(target);
                edge.setKind(kind);
                edges.add(edge);
            }
        if (data.getParentId() != null) {
            if (data.getParentId() < 0 || data.getParentId().equals(data.getId()))
                throw new ServiceException("父考点必须为其他考点，顶级使用0", 400);
            edges.removeIf(e -> "NEST".equals(e.getKind()) && data.getId().equals(e.getToKpId()));
            if (data.getParentId() != 0) {
                KbKp parent = found(mapper.selectById(data.getParentId()));
                if (!java.util.Objects.equals(parent.getSubject(), data.getSubject()))
                    throw new ServiceException("父子考点的科目必须一致", 400);
                KbKpEdge edge = new KbKpEdge();
                edge.setFromKpId(parent.getId()); edge.setToKpId(data.getId()); edge.setKind("NEST");
                edges.add(edge);
            }
        }
        KbKpHierarchy.parents(edges);
        ensureAcyclic(edges);
        Set<EdgeKey> oldKeys = new HashSet<>(), newKeys = new HashSet<>();
        previous.forEach(e -> oldKeys.add(key(e))); edges.forEach(e -> newKeys.add(key(e)));
        Set<Long> affected = new HashSet<>();
        for (KbKpEdge edge : previous) if (!newKeys.contains(key(edge))) {
            mapper.deleteEdge(edge); affected.add(edge.getFromKpId()); affected.add(edge.getToKpId());
        }
        for (KbKpEdge edge : edges) if (!oldKeys.contains(key(edge))) {
            mapper.insertEdge(edge); affected.add(edge.getFromKpId()); affected.add(edge.getToKpId());
        }
        affected.remove(data.getId());
        if (!affected.isEmpty()) mapper.touchRelations(affected.stream().sorted().toList(),
                data.getUpdateBy() == null ? data.getCreateBy() : data.getUpdateBy());
    }

    private record EdgeKey(Long from, Long to, String kind) {}
    private static EdgeKey key(KbKpEdge edge) { return new EdgeKey(edge.getFromKpId(), edge.getToKpId(), edge.getKind()); }

    public static void ensureAcyclic(List<KbKpEdge> edges) {
        KbKpHierarchy.topological(edges);
    }

    public Set<Long> matchingQuestionIds(Long id, boolean expand) {
        found(mapper.selectById(id));
        return match(id, expand, mapper.selectEdges(), mapper.selectQuestionPoints());
    }

    public static Set<Long> match(
            Long id, boolean expand, List<KbKpEdge> edges, List<KbQuestionKp> links) {
        ensureAcyclic(edges);
        Map<Long, Set<Long>> direct = new HashMap<>();
        for (KbQuestionKp link : links)
            direct.computeIfAbsent(link.getKpId(), k -> new HashSet<>()).add(link.getQuestionId());
        if (!expand) return new HashSet<>(direct.getOrDefault(id, Set.of()));
        List<Long> order = KbKpHierarchy.topological(edges);
        Map<Long, List<KbKpEdge>> outgoing = new HashMap<>();
        edges.forEach(e -> outgoing.computeIfAbsent(e.getFromKpId(), k -> new ArrayList<>()).add(e));
        Map<Long, Set<Long>> memo = new HashMap<>();
        for (int index = order.size() - 1; index >= 0; index--) {
            Long current = order.get(index);
            Set<Long> result = new HashSet<>(direct.getOrDefault(current, Set.of()));
            Set<Long> intersection = null;
            for (KbKpEdge edge : outgoing.getOrDefault(current, List.of())) {
                Set<Long> child = memo.getOrDefault(edge.getToKpId(), Set.of());
                if ("NEST".equals(edge.getKind())) result.addAll(child);
                else if (intersection == null) intersection = new HashSet<>(child);
                else intersection.retainAll(child);
            }
            if (intersection != null) result.addAll(intersection);
            memo.put(current, result);
        }
        return memo.getOrDefault(id, new HashSet<>(direct.getOrDefault(id, Set.of())));
    }
}
