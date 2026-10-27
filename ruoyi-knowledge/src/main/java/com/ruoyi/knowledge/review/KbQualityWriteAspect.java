package com.ruoyi.knowledge.review;

import com.ruoyi.knowledge.mapper.KbReviewMapper;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.knowledge.domain.KbKp;
import com.ruoyi.knowledge.domain.KbOccurrence;
import com.ruoyi.knowledge.domain.KbOutline;
import com.ruoyi.knowledge.domain.KbOutlineNode;
import com.ruoyi.knowledge.domain.KbQuestion;
import com.ruoyi.knowledge.domain.KbSource;
import com.ruoyi.knowledge.mapper.KbOutlineNodeMapper;
import java.util.List;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Quality serialization encloses Spring transaction advice, including importer REQUIRES_NEW. */
@Aspect
@Component
@Order(-100)
public class KbQualityWriteAspect {
    private static final int MAX_IMPACT = 1000;
    private final KbReviewGate gate;
    private final KbReviewService reviews;
    private final KbReviewMapper mapper;
    private final KbOutlineNodeMapper nodes;
    private final TransactionTemplate transaction;

    public KbQualityWriteAspect(KbReviewGate gate, KbReviewService reviews, KbReviewMapper mapper,
            KbOutlineNodeMapper nodes, PlatformTransactionManager manager) {
        this.gate = gate;
        this.reviews = reviews;
        this.mapper = mapper;
        this.nodes = nodes;
        transaction = new TransactionTemplate(manager);
    }

    @Around("@annotation(command)")
    public Object write(ProceedingJoinPoint invocation, KbQualityWrite command) throws Throwable {
        boolean outer = !gate.held();
        if (outer && TransactionSynchronizationManager.isActualTransactionActive())
            { throw new IllegalStateException("Quality write started inside an unguarded transaction"); }
        gate.enter();
        try {
            if (outer) { reviews.recover(); }
            if ("NONE".equals(command.dependency())) { return invocation.proceed(); }
            return transaction.execute(status -> {
                Long questionId = "QUESTION".equals(command.dependency()) && invocation.getArgs()[0] instanceof KbQuestion q ? q.getId() : null;
                Long previousVersion = questionId == null ? null : mapper.questionVersion(questionId);
                List<Long> before = impact(command.dependency(), invocation.getArgs()[0]);
                if (invocation.getSignature().getName().startsWith("delete") && !before.isEmpty()) {
                    List<Long> ready = mapper.readyIds(before);
                    if (!ready.isEmpty()) { throw new ServiceException("Deletion would invalidate reviewed questions: " + ready, 409); }
                }
                Object result;
                try { result = invocation.proceed(); }
                catch (RuntimeException | Error error) { throw error; }
                catch (Throwable error) { throw new IllegalStateException("Quality mutation failed", error); }
                if (questionId != null && java.util.Objects.equals(previousVersion, mapper.questionVersion(questionId))) { return result; }
                // Include newly added chapter/point bindings; both sets remain bounded in this transaction.
                List<Long> after = impact(command.dependency(), invocation.getArgs()[0]);
                List<Long> affected = java.util.stream.Stream.concat(before.stream(), after.stream()).distinct().sorted().toList();
                if (affected.size() > MAX_IMPACT) { throw new ServiceException("依赖影响超过1000题，请提交精确治理计划", 409); }
                if (!affected.isEmpty()) { mapper.draft(affected, actor()); }
                if (result instanceof KbQualityImpact impact) { impact.setAffectedQuestionIds(affected.stream().map(String::valueOf).toList()); }
                return result;
            });
        } finally { gate.leave(); }
    }

    @Around("@annotation(com.ruoyi.knowledge.review.KbQualityRead)")
    public Object read(ProceedingJoinPoint invocation) throws Throwable {
        gate.enter();
        try { return invocation.proceed(); }
        finally { gate.leave(); }
    }

    private List<Long> impact(String kind, Object input) {
        if (input instanceof KbKp point && ("KP".equals(kind) || "KP_CREATE".equals(kind))) {
            java.util.Set<Long> pointIds = new java.util.TreeSet<>();
            if ("KP".equals(kind) && point.getId() != null) {
                pointIds.add(point.getId());
                pointIds.addAll(mapper.relatedKpIds(point.getId()));
            }
            if (point.getNestedIds() != null) { pointIds.addAll(point.getNestedIds()); }
            if (point.getComponentIds() != null) { pointIds.addAll(point.getComponentIds()); }
            if (point.getParentId() != null && point.getParentId() > 0) { pointIds.add(point.getParentId()); }
            java.util.Set<Long> affected = new java.util.TreeSet<>();
            for (Long pointId : pointIds) {
                affected.addAll(mapper.impacted("KP", pointId, MAX_IMPACT + 1));
                if (affected.size() > MAX_IMPACT) { throw new ServiceException("Dependency impact exceeds the bounded limit",409); }
            }
            return List.copyOf(affected);
        }
        Long id;
        if (input instanceof KbQuestion value) { id = value.getId(); }
        else if (input instanceof KbSource value) { id = value.getId(); }
        else if (input instanceof KbKp value) { id = value.getId(); }
        else if (input instanceof KbOutline value) { id = value.getId(); }
        else if (input instanceof KbOutlineNode value) { id = value.getOutlineId(); kind = "OUTLINE"; }
        else if (input instanceof KbOccurrence value) { id = value.getSourceId(); kind = "SOURCE"; }
        else if (input instanceof Long value) { id = value; }
        else { throw new IllegalStateException("Unsupported quality dependency argument"); }
        if ("NODE".equals(kind)) {
            KbOutlineNode node = nodes.selectById(id);
            if (node == null) { return List.of(); }
            kind = "OUTLINE";
            id = node.getOutlineId();
        }
        if (id == null) { return List.of(); }
        List<Long> result = mapper.impacted(kind, id, MAX_IMPACT + 1);
        if (result.size() > MAX_IMPACT) { throw new ServiceException("依赖影响超过1000题，请提交精确治理计划", 409); }
        return result;
    }

    private static String actor() {
        try { return SecurityUtils.getUsername(); }
        catch (ServiceException exception) { return "system"; }
    }
}
