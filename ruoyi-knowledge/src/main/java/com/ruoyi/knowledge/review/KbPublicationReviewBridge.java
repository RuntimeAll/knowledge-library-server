package com.ruoyi.knowledge.review;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Typed publication bridge; a verified scope never substitutes for approval permission. */
@Service
public class KbPublicationReviewBridge {
    private final KbReviewService reviews;
    private final KbReviewLedger ledger;
    private final KbReviewProofs proofs;
    private final KbReviewGate gate;
    private final ObjectProvider<PublicationReviewContextValidator> validators;

    public KbPublicationReviewBridge(KbReviewService reviews, KbReviewLedger ledger, KbReviewGate gate, KbReviewProofs proofs,
            ObjectProvider<PublicationReviewContextValidator> validators) {
        this.reviews = reviews;
        this.ledger = ledger;
        this.proofs = proofs;
        this.gate = gate;
        this.validators = validators;
    }

    public ReviewModels.Readiness inspect(ReviewModels.PublicationContext context, Long questionId) {
        if (!SecurityUtils.hasPermi("knowledge:publication:check") && !SecurityUtils.hasPermi("knowledge:publication:sale"))
            { throw new ServiceException("缺少发布自检权限", 403); }
        validateContext(context, questionId);
        outsideTransaction();
        gate.enter();
        try {
            validator().validateCheck(context, questionId);
            reviews.recover();
            return reviews.publicationReadiness(questionId);
        } finally { gate.leave(); }
    }

    public ReviewModels.PublicationApprovalResult approveOrReuse(ReviewModels.PublicationContext context,
            String saleOperationKey, Long questionId, ReviewModels.Command command) {
        requireSalePermission();
        validateContext(context, questionId);
        validateApprovalInput(saleOperationKey, command);
        outsideTransaction();
        gate.enter();
        try {
            validator().validateApproval(context, saleOperationKey, questionId, command);
            reviews.recover();
            ReviewModels.Prepared prior = ledger.prepared(command.operationId());
            ReviewModels.Readiness current = reviews.publicationReadiness(questionId);
            if (prior == null && current.eligible()) {
                if (!Objects.equals(current.rowVersion(), command.expectedRowVersion().toString())
                        || !Objects.equals(current.contentHash(), command.expectedContentHash()))
                    { throw new ServiceException("正式题版本或批准依据已变化，请重新自检", 409); }
                return result("REUSED", current, questionId);
            }
            if (prior == null && (!"DRAFT".equals(current.status()) || !current.canApprove()))
                { throw new ServiceException("题目不满足当前发布批准条件", 409); }
            if (!SecurityUtils.hasPermi("knowledge:question:approve"))
                { throw new ServiceException("发售包含待批准题，须具有题目批准权限", 403); }
            reviews.approvePublication(questionId, command, context, saleOperationKey);
            ReviewModels.Readiness approved = reviews.publicationReadiness(questionId);
            if (!approved.eligible()) { throw new ServiceException("批准后的题目资格尚未核实", 503); }
            return result("APPROVED", approved, questionId);
        } finally { gate.leave(); }
    }

    /** Read-only reconciliation of an existing child identity; never starts a new approval. */
    public ReviewModels.PublicationOperationEvidence lookup(String childOperationId) {
        requireSalePermission();
        if (childOperationId == null || childOperationId.isBlank() || childOperationId.length() > 128)
            { throw new ServiceException("题目批准操作身份无效", 400); }
        outsideTransaction();
        gate.enter();
        try {
            reviews.recover();
            ReviewModels.Prepared prepared = ledger.prepared(childOperationId);
            return new ReviewModels.PublicationOperationEvidence(prepared,
                    prepared == null ? null : ledger.terminal(childOperationId));
        } finally { gate.leave(); }
    }

    /** Caller first enforces publication scope; only immutable verified ancestry identifiers are returned. */
    public ReviewModels.PublicationLineageEvidence lineageEvidence(Long questionId) {
        if (questionId == null || questionId <= 0) { throw new ServiceException("题目ID无效", 400); }
        outsideTransaction();
        gate.enter();
        try {
            reviews.recover();
            return proofs.publicationLineage(questionId);
        } finally { gate.leave(); }
    }

    private ReviewModels.PublicationApprovalResult result(String mode, ReviewModels.Readiness current, Long id) {
        ReviewModels.ApprovalEvidence evidence = reviews.freezeApprovals(List.of(id)).get(id);
        if (evidence == null || current.approvalReceipt() == null
                || !evidence.operationId().equals(current.approvalReceipt().operationId()))
            { throw new ServiceException("题目批准证据尚未核实", 503); }
        return new ReviewModels.PublicationApprovalResult(mode, current, evidence);
    }

    private PublicationReviewContextValidator validator() {
        PublicationReviewContextValidator value = validators.getIfAvailable();
        if (value == null) { throw new ServiceException("发布上下文校验尚未配置", 503); }
        return value;
    }

    static void validateContext(ReviewModels.PublicationContext context, Long questionId) {
        if (context == null || context.publicationId() == null || context.publicationId() <= 0
                || context.expectedRowVersion() == null || context.expectedRowVersion() < 0
                || questionId == null || questionId <= 0)
            { throw new ServiceException("发布上下文须包含有效资料ID、当前版本及已入库题目", 400); }
    }

    private static void validateApprovalInput(String saleOperationKey, ReviewModels.Command command) {
        if (saleOperationKey == null || saleOperationKey.isBlank() || saleOperationKey.length() > 128
                || command == null || command.operationId() == null || command.operationId().isBlank()
                || command.operationId().length() > 128 || command.expectedRowVersion() == null
                || command.expectedRowVersion() < 0 || command.reason() == null || command.reason().isBlank()
                || command.reason().length() > 2000 || command.expectedContentHash() == null
                || !command.expectedContentHash().matches("[a-f0-9]{64}"))
            { throw new ServiceException("发布批准须携带稳定操作身份、当前版本、原因和完整摘要", 400); }
    }

    private static void requireSalePermission() {
        if (!SecurityUtils.hasPermi("knowledge:publication:sale"))
            { throw new ServiceException("缺少发售权限", 403); }
    }

    private static void outsideTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive())
            { throw new IllegalStateException("Publication review gate must precede the outermost transaction"); }
    }
}
