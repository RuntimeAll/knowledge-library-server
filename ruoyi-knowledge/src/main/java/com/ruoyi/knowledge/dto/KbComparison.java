package com.ruoyi.knowledge.dto;

import com.ruoyi.knowledge.domain.KbComparisonRow;
import com.ruoyi.knowledge.domain.KbKp;

import java.util.List;

/** 对照事实、来源覆盖及训练考点的独立分页结果。 */
public class KbComparison {
    private List<KbComparisonRow> matches;
    private List<KbComparisonRow> coverage;
    private String notice;
    private long matchTotal;
    private long coverageTotal;
    private List<KbKp> points;
    private long pointTotal;

    public long getMatchTotal() {
        return matchTotal;
    }

    public void setMatchTotal(long matchTotal) {
        this.matchTotal = matchTotal;
    }

    public long getCoverageTotal() {
        return coverageTotal;
    }

    public void setCoverageTotal(long coverageTotal) {
        this.coverageTotal = coverageTotal;
    }

    public List<KbKp> getPoints() {
        return points;
    }

    public void setPoints(List<KbKp> points) {
        this.points = points;
    }

    public long getPointTotal() {
        return pointTotal;
    }

    public void setPointTotal(long pointTotal) {
        this.pointTotal = pointTotal;
    }

    public List<KbComparisonRow> getMatches() {
        return matches;
    }

    public void setMatches(List<KbComparisonRow> matches) {
        this.matches = matches;
    }

    public List<KbComparisonRow> getCoverage() {
        return coverage;
    }

    public void setCoverage(List<KbComparisonRow> coverage) {
        this.coverage = coverage;
    }

    public String getNotice() {
        return notice;
    }

    public void setNotice(String notice) {
        this.notice = notice;
    }
}
