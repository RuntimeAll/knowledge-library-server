package com.ruoyi.knowledge.dto;

import com.ruoyi.knowledge.domain.KbDerivation;
import com.ruoyi.knowledge.domain.KbOccurrence;

import java.util.List;

/** 当前题目的直接血缘与祖先来源追溯。 */
public class KbLineage {
    private List<KbDerivation> parents;
    private List<KbDerivation> children;
    private List<KbOccurrence> ancestorSources;
    private String notice;

    public List<KbDerivation> getParents() {
        return parents;
    }

    public void setParents(List<KbDerivation> parents) {
        this.parents = parents;
    }

    public List<KbDerivation> getChildren() {
        return children;
    }

    public void setChildren(List<KbDerivation> children) {
        this.children = children;
    }

    public List<KbOccurrence> getAncestorSources() {
        return ancestorSources;
    }

    public void setAncestorSources(List<KbOccurrence> ancestorSources) {
        this.ancestorSources = ancestorSources;
    }

    public String getNotice() {
        return notice;
    }

    public void setNotice(String notice) {
        this.notice = notice;
    }
}
