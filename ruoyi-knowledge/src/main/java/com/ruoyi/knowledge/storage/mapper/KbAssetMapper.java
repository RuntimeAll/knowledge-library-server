package com.ruoyi.knowledge.storage.mapper;

import java.util.List;
import com.ruoyi.knowledge.storage.domain.KbAsset;
import org.apache.ibatis.annotations.Param;

public interface KbAssetMapper {
    KbAsset selectById(Long assetId);
    KbAsset selectByIdForUpdate(Long assetId);
    KbAsset selectByExternalKey(@Param("createBy") String createBy, @Param("externalKey") String externalKey);
    int claimRetry(@Param("assetId") Long assetId, @Param("uploadToken") String uploadToken);
    int finishUpload(KbAsset asset);
    List<KbAsset> selectByRunId(String testRunId);
    int insert(KbAsset asset);
    int updateState(KbAsset asset);
    int countReferences(Long assetId);
    int markDeletePending(@Param("assetId") Long assetId, @Param("updateBy") String updateBy);
}
