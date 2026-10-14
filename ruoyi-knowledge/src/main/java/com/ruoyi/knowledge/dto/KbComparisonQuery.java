package com.ruoyi.knowledge.dto;

import java.util.List;

/** 对照查询的内部参数；分页由 SQL 执行。 */
public class KbComparisonQuery {
    private List<Long> sourceIds;
    private List<KbPointMatch> pointMatches;
    private List<Long> pointIds;
    private int limit;
    private long offset;

    public List<Long> getSourceIds() {
        return sourceIds;
    }

    public void setSourceIds(List<Long> sourceIds) {
        this.sourceIds = sourceIds;
    }

    public List<KbPointMatch> getPointMatches() {
        return pointMatches;
    }

    public void setPointMatches(List<KbPointMatch> pointMatches) {
        this.pointMatches = pointMatches;
    }

    public List<Long> getPointIds() {
        return pointIds;
    }

    public void setPointIds(List<Long> pointIds) {
        this.pointIds = pointIds;
    }

    public int getLimit() {
        return limit;
    }

    public long getOffset() {
        return offset;
    }

    public void page(int number, int size) {
        limit = Math.min(100, Math.max(1, size));
        offset = (long) (Math.max(1, number) - 1) * limit;
    }
}
