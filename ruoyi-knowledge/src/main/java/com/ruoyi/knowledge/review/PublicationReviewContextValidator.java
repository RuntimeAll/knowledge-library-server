package com.ruoyi.knowledge.review;

/** Publication-owned scope validation. Implementations must not grant review authority. */
public interface PublicationReviewContextValidator {
    /** Validate the current PREPARING publication version and membership of this saved question. */
    void validateCheck(ReviewModels.PublicationContext context, Long questionId);

    /** Also validate the active SALE, frozen input and deterministic child approval identity. */
    void validateApproval(ReviewModels.PublicationContext context, String saleOperationKey,
            Long questionId, ReviewModels.Command command);

    /** Validate current PREPARING version and saved-question membership for a quantity exception. */
    void validateException(ReviewModels.PublicationContext context, Long questionId);
}
