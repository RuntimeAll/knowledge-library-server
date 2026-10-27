package com.ruoyi.knowledge.review;

import com.ruoyi.knowledge.mapper.KbReviewMapper;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.knowledge.domain.KbQuestion;
import com.ruoyi.knowledge.html.KbHtmlLayout;
import com.ruoyi.knowledge.mapper.KbQuestionMapper;
import com.ruoyi.knowledge.service.impl.KnowledgeDictionary;
import com.ruoyi.knowledge.service.impl.QuestionContentRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Review use cases and recovery own their outermost transaction, independent of ordinary edits. */
@Service
public class KbReviewService {
    public static final String POLICY = "PRD010-QUALITY-1";
    private final KbReviewLedger ledger;
    private final KbReviewGate gate;
    private final KbQuestionMapper questions;
    private final KbReviewMapper mapper;
    private final KnowledgeDictionary dictionary;
    private final TransactionTemplate transaction;
    private final boolean enabled;
    private final KbReviewProofs proofs;
    private long projectionGeneration = -1;
    private String projectionJson;

    public KbReviewService(KbReviewLedger ledger, KbReviewGate gate, KbQuestionMapper questions,
            KbReviewMapper mapper, KnowledgeDictionary dictionary, KbReviewProofs proofs, PlatformTransactionManager manager,
            @Value("${knowledge.review.enabled:false}") boolean enabled) {
        this.ledger = ledger;
        this.gate = gate;
        this.questions = questions;
        this.mapper = mapper;
        this.dictionary = dictionary;
        this.proofs = proofs;
        transaction = new TransactionTemplate(manager);
        this.enabled = enabled;
    }

    public boolean enabled() { return enabled; }
    public String contentHash(KbQuestion question) { return proofs.basis(question); }

    public ReviewModels.Readiness readiness(Long id) {
        requireEnabled();
        gate.enter();
        try {
            assertResolved();
            KbQuestion question = current(id, false);
            List<ReviewModels.Check> checks = checks(question);
            ReviewModels.Prepared approved = approval(id);
            boolean eligible = approved != null && "READY".equals(question.getStatus())
                    && snapshot(question).equals(approved.after()) && proofs.evidenceValid(approved)
                    && mapper.usableIds(List.of(id), approvalJson()).contains(id);
            return new ReviewModels.Readiness(id.toString(), question.getRowVersion().toString(),
                    question.getStatus(), eligible, "DRAFT".equals(question.getStatus())
                    && checks.stream().allMatch(ReviewModels.Check::passed), POLICY, checks,
                    mapper.impact(id), approved == null ? null : receipt(approved, "CONFIRMED"), proofs.basis(question),
                    approved == null ? null : KbReviewLedger.hash(JSON.toJSONBytes(
                            List.of(approved.after().contentHash(), approved.after().facts()))));
        } finally { gate.leave(); }
    }

    public ReviewModels.Receipt act(Long id, String action, ReviewModels.Command command) {
        requireEnabled();
        validateCommand(command);
        if (!List.of("APPROVE", "RETURN_TO_DRAFT", "SOFT_DELETE", "RESTORE").contains(action))
            { throw new ServiceException("审核动作无效", 400); }
        String actor = SecurityUtils.getUsername();
        String requestHash = KbReviewLedger.hash(JSON.toJSONBytes(List.of(id, action, actor, command)));
        gate.enter();
        try {
            requireOutsideTransaction();
            if (ledger.exception(command.operationId()) != null) { throw new ServiceException("Operation identity already used",409); }
            ReviewModels.Prepared previous = ledger.prepared(command.operationId());
            if (previous != null) {
                if (!requestHash.equals(previous.requestHash())) { throw new ServiceException("operationId已用于其他请求", 409); }
                ReviewModels.Terminal terminal = ledger.terminal(command.operationId());
                if (terminal == null) { recover(); terminal = ledger.terminal(command.operationId()); }
                if (terminal == null) { throw pending(command.operationId()); }
                if ("ABORTED".equals(terminal.outcome())) { throw new ServiceException("该操作已回滚；查看当前版本后使用新operationId", 409); }
                return receipt(previous, terminal.outcome());
            }
            recover();
            ReviewModels.Prepared prepared;
            try {
                prepared = transaction.execute(status -> perform(id, action, command, actor, requestHash));
            } catch (RuntimeException failure) {
                if (ledger.prepared(command.operationId()) != null) {
                    // Commit outcome may be unknown: a NEW locking transaction, never the failed snapshot.
                    recover();
                    ReviewModels.Terminal terminal = ledger.terminal(command.operationId());
                    if (terminal != null && "CONFIRMED".equals(terminal.outcome()))
                        { return receipt(ledger.prepared(command.operationId()), terminal.outcome()); }
                }
                throw failure;
            }
            ledger.finish(command.operationId(), "CONFIRMED");
            return receipt(prepared, "CONFIRMED");
        } finally { gate.leave(); }
    }

