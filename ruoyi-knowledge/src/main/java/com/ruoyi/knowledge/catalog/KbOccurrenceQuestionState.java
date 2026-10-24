package com.ruoyi.knowledge.catalog;

/** Only the current question fields required to evaluate an occurrence's flags. */
public class KbOccurrenceQuestionState {
    private Long occurrenceId;
    private Long questionId;
    private String questionTitle;
    private String stemHash;
    private String contentKind;
    public Long getOccurrenceId() { return occurrenceId; }
    public void setOccurrenceId(Long value) { occurrenceId = value; }
    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long value) { questionId = value; }
    public String getQuestionTitle() { return questionTitle; }
    public void setQuestionTitle(String value) { questionTitle = value; }
    public String getStemHash() { return stemHash; }
    public void setStemHash(String value) { stemHash = value; }
    public String getContentKind() { return contentKind; }
    public void setContentKind(String value) { contentKind = value; }
}
