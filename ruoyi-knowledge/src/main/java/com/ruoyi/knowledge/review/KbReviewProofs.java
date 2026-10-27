package com.ruoyi.knowledge.review;

import com.ruoyi.knowledge.mapper.KbReviewMapper;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.knowledge.domain.KbQuestion;
import com.ruoyi.knowledge.html.HtmlQuestionDocument;
import com.ruoyi.knowledge.html.KbHtmlCodec;
import com.ruoyi.knowledge.html.KbHtmlLayout;
import com.ruoyi.knowledge.importing.KbRecordHtmlConverter;
import com.ruoyi.knowledge.importing.dto.KbRecordsRequest;
import com.ruoyi.knowledge.mapper.KbQuestionMapper;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Candidate quantity proofs and committed import bindings; they never grant READY. */
@Service
public class KbReviewProofs {
    private final KbReviewLedger ledger;
    private final KbReviewGate gate;
    private final KbReviewMapper mapper;
    private final KbQuestionMapper questions;
    private final KbCandidateProxy candidates;
    private final KbRecordHtmlConverter converter;
    private final KbHtmlCodec codec;
    private final KbReviewLineage lineage;

    public KbReviewProofs(KbReviewLedger ledger, KbReviewGate gate, KbReviewMapper mapper,
            KbQuestionMapper questions, KbCandidateProxy candidates, KbRecordHtmlConverter converter, KbHtmlCodec codec) {
        this.ledger = ledger;
        this.gate = gate;
        this.mapper = mapper;
        this.questions = questions;
        this.candidates = candidates;
        this.converter = converter;
        this.codec = codec;
        this.lineage = new KbReviewLineage(ledger, mapper, questions);
    }

    public ReviewModels.GranularityException grant(ReviewModels.ExceptionCommand command) {
        if (command == null || command.operationId() == null || command.operationId().isBlank()
                || command.operationId().length() > 128 || command.reason() == null || command.reason().isBlank()
                || command.reason().length() > 2000) { throw new ServiceException("专项例外须填写稳定操作身份和真实理由", 400); }
        if ((command.questionId() == null) == (command.runId() == null)) { throw new ServiceException("已有题目和未入库候选身份必须二选一", 400); }
        gate.enter();
        try {
            if (ledger.prepared(command.operationId()) != null) { throw new ServiceException("Operation identity already used",409); }
            ReviewModels.GranularityException previous = ledger.exception(command.operationId());
            if (previous != null) {
                boolean same = Objects.equals(previous.requestHash(), requestHash(command));
                if (!same) { throw new ServiceException("operationId已用于其他数量例外请求", 409); }
                return previous;
            }
        } finally { gate.leave(); }
        KbCandidateProxy.Candidate candidate = command.questionId() == null ? candidates.get(command.runId(), command.candidateKey()) : null;
        gate.enter();
        try {
            if (ledger.operations().stream().anyMatch(p -> ledger.terminal(p.operationId()) == null))
                { throw new ServiceException("有未决审核操作，须先同键对账", 503); }
            ReviewModels.GranularityException proof;
            if (candidate != null) { proof = candidateProof(command, candidate); }
            else {
                KbQuestion question = fullQuestion(command.questionId());
                if (!"DRAFT".equals(question.getStatus()) || !Objects.equals(command.expectedRowVersion(), question.getRowVersion()))
                    { throw new ServiceException("数量例外须针对当前草稿版本", 409); }
                int count = count(question);
                requireSmallCalculation(question.getQuestionType(), count);
                if (!ReviewContentChecks.completeAnswer(question.getStemHtml(), question.getAnswerHtml(),
                        "GROUP".equals(question.getUnitKind())))
                    { throw new ServiceException("数量例外必须展示完整解答", 409); }
                String content = KbQuestionFingerprint.content(question);
                proof = new ReviewModels.GranularityException(command.operationId(), command.operationId(),
                        question.getId().toString(), question.getRowVersion().toString(), null, null, content, count,
                        KbReviewService.POLICY, SecurityUtils.getUsername(), command.reason(), null, null, basis(question), requestHash(command), null);
            }
            ledger.saveException(proof);
            return proof;
        } finally { gate.leave(); }
    }

