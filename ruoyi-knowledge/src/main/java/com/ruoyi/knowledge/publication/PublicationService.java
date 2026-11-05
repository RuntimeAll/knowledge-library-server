package com.ruoyi.knowledge.publication;

import com.ruoyi.knowledge.mapper.PublicationMapper;
import static com.ruoyi.knowledge.publication.PublicationSupport.fail;
import static com.ruoyi.knowledge.publication.PublicationSupport.id;
import com.ruoyi.knowledge.domain.KbAgentSubmission;
import com.ruoyi.knowledge.mapper.KbAgentSubmissionMapper;
import com.ruoyi.knowledge.publication.PublicationModels.Command;
import com.ruoyi.knowledge.publication.PublicationModels.Input;
import com.ruoyi.knowledge.publication.PublicationModels.ListQuery;
import com.ruoyi.knowledge.publication.PublicationModels.Summary;
import com.ruoyi.knowledge.publication.PublicationModels.Detail;
import com.ruoyi.knowledge.publication.PublicationModels.Registration;
import com.ruoyi.knowledge.publication.PublicationModels.AllowedActions;
import com.ruoyi.knowledge.publication.PublicationModels.Page;
import com.ruoyi.knowledge.publication.PublicationModels.ImportResult;
import com.ruoyi.knowledge.publication.PublicationModels.Issue;
import com.ruoyi.knowledge.publication.PublicationModels.CheckResult;
import com.ruoyi.knowledge.publication.PublicationModels.OperationReceipt;
import com.ruoyi.knowledge.publication.PublicationModels.OperationResult;
import com.ruoyi.knowledge.publication.PublicationModels.Snapshot;
import com.ruoyi.knowledge.publication.PublicationModels.RelationQuery;
import com.ruoyi.knowledge.publication.PublicationRows.Publication;
import com.ruoyi.knowledge.publication.PublicationRows.PublicationCheck;
import com.ruoyi.knowledge.publication.PublicationRows.PublicationOperation;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

