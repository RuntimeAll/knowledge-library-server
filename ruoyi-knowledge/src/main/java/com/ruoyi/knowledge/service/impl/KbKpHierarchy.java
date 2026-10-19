package com.ruoyi.knowledge.service.impl;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.knowledge.domain.KbKp;
import com.ruoyi.knowledge.domain.KbKpEdge;
import java.util.*;

/** NEST defines the single-parent hierarchy; COMBINE is orthogonal and never changes depth. */
final class KbKpHierarchy {
    record Position(long parentId, long rootId, String rootName, int depth) {}

    static Map<Long, Position> positions(List<KbKp> points, List<KbKpEdge> edges) {
        Map<Long, Long> parents = parents(edges);
        Map<Long, List<Long>> children = new HashMap<>();
        Map<Long, String> names = new HashMap<>();
        for (KbKp point : points) names.put(point.getId(), point.getName());
        parents.forEach((child, parent) -> children.computeIfAbsent(parent, k -> new ArrayList<>()).add(child));
        Map<Long, Position> result = new HashMap<>();
        Deque<Long> queue = new ArrayDeque<>();
        for (Long id : names.keySet()) if (!parents.containsKey(id)) {
            result.put(id, new Position(0, id, names.get(id), 1));
            queue.add(id);
        }
        while (!queue.isEmpty()) {
            long id = queue.remove();
            Position parent = result.get(id);
            for (Long child : children.getOrDefault(id, List.of())) {
                result.put(child, new Position(id, parent.rootId(), parent.rootName(), parent.depth() + 1));
                queue.add(child);
            }
        }
        if (result.size() != names.size()) throw new ServiceException("考点层级存在循环或失效的父考点", 409);
        return result;
    }

    static Map<Long, Long> parents(List<KbKpEdge> edges) {
        Map<Long, Long> parents = new HashMap<>();
        for (KbKpEdge edge : edges) if ("NEST".equals(edge.getKind())) {
            Long old = parents.putIfAbsent(edge.getToKpId(), edge.getFromKpId());
            if (old != null && !old.equals(edge.getFromKpId()))
                throw new ServiceException("一个考点只能有一个直属父考点，请通过父考点字段调整层级", 409);
        }
        return parents;
    }

    /** Iterative traversal avoids a stack-depth limit for deeply nested hierarchies. */
    static List<Long> topological(List<KbKpEdge> edges) {
        Map<Long, Integer> degrees = new HashMap<>();
        Map<Long, List<Long>> children = new HashMap<>();
        for (KbKpEdge edge : edges) {
            degrees.putIfAbsent(edge.getFromKpId(), 0);
            degrees.merge(edge.getToKpId(), 1, Integer::sum);
            children.computeIfAbsent(edge.getFromKpId(), k -> new ArrayList<>()).add(edge.getToKpId());
        }
        Deque<Long> queue = new ArrayDeque<>();
        degrees.forEach((id, degree) -> { if (degree == 0) queue.add(id); });
        List<Long> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            Long id = queue.remove();
            order.add(id);
            for (Long child : children.getOrDefault(id, List.of()))
                if (degrees.merge(child, -1, Integer::sum) == 0) queue.add(child);
        }
        if (order.size() != degrees.size()) throw new ServiceException("嵌套与组合关系不能形成循环", 409);
        return order;
    }
}
