package com.ruoyi.knowledge.review;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Typed review commands, durable evidence and user-visible receipts. */
public final class ReviewModels {
    private ReviewModels() { }

    public record Command(@NotBlank @Size(max = 128) String operationId,
            @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING) Long expectedRowVersion,
            @NotBlank @Size(max = 2000) String reason, Boolean contentReviewed, String reasonCategory,
            @Size(max = 64) String expectedContentHash) {
        public Command(String operationId, Long expectedRowVersion, String reason, Boolean contentReviewed) {
            this(operationId, expectedRowVersion, reason, contentReviewed, null, null);
        }
        public Command(String operationId, Long expectedRowVersion, String reason, Boolean contentReviewed, String reasonCategory) {
            this(operationId, expectedRowVersion, reason, contentReviewed, reasonCategory, null);
        }
    }
    public record BatchItem(@NotNull @JsonFormat(shape = JsonFormat.Shape.STRING) Long questionId,
            String operationId, @JsonFormat(shape = JsonFormat.Shape.STRING) Long expectedRowVersion,
            String reason, Boolean contentReviewed, @Size(max = 64) String expectedContentHash) { }
    public record BatchCommand(@NotNull @Size(min = 1, max = 50) List<BatchItem> items) { }
    public record BatchResult(List<ItemResult> items) { }
    public record ItemResult(String questionId, String operationId, String outcome, Receipt receipt,
            String errorCode, String message, boolean retryable, String nextAction) { }
    public record Check(String code, String message, boolean passed) { }
    public record Impact(long materialCount, long productCount, long childCount) { }
    public record Readiness(String questionId, String rowVersion, String status, boolean eligible,
            boolean canApprove, String policyVersion, List<Check> checks, Impact impact,
            Receipt approvalReceipt, String contentHash, String approvalBasisHash) { }
    public record QuestionAsset(Long questionId, Long assetId) { }
    public record Fact(String kind, String identity, String stamp) { }
    public record Snapshot(String questionId, String rowVersion, String status, String contentHash,
            List<Fact> facts) { }
    public record Prepared(String operationId, String requestHash, String action, String actor,
            String reason, String policyVersion, Snapshot before, Snapshot after, String granularityExceptionId, String reasonCategory, List<Snapshot> affectedBefore, List<Snapshot> affectedAfter, List<Evidence> evidence) {
        public Prepared(String operationId, String requestHash, String action, String actor, String reason,
                String policyVersion, Snapshot before, Snapshot after, String granularityExceptionId) {
            this(operationId, requestHash, action, actor, reason, policyVersion, before, after, granularityExceptionId, null, List.of(), List.of(), List.of());
        }
    }
    public record Evidence(String kind, String identity, String hash) { }
    public record Terminal(String operationId, String prepareHash, String outcome) { }
    public record Receipt(String operationId, String action, String questionId, String beforeRowVersion,
            String rowVersion, String status, String contentHash, String policyVersion, String actor,
            String reason, String outcome, String currentRowVersion, String currentStatus, String reasonCategory, List<String> affectedQuestionIds) { }
    public record ApprovalEvidence(String operationId, String questionId, String rowVersion, String contentHash,
            String policyVersion, String actor, String reason, String granularityExceptionId, List<Fact> facts, List<Evidence> evidence) { }
    public record Approval(String questionId, String rowVersion, String contentHash, List<Fact> facts) { }
    public record ExceptionCommand(String operationId, String reason,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Long questionId,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Long expectedRowVersion,
            String runId, String candidateKey, String candidateHash) { }
    public record GranularityException(String operationId, String exceptionId, String questionId,
            String rowVersion, String clientKey, String candidateHash, String contentHash,
            int itemCount, String policyVersion, String actor, String reason, String sourceId,
            String articleHash, String basisHash, String requestHash, KbCandidateProxy.SourceLocator sourceLocator) { }
    public record ParentFreeze(String questionId, String rowVersion, String contentHash,
            String approvalOperationId, com.ruoyi.knowledge.domain.KbQuestion question, List<Fact> facts) { }
    public record ImportBinding(String bindingId, String clientKey, String createBy, String requestFingerprint,
            String questionId, String rowVersion, String contentHash, List<Fact> facts,
            String granularityExceptionId, ParentFreeze parentFreeze, List<ParentFreeze> parentFreezes) {
        public ImportBinding(String bindingId, String clientKey, String createBy, String requestFingerprint,
                String questionId, String rowVersion, String contentHash, List<Fact> facts,
                String granularityExceptionId, ParentFreeze parentFreeze) {
            this(bindingId, clientKey, createBy, requestFingerprint, questionId, rowVersion, contentHash,
                    facts, granularityExceptionId, parentFreeze, null);
        }
    }
}
