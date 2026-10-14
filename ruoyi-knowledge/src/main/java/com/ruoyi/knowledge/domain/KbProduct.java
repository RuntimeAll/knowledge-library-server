package com.ruoyi.knowledge.domain;

import com.ruoyi.common.core.domain.BaseEntity;

/** 不可变成品文件的版本记录与创建时溯源。 */
public class KbProduct extends BaseEntity {
    private Long id;
    private Long rowVersion;
    private String title;
    private String snapshotJson;
    private Long materialId;
    private Long assetId;
    private Long previousProductId;
    private Long rootProductId;
    private Integer versionNo;

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getRowVersion() {
        return rowVersion;
    }

    public void setRowVersion(Long rowVersion) {
        this.rowVersion = rowVersion;
    }

    public String getSnapshotJson() {
        return snapshotJson;
    }

    public void setSnapshotJson(String value) {
        snapshotJson = value;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getMaterialId() {
        return materialId;
    }

    public void setMaterialId(Long materialId) {
        this.materialId = materialId;
    }

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getAssetId() {
        return assetId;
    }

    public void setAssetId(Long assetId) {
        this.assetId = assetId;
    }

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getPreviousProductId() {
        return previousProductId;
    }

    public void setPreviousProductId(Long previousProductId) {
        this.previousProductId = previousProductId;
    }

    @com.fasterxml.jackson.annotation.JsonFormat(
            shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public Long getRootProductId() {
        return rootProductId;
    }

    public void setRootProductId(Long rootProductId) {
        this.rootProductId = rootProductId;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }
}