    private ReviewModels.Prepared perform(Long id, String action, ReviewModels.Command command,
            String actor, String requestHash) {
        boolean lifecycle = List.of("RETURN_TO_DRAFT", "SOFT_DELETE").contains(action);
        if (lifecycle && !List.of("STOP_USE", "CONTENT_ERROR", "SOURCE_ERROR").contains(String.valueOf(command.reasonCategory())))
            { throw new ServiceException("退回或删除须明确STOP_USE、CONTENT_ERROR或SOURCE_ERROR", 400); }
        List<Long> affectedIds = lifecycle && !"STOP_USE".equals(command.reasonCategory())
                ? mapper.impacted("QUESTION", id, 1001) : List.of(id);
        if (affectedIds.size() > 1000) { throw new ServiceException("影响超过1000题，须提交精确治理计划", 409); }
        List<KbQuestion> locked = affectedIds.stream().sorted().map(value -> current(value, true)).toList();
        KbQuestion question = locked.stream().filter(q -> id.equals(q.getId())).findFirst().orElseThrow(() -> new ServiceException("题目不存在", 404));
        if (!Objects.equals(question.getRowVersion(), command.expectedRowVersion()))
            { throw new ServiceException("题目版本已变化，请重新查看后审核", 409); }
        String next = switch (action) {
            case "APPROVE" -> {
                if (!"DRAFT".equals(question.getStatus())) { throw new ServiceException("只有草稿可批准", 409); }
                if (!Boolean.TRUE.equals(command.contentReviewed())) { throw new ServiceException("须确认已审查当前完整题面和解答", 400); }
                if (command.expectedContentHash() == null || !command.expectedContentHash().matches("[a-f0-9]{64}"))
                    { throw new ServiceException("批准须携带已审查版本的完整contentHash，请重新读取题目", 400); }
                if (!Objects.equals(command.expectedContentHash(), proofs.basis(question)))
                    { throw new ServiceException("题目内容或依赖已变化，请重新读取并审查", 409); }
                List<String> failures = checks(question).stream().filter(check -> !check.passed())
                        .map(ReviewModels.Check::message).toList();
                if (!failures.isEmpty()) { throw new ServiceException(String.join("；", failures), 409); }
                yield "READY";
            }
            case "SOFT_DELETE" -> {
                if (!List.of("DRAFT", "READY").contains(question.getStatus())) { throw new ServiceException("当前状态不能删除", 409); }
                yield "DELETED";
            }
            case "RESTORE" -> {
                if (!List.of("DELETED", "RETIRED").contains(question.getStatus())) { throw new ServiceException("只有删除或停用题可恢复", 409); }
                yield "DRAFT";
            }
            default -> {
                if (!"READY".equals(question.getStatus())) { throw new ServiceException("只有正常题可退回草稿", 409); }
                yield "DRAFT";
            }
        };
        ReviewModels.Snapshot before = snapshot(question);
        ReviewModels.Snapshot after = new ReviewModels.Snapshot(id.toString(),
                Long.toString(question.getRowVersion() + 1), next, before.contentHash(), before.facts());
        // Error evidence includes stopped and draft intermediates, even when their state does not change.
        List<ReviewModels.Snapshot> affectedBefore = locked.stream().filter(q -> !id.equals(q.getId())).map(this::snapshot).toList();
        List<ReviewModels.Snapshot> affectedAfter = affectedBefore.stream().map(q -> "READY".equals(q.status())
                ? new ReviewModels.Snapshot(q.questionId(), Long.toString(Long.parseLong(q.rowVersion()) + 1),
                        "DRAFT", q.contentHash(), q.facts()) : q).toList();
        ReviewModels.GranularityException exception = "APPROVE".equals(action) ? proofs.matching(question) : null;
        ReviewModels.Prepared prepared = new ReviewModels.Prepared(command.operationId(), requestHash,
                action, actor, command.reason(), POLICY, before, after,
                exception == null ? null : exception.exceptionId(), command.reasonCategory(), affectedBefore, affectedAfter,
                "APPROVE".equals(action) ? proofs.evidence(question, exception) : List.of());
        ledger.prepare(prepared);
        if (mapper.changeState(id, question.getRowVersion(), next, actor) != 1)
            { throw new ServiceException("题目版本冲突", 409); }
        for (ReviewModels.Snapshot child : affectedBefore)
            { if ("READY".equals(child.status()) && mapper.changeState(Long.valueOf(child.questionId()), Long.valueOf(child.rowVersion()), "DRAFT", actor) != 1)
                { throw new ServiceException("派生题版本冲突，整个操作已回滚", 409); } }
        return prepared;
    }

