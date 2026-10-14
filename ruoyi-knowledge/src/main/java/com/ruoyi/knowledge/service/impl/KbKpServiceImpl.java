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
import com.ruoyi.knowledge.mapper.KbKpMapper;
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

    public KbKpServiceImpl(KbKpMapper mapper) {
        this.mapper = mapper;
    }

    public List<KbKp> list(KbKp query) {
        return mapper.selectList(query);
    }

    public KbKp get(Long id) {
        KbKp item = found(mapper.selectById(id));
        List<KbKpEdge> edges =
                mapper.selectEdges().stream().filter(e -> id.equals(e.getFromKpId())).toList();
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
        validate(data);
        mapper.lockGraph();
        mapper.insert(data);
        relations(data);
        return get(data.getId());
    }

    @Transactional
    public KbKp update(KbKp data) {
        mapper.lockGraph();
        KbKp current = found(mapper.lockById(data.getId()));
        version(data.getRowVersion(), current.getRowVersion());
        validate(data);
        changed(mapper.update(data));
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
            mapper.deleteById(id);
        }
    }

    private void validate(KbKp data) {
        data.setName(text(data.getName(), "考点名称", 200));
        if (data.getDescription() == null) data.setDescription("");
        if (data.getGradeHint() == null) data.setGradeHint("");
    }

    private void relations(KbKp data) {
        if (data.getNestedIds() == null && data.getComponentIds() == null) return;
        KbKp old = get(data.getId());
        List<Long> nested =
                ids(data.getNestedIds() == null ? old.getNestedIds() : data.getNestedIds());
        List<Long> combined =
                ids(
                        data.getComponentIds() == null
                                ? old.getComponentIds()
                                : data.getComponentIds());
        if (combined.size() == 1 || combined.size() > 32)
            throw new ServiceException("组合考点需要2至32个组成项", 400);
        List<KbKpEdge> edges =
                new ArrayList<>(
                        mapper.selectEdges().stream()
                                .filter(e -> !data.getId().equals(e.getFromKpId()))
                                .toList());
        for (String kind : List.of("NEST", "COMBINE"))
            for (Long target : (kind.equals("NEST") ? nested : combined)) {
                found(mapper.selectById(target));
                if (target.equals(data.getId())) throw new ServiceException("考点不能关联自身", 400);
                KbKpEdge edge = new KbKpEdge();
                edge.setFromKpId(data.getId());
                edge.setToKpId(target);
                edge.setKind(kind);
                edges.add(edge);
            }
        ensureAcyclic(edges);
        mapper.deleteEdges(data.getId());
        edges.stream()
                .filter(e -> data.getId().equals(e.getFromKpId()))
                .forEach(mapper::insertEdge);
    }

    public static void ensureAcyclic(List<KbKpEdge> edges) {
        Map<Long, List<Long>> graph = new HashMap<>();
        for (KbKpEdge e : edges)
            graph.computeIfAbsent(e.getFromKpId(), k -> new ArrayList<>()).add(e.getToKpId());
        Set<Long> done = new HashSet<>(), visiting = new HashSet<>();
        for (Long vertex : graph.keySet()) visit(vertex, graph, done, visiting);
    }

    private static void visit(
            Long id, Map<Long, List<Long>> graph, Set<Long> done, Set<Long> visiting) {
        if (done.contains(id)) return;
        if (!visiting.add(id)) throw new ServiceException("嵌套与组合关系不能形成循环", 409);
        for (Long child : graph.getOrDefault(id, List.of())) visit(child, graph, done, visiting);
        visiting.remove(id);
        done.add(id);
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
        return expand
                ? expand(id, edges, direct, new HashMap<>())
                : new HashSet<>(direct.getOrDefault(id, Set.of()));
    }

    private static Set<Long> expand(
            Long id, List<KbKpEdge> edges, Map<Long, Set<Long>> direct, Map<Long, Set<Long>> memo) {
        if (memo.containsKey(id)) return memo.get(id);
        Set<Long> result = new HashSet<>(direct.getOrDefault(id, Set.of()));
        Set<Long> intersection = null;
        for (KbKpEdge edge : edges)
            if (id.equals(edge.getFromKpId())) {
                Set<Long> child = expand(edge.getToKpId(), edges, direct, memo);
                if ("NEST".equals(edge.getKind())) result.addAll(child);
                else if (intersection == null) intersection = new HashSet<>(child);
                else intersection.retainAll(child);
            }
        if (intersection != null) result.addAll(intersection);
        memo.put(id, result);
        return result;
    }
}
