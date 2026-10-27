package com.ruoyi.knowledge.review;

import com.ruoyi.knowledge.mapper.KbReviewMapper;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.knowledge.domain.KbQuestion;
import com.ruoyi.knowledge.mapper.KbQuestionMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Validates immutable ancestry separately from the current question's normal-use projection. */
final class KbReviewLineage {
    private static final int MAX_DEPTH = 64;
    private static final int MAX_NODES = 1000;
    private final KbReviewLedger ledger;
    private final KbReviewMapper mapper;
    private final KbQuestionMapper questions;

    KbReviewLineage(KbReviewLedger ledger, KbReviewMapper mapper, KbQuestionMapper questions) {
        this.ledger = ledger;
        this.mapper = mapper;
        this.questions = questions;
    }

    List<ReviewModels.ParentFreeze> freezeParents(ReviewModels.ParentFreeze primary, List<ReviewModels.Fact> facts) {
        if (primary == null) { return List.of(); }
        List<ReviewModels.ParentFreeze> result = new ArrayList<>();
        for (ReviewModels.Fact fact : parentFacts(facts)) {
            if (primary.questionId().equals(fact.identity())) { result.add(primary); continue; }
            KbQuestion parent = full(Long.valueOf(fact.identity()));
            List<ReviewModels.Fact> currentFacts = mapper.facts(parent.getId());
            ReviewModels.Prepared approval = ledger.operations().stream().filter(p -> confirmedApproval(p)
                    && p.after().questionId().equals(fact.identity())
                    && p.after().rowVersion().equals(parent.getRowVersion().toString())
                    && p.after().contentHash().equals(KbQuestionFingerprint.content(parent))
                    && p.after().facts().equals(currentFacts)).findFirst().orElse(null);
            if (!"READY".equals(parent.getStatus()) || approval == null || !evidenceValid(approval))
                { throw new ServiceException("辅助父题缺少当前有效批准，不能建立派生冻结", 409); }
            result.add(new ReviewModels.ParentFreeze(fact.identity(), parent.getRowVersion().toString(),
                    basis(parent, currentFacts), approval.operationId(), parent, currentFacts));
        }
        if (result.isEmpty() || result.size() > MAX_NODES || result.stream().noneMatch(p -> p.equals(primary)))
            { throw new ServiceException("派生冻结与实际父关系不一致", 409); }
        return List.copyOf(result);
    }

    ReviewModels.ImportBinding validBinding(KbQuestion child) {
        List<ReviewModels.Fact> currentParents = parentFacts(mapper.facts(child.getId()));
        for (ReviewModels.ImportBinding binding : ledger.bindings()) {
            if (!child.getId().toString().equals(binding.questionId()) || binding.parentFreeze() == null
                    || mapper.importIdentityMatches(binding) != 1
                    || !currentParents.equals(parentFacts(binding.facts()))
                    || mapper.frozenParentMatches(child.getId(), Long.valueOf(binding.parentFreeze().questionId())) != 1)
                { continue; }
            Traversal traversal = new Traversal(true);
            traversal.active.add(child.getId().toString());
            if (validParents(binding, traversal, 0)) { return binding; }
        }
        return null;
    }

    /** Deliberately performs no SQL: safe before PageHelper's first count/page statement. */
    boolean evidenceValid(ReviewModels.Prepared prepared) {
        return validEvidence(prepared, new Traversal(false), 0);
    }

    private boolean validEvidence(ReviewModels.Prepared prepared, Traversal traversal, int depth) {
        if (prepared == null || depth > MAX_DEPTH) { return false; }
        if (prepared.granularityExceptionId() != null && ledger.exception(prepared.granularityExceptionId()) == null)
            { return false; }
        for (ReviewModels.Evidence evidence : prepared.evidence() == null ? List.<ReviewModels.Evidence>of() : prepared.evidence()) {
            Object value;
            if ("EXCEPTION".equals(evidence.kind())) { value = ledger.exception(evidence.identity()); }
            else if ("BINDING".equals(evidence.kind())) {
                ReviewModels.ImportBinding binding = traversal.bindings.get(evidence.identity());
                if (binding == null || !prepared.after().questionId().equals(binding.questionId())
                        || !parentFacts(prepared.after().facts()).equals(parentFacts(binding.facts()))
                        || !validParents(binding, traversal, depth)) { return false; }
                value = binding;
            } else { return false; }
            if (value == null || !evidence.hash().equals(KbReviewLedger.hash(JSON.toJSONBytes(value)))) { return false; }
        }
        return true;
    }

    private boolean validParents(ReviewModels.ImportBinding binding, Traversal traversal, int depth) {
        List<ReviewModels.ParentFreeze> parents = binding.parentFreezes();
        // Old single-parent evidence is readable, but an old multi-parent file cannot acquire missing signatures.
        if (parents == null) { parents = binding.parentFreeze() == null ? List.of() : List.of(binding.parentFreeze()); }
        Set<String> expected = new HashSet<>();
        for (ReviewModels.Fact fact : parentFacts(binding.facts())) { expected.add(fact.identity()); }
        Set<String> actual = new HashSet<>();
        for (ReviewModels.ParentFreeze parent : parents) {
            if (parent == null || !actual.add(parent.questionId())) { return false; }
        }
        if (actual.isEmpty() || !actual.equals(expected) || binding.parentFreeze() == null
                || parents.stream().noneMatch(p -> JSON.toJSONString(p).equals(JSON.toJSONString(binding.parentFreeze())))) { return false; }
        for (ReviewModels.ParentFreeze parent : parents) {
            if (!validParent(parent, traversal, depth + 1)) { return false; }
        }
        return true;
    }