    private ReviewModels.GranularityException candidateProof(ReviewModels.ExceptionCommand command, KbCandidateProxy.Candidate candidate) {
        if (!Boolean.TRUE.equals(candidate.current()) || !"CODEX".equals(candidate.workerKind())
                || !Objects.equals(command.candidateHash(), candidate.candidateHash()))
            { throw new ServiceException("候选过期、哈希不符或非真实制作，不能批准例外", 409); }
        if (candidate.evidenceRefs() == null || candidate.evidenceRefs().isEmpty()
                || candidate.sourceLocator() == null || (blank(candidate.sourceLocator().physicalPage())
                && blank(candidate.sourceLocator().printedPage()) && blank(candidate.sourceLocator().questionNo())) || candidate.evidenceRefs().stream().anyMatch(e ->
                e == null || e.sha256() == null || !e.sha256().matches("[0-9a-f]{64}") || e.artifactId() == null))
            { throw new ServiceException("数量例外需完整原件证据及母题位置", 409); }
        KbRecordsRequest.Record record = candidate.record();
        if (record.granularityExceptionId() != null) { throw new ServiceException("候选不能自带数量批准", 400); }
        byte[] html = converter.convert(new KbRecordsRequest(KbRecordsRequest.SCHEMA, "candidate-preview",
                candidate.sourceId() == null ? null : Long.valueOf(candidate.sourceId()), candidate.instanceId(), List.of(record), List.of()));
        HtmlQuestionDocument parsed = codec.readArticle(codec.readArticles(html).get(0));
        int count = KbHtmlLayout.validateParts(parsed.stem(), parsed.answer(), parsed.analysis()).itemCount();
        requireSmallCalculation(parsed.metadata().questionType(), count);
        if (parsed.answer() == null || !ReviewContentChecks.completeAnswer(parsed.stem().html(), parsed.answer().html(),
                "GROUP".equals(parsed.metadata().unitKind())))
            { throw new ServiceException("数量例外候选必须展示完整解答", 409); }
        return new ReviewModels.GranularityException(command.operationId(), command.operationId(), null,
                null, candidate.clientKey(), candidate.candidateHash(), parsed.candidateArticleHash(), count,
                KbReviewService.POLICY, SecurityUtils.getUsername(), command.reason(), candidate.sourceId(),
                parsed.candidateArticleHash(), candidate.candidateHash(), requestHash(command), candidate.sourceLocator());
    }

    public void validateNewUnit(KbQuestion question) {
        int count = count(question);
        if (!"CALCULATION".equals(question.getQuestionType()) || count >= 3) { return; }
        if (question.getGranularityExceptionId() == null) { throw new ServiceException("计算练习不足3个完整小题，须先申请专项数量例外", 409); }
        ReviewModels.GranularityException proof = ledger.exception(question.getGranularityExceptionId());
        if (proof == null || proof.questionId() != null || !KbReviewService.POLICY.equals(proof.policyVersion())
                || !Objects.equals(proof.clientKey(), question.getImportClientKey())
                || !Objects.equals(proof.articleHash(), question.getCandidateArticleHash())
                || !Objects.equals(proof.sourceId(), question.getImportSourceId() == null ? null : question.getImportSourceId().toString())
                || count != proof.itemCount()) { throw new ServiceException("数量例外未绑定此完整候选、来源或clientKey", 409); }
    }

    public ReviewModels.GranularityException matching(KbQuestion question) {
        String content = KbQuestionFingerprint.content(question);
        String basis = basis(question);
        for (ReviewModels.GranularityException proof : ledger.exceptions()) {
            if (!KbReviewService.POLICY.equals(proof.policyVersion())) { continue; }
            if (Objects.equals(proof.questionId(), question.getId().toString())
                    && Objects.equals(proof.rowVersion(), question.getRowVersion().toString())
                    && Objects.equals(proof.contentHash(), content) && Objects.equals(proof.basisHash(), basis)) { return proof; }
        }
        for (ReviewModels.ImportBinding binding : ledger.bindings()) {
            if (binding.granularityExceptionId() == null || !Objects.equals(binding.questionId(), question.getId().toString())
                    || !Objects.equals(binding.rowVersion(), question.getRowVersion().toString())
                    || !Objects.equals(binding.contentHash(), content)
                    || mapper.importIdentityMatches(binding) != 1) { continue; }
            ReviewModels.GranularityException proof = ledger.exception(binding.granularityExceptionId());
            if (proof != null && KbReviewService.POLICY.equals(proof.policyVersion())
                    && new KbReviewCandidateBinding(mapper).factsMatch(binding, proof, question, mapper.facts(question.getId()))) { return proof; }
        }
        return null;
    }

