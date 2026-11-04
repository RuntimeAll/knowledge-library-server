package com.ruoyi.knowledge.publication;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;

/** Persistence rows; never returned directly from anonymous endpoints. */
public final class PublicationRows {
    private PublicationRows() { }
    public static class BaseRow {
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        public Long id;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        public Long rowVersion;
        public String createBy;
        public Date createTime;
        public String updateBy;
        public Date updateTime;
    }
    public static class Publication extends BaseRow {
        public String title;
        public String postTitle;
        public String postBody;
        public String listingDescription;
        public String status;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        public Long contentVersion;
        public String originSystem;
        public String originRecordKey;
        public String legacySaleStatus;
        public Date legacySoldAt;
        public String shareCode;
        public String shareContentType;
        public String shareState;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        public Long shareAssetId;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        public Long sharedContentVersion;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        public Long lastSaleOperationId;
        public Date soldAt;
        public Date deletedAt;
        public String remark;
    }
    public static class PublicationProduct extends BaseRow {
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        public Long publicationId;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        public Long productId;
        public Integer sortOrder;
        public String questionListStatus;
        public Integer expectedQuestionCount;
        public String verifiedBy;
        public Date verifiedAt;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        public Long evidenceAssetId;
        public String note;
    }
    public static class PublicationQuestion extends BaseRow {
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        public Long publicationProductId;
        public Integer seq;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        public Long questionId;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        public Long observedQuestionVersion;
        public String observedContentHash;
        public String itemLocator;
        public String answerAvailability;
        public String answerEvidenceNote;
    }
    public static class PublicationReferenceSource extends BaseRow {
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        public Long publicationId;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        public Long sourceId;
        public Integer sortOrder;
        public String note;
    }
    public static class PublicationImage extends BaseRow {
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        public Long publicationId;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        public Long assetId;
        public Integer sortOrder;
        public String caption;
    }
    public static class PublicationBaiduShare extends BaseRow {
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        public Long publicationId;
        public String shareUrl;
        public String shareUrlHash;
        public String extractionCode;
        public String remoteShareId;
        public Integer sortOrder;
    }
    public static class PublicationBaiduFile extends BaseRow {
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        public Long publicationId;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        public Long baiduShareId;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        public Long publicationProductId;
        public String remoteFileId;
        public String remotePath;
        public String remoteFileName;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        public Long remoteFileSize;
        public String mappingStatus;
        public String verifiedBy;
        public Date verifiedAt;
        public String verificationNote;
    }
    public static class PublicationCheck extends BaseRow {
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        public Long publicationId;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        public Long contentVersion;
        public String dependencyHash;
        public String policyVersion;
        public String status;
        public Integer questionCount;
        public Integer blockingCount;
        public Integer noticeCount;
        public String resultsJson;
        public String failureCode;
        public String failureMessage;
        public Date finishedAt;
    }
    public static class PublicationOperation extends BaseRow {
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        public Long publicationId;
        public String operationKey;
        public String action;
        public String requestHash;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        public Long contentVersion;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        public Long expectedRowVersion;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        public Long actorId;
        public String actorName;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        public Long parentOperationId;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        public Long checkId;
        public String status;
        public Integer attemptCount;
        public String snapshotJson;
        public String resultJson;
        public String errorCode;
        public String errorMessage;
        public Date finishedAt;
    }
}