    private boolean validParent(ReviewModels.ParentFreeze frozen, Traversal traversal, int depth) {
        if (depth > MAX_DEPTH || traversal.active.contains(frozen.questionId())) { return false; }
        String memoKey = KbReviewLedger.hash(JSON.toJSONBytes(frozen));
        Boolean known = traversal.completed.get(memoKey);
        if (known != null) { return known; }
        if (++traversal.nodes > MAX_NODES) { return false; }
        ReviewModels.Prepared approval = ledger.prepared(frozen.approvalOperationId());
        if (!matchesApproval(frozen, approval) || invalidated(frozen, traversal.operations)) { return false; }
        if (traversal.live) {
            KbQuestion parent = full(Long.valueOf(frozen.questionId()));
            List<ReviewModels.Fact> currentFacts = mapper.facts(parent.getId());
            if (!frozen.facts().equals(currentFacts) || !frozen.contentHash().equals(basis(parent, currentFacts))) { return false; }
        }
        traversal.active.add(frozen.questionId());
        boolean valid;
        try { valid = validEvidence(approval, traversal, depth); }
        finally { traversal.active.remove(frozen.questionId()); }
        traversal.completed.put(memoKey, valid);
        return valid;
    }

    private boolean matchesApproval(ReviewModels.ParentFreeze frozen, ReviewModels.Prepared approval) {
        if (!confirmedApproval(approval) || frozen.question() == null || frozen.facts() == null
                || !frozen.questionId().equals(approval.after().questionId())
                || !frozen.rowVersion().equals(approval.after().rowVersion())
                || !frozen.facts().equals(approval.after().facts())
                || !frozen.questionId().equals(String.valueOf(frozen.question().getId()))
                // Optional empty fields may deserialize as null; the immutable approval owns the exact digest.
                || !frozen.contentHash().equals(KbReviewLedger.hash(JSON.toJSONBytes(
                        List.of(approval.after().contentHash(), approval.after().facts()))))) { return false; }
        return !"DERIVED".equals(frozen.question().getCreationKind()) || (approval.evidence() != null
                && approval.evidence().stream().anyMatch(e -> "BINDING".equals(e.kind())));
    }

    private boolean invalidated(ReviewModels.ParentFreeze frozen, List<ReviewModels.Prepared> operations) {
        for (ReviewModels.Prepared operation : operations) {
            if (!List.of("CONTENT_ERROR", "SOURCE_ERROR").contains(String.valueOf(operation.reasonCategory()))
                    || !confirmed(operation)) { continue; }
            List<ReviewModels.Snapshot> affected = new ArrayList<>();
            affected.add(operation.after());
            if (operation.affectedAfter() != null) { affected.addAll(operation.affectedAfter()); }
            for (ReviewModels.Snapshot snapshot : affected) {
                if (frozen.questionId().equals(snapshot.questionId())
                        && Long.parseLong(snapshot.rowVersion()) > Long.parseLong(frozen.rowVersion())) { return true; }
            }
        }
        return false;
    }

    private boolean confirmedApproval(ReviewModels.Prepared value) {
        return value != null && "APPROVE".equals(value.action()) && KbReviewService.POLICY.equals(value.policyVersion())
                && "READY".equals(value.after().status()) && confirmed(value);
    }

    private boolean confirmed(ReviewModels.Prepared value) {
        ReviewModels.Terminal terminal = ledger.terminal(value.operationId());
        return terminal != null && "CONFIRMED".equals(terminal.outcome());
    }

    private KbQuestion full(Long id) {
        KbQuestion question = questions.selectById(id);
        if (question == null) { throw new ServiceException("冻结父题不存在，须核对血缘", 409); }
        question.setKpIds(questions.selectKpIds(id));
        question.setAssetIds(questions.selectAssetIds(id));
        return question;
    }

    static List<ReviewModels.Fact> parentFacts(List<ReviewModels.Fact> facts) {
        return facts == null ? List.of() : facts.stream().filter(f -> "PARENT".equals(f.kind())).toList();
    }

    private static String basis(KbQuestion question, List<ReviewModels.Fact> facts) {
        return KbReviewLedger.hash(JSON.toJSONBytes(List.of(KbQuestionFingerprint.content(question), facts)));
    }

    private final class Traversal {
        private final boolean live;
        private final Set<String> active = new HashSet<>();
        private final Map<String, Boolean> completed = new HashMap<>();
        private final Map<String, ReviewModels.ImportBinding> bindings = new HashMap<>();
        private final List<ReviewModels.Prepared> operations = ledger.operations();
        private int nodes;
        private Traversal(boolean live) {
            this.live = live;
            for (ReviewModels.ImportBinding binding : ledger.bindings()) { bindings.put(binding.bindingId(), binding); }
        }
    }
}
