package com.ruoyi.knowledge.publication;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Typed publication commands and views. Persistent operation snapshots use the same models. */
public final class PublicationModels {
    private PublicationModels() { }
    public static class Command {
        @com.fasterxml.jackson.annotation.JsonAnySetter
        public void rejectUnknown(String name, Object ignored) {
            throw new IllegalArgumentException("不支持的发布命令字段: " + name);
        }
        @NotBlank @Size(max = 128) public String operationKey;
        @JsonFormat(shape = JsonFormat.Shape.STRING) public Long expectedRowVersion;
        @JsonFormat(shape = JsonFormat.Shape.STRING) public Long contentVersion;
        @JsonFormat(shape = JsonFormat.Shape.STRING) public Long checkId;
        @JsonFormat(shape = JsonFormat.Shape.STRING) public Long saleOperationId;
        @JsonFormat(shape = JsonFormat.Shape.STRING) public Long expectedOperationRowVersion;
        @Size(max = 1000) public String reason;
        public Boolean closeIfSafe;
        @Valid public Input publication;
        @Size(max = 64) public String originSystem;
        @Size(max = 160) public String originRecordKey;
        public String legacySaleStatus;
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC") public Date legacySoldAt;
    }
    public static class Input {
        @NotBlank @Size(max = 300) public String title;
        @Size(max = 300) public String postTitle;
        @Size(max = 1000000) public String postBody;
        @Size(max = 1000000) public String listingDescription;
        @Size(max = 500) public String remark;
        @NotNull @Valid public List<ProductInput> products;
        @NotNull @Valid public List<ReferenceInput> referenceSources;
        @NotNull @Valid public List<ImageInput> images;
        @NotNull @Valid public List<ShareInput> baiduShares;
    }
    public static class ProductInput {
        @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING) public Long productId;
        public Integer sortOrder;
        public String questionListStatus;
        public Integer expectedQuestionCount;
        @JsonFormat(shape = JsonFormat.Shape.STRING) public Long evidenceAssetId;
        @Size(max = 1000) public String note;
        @NotNull @Valid public List<QuestionInput> questions;
    }
    public static class QuestionInput {
        public Integer seq;
        @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING) public Long questionId;
        @JsonFormat(shape = JsonFormat.Shape.STRING) public Long observedQuestionVersion;
        public String observedContentHash;
        @Size(max = 500) public String itemLocator;
        public String answerAvailability;
        @Size(max = 1000) public String answerEvidenceNote;
    }
    public static class ReferenceInput {
        @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING) public Long sourceId;
        public Integer sortOrder;
        @Size(max = 1000) public String note;
    }
    public static class ImageInput {
        @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING) public Long assetId;
        public Integer sortOrder;
        @Size(max = 300) public String caption;
    }
    public static class ShareInput {
        @NotBlank @Size(max = 2048) public String shareUrl;
        @Size(max = 32) public String extractionCode;
        @Size(max = 128) public String remoteShareId;
        public Integer sortOrder;
        @NotNull @Valid public List<FileInput> files;
    }
    public static class FileInput {
        @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING) public Long productId;
        @Size(max = 128) public String remoteFileId;
        @Size(max = 2048) public String remotePath;
        @NotBlank @Size(max = 500) public String remoteFileName;
        @JsonFormat(shape = JsonFormat.Shape.STRING) public Long remoteFileSize;
        public String mappingStatus;
        @Size(max = 1000) public String verificationNote;
    }
    public static class ListQuery {
        public String keyword;
        public String status;
        public Long outlineId;
        public Long kpId;
        public int pageNum = 1;
        public int pageSize = 20;
        public int offset;
        public String owner;
    }
    public static class Summary extends Input {
        @JsonFormat(shape = JsonFormat.Shape.STRING) public Long id;
        @JsonFormat(shape = JsonFormat.Shape.STRING) public Long rowVersion;
        @JsonFormat(shape = JsonFormat.Shape.STRING) public Long contentVersion;
        public String status;
        public String deliveryMode;
        public String shareState;
        public String shareUrl;
        @com.fasterxml.jackson.annotation.JsonIgnore public String shareCode;
        @com.fasterxml.jackson.annotation.JsonIgnore public String shareContentType;
        public int productCount;
        public int imageCount;
        public int questionCount;
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC") public Date createTime;
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC") public Date updateTime;
    }
    public static class Detail extends Summary {
        public Registration registration;
        public List<Issue> missingItems = new ArrayList<>();
        public CheckResult latestCheck;
        public OperationReceipt latestOperation;
        public AllowedActions permissions;
        public RelationSummary relationsSummary;
    }
    public record Registration(String operationKey, boolean replayed, String resourceId) { }
    public record AllowedActions(boolean canEdit, boolean canCheck, boolean canSale, boolean canRetract,
            boolean canDelete, boolean canRestore, boolean canRetryGenerate, boolean canReconcile) { }
    public record Page<T>(List<T> rows, long total) { }
    public record ImportResult(Detail publication, boolean imported, boolean existing, List<Issue> issues) { }
    public record Issue(String code, String field, String productId, String questionId, String message) { }
    public static class CheckResult {
        @JsonFormat(shape = JsonFormat.Shape.STRING) public Long checkId;
        @JsonFormat(shape = JsonFormat.Shape.STRING) public Long publicationId;
        @JsonFormat(shape = JsonFormat.Shape.STRING) public Long contentVersion;
        public String status;
        public String dependencyHash;
        public String policyVersion;
        public int questionCount;
        public int checkedCount;
        public int blockingCount;
        public int noticeCount;
        public List<QuestionCheck> items = new ArrayList<>();
        public List<Issue> issues = new ArrayList<>();
        public String failureCode;
        public String failureMessage;
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC") public Date finishedAt;
    }
    public static class QuestionCheck {
        @JsonFormat(shape = JsonFormat.Shape.STRING) public Long questionId;
        @JsonFormat(shape = JsonFormat.Shape.STRING) public Long observedRowVersion;
        public String contentHash;
        public String basisHash;
        public String answerAvailability;
        public boolean answerPresent;
        public List<CheckItem> checks = new ArrayList<>();
        public List<Issue> issues = new ArrayList<>();
        public List<SourceEvidence> sourceEvidence = new ArrayList<>();
        public List<String> kpIds = new ArrayList<>();
        public List<String> outlineNodeIds = new ArrayList<>();
    }
    public record CheckItem(String code, String status, String message) { }
    public static class SourceEvidence {
        public String kind;
        @JsonFormat(shape = JsonFormat.Shape.STRING) public Long sourceId;
        @JsonFormat(shape = JsonFormat.Shape.STRING) public Long relationId;
        public String lineageEvidenceId;
    }
    public static class OperationReceipt {
        @JsonFormat(shape = JsonFormat.Shape.STRING) public Long operationId;
        @JsonFormat(shape = JsonFormat.Shape.STRING) public Long publicationId;
        @JsonFormat(shape = JsonFormat.Shape.STRING) public Long operationRowVersion;
        public String operationKey;
        public String action;
        public String status;
        @JsonFormat(shape = JsonFormat.Shape.STRING) public Long contentVersion;
        @JsonFormat(shape = JsonFormat.Shape.STRING) public Long expectedRowVersion;
        @JsonFormat(shape = JsonFormat.Shape.STRING) public Long currentRowVersion;
        public String currentStatus;
        @JsonFormat(shape = JsonFormat.Shape.STRING) public Long parentOperationId;
        @JsonFormat(shape = JsonFormat.Shape.STRING) public Long checkId;
        public int attemptCount;
        public String errorCode;
        public String errorMessage;
        public String nextAction;
        public boolean retryable;
        public OperationResult result;
        public Command resumeCommand;
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC") public Date createdAt;
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC") public Date finishedAt;
    }
    public static class OperationResult {
        public String publicationStatus;
        @JsonFormat(shape = JsonFormat.Shape.STRING) public Long rowVersion;
        public String deliveryMode;
        public String shareState;
        public String shareUrl;
        public List<QuestionResult> questionResults = new ArrayList<>();
    }
    public record QuestionResult(String questionId, String mode, String beforeRowVersion,
            String afterRowVersion, String contentHash, String approvalOperationId, String policyVersion) { }
    public record SaleResult(String publicationId, String status, String rowVersion, String contentVersion,
            OperationReceipt sale, OperationReceipt generation, String shareUrl, String deliveryMode) { }
    public record Download(String url, int expiresInSeconds, String fileName, String assetId, String sha256) { }
    public record Snapshot(Input publication, CheckResult check, Command originalCommand, String deliveryMode) {
        public Snapshot(Input publication, CheckResult check, Command originalCommand) { this(publication, check, originalCommand, null); }
        public Snapshot(Input publication, CheckResult check) { this(publication, check, null); }
    }
    public static class ProductFact {
        @JsonFormat(shape = JsonFormat.Shape.STRING) public Long id;
        public String title;
        @JsonFormat(shape = JsonFormat.Shape.STRING) public Long rowVersion;
        @JsonFormat(shape = JsonFormat.Shape.STRING) public Long assetId;
        public Integer versionNo;
        public String sha256;
        public String fileName;
        public String mimeType;
        public String resourceType;
    }
    public static class FrozenQuestion {
        public Long productId;
        public Integer seq;
        public Long questionId;
    }
    public static class RelationQuery {
        public Long publicationId;
        public List<Long> frozenSourceIds = new ArrayList<>();
        public List<Long> pageIds = new ArrayList<>();
        public String objectType;
        public Long objectId;
        public int pageNum = 1;
        public int pageSize = 20;
        public int offset;
    }
    public static class Relation {
        @JsonFormat(shape = JsonFormat.Shape.STRING) public Long id;
        public String objectType;
        public String name;
        @JsonFormat(shape = JsonFormat.Shape.STRING) public Long rowVersion;
        public String questionType;
        public String stemHtml;
        public String answerHtml;
        @JsonFormat(shape = JsonFormat.Shape.STRING) public Long assetId;
        public String contentUrl;
        public String relationKind;
        public List<String> relationKinds = new ArrayList<>();
        public String subject;
        public String sourceKind;
        public String textbookVersion;
        public String grade;
        public String term;
        public String edition;
        public List<RelationFile> files = new ArrayList<>();
        public List<OutlineLocation> nodes = new ArrayList<>();
        public List<RelationLink> links = new ArrayList<>();
        public List<Issue> missingItems = new ArrayList<>();
    }
    public static class RelationFile {
        public String objectType;
        @JsonFormat(shape = JsonFormat.Shape.STRING) public Long objectId;
        @JsonFormat(shape = JsonFormat.Shape.STRING) public Long assetId;
        public String fileName;
        public String mimeType;
        @JsonFormat(shape = JsonFormat.Shape.STRING) public Long byteSize;
        public String role;
        public String contentUrl;
    }
    public static class OutlineLocation {
        @JsonFormat(shape = JsonFormat.Shape.STRING) public Long kpId;
        @JsonFormat(shape = JsonFormat.Shape.STRING) public Long outlineId;
        @JsonFormat(shape = JsonFormat.Shape.STRING) public Long nodeId;
        @JsonFormat(shape = JsonFormat.Shape.STRING) public Long parentId;
        @JsonFormat(shape = JsonFormat.Shape.STRING) public Long rowVersion;
        public String title;
        public Integer sortOrder;
    }
    public record RelationLink(String type, String fromType, String fromId, String toType, String toId) { }
    public record RelationCounts(long productCount, long questionCount, long sourceCount, long kpCount, long outlineCount) { }
    public record RelationRef(String objectType, String objectId, String name, String rowVersion, List<String> relationKinds) { }
    public record RelationSummary(RelationCounts counts, List<RelationRef> items, List<Issue> missingItems) { }
    public record PublicImage(String assetId, int seq, String caption, String mimeType,
            Integer width, Integer height, String byteSize, String contentUrl) { }
    public record PublicProduct(String productId, String title, String versionLabel, String assetId,
            String fileName, String mimeType, String contentUrl) { }
    public record PublicFile(String productId, String remoteFileName) { }
    public record PublicShare(String shareUrl, String extractionCode, List<PublicFile> files) { }
    public record PublicPublication(String shareCode, String contentVersion, String title, String status,
            String postTitle, String postBody, String listingDescription, List<PublicImage> images,
            List<PublicProduct> products, List<PublicShare> baiduShares, RelationCounts relationCounts) { }
    public record Materials(String kind, String contentVersion, Integer total, List<PublicImage> images, String text) { }
    public record ErrorDetail(String errorCode, boolean retryable, List<Issue> issues, OperationReceipt operation) { }
}
