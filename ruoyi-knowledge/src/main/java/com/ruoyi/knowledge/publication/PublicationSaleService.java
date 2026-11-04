package com.ruoyi.knowledge.publication;

import static com.ruoyi.knowledge.publication.PublicationSupport.fail;
import static com.ruoyi.knowledge.publication.PublicationSupport.id;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.knowledge.publication.PublicationModels.Command;
import com.ruoyi.knowledge.publication.PublicationModels.CheckResult;
import com.ruoyi.knowledge.publication.PublicationModels.QuestionCheck;
import com.ruoyi.knowledge.publication.PublicationModels.OperationReceipt;
import com.ruoyi.knowledge.publication.PublicationModels.OperationResult;
import com.ruoyi.knowledge.publication.PublicationModels.QuestionResult;
import com.ruoyi.knowledge.publication.PublicationModels.SaleResult;
import com.ruoyi.knowledge.publication.PublicationModels.Snapshot;
import com.ruoyi.knowledge.publication.PublicationRows.Publication;
import com.ruoyi.knowledge.publication.PublicationRows.PublicationCheck;
import com.ruoyi.knowledge.publication.PublicationRows.PublicationOperation;
import com.ruoyi.knowledge.review.KbPublicationReviewBridge;
import com.ruoyi.knowledge.review.KbReviewService;
import com.ruoyi.knowledge.review.ReviewModels;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Resumable coordinator: independent review transactions precede the publication commit. */
@Service
public class PublicationSaleService {
    private final PublicationService service;
    private final PublicationCheckService checks;
    private final KbPublicationReviewBridge bridge;
    private final KbReviewService reviews;
    private final PublicationShareService shares;
    public PublicationSaleService(PublicationService service, PublicationCheckService checks,
            KbPublicationReviewBridge bridge, KbReviewService reviews, PublicationShareService shares) {
        this.service = service; this.checks = checks; this.bridge = bridge; this.reviews = reviews; this.shares = shares;
    }
    public SaleResult sale(Long id, Command command) {
        service.support.permission("sale"); PublicationService.key(command.operationKey);
        PublicationOperation op = service.support.gated(() -> advance(id, command));
        PublicationOperation generation = null;
        if ("SUCCEEDED".equals(op.status)) { generation = shares.generate(id, op.id); }
        Publication current = service.require(id);
        return new SaleResult(id(id), current.status, id(current.rowVersion), id(current.contentVersion),
                service.receipt(current, op), service.receipt(current, generation), service.shareUrl(current), service.support.deliveryMode());
    }
    private PublicationOperation advance(Long id, Command command) {
        Publication row = service.require(id); service.support.own(row);
        PublicationOperation op = service.mapper.operation(id, command.operationKey);
        if (op == null) { op = begin(row, command); }
        else { service.same(op, command, "SALE"); }
        if (List.of("SUCCEEDED", "FAILED").contains(op.status)) { return op; }
        if (!service.support.sameActor(op)) {
            throw fail(403, "FORBIDDEN", "只有原发起人可继续未完成批准；其他管理员可对账");
        }
        PublicationService.preparing(row); service.support.versions(row, command);
        op.status = "PENDING"; op.attemptCount++; save(op);
        Snapshot snapshot = service.support.decode(op.snapshotJson, Snapshot.class);
        OperationResult result = service.support.decode(op.resultJson, OperationResult.class);
        if (result == null) { result = service.result(row); }
        try {
            advanceQuestions(row, op, snapshot, result, command.reason);
            CheckResult after = checks.evaluate(row, false);
            if (!"PASSED".equals(after.status) || !Objects.equals(after.dependencyHash, snapshot.check().dependencyHash)) {
                throw fail(409, "DEPENDENCY_CHANGED", "发售最终核对失败，请核对原操作后重检");
            }
            commit(row, op, result, command);
        } catch (RuntimeException e) {
            op.status = "UNCERTAIN"; op.errorCode = "DEPENDENCY_UNAVAILABLE";
            op.errorMessage = "发售尚未全部确认，请按原操作核对后继续"; op.resultJson = service.support.encode(result); save(op);
        }
        return op;
    }
    private PublicationOperation begin(Publication row, Command c) {
        PublicationService.preparing(row); service.support.versions(row, c); service.noActiveSale(row.id);
        PublicationCheck stored = service.mapper.getCheck(row.id, c.checkId);
        if (stored == null || !"PASSED".equals(stored.status) || !Objects.equals(stored.contentVersion, row.contentVersion)) {
            throw fail(409, "CHECK_STALE", "请先完成当前资料自检");
        }
        CheckResult current = checks.evaluate(row, true);
        if (!"PASSED".equals(current.status)) { throw new PublicationException(422, "CHECK_BLOCKED", "资料仍有阻断项", current.issues); }
        if (!Objects.equals(current.dependencyHash, stored.dependencyHash)) { throw fail(409, "CHECK_STALE", "自检依据已变化"); }
        if (c.reason == null || c.reason.isBlank()) { throw fail(400, "INVALID_REQUEST", "发售核实说明必填"); }
        for (QuestionCheck q : current.items) {
            if (!reviews.readiness(q.questionId).eligible()
                    && !com.ruoyi.common.utils.SecurityUtils.hasPermi("knowledge:question:approve")) {
                throw fail(403, "FORBIDDEN", "本次包含草稿题，需要题目批准权限");
            }
        }
        current.checkId = stored.id;
        return service.support.transaction(() -> {
            Publication locked = service.lock(row.id); service.support.versions(locked, c); service.noActiveSale(row.id);
            return service.newOperation(locked, c, "SALE", new Snapshot(service.graph.read(locked), current));
        });
    }
    private void advanceQuestions(Publication row, PublicationOperation op, Snapshot snapshot,
            OperationResult result, String reason) {
        for (QuestionCheck q : snapshot.check().items) {
            if (result.questionResults.stream().anyMatch(r -> Objects.equals(r.questionId(), id(q.questionId)))) { continue; }
            ReviewModels.Command command = new ReviewModels.Command(PublicationReviewValidator.childKey(op, q.questionId),
                    q.observedRowVersion, reason, true, null, q.contentHash);
            ReviewModels.PublicationApprovalResult approved = bridge.approveOrReuse(
                    new ReviewModels.PublicationContext(row.id, row.rowVersion), op.operationKey, q.questionId, command);
            result.questionResults.add(new QuestionResult(id(q.questionId), approved.mode(), id(q.observedRowVersion),
                    approved.readiness().rowVersion(), approved.readiness().contentHash(),
                    approved.evidence().operationId(), approved.evidence().policyVersion()));
            op.resultJson = service.support.encode(result); save(op);
        }
    }
    private void commit(Publication row, PublicationOperation op, OperationResult result, Command c) {
        service.support.transaction(() -> {
            Publication locked = service.lock(row.id); service.support.versions(locked, c); PublicationService.preparing(locked);
            locked.status = "ON_SALE"; locked.lastSaleOperationId = op.id; locked.soldAt = new Date();
            locked.shareState = "NOT_CREATED"; service.support.updated(locked);
            PublicationService.changed(service.mapper.updatePublication(locked)); locked.rowVersion++;
            result.publicationStatus = locked.status; result.rowVersion = locked.rowVersion; result.shareState = locked.shareState;
            op.status = "SUCCEEDED"; op.finishedAt = new Date(); op.errorCode = null; op.errorMessage = null;
            op.resultJson = service.support.encode(result); PublicationService.changed(service.mapper.updatePublicationOperation(op)); op.rowVersion++;
            return null;
        });
    }
    /** Reconciliation is outside database transactions because review ledger recovery owns its transactions. */
    public OperationReceipt reconcileAndClose(Long id, Long operationId, Command command) {
        service.support.permission("query"); service.support.permission("sale");
        if (command.reason == null || command.reason.isBlank() || command.reason.length() > 1000) {
            throw fail(400, "INVALID_REQUEST", "对账原因须为1至1000字符");
        }
        return service.support.gated(() -> {
            Publication row = service.require(id); service.support.own(row);
            PublicationOperation op = service.mapper.getOperation(id, operationId);
            if (op == null || !"SALE".equals(op.action)) { throw fail(404, "NOT_FOUND", "发售操作不存在"); }
            boolean replayed = service.support.transaction(() -> service.replayed("publicationReconcile", id(operationId), command));
            if (replayed) { return service.receipt(row, op); }
            service.support.versions(row, command);
            if (!Objects.equals(command.expectedOperationRowVersion, op.rowVersion)) { throw fail(409, "VERSION_CONFLICT", "操作版本变化"); }
            if (List.of("FAILED", "SUCCEEDED").contains(op.status)) {
                service.support.transaction(() -> service.register("publicationReconcile", id(operationId), command, () -> op.id));
                return service.receipt(row, op);
            }
            Snapshot snapshot = service.support.decode(op.snapshotJson, Snapshot.class);
            OperationResult result = service.result(row); boolean unresolved = false;
            for (QuestionCheck q : snapshot.check().items) {
                try {
                    ReviewModels.PublicationOperationEvidence evidence = bridge.lookup(PublicationReviewValidator.childKey(op, q.questionId));
                    if (evidence.prepared() != null && evidence.terminal() == null) { unresolved = true; }
                    ReviewModels.Readiness ready = reviews.readiness(q.questionId);
                    if (ready.eligible() && ready.approvalReceipt() != null) {
                        result.questionResults.add(new QuestionResult(id(q.questionId), evidence.prepared() == null ? "REUSED" : "APPROVED",
                                id(q.observedRowVersion), ready.rowVersion(), ready.contentHash(), ready.approvalReceipt().operationId(), ready.approvalReceipt().policyVersion()));
                    }
                } catch (ServiceException e) { unresolved = true; }
            }
            boolean unknown = unresolved;
            service.support.transaction(() -> service.register("publicationReconcile", id(operationId), command, () -> {
                Publication locked = service.lock(id); service.support.versions(locked, command);
                PublicationOperation current = service.mapper.getOperation(id, operationId);
                if (!Objects.equals(current.rowVersion, command.expectedOperationRowVersion)) { throw fail(409, "VERSION_CONFLICT", "操作已变化"); }
                op.status = unknown ? "UNCERTAIN" : Boolean.TRUE.equals(command.closeIfSafe) ? "FAILED" : "PENDING";
                op.resultJson = service.support.encode(result); op.errorCode = unknown ? "DEPENDENCY_UNAVAILABLE" : null;
                op.errorMessage = unknown ? "批准事实尚未全部查明" : null;
                if ("FAILED".equals(op.status)) { op.finishedAt = new Date(); }
                PublicationService.changed(service.mapper.updatePublicationOperation(op)); op.rowVersion++; return op.id;
            }));
            return service.receipt(row, op);
        });
    }
    private void save(PublicationOperation op) {
        service.support.transaction(() -> { service.support.updated(op); PublicationService.changed(service.mapper.updatePublicationOperation(op)); op.rowVersion++; return null; });
    }
}