    public void bindImport(KbQuestion saved, KbQuestion input, String actor, String requestFingerprint) {
        if (input.getGranularityExceptionId() == null && saved.getReviewParentFreeze() == null) { return; }
        KbQuestion question = fullQuestion(saved.getId());
        String bindingId = KbReviewLedger.hash(JSON.toJSONBytes(List.of(actor, input.getImportClientKey(), saved.getId())));
        ledger.saveBinding(new ReviewModels.ImportBinding(bindingId, input.getImportClientKey(), actor,
                requestFingerprint, saved.getId().toString(), question.getRowVersion().toString(),
                KbQuestionFingerprint.content(question), mapper.facts(saved.getId()), input.getGranularityExceptionId(),
                saved.getReviewParentFreeze(), lineage.freezeParents(saved.getReviewParentFreeze(), mapper.facts(saved.getId()))));
    }

    public boolean derivedEvidence(KbQuestion child) {
        return lineage.validBinding(child) != null;
    }

    public List<ReviewModels.Evidence> evidence(KbQuestion question, ReviewModels.GranularityException exception) {
        var result = new java.util.ArrayList<ReviewModels.Evidence>();
        if (exception != null) { result.add(new ReviewModels.Evidence("EXCEPTION", exception.exceptionId(), KbReviewLedger.hash(JSON.toJSONBytes(exception)))); }
        if ("DERIVED".equals(question.getCreationKind())) {
            ReviewModels.ImportBinding binding = lineage.validBinding(question);
            if (binding == null) { throw new ServiceException("缺少有效完整父题冻结存证", 409); }
            result.add(new ReviewModels.Evidence("BINDING", binding.bindingId(), KbReviewLedger.hash(JSON.toJSONBytes(binding))));
        }
        return List.copyOf(result);
    }

    public boolean evidenceValid(ReviewModels.Prepared prepared) {
        return lineage.evidenceValid(prepared);
    }

    public String basis(KbQuestion question) {
        return KbReviewLedger.hash(JSON.toJSONBytes(List.of(KbQuestionFingerprint.content(question), mapper.facts(question.getId()))));
    }

    public KbCandidateProxy.Candidate candidate(String runId, String key) {
        KbCandidateProxy.Candidate value = candidates.get(runId, key);
        ReviewModels.GranularityException receipt = ledger.exceptions().stream().filter(proof ->
                KbReviewService.POLICY.equals(proof.policyVersion()) && Objects.equals(proof.candidateHash(), value.candidateHash())
                && Objects.equals(proof.clientKey(), value.clientKey())).findFirst().orElse(null);
        return new KbCandidateProxy.Candidate(value.schemaVersion(),value.runId(),value.groupKey(),value.role(),
                value.ownerUsername(),value.instanceId(),value.candidateKey(),value.candidateRevision(),value.clientKey(),
                value.candidateHash(),value.canonicalCandidateJson(),value.record(),value.sourceId(),value.sourceLocator(),
                value.evidenceRefs(),value.createdAt(),value.workerKind(),value.current(),receipt);
    }

    private static String requestHash(ReviewModels.ExceptionCommand command) {
        return KbReviewLedger.hash(JSON.toJSONBytes(List.of(SecurityUtils.getUsername(),command)));
    }

    private KbQuestion fullQuestion(Long id) {
        KbQuestion q = questions.selectById(id);
        if (q == null) { throw new ServiceException("题目不存在", 404); }
        q.setKpIds(questions.selectKpIds(id));
        q.setAssetIds(questions.selectAssetIds(id));
        return q;
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static int count(KbQuestion q) { return KbHtmlLayout.validateParts(q.getStemHtml(), q.getAnswerHtml(), q.getAnalysisHtml()).itemCount(); }
    private static void requireSmallCalculation(String type, int count) {
        if (!"CALCULATION".equals(type) || count < 1 || count > 2)
            { throw new ServiceException("专项数量例外仅适用于真实1或2小题的计算练习", 400); }
    }
}