@Service
public class PublicationService {
    final PublicationMapper mapper;
    final PublicationSupport support;
    final PublicationGraph graph;
    final KbAgentSubmissionMapper submissions;
    public PublicationService(PublicationMapper mapper, PublicationSupport support,
            PublicationGraph graph, KbAgentSubmissionMapper submissions) {
        this.mapper = mapper; this.support = support; this.graph = graph; this.submissions = submissions;
    }
    public Page<Summary> list(ListQuery query) {
        support.permission("list");
        if ((query.keyword != null && query.keyword.length() > 300) || (query.kpId != null && query.kpId < 1)
                || (query.outlineId != null && query.outlineId < 1)) { throw fail(400, "INVALID_REQUEST", "检索字段格式无效"); }
        if (query.pageNum < 1 || query.pageSize < 1 || query.pageSize > 100 || query.pageNum > 1000000) {
            throw fail(400, "INVALID_REQUEST", "分页范围无效");
        }
        if (query.status != null && !List.of("PREPARING", "ON_SALE", "DELETED").contains(query.status)) {
            throw fail(400, "INVALID_REQUEST", "状态无效");
        }
        query.owner = support.registrationOnly() ? support.actor() : null;
        query.offset = (query.pageNum - 1) * query.pageSize;
        List<Summary> items = mapper.list(query);
        for (Summary item : items) {
            item.deliveryMode = support.deliveryMode();
            item.topicKeywords = PublicationTopics.read(item.topicKeywordsJson, support);
            if ("ON_SALE".equals(item.status) && "READY".equals(item.shareState) && item.shareCode != null && "text/html".equals(item.shareContentType)) { item.shareUrl = support.publicUrl(item.shareCode); }
        }
        return new Page<>(items, mapper.count(query));
    }
    public Detail detail(Long publicationId) {
        support.permission("query"); Publication row = require(publicationId); support.own(row);
        return detailOf(row);
    }
    public Detail detailOf(Publication row) {
        Input input = graph.read(row);
        Detail out = support.json.convertValue(input, Detail.class);
        out.topicKeywords = input.topicKeywords == null ? List.of() : input.topicKeywords;
        out.id = row.id; out.rowVersion = row.rowVersion; out.contentVersion = row.contentVersion;
        out.status = row.status; out.shareState = row.shareState; out.deliveryMode = support.deliveryMode();
        out.createTime = row.createTime; out.updateTime = row.updateTime;
        out.productCount = out.products.size(); out.imageCount = out.images.size();
        out.questionCount = (int) out.products.stream().flatMap(p -> p.questions.stream()).map(q -> q.questionId).distinct().count();
        out.shareUrl = shareUrl(row);
        if ("PUBLIC_PAGE".equals(out.deliveryMode) && "READY".equals(row.shareState) && !"text/html".equals(row.shareContentType)) {
            out.missingItems.add(new Issue("ONLINE_PAGE_REQUIRED", "shareState", null, null, "旧发布包不能作为在线页，请撤回后重新发售生成页面"));
        }
        out.latestCheck = checkView(mapper.latestCheck(row.id));
        out.latestOperation = receipt(row, mapper.latestOperation(row.id));
        out.relationsSummary = relationSummary(row, out.latestCheck);
        out.permissions = new AllowedActions(support.has("edit") && "PREPARING".equals(row.status),
                support.has("check"), support.has("sale"), support.has("retract"),
                support.has("remove"), support.has("restore"), canRetryGeneration(row), support.has("sale") && support.has("query"));
        if (out.products.isEmpty()) { out.missingItems.add(new Issue("PRODUCT_REQUIRED", "products", null, null, "成品尚未登记")); }
        return out;
    }
    private boolean canRetryGeneration(Publication row) {
        if (!support.has("shareRetry") || !"ON_SALE".equals(row.status) || row.lastSaleOperationId == null) { return false; }
        PublicationOperation generation = mapper.operation(row.id, "generate:" + row.lastSaleOperationId);
        return generation != null && !"SUCCEEDED".equals(generation.status) && support.sameActor(generation);
    }
    private com.ruoyi.knowledge.publication.PublicationModels.RelationSummary relationSummary(Publication row, CheckResult check) {
        RelationQuery scope = scope(row.id, check);
        var refs = mapper.relationRefs(scope); var links = mapper.relationLinks(scope);
        var items = refs.stream().map(ref -> new com.ruoyi.knowledge.publication.PublicationModels.RelationRef(
                ref.objectType, id(ref.id), ref.name, id(ref.rowVersion), links.stream()
                .filter(l -> (l.fromType().equals(ref.objectType) && l.fromId().equals(id(ref.id)))
                        || (l.toType().equals(ref.objectType) && l.toId().equals(id(ref.id))))
                .map(com.ruoyi.knowledge.publication.PublicationModels.RelationLink::type).distinct().toList())).toList();
        var counts = new com.ruoyi.knowledge.publication.PublicationModels.RelationCounts(
                refs.stream().filter(r -> "PRODUCT".equals(r.objectType)).count(), refs.stream().filter(r -> "QUESTION".equals(r.objectType)).count(),
                refs.stream().filter(r -> "SOURCE".equals(r.objectType)).count(), refs.stream().filter(r -> "KP".equals(r.objectType)).count(),
                refs.stream().filter(r -> "OUTLINE".equals(r.objectType)).count());
        return new com.ruoyi.knowledge.publication.PublicationModels.RelationSummary(counts, items, List.of());
    }
    public Detail create(Command command) {
        support.permission("add"); requireInput(command);
        return support.gated(() -> support.transaction(() -> {
            Registration reg = register("publicationCreate", "new", command, () -> { graph.validate(command.publication); return insert(command, false).id; });
            Detail out = detailOf(require(Long.valueOf(reg.resourceId()))); out.registration = reg; return out;
        }));
    }
    public Detail update(Long publicationId, Command command) {
        support.permission("edit"); requireInput(command);
        return support.gated(() -> support.transaction(() -> {
            Registration reg = register("publicationUpdate", id(publicationId), command, () -> {
                Publication row = lock(publicationId); support.own(row); support.versions(row, command);
                preparing(row); noActiveSale(row.id); graph.validate(command.publication); apply(row, command.publication);
                row.contentVersion++; support.updated(row); changed(mapper.updatePublication(row));
                graph.replace(publicationId, command.publication); return publicationId;
            });
            Detail out = detailOf(require(publicationId)); out.registration = reg; return out;
        }));
    }
    public ImportResult importOne(Command command) {
        support.permission("import"); requireInput(command);
        if (command.originSystem == null || command.originSystem.isBlank() || command.originRecordKey == null
                || command.originRecordKey.isBlank() || command.legacySaleStatus == null || !List.of("SOLD", "NOT_SOLD", "UNKNOWN").contains(command.legacySaleStatus)) {
            throw fail(400, "INVALID_REQUEST", "迁入身份和历史事实必填");
        }
        return support.gated(() -> support.transaction(() -> {
            Registration reg = register("publicationImport", command.originSystem + "/" + command.originRecordKey,
                    command, () -> {
                        Publication old = mapper.byOrigin(command.originSystem, command.originRecordKey);
                        if (old != null) { throw fail(409, "IDEMPOTENCY_CONFLICT", "该历史记录已有迁入映射，请查原回执"); }
                        graph.validate(command.publication); return insert(command, true).id;
                    });
            Detail out = detailOf(require(Long.valueOf(reg.resourceId()))); out.registration = reg;
            return new ImportResult(out, !reg.replayed(), reg.replayed(), out.missingItems);
        }));
    }
    private Publication insert(Command command, boolean imported) {
        Publication row = support.stamp(new Publication()); apply(row, command.publication);
        if (row.topicKeywordsJson == null) { row.topicKeywordsJson = "[]"; }
        row.status = "PREPARING"; row.contentVersion = 1L; row.shareState = "NOT_CREATED";
        if (imported) { row.originSystem = command.originSystem; row.originRecordKey = command.originRecordKey;
            row.legacySaleStatus = command.legacySaleStatus; row.legacySoldAt = command.legacySoldAt; }
        mapper.insertPublication(row); graph.replace(row.id, command.publication); return row;
    }
    public boolean replayed(String action, String target, Command command) {
        key(command.operationKey);
        KbAgentSubmission prior = submissions.lock(support.actor(), action, PublicationSupport.digest(target + ":" + command.operationKey));
        if (prior == null) { return false; }
        if (!Objects.equals(prior.getRequestHash(), registrationHash(action, command))) { throw fail(409, "IDEMPOTENCY_CONFLICT", "同一操作键的输入已改变"); }
        return prior.getResourceId() != null;
    }
    public Registration register(String action, String target, Command command, Supplier<Long> work) {
        key(command.operationKey);
        String actor = support.actor(); String key = PublicationSupport.digest(target + ":" + command.operationKey);
        String hash = registrationHash(action, command);
        submissions.claim(actor, action, key, hash);
        KbAgentSubmission prior = submissions.lock(actor, action, key);
        if (!hash.equals(prior.getRequestHash())) { throw fail(409, "IDEMPOTENCY_CONFLICT", "同一操作键的输入已改变"); }
        if (prior.getResourceId() != null) { return new Registration(command.operationKey, true, id(prior.getResourceId())); }
        Long result = work.get(); submissions.complete(actor, action, key, result);
        return new Registration(command.operationKey, false, id(result));
    }
    private String registrationHash(String action, Command command) {
        // Empty inputs keep historical snapshot hashes, but an explicit update clear is a distinct command.
        boolean clearsTopics = "publicationUpdate".equals(action) && command.publication != null
                && command.publication.topicKeywords != null && command.publication.topicKeywords.isEmpty();
        return clearsTopics ? support.hash(List.of(command, "TOPIC_KEYWORDS_CLEAR")) : support.hash(command);
    }
    public OperationReceipt operation(Long publicationId, String key) {
        support.permission("query"); Publication row = require(publicationId); support.own(row);
        PublicationOperation op = mapper.operation(publicationId, key);
        if (op == null) { throw fail(404, "NOT_FOUND", "操作回执不存在"); }
        return receipt(row, op);
    }
    public CheckResult check(Long publicationId, Long checkId) {
        support.permission("query"); Publication row = require(publicationId); support.own(row);
        PublicationCheck check = mapper.getCheck(publicationId, checkId);
        if (check == null) { throw fail(404, "NOT_FOUND", "自检记录不存在"); }
        return checkView(check);
    }
    public CheckResult checkView(PublicationCheck row) {
        if (row == null) { return null; }
        CheckResult out = support.decode(row.resultsJson, CheckResult.class);
        if (out == null) { out = new CheckResult(); }
        out.checkId = row.id; out.publicationId = row.publicationId; out.contentVersion = row.contentVersion;
        out.status = row.status; out.dependencyHash = row.dependencyHash; out.policyVersion = row.policyVersion;
        out.questionCount = row.questionCount; out.blockingCount = row.blockingCount; out.noticeCount = row.noticeCount;
        out.failureCode = row.failureCode; out.failureMessage = row.failureMessage; out.finishedAt = row.finishedAt;
        return out;
    }
    public OperationReceipt receipt(Publication row, PublicationOperation op) {
        if (op == null) { return null; }
        OperationReceipt out = support.json.convertValue(op, OperationReceipt.class);
        out.operationId = op.id; out.operationRowVersion = op.rowVersion; out.currentRowVersion = row.rowVersion;
        out.currentStatus = row.status; out.createdAt = op.createTime;
        out.result = support.decode(op.resultJson, OperationResult.class);
        if (out.result != null && out.result.deliveryMode == null) { out.result.deliveryMode = "PUBLIC_PAGE"; }
        if ("SALE".equals(op.action) && List.of("PENDING", "UNCERTAIN").contains(op.status)
                && support.sameActor(op) && support.has("sale")) {
            Snapshot snapshot = support.decode(op.snapshotJson, Snapshot.class);
            out.resumeCommand = snapshot == null ? null : snapshot.originalCommand();
        }
        out.retryable = !"SUCCEEDED".equals(op.status);
        out.nextAction = switch (op.status) {
            case "PENDING", "UNCERTAIN" -> "SALE".equals(op.action)
                    ? (support.sameActor(op) ? "RESUME_ORIGINAL" : "RECONCILE_ORIGINAL") : "POLL_ORIGINAL";
            case "FAILED" -> "GENERATE_LINK".equals(op.action) ? "RETRY_GENERATE" : "EDIT_AND_RECHECK";
            default -> "NONE";
        };
        return out;
    }
    public OperationReceipt state(Long publicationId, Command command, String action) {
        String permission = switch (action) { case "RETRACT" -> "retract"; case "DELETE" -> "remove"; default -> "restore"; };
        support.permission(permission); key(command.operationKey);
        return support.gated(() -> support.transaction(() -> {
            Publication row = lock(publicationId); support.own(row);
            PublicationOperation old = mapper.operation(publicationId, command.operationKey);
            if (old != null) { same(old, command, action); return receipt(row, old); }
            support.versions(row, command); noActiveSale(row.id);
            if (command.reason == null || command.reason.isBlank()) { throw fail(400, "INVALID_REQUEST", "状态变更原因必填"); }
            boolean allowed = switch (action) { case "RETRACT" -> "ON_SALE".equals(row.status);
                case "DELETE" -> !"DELETED".equals(row.status); default -> "DELETED".equals(row.status); };
            if (!allowed) { throw fail(409, "STATE_CONFLICT", "当前状态不能执行此操作"); }
            PublicationOperation op = newOperation(row, command, action, new Snapshot(graph.read(row), null));
            row.status = "DELETE".equals(action) ? "DELETED" : "PREPARING";
            row.deletedAt = "DELETE".equals(action) ? new Date() : null; row.shareState = "DISABLED";
            support.updated(row); changed(mapper.updatePublication(row)); row.rowVersion++;
            op.status = "SUCCEEDED"; op.finishedAt = new Date(); op.resultJson = support.encode(result(row));
            changed(mapper.updatePublicationOperation(op)); return receipt(row, op);
        }));
    }
    public PublicationOperation newOperation(Publication row, Command c, String action, Snapshot snapshot) {
        PublicationOperation op = support.stamp(new PublicationOperation());
        op.publicationId = row.id; op.operationKey = c.operationKey; op.action = action;
        op.requestHash = support.hash(c); op.contentVersion = row.contentVersion; op.expectedRowVersion = c.expectedRowVersion;
        op.actorId = com.ruoyi.common.utils.SecurityUtils.getUserId(); op.actorName = support.actor();
        op.checkId = c.checkId; op.status = "PENDING"; op.attemptCount = 0; op.snapshotJson = support.encode(new Snapshot(snapshot.publication(), snapshot.check(), c, snapshot.deliveryMode()));
        mapper.insertPublicationOperation(op); return op;
    }
    public void same(PublicationOperation op, Command c, String action) {
        if (!Objects.equals(op.action, action) || !Objects.equals(op.requestHash, support.hash(c))) {
            throw fail(409, "IDEMPOTENCY_CONFLICT", "原操作键与本次输入不同");
        }
    }
    public OperationResult result(Publication row) {
        OperationResult r = new OperationResult(); r.publicationStatus = row.status; r.rowVersion = row.rowVersion;
        r.deliveryMode = support.deliveryMode(); r.shareState = row.shareState; r.shareUrl = shareUrl(row); return r;
    }
    public String shareUrl(Publication row) {
        return "ON_SALE".equals(row.status) && "READY".equals(row.shareState) && row.shareCode != null && "text/html".equals(row.shareContentType)
                ? support.publicUrl(row.shareCode) : null;
    }
    public static RelationQuery scope(Long publicationId, CheckResult check) {
        RelationQuery scope = new RelationQuery(); scope.publicationId = publicationId;
        if (check != null) {
            scope.frozenSourceIds = check.items.stream().flatMap(q -> q.sourceEvidence.stream())
                    .filter(e -> "FROZEN_LINEAGE".equals(e.kind) && e.sourceId != null)
                    .map(e -> e.sourceId).distinct().sorted().toList();
        }
        return scope;
    }
    public Publication require(Long id) {
        Publication row = mapper.get(id); if (row == null) { throw fail(404, "NOT_FOUND", "发布资料不存在"); } return row;
    }
    public Publication lock(Long id) {
        Publication row = mapper.lock(id); if (row == null) { throw fail(404, "NOT_FOUND", "发布资料不存在"); } return row;
    }
    public void noActiveSale(Long id) {
        if (mapper.activeSales(id) > 0) { throw fail(409, "OPERATION_IN_PROGRESS", "请先核对未完成发售操作"); }
    }
    public static void preparing(Publication row) {
        if (!"PREPARING".equals(row.status)) { throw fail(409, "STATE_CONFLICT", "请先撤回或恢复到准备中"); }
    }
    public static void changed(int n) { if (n != 1) { throw fail(409, "VERSION_CONFLICT", "资料已变化，请重新读取"); } }
    public static void key(String key) { if (key == null || key.isBlank() || key.length() > 128) { throw fail(400, "INVALID_REQUEST", "有效操作键必填"); } }
    private static void requireInput(Command c) { if (c.publication == null) { throw fail(400, "INVALID_REQUEST", "发布资料必填"); } }
    private void apply(Publication row, Input input) {
        row.title = input.title; row.postTitle = input.postTitle; row.postBody = input.postBody;
        row.listingDescription = input.listingDescription; row.remark = input.remark;
        if (input.topicKeywords != null) { row.topicKeywordsJson = support.encode(input.topicKeywords); }
    }
}