    public ReviewModels.BatchResult approveBatch(ReviewModels.BatchCommand command) {
        if (command == null || command.items() == null || command.items().isEmpty() || command.items().size() > 50
                || command.items().stream().anyMatch(item -> item == null || item.questionId() == null)
                || command.items().stream().map(ReviewModels.BatchItem::questionId).distinct().count() != command.items().size())
            { throw new ServiceException("批审需1至50个不重复题目", 400); }
        List<ReviewModels.ItemResult> results = new ArrayList<>();
        for (ReviewModels.BatchItem item : command.items()) {
            try {
                ReviewModels.Receipt receipt = act(item.questionId(), "APPROVE", new ReviewModels.Command(
                        item.operationId(), item.expectedRowVersion(), item.reason(), item.contentReviewed(), null, item.expectedContentHash()));
                results.add(new ReviewModels.ItemResult(item.questionId().toString(), item.operationId(),
                        "SUCCESS", receipt, null, null, false, null));
            } catch (ServiceException failure) {
                int code = failure.getCode() == null ? 500 : failure.getCode();
                results.add(new ReviewModels.ItemResult(item.questionId().toString(), item.operationId(),
                        code == 503 ? "PENDING" : code == 409 ? "CONFLICT" : "FAILED", null,
                        Integer.toString(code), failure.getMessage(), code == 503,
                        code == 503 ? "RETRY_SAME_OPERATION" : "REVIEW_CURRENT_VERSION"));
            }
        }
        return new ReviewModels.BatchResult(List.copyOf(results));
    }

    /** Caller holds the global gate before opening any transaction. */
    public void recover() {
        if (!enabled && !ledger.configured()) { return; }
        if (!gate.held()) { throw new IllegalStateException("Review recovery requires outer gate"); }
        requireOutsideTransaction();
        ledger.generation();
        for (ReviewModels.Prepared prepared : ledger.operations()) {
            if (ledger.terminal(prepared.operationId()) != null) { continue; }
            String outcome = transaction.execute(status -> {
                List<ReviewModels.Snapshot> before = new ArrayList<>();
                before.add(prepared.before());
                if (prepared.affectedBefore() != null) { before.addAll(prepared.affectedBefore()); }
                List<ReviewModels.Snapshot> after = new ArrayList<>();
                after.add(prepared.after());
                if (prepared.affectedAfter() != null) { after.addAll(prepared.affectedAfter()); }
                Comparator<ReviewModels.Snapshot> order = Comparator.comparingLong(q -> Long.parseLong(q.questionId()));
                before.sort(order); after.sort(order);
                List<ReviewModels.Snapshot> actual = before.stream().map(q -> snapshot(current(Long.valueOf(q.questionId()), true))).toList();
                if (actual.equals(after)) { return "CONFIRMED"; }
                if (actual.equals(before)) { return "ABORTED"; }
                throw pending(prepared.operationId());
            });
            ledger.finish(prepared.operationId(), outcome);
        }
    }

