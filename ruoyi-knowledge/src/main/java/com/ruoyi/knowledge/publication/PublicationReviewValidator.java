package com.ruoyi.knowledge.publication;

import static com.ruoyi.knowledge.publication.PublicationSupport.fail;
import com.ruoyi.knowledge.publication.PublicationModels.QuestionCheck;
import com.ruoyi.knowledge.publication.PublicationModels.Snapshot;
import com.ruoyi.knowledge.publication.PublicationRows.Publication;
import com.ruoyi.knowledge.publication.PublicationRows.PublicationOperation;
import com.ruoyi.knowledge.review.PublicationReviewContextValidator;
import com.ruoyi.knowledge.review.ReviewModels;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class PublicationReviewValidator implements PublicationReviewContextValidator {
    private final PublicationService service;
    public PublicationReviewValidator(PublicationService service) { this.service = service; }
    @Override
    public void validateCheck(ReviewModels.PublicationContext ctx, Long questionId) {
        Publication row = service.require(ctx.publicationId()); service.support.own(row); PublicationService.preparing(row);
        if (!Objects.equals(row.rowVersion, ctx.expectedRowVersion()) || service.mapper.questions(row.id).stream()
                .noneMatch(q -> q.questionId.equals(questionId))) { throw fail(409, "CHECK_STALE", "发布资料版本或题单已变化"); }
    }
    @Override
    public void validateException(ReviewModels.PublicationContext ctx, Long questionId) { validateCheck(ctx, questionId); }
    @Override
    public void validateApproval(ReviewModels.PublicationContext ctx, String saleKey, Long questionId, ReviewModels.Command command) {
        validateCheck(ctx, questionId);
        PublicationOperation op = service.mapper.operation(ctx.publicationId(), saleKey);
        if (op == null || !"SALE".equals(op.action) || !"PENDING".equals(op.status)
                || !service.support.sameActor(op) || !Objects.equals(op.expectedRowVersion, ctx.expectedRowVersion())) {
            throw fail(409, "OPERATION_IN_PROGRESS", "发售操作身份或状态不匹配");
        }
        Snapshot snapshot = service.support.decode(op.snapshotJson, Snapshot.class);
        QuestionCheck item = snapshot.check().items.stream().filter(q -> q.questionId.equals(questionId)).findFirst()
                .orElseThrow(() -> fail(409, "INVALID_REFERENCE", "题目不在发售快照中"));
        if (!"PASSED".equals(snapshot.check().status) || !Boolean.TRUE.equals(command.contentReviewed())
                || snapshot.originalCommand() == null || !Objects.equals(snapshot.originalCommand().reason, command.reason())
                || !Objects.equals(item.observedRowVersion, command.expectedRowVersion())
                || !Objects.equals(item.contentHash, command.expectedContentHash())
                || !Objects.equals(childKey(op, questionId), command.operationId())) {
            throw fail(409, "CHECK_STALE", "批准命令与冻结自检不一致");
        }
    }
    public static String childKey(PublicationOperation op, Long questionId) {
        return "publication-" + PublicationSupport.digest(op.publicationId + ":" + op.operationKey + ":" + questionId);
    }
}
