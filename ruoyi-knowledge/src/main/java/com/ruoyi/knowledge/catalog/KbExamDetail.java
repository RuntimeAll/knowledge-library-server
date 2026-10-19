package com.ruoyi.knowledge.catalog;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class KbExamDetail extends CatalogFields {
    private String examPurpose;
    private String grade;
    private String schoolName;
    private String scopeNote;
    private String entranceStage;
    private String organizerLevel;
    private String paperNature;
    private String term;
    private String sessionLabel;
    private String paperCode;
    private String organizerName;
    private String authenticityStatus;
    private String verificationNote;
    private String verifiedBy;
    private String identityFingerprint;
    private Integer examYear;
    private Integer academicYearStart;
    private LocalDate examDate;
    private Long evidenceAssetId;
    private LocalDateTime verifiedAt;
    private Boolean legacyVerification;

    public String getExamPurpose() {
        return examPurpose;
    }

    public void setExamPurpose(String value) {
        examPurpose = value;
        supplied("examPurpose");
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String value) {
        grade = value;
        supplied("grade");
    }

    public String getSchoolName() {
        return schoolName;
    }

    public void setSchoolName(String value) {
        schoolName = value;
        supplied("schoolName");
    }

    public String getScopeNote() {
        return scopeNote;
    }

    public void setScopeNote(String value) {
        scopeNote = value;
        supplied("scopeNote");
    }

    public String getEntranceStage() {
        return entranceStage;
    }

    public void setEntranceStage(String value) {
        entranceStage = value;
        supplied("entranceStage");
    }

    public String getOrganizerLevel() {
        return organizerLevel;
    }

    public void setOrganizerLevel(String value) {
        organizerLevel = value;
        supplied("organizerLevel");
    }

    public String getPaperNature() {
        return paperNature;
    }

    public void setPaperNature(String value) {
        paperNature = value;
        supplied("paperNature");
    }

    public String getTerm() {
        return term;
    }

    public void setTerm(String value) {
        term = value;
        supplied("term");
    }

    public String getSessionLabel() {
        return sessionLabel;
    }

    public void setSessionLabel(String value) {
        sessionLabel = value;
        supplied("sessionLabel");
    }

    public String getPaperCode() {
        return paperCode;
    }

    public void setPaperCode(String value) {
        paperCode = value;
        supplied("paperCode");
    }

    public String getOrganizerName() {
        return organizerName;
    }

    public void setOrganizerName(String value) {
        organizerName = value;
        supplied("organizerName");
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

    public String getVerifiedBy() {
        return verifiedBy;
    }

    public void setVerifiedBy(String value) {
        verifiedBy = value;
        supplied("verifiedBy");
    }

    public String getIdentityFingerprint() {
        return identityFingerprint;
    }

    public void setIdentityFingerprint(String value) {
        identityFingerprint = value;
        supplied("identityFingerprint");
    }

    public Integer getExamYear() {
        return examYear;
    }

    public void setExamYear(Integer value) {
        examYear = value;
        supplied("examYear");
    }

    public Integer getAcademicYearStart() {
        return academicYearStart;
    }

    public void setAcademicYearStart(Integer value) {
        academicYearStart = value;
        supplied("academicYearStart");
    }

    public LocalDate getExamDate() {
        return examDate;
    }

    public void setExamDate(LocalDate value) {
        examDate = value;
        supplied("examDate");
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

    public LocalDateTime getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(LocalDateTime value) {
        verifiedAt = value;
        supplied("verifiedAt");
    }

    public Boolean getLegacyVerification() {
        return legacyVerification;
    }

    public void setLegacyVerification(Boolean value) {
        legacyVerification = value;
        supplied("legacyVerification");
    }

    public void apply(KbExamDetail patch) {
        if (patch.has("examPurpose")) setExamPurpose(patch.getExamPurpose());
        if (patch.has("grade")) setGrade(patch.getGrade());
        if (patch.has("schoolName")) setSchoolName(patch.getSchoolName());
        if (patch.has("scopeNote")) setScopeNote(patch.getScopeNote());
        if (patch.has("entranceStage")) setEntranceStage(patch.getEntranceStage());
        if (patch.has("organizerLevel")) setOrganizerLevel(patch.getOrganizerLevel());
        if (patch.has("paperNature")) setPaperNature(patch.getPaperNature());
        if (patch.has("term")) setTerm(patch.getTerm());
        if (patch.has("sessionLabel")) setSessionLabel(patch.getSessionLabel());
        if (patch.has("paperCode")) setPaperCode(patch.getPaperCode());
        if (patch.has("organizerName")) setOrganizerName(patch.getOrganizerName());
        if (patch.has("verificationNote")) setVerificationNote(patch.getVerificationNote());
        if (patch.has("examYear")) setExamYear(patch.getExamYear());
        if (patch.has("academicYearStart")) setAcademicYearStart(patch.getAcademicYearStart());
        if (patch.has("examDate")) setExamDate(patch.getExamDate());
        if (patch.has("evidenceAssetId")) setEvidenceAssetId(patch.getEvidenceAssetId());
    }
}
