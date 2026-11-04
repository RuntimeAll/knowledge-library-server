package com.ruoyi.knowledge.publication;

import static com.ruoyi.knowledge.publication.PublicationSupport.id;
import com.ruoyi.knowledge.domain.KbQuestion;
import com.ruoyi.knowledge.mapper.KbQuestionMapper;
import com.ruoyi.knowledge.publication.PublicationModels.Command;
import com.ruoyi.knowledge.publication.PublicationModels.Input;
import com.ruoyi.knowledge.publication.PublicationModels.ProductInput;
import com.ruoyi.knowledge.publication.PublicationModels.QuestionInput;
import com.ruoyi.knowledge.publication.PublicationModels.Registration;
import com.ruoyi.knowledge.publication.PublicationModels.Issue;
import com.ruoyi.knowledge.publication.PublicationModels.CheckResult;
import com.ruoyi.knowledge.publication.PublicationModels.QuestionCheck;
import com.ruoyi.knowledge.publication.PublicationModels.CheckItem;
import com.ruoyi.knowledge.publication.PublicationModels.SourceEvidence;
import com.ruoyi.knowledge.publication.PublicationModels.RelationQuery;
import com.ruoyi.knowledge.publication.PublicationRows.Publication;
import com.ruoyi.knowledge.publication.PublicationRows.PublicationCheck;
import com.ruoyi.knowledge.review.KbPublicationReviewBridge;
import com.ruoyi.knowledge.review.KbReviewService;
import com.ruoyi.knowledge.review.ReviewModels;
import com.ruoyi.knowledge.service.impl.QuestionContentRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class PublicationCheckService {
    public static final String POLICY = "PRD012-QUALITY-2";
    private final PublicationService service;
    private final KbPublicationReviewBridge bridge;
    private final KbQuestionMapper questions;
    private final KbReviewService reviews;
    public PublicationCheckService(PublicationService service, KbPublicationReviewBridge bridge,
            KbQuestionMapper questions, KbReviewService reviews) {
        this.service = service; this.bridge = bridge; this.questions = questions; this.reviews = reviews;
    }
    public CheckResult run(Long publicationId, Command command) {
        service.support.permission("check");
        return service.support.gated(() -> {
            Publication row = service.require(publicationId); service.support.own(row);
            PublicationService.preparing(row); service.support.versions(row, command); service.noActiveSale(publicationId);
            Registration registration = service.support.transaction(() -> service.register("publicationCheck", id(publicationId), command, () -> {
                PublicationCheck check = service.support.stamp(new PublicationCheck());
                check.publicationId = row.id; check.contentVersion = row.contentVersion; check.policyVersion = POLICY;
                check.status = "RUNNING"; check.questionCount = 0; check.blockingCount = 0; check.noticeCount = 0;
                service.mapper.insertPublicationCheck(check); return check.id;
            }));
            PublicationCheck stored = service.mapper.getCheck(publicationId, Long.valueOf(registration.resourceId()));
            if (!"RUNNING".equals(stored.status)) { return service.checkView(stored); }
            CheckResult result;
            try { result = evaluate(row, true); }
            catch (RuntimeException e) {
                result = new CheckResult(); result.status = "ERROR"; result.failureCode = "DEPENDENCY_UNAVAILABLE";
                result.failureMessage = "自检未完成，依赖暂不可核对，请重新自检";
            }
            result.checkId = stored.id; result.publicationId = row.id; result.contentVersion = row.contentVersion;
            result.policyVersion = POLICY; result.finishedAt = new Date();
            stored.status = result.status; stored.dependencyHash = result.dependencyHash;
            stored.questionCount = result.questionCount; stored.blockingCount = result.blockingCount;
            stored.noticeCount = result.noticeCount; stored.resultsJson = service.support.encode(result);
            stored.failureCode = result.failureCode; stored.failureMessage = result.failureMessage; stored.finishedAt = result.finishedAt;
            service.support.transaction(() -> { PublicationService.changed(service.mapper.updatePublicationCheck(stored)); return null; });
            return result;
        });
    }
    public CheckResult evaluate(Publication row, boolean preparing) {
        Input input = service.graph.read(row); CheckResult result = new CheckResult();
        result.publicationId = row.id; result.contentVersion = row.contentVersion; result.policyVersion = POLICY;
        result.status = "PASSED"; result.issues.addAll(materialIssues(input));
        List<QuestionInput> refs = input.products.stream().flatMap(p -> p.questions.stream()).toList();
        List<Long> ids = refs.stream().map(q -> q.questionId).distinct().sorted().toList(); result.questionCount = ids.size();
        for (Long qid : ids) {
            QuestionCheck item;
            try {
                ReviewModels.Readiness readiness = preparing
                        ? bridge.inspect(new ReviewModels.PublicationContext(row.id, row.rowVersion), qid) : reviews.readiness(qid);
                List<QuestionInput> occurrences = refs.stream().filter(q -> q.questionId.equals(qid)).toList();
                item = inspect(qid, occurrences, readiness, preparing);
            } catch (com.ruoyi.common.exception.ServiceException e) {
                if (e.getCode() == null || !List.of(400, 404, 409).contains(e.getCode())) { throw e; }
                item = new QuestionCheck(); item.questionId = qid;
                item.issues.add(issue("QUESTION_INVALID", qid, e.getMessage()));
            }
            result.items.add(item); result.checkedCount++;
            result.blockingCount += item.issues.size();
            if (item.checks.stream().anyMatch(c -> "NOTICE".equals(c.status()))) { result.noticeCount++; }
        }
        result.blockingCount += result.issues.size();
        if (result.blockingCount > 0) { result.status = "BLOCKED"; }
        result.dependencyHash = dependency(row.id, input, result.items);
        result.finishedAt = new Date(); return result;
    }
    private QuestionCheck inspect(Long qid, List<QuestionInput> refs, ReviewModels.Readiness readiness, boolean preparing) {
        QuestionCheck out = new QuestionCheck(); out.questionId = qid;
        out.observedRowVersion = Long.valueOf(readiness.rowVersion()); out.contentHash = readiness.contentHash();
        out.basisHash = readiness.approvalBasisHash();
        out.answerAvailability = refs.stream().anyMatch(q -> "PROVIDED".equals(q.answerAvailability)) ? "PROVIDED"
                : refs.stream().allMatch(q -> "NOT_PROVIDED".equals(q.answerAvailability)) ? "NOT_PROVIDED" : "UNKNOWN";
        KbQuestion q = questions.selectById(qid); out.answerPresent = q != null && QuestionContentRules.hasContent(q.getAnswerHtml());
        boolean mismatch = refs.stream().anyMatch(r -> !Objects.equals(r.observedContentHash, readiness.contentHash())
                || (!readiness.eligible() && !Objects.equals(r.observedQuestionVersion, out.observedRowVersion)));
        if (mismatch) { out.issues.add(issue("CHECK_STALE", qid, "题目核实版本或完整摘要变化，请重新核实题单")); }
        if ("PROVIDED".equals(out.answerAvailability) && !out.answerPresent) {
            out.issues.add(issue("ANSWER_NOT_INGESTED", qid, "已有答案尚未录入题目"));
        }
        if (preparing && !readiness.eligible()) {
            for (ReviewModels.Check check : readiness.checks()) {
                out.checks.add(new CheckItem(check.code(), check.passed() ? "PASS" : "BLOCKED", check.message()));
                if (!check.passed()) { out.issues.add(issue(check.code(), qid, check.message())); }
            }
            if (!readiness.canApprove()) { out.issues.add(issue("NOT_APPROVABLE", qid, "题目不能在本次发售转正")); }
        } else if (!preparing && !readiness.eligible()) { out.issues.add(issue("NOT_ELIGIBLE", qid, "正式题目资格已失效")); }
        if (!out.answerPresent) { out.checks.add(new CheckItem("ANSWER", "NOTICE", "未提供答案，不阻断")); }
        out.sourceEvidence = new ArrayList<>(service.mapper.sourceEvidence(qid));
        ReviewModels.PublicationLineageEvidence lineage;
        try { lineage = bridge.lineageEvidence(qid); }
        catch (com.ruoyi.common.exception.ServiceException e) {
            if (!Integer.valueOf(409).equals(e.getCode())) { throw e; }
            lineage = null; out.issues.add(issue("ORIGIN", qid, "派生题的冻结来源证据无效"));
        }
        if (lineage != null) {
            for (String sourceId : lineage.sourceIds()) {
                SourceEvidence evidence = new SourceEvidence(); evidence.kind = "FROZEN_LINEAGE";
                evidence.sourceId = Long.valueOf(sourceId); evidence.lineageEvidenceId = lineage.bindingId();
                out.sourceEvidence.add(evidence);
            }
        }
        out.kpIds = service.mapper.questionKps(qid).stream().map(PublicationSupport::id).toList();
        out.outlineNodeIds = service.mapper.questionNodes(qid).stream().map(PublicationSupport::id).toList();
        return out;
    }
    List<Issue> materialIssues(Input input) {
        List<Issue> issues = new ArrayList<>();
        if (input.products.isEmpty()) { issues.add(issue("PRODUCT_REQUIRED", null, "必须关联实际成品及完整题单")); }
        for (ProductInput p : input.products) {
            if (!PublicationGraph.withoutQuestions(p) && (!"VERIFIED".equals(p.questionListStatus) || p.expectedQuestionCount == null
                    || p.expectedQuestionCount < 1 || p.expectedQuestionCount != p.questions.size())) {
                issues.add(new Issue("QUESTION_LIST_REQUIRED", "products", id(p.productId), null, "成品完整题单尚未核实"));
            }
            boolean mapped = input.baiduShares.stream().flatMap(s -> s.files.stream())
                    .anyMatch(f -> f.productId.equals(p.productId) && "VERIFIED".equals(f.mappingStatus));
            if (!mapped) { issues.add(new Issue("BAIDU_MAPPING_REQUIRED", "baiduShares", id(p.productId), null, "成品网盘文件尚未核实")); }
        }
        if (input.images.isEmpty() || input.postTitle == null || input.postTitle.isBlank() || input.postBody == null || input.postBody.isBlank()) {
            issues.add(issue("MATERIAL_REQUIRED", null, "发布图片、标题及正文须备齐"));
        }
        // Revalidates all file and asset references, without changing the frozen records.
        try { service.graph.validate(input); }
        catch (PublicationException e) {
            if (e.getCode() != 400) { throw e; }
            issues.add(issue("INVALID_REFERENCE", null, e.getMessage()));
        }
        return issues;
    }
    String dependency(Long publicationId, Input input, List<QuestionCheck> items) {
        List<String> contents = items.stream().sorted(Comparator.comparing(i -> i.questionId))
                .map(i -> id(i.questionId) + ":" + i.contentHash).toList();
        return service.support.hash(List.of(input, contents, service.mapper.dependencyFacts(scope(publicationId, items)), service.mapper.relationLinks(scope(publicationId, items)), items.stream().map(i -> i.sourceEvidence).toList()));
    }
    private static RelationQuery scope(Long id, List<QuestionCheck> items) {
        CheckResult check = new CheckResult(); check.items = items; return PublicationService.scope(id, check);
    }
    private static Issue issue(String code, Long qid, String message) { return new Issue(code, null, null, id(qid), message); }
}