    /** Pure ledger projection; must run before the paginated mapper's first SQL. */
    public String approvalJson() {
        requireEnabled();
        long generation = ledger.generation();
        if (generation == projectionGeneration && projectionJson != null) { return projectionJson; }
        assertResolved();
        var approvals = new HashMap<String, ReviewModels.Approval>();
        for (ReviewModels.Prepared prepared : ledger.operations()) {
            if (!proofs.evidenceValid(prepared)) { continue; }
            if (!"APPROVE".equals(prepared.action()) || !POLICY.equals(prepared.policyVersion())
                    || !"CONFIRMED".equals(ledger.terminal(prepared.operationId()).outcome())) { continue; }
            ReviewModels.Snapshot after = prepared.after();
            ReviewModels.Approval old = approvals.get(after.questionId());
            if (old == null || Long.parseLong(old.rowVersion()) < Long.parseLong(after.rowVersion()))
                { approvals.put(after.questionId(), new ReviewModels.Approval(after.questionId(),
                        after.rowVersion(), after.contentHash(), after.facts())); }
        }
        String json = JSON.toJSONString(approvals.values().stream().sorted(Comparator.comparing(ReviewModels.Approval::questionId)).toList());
        if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 8 * 1024 * 1024)
            { throw new ServiceException("审核投影超过已验证上限，需调整查询方案；不会返回截断题库", 503); }
        projectionJson = json;
        projectionGeneration = ledger.generation();
        return json;
    }

    public List<Long> unavailableIds(List<Long> ids) {
        if (ids == null || ids.size() > 500 || ids.stream().anyMatch(Objects::isNull)) { throw new ServiceException("题单数量最多500且ID不可为空", 400); }
        if (ids.isEmpty()) { return List.of(); }
        requireEnabled();
        gate.enter();
        try {
            java.util.Set<Long> usable = new java.util.HashSet<>(mapper.usableIds(ids.stream().distinct().toList(), approvalJson()));
            return ids.stream().distinct().filter(id -> !usable.contains(id)).toList();
        } finally { gate.leave(); }
    }

    public void assertUsable(List<Long> ids) {
        List<Long> failed = unavailableIds(ids);
        if (!failed.isEmpty()) { throw new ServiceException("以下题目尚不可正式使用：" + failed, 409); }
    }

    public java.util.Map<Long,ReviewModels.ApprovalEvidence> freezeApprovals(List<Long> ids) {
        if (!gate.held()) { throw new IllegalStateException("Approval freeze requires the outer quality gate"); }
        var result=new java.util.HashMap<Long,ReviewModels.ApprovalEvidence>();
        for (ReviewModels.Prepared p : ledger.operations()) {
            Long id=Long.valueOf(p.after().questionId());
            if (!ids.contains(id) || !"APPROVE".equals(p.action()) || !POLICY.equals(p.policyVersion())
                    || ledger.terminal(p.operationId())==null || !"CONFIRMED".equals(ledger.terminal(p.operationId()).outcome())) { continue; }
            var previous=result.get(id);
            if (previous==null || Long.parseLong(previous.rowVersion())<Long.parseLong(p.after().rowVersion()))
                { result.put(id,new ReviewModels.ApprovalEvidence(p.operationId(),p.after().questionId(),p.after().rowVersion(),p.after().contentHash(),
                        p.policyVersion(),p.actor(),p.reason(),p.granularityExceptionId(),p.after().facts(),p.evidence())); }
        }
        return java.util.Map.copyOf(result);
    }

    public ReviewModels.ParentFreeze freezeParent(Long id, Long expectedVersion, String expectedStemHash, String expectedContentHash) {
        KbQuestion parent = current(id, true);
        if (expectedVersion == null || expectedContentHash == null
                || !Objects.equals(expectedVersion, parent.getRowVersion())
                || (expectedStemHash != null && !Objects.equals(expectedStemHash, parent.getStemHash()))
                || !Objects.equals(expectedContentHash, proofs.basis(parent)))
            { throw new ServiceException("母题版本或完整内容摘要已变化，请重新冻结母题", 409); }
        assertUsable(List.of(id));
        ReviewModels.Prepared approval = approval(id);
        return new ReviewModels.ParentFreeze(id.toString(), parent.getRowVersion().toString(),
                proofs.basis(parent), approval.operationId(), parent, mapper.facts(id));
    }

    public void validateNewUnit(KbQuestion question) { proofs.validateNewUnit(question); }
    public void bindImport(KbQuestion saved, KbQuestion input, String actor, String fingerprint) {
        proofs.bindImport(saved, input, actor, fingerprint);
    }

    private List<ReviewModels.Check> checks(KbQuestion question) {
        List<ReviewModels.Check> checks = new ArrayList<>();
        checks.add(new ReviewModels.Check("CONTENT", "题干须有完整可见内容", QuestionContentRules.hasContent(question.getStemHtml())));
        checks.add(new ReviewModels.Check("CLASSIFICATION", "须填写学科、内容类型、题型和考点", notBlank(question.getSubject())
                && notBlank(question.getContentKind()) && notBlank(question.getQuestionType()) && !question.getKpIds().isEmpty()));
        checks.add(new ReviewModels.Check("CHAPTER", "每个考点须挂同学科已确认教材的具体章节", !question.getKpIds().isEmpty() && mapper.invalidBindings(question.getId()) == 0));
        boolean origin = "DERIVED".equals(question.getCreationKind()) ? proofs.derivedEvidence(question)
                : mapper.validOrigin(question.getId(), question.getCreationKind()) > 0;
        checks.add(new ReviewModels.Check("ORIGIN", "须有当前真实出处或已验证的冻结母题证据", origin));
        checks.add(new ReviewModels.Check("ANSWER", "练习须有完整答案", !"EXERCISE".equals(question.getContentKind()) || completeAnswer(question)));
        checks.add(new ReviewModels.Check("ASSETS", "必要资产须完整且READY", mapper.invalidAssets(question.getId()) == 0));
        int count = KbHtmlLayout.validateParts(question.getStemHtml(), question.getAnswerHtml(), question.getAnalysisHtml()).itemCount();
        checks.add(new ReviewModels.Check("GRANULARITY", "计算练习需至少3道完整小题或有效专项例外", !"CALCULATION".equals(question.getQuestionType()) || count >= 3 || hasException(question)));
        dictionary.validate("kb_subject", question.getSubject(), "学科");
        dictionary.validate("kb_question_type", question.getQuestionType(), "题型");
        return List.copyOf(checks);
    }

    private static boolean completeAnswer(KbQuestion question) {
        return ReviewContentChecks.completeAnswer(question.getStemHtml(), question.getAnswerHtml(),
                "GROUP".equals(question.getUnitKind()));
    }

    private boolean hasException(KbQuestion question) {
        if ("DRAFT".equals(question.getStatus())) { return proofs.matching(question) != null; }
        ReviewModels.Prepared approved = approval(question.getId());
        return approved != null && snapshot(question).equals(approved.after())
                && approved.granularityExceptionId() != null && proofs.evidenceValid(approved);
    }

    private KbQuestion current(Long id, boolean lock) {
        KbQuestion question = lock ? questions.lockById(id) : questions.selectById(id);
        if (question == null) { throw new ServiceException("题目不存在", 404); }
        question.setKpIds(questions.selectKpIds(id));
        question.setAssetIds(questions.selectAssetIds(id));
        return question;
    }

    private ReviewModels.Snapshot snapshot(KbQuestion question) {
        return new ReviewModels.Snapshot(question.getId().toString(), question.getRowVersion().toString(),
                question.getStatus(), KbQuestionFingerprint.content(question), mapper.facts(question.getId()));
    }

    private ReviewModels.Prepared approval(Long id) {
        return ledger.operations().stream().filter(p -> "APPROVE".equals(p.action()) && POLICY.equals(p.policyVersion())
                && id.toString().equals(p.after().questionId()) && ledger.terminal(p.operationId()) != null
                && "CONFIRMED".equals(ledger.terminal(p.operationId()).outcome()))
                .max(Comparator.comparingLong(p -> Long.parseLong(p.after().rowVersion()))).orElse(null);
    }

    private ReviewModels.Receipt receipt(ReviewModels.Prepared value, String outcome) {
        KbQuestion current = questions.selectById(Long.valueOf(value.after().questionId()));
        return new ReviewModels.Receipt(value.operationId(), value.action(), value.after().questionId(),
                value.before().rowVersion(), value.after().rowVersion(), value.after().status(),
                value.after().contentHash(), value.policyVersion(), value.actor(), value.reason(), outcome,
                current == null ? null : current.getRowVersion().toString(), current == null ? null : current.getStatus(),
                value.reasonCategory(), value.affectedAfter() == null ? List.of() : value.affectedAfter().stream().map(ReviewModels.Snapshot::questionId).toList());
    }

    private void assertResolved() {
        if (ledger.operations().stream().anyMatch(p -> ledger.terminal(p.operationId()) == null))
            { throw new ServiceException("存在未决审核操作，请同键重试完成对账", 503); }
    }
    private void requireEnabled() { if (!enabled) { throw new ServiceException("质量审批尚未启用，需完成隔离验收后配置", 503); } }
    private static boolean notBlank(String value) { return value != null && !value.isBlank(); }
    private static void requireOutsideTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) { throw new IllegalStateException("Review gate must precede the outermost transaction"); }
    }
    private static ServiceException pending(String operationId) { return new ServiceException("审核操作待对账，请保留operationId重试：" + operationId, 503); }
    private static void validateCommand(ReviewModels.Command command) {
        if (command == null || !notBlank(command.operationId()) || command.operationId().length() > 128
                || command.expectedRowVersion() == null || command.expectedRowVersion() < 0
                || !notBlank(command.reason()) || command.reason().length() > 2000)
            { throw new ServiceException("审核需稳定operationId、当前版本及1至2000字符原因", 400); }
    }
}
