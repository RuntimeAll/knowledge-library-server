package com.ruoyi.knowledge.publication;

import java.util.List;

/** Stable publication-specific error independent of the framework's HTTP envelope. */
public final class PublicationException extends RuntimeException {
    private final int code;
    private final PublicationModels.ErrorDetail detail;
    public PublicationException(int code, String errorCode, String message) {
        this(code, errorCode, message, List.of());
    }
    public PublicationException(int code, String errorCode, String message, List<PublicationModels.Issue> issues) {
        super(message);
        this.code = code;
        this.detail = new PublicationModels.ErrorDetail(errorCode, code == 503, issues, null);
    }
    public int getCode() { return code; }
    public PublicationModels.ErrorDetail getDetail() { return detail; }
}
