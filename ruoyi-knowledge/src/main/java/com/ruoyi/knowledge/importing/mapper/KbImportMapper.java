package com.ruoyi.knowledge.importing.mapper;

import java.util.List;
import com.ruoyi.knowledge.importing.domain.KbImportBatch;
import com.ruoyi.knowledge.importing.domain.KbImportItem;
import com.ruoyi.knowledge.importing.domain.KbImportIdentity;
import com.ruoyi.knowledge.importing.domain.KbImportResource;
import org.apache.ibatis.annotations.Param;

public interface KbImportMapper {
    KbImportBatch selectBatch(Long batchId);
    KbImportBatch selectByKey(@Param("createBy") String createBy, @Param("idempotencyKey") String idempotencyKey);
    int insertBatch(KbImportBatch batch);
    int updateBatch(KbImportBatch batch);
    List<KbImportItem> selectItems(Long batchId);
    int insertItem(KbImportItem item);
    KbImportIdentity selectIdentity(@Param("createBy") String createBy, @Param("clientKey") String clientKey);
    int insertIdentity(KbImportIdentity identity);
    int updateIdentity(KbImportIdentity identity);
    String selectInstanceId();
    int claim(@Param("batchId") Long batchId, @Param("token") String token);
    int renewClaim(@Param("batchId") Long batchId, @Param("token") String token);
    int releaseClaim(@Param("batchId") Long batchId, @Param("token") String token);
    KbImportBatch lockOwnedBatch(@Param("batchId") Long batchId, @Param("token") String token);
    KbImportItem selectItem(@Param("batchId") Long batchId, @Param("itemOrder") int itemOrder);
    List<KbImportResource> selectResources(Long batchId);
    int insertResource(KbImportResource resource);
}
