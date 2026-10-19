package com.ruoyi.knowledge.catalog;


public class KbExamVerificationCommand extends CatalogFields {
    private Long rowVersion;
    private String authenticityStatus;
    private String verificationNote;
    private Long evidenceAssetId;

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getRowVersion() {
        return rowVersion;
    }

    public void setRowVersion(Long value) {
        rowVersion = value;
        supplied("rowVersion");
    }

    public String getAuthenticityStatus() {
        return authenticityStatus;
    }

    public void setAuthenticityStatus(String value) {
        authenticityStatus = value;
        supplied("authenticityStatus");
    }

    public String getVerificationNote() {
        return verificationNote;
    }

    public void setVerificationNote(String value) {
        verificationNote = value;
        supplied("verificationNote");
    }

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getEvidenceAssetId() {
        return evidenceAssetId;
    }

    public void setEvidenceAssetId(Long value) {
        evidenceAssetId = value;
        supplied("evidenceAssetId");
    }
}
