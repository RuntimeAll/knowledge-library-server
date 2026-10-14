package com.ruoyi.knowledge.service.impl;

import static com.ruoyi.knowledge.service.impl.KnowledgeRules.changed;
import static com.ruoyi.knowledge.service.impl.KnowledgeRules.found;
import static com.ruoyi.knowledge.service.impl.KnowledgeRules.text;
import static com.ruoyi.knowledge.service.impl.KnowledgeRules.unreferenced;
import static com.ruoyi.knowledge.service.impl.KnowledgeRules.version;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.knowledge.domain.KbMaterial;
import com.ruoyi.knowledge.domain.KbProduct;
import com.ruoyi.knowledge.dto.KbProductSnapshot;
import com.ruoyi.knowledge.mapper.KbMaterialMapper;
import com.ruoyi.knowledge.mapper.KbProductMapper;
import com.ruoyi.knowledge.service.IKbMaterialService;
import com.ruoyi.knowledge.service.IKbProductService;
import com.ruoyi.knowledge.storage.IKbAssetService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 成品文件不可变，修改版使用新的记录与资产。 */
@Service
public class KbProductServiceImpl implements IKbProductService {
    private final KbProductMapper mapper;
    private final KbMaterialMapper materials;
    private final IKbAssetService assets;
    private final IKbMaterialService materialService;

    public KbProductServiceImpl(
            KbProductMapper mapper,
            KbMaterialMapper materials,
            IKbAssetService assets,
            IKbMaterialService materialService) {
        this.mapper = mapper;
        this.materials = materials;
        this.assets = assets;
        this.materialService = materialService;
    }

    public List<KbProduct> list(KbProduct query) {
        return mapper.selectList(query);
    }

    public KbProduct get(Long id) {
        return found(mapper.selectById(id));
    }

    public List<KbProduct> versions(Long id) {
        KbProduct data = get(id);
        KbProduct query = new KbProduct();
        query.setRootProductId(data.getRootProductId());
        return mapper.selectList(query);
    }

    @Transactional
    public KbProduct create(KbProduct data) {
        validate(data);
        if (data.getMaterialId() != null) {
            KbMaterial material = materialService.get(data.getMaterialId());
            KbProductSnapshot snapshot =
                    new KbProductSnapshot(
                            material.getId().toString(),
                            material.getRowVersion().toString(),
                            material.getTitle(),
                            material.getQuestions().stream()
                                    .map(
                                            q ->
                                                    new KbProductSnapshot.QuestionVersion(
                                                            q.getId().toString(),
                                                            q.getRowVersion().toString()))
                                    .toList());
            data.setSnapshotJson(com.alibaba.fastjson2.JSON.toJSONString(snapshot));
        } else data.setSnapshotJson(null);
        if (data.getPreviousProductId() != null) {
            KbProduct previous = found(mapper.selectById(data.getPreviousProductId()));
            found(mapper.lockById(previous.getRootProductId()));
            data.setRootProductId(previous.getRootProductId());
            data.setVersionNo(mapper.nextVersion(previous.getRootProductId()));
        } else {
            data.setRootProductId(null);
            data.setVersionNo(1);
        }
        mapper.insert(data);
        if (data.getRootProductId() == null) mapper.initializeRoot(data.getId());
        return get(data.getId());
    }

    @Transactional
    public KbProduct update(KbProduct data) {
        KbProduct old = found(mapper.lockById(data.getId()));
        version(data.getRowVersion(), old.getRowVersion());
        if (data.getAssetId() != null && !data.getAssetId().equals(old.getAssetId()))
            throw new ServiceException("成品文件已冻结，请另存新版本", 409);
        old.setTitle(data.getTitle());
        old.setRemark(data.getRemark());
        old.setUpdateBy(data.getUpdateBy());
        old.setMaterialId(data.getMaterialId());
        validate(old);
        changed(mapper.update(old));
        return get(old.getId());
    }

    @Transactional
    public void delete(Long[] values) {
        for (Long id : values) {
            found(mapper.lockById(id));
            unreferenced(mapper.countNewerVersions(id), "该成品还有后续版本引用，请从最新版本处理");
            mapper.deleteById(id);
        }
    }

    private void validate(KbProduct data) {
        data.setTitle(text(data.getTitle(), "成品标题", 300));
        assets.requireReady(data.getAssetId());
        if (data.getMaterialId() != null) found(materials.lockById(data.getMaterialId()));
    }
}
