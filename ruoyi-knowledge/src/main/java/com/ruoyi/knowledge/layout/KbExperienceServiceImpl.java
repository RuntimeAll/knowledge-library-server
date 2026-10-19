package com.ruoyi.knowledge.layout;

import static com.ruoyi.knowledge.service.impl.KnowledgeRules.*;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.knowledge.mapper.KbLayoutMapper;
import com.ruoyi.knowledge.service.impl.KnowledgeDictionary;
import com.ruoyi.knowledge.storage.IKbAssetService;

import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class KbExperienceServiceImpl implements IKbExperienceService {
    private final KbLayoutMapper mapper;
    private final IKbAssetService assets;
    private final KnowledgeDictionary dict;

    public KbExperienceServiceImpl(
            KbLayoutMapper mapper, IKbAssetService assets, KnowledgeDictionary dict) {
        this.mapper = mapper;
        this.assets = assets;
        this.dict = dict;
    }

    public List<KbExperience> list(KbExperience q) {
        return mapper.experiences(q);
    }

    public KbExperience get(Long id) {
        return found(mapper.experience(id));
    }

    private void validate(KbExperience d) {
        d.setName(text(d.getName(), "经验名称", 200));
        d.setExperienceType(choice(d.getExperienceType(), "LAYOUT", "LAYOUT"));
        d.setStatus(choice(d.getStatus(), "ACTIVE", "ACTIVE", "DISABLED"));
        dict.validate("kb_experience_type", d.getExperienceType(), "经验类型");
        dict.validate("kb_experience_status", d.getStatus(), "经验状态");
        KbLayoutProtocol.limit(d.getDescription(), 10000, "说明");
        KbLayoutProtocol.limit(d.getUsageNotes(), 20000, "使用经验");
    }

    @Transactional
    public KbExperience create(KbExperience d) {
        validate(d);
        d.setCode(KbLayoutProtocol.key(d.getCode(), 64));
        d.setId(null);
        d.setCreateBy(SecurityUtils.getUsername());
        mapper.insertExperience(d);
        return get(d.getId());
    }

    @Transactional
    public KbExperience update(KbExperience d) {
        var old = found(mapper.lockExperience(d.getId()));
        com.ruoyi.knowledge.service.impl.KnowledgeRules.version(
                d.getRowVersion(), old.getRowVersion());
        if (d.getCode() != null && !d.getCode().equals(old.getCode()))
            throw new ServiceException("经验code不能更换", 409);
        d.setCode(old.getCode());
        validate(d);
        d.setUpdateBy(SecurityUtils.getUsername());
        changed(mapper.updateExperience(d));
        return get(d.getId());
    }

    @Transactional
    public void delete(Long[] ids) {
        for (Long id : ids) {
            found(mapper.lockExperience(id));
            var versions = mapper.versions(id, null);
            for (var v : versions) {
                unreferenced(mapper.versionJobs(v.getId()), "模板版本仍被生成任务引用");
                mapper.deleteVersionAssets(v.getId());
                mapper.deleteVersion(v.getId());
            }
            mapper.deleteExperience(id);
        }
    }

    public List<KbExperienceVersion> versions(Long id, String status) {
        return mapper.versions(id, status);
    }

    public List<KbExperienceVersion> options() {
        return mapper.options();
    }

    public static KbExperienceVersion decode(KbExperienceVersion v) {
        v.setFieldSchema(JSON.parseArray(v.getFieldSchemaJson(), KbTemplateField.class));
        v.setDefaultValues(JSON.parseObject(v.getDefaultValuesJson()));
        v.setDefinition(JSON.parseObject(v.getDefinitionJson(), KbTemplateDefinition.class));
        return v;
    }

    public KbExperienceVersion version(Long id) {
        var v = decode(found(mapper.version(id)));
        v.setAssets(mapper.versionAssets(id));
        return v;
    }

    public KbExperienceVersion usableVersion(Long id) {
        var v = version(id);
        if (!"PUBLISHED".equals(v.getStatus())
                || !"ACTIVE".equals(get(v.getExperienceId()).getStatus()))
            throw new ServiceException("模板未发布或已停用", 409);
        return v;
    }

    private void prepare(KbExperienceVersion v) {
        KbLayoutProtocol.template(v);
        Set<String> keys = new HashSet<>();
        Set<Long> ids = new HashSet<>();
        for (var a :
                KbLayoutProtocol.list(v.getAssets()).stream()
                        .sorted(
                                Comparator.comparing(
                                        KbLayoutAsset::getAssetId,
                                        Comparator.nullsFirst(Comparator.naturalOrder())))
                        .toList()) {
            KbLayoutProtocol.key(a.getResourceKey(), 128);
            if (a.getResourceKey().matches("^(question|output|source|template):.*"))
                throw new ServiceException("模板资源不能使用系统保留前缀", 400);
            if (!keys.add(a.getResourceKey())) throw new ServiceException("模板资源标识重复", 400);
            var stored = assets.requireReady(a.getAssetId());
            if (!stored.getContentType().startsWith("image/"))
                throw new ServiceException("模板资源只支持图片", 400);
            a.setAssetRole("TEMPLATE_IMAGE");
            a.setSha256(stored.getSha256());
            a.setMimeType(stored.getContentType());
            ids.add(a.getAssetId());
        }
        for (var image : Jsoup.parseBodyFragment(v.getDefinition().getHtml()).select("img")) {
            if (image.hasAttr("data-kb-resource") && !keys.contains(image.attr("data-kb-resource")))
                throw new ServiceException("模板缺少资源：" + image.attr("data-kb-resource"), 400);
            if (image.hasAttr("data-asset-id")) {
                try {
                    if (!ids.contains(Long.valueOf(image.attr("data-asset-id"))))
                        throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    throw new ServiceException("模板图片资产未登记", 400);
                }
            }
        }
        v.setFieldSchemaJson(KbLayoutProtocol.json(v.getFieldSchema()));
        v.setDefaultValuesJson(KbLayoutProtocol.json(v.getDefaultValues()));
        v.setDefinitionJson(KbLayoutProtocol.json(v.getDefinition()));
        v.setDefinitionHash(
                KbLayoutProtocol.hash(
                        List.of(
                                v.getFieldSchema(),
                                v.getDefaultValues(),
                                v.getDefinition(),
                                KbLayoutProtocol.list(v.getAssets()).stream()
                                        .sorted(Comparator.comparing(KbLayoutAsset::getResourceKey))
                                        .map(
                                                a ->
                                                        new ResourceIdentity(
                                                                a.getResourceKey(),
                                                                a.getAssetId(),
                                                                a.getAssetRole(),
                                                                a.getSha256()))
                                        .toList())));
    }

    private record ResourceIdentity(
            String resourceKey, Long assetId, String assetRole, String sha256) {}

    private void saveAssets(KbExperienceVersion v) {
        mapper.deleteVersionAssets(v.getId());
        for (var a : KbLayoutProtocol.list(v.getAssets())) {
            a.setVersionId(v.getId());
            mapper.insertVersionAsset(a);
        }
    }

    @Transactional
    public KbExperienceVersion createVersion(Long id, KbExperienceVersion d) {
        found(mapper.lockExperience(id));
        prepare(d);
        d.setId(null);
        d.setExperienceId(id);
        d.setVersionNo(mapper.nextVersion(id));
        d.setCreateBy(SecurityUtils.getUsername());
        mapper.insertVersion(d);
        saveAssets(d);
        return version(d.getId());
    }

    @Transactional
    public KbExperienceVersion updateVersion(Long id, KbExperienceVersion d) {
        var old = found(mapper.lockVersion(id));
        com.ruoyi.knowledge.service.impl.KnowledgeRules.version(
                d.getRowVersion(), old.getRowVersion());
        if (!"DRAFT".equals(old.getStatus())) throw new ServiceException("已发布模板不可修改，请创建新版本", 409);
        d.setId(id);
        d.setExperienceId(old.getExperienceId());
        prepare(d);
        d.setUpdateBy(SecurityUtils.getUsername());
        changed(mapper.updateVersion(d));
        saveAssets(d);
        return version(id);
    }

    @Transactional
    public KbExperienceVersion publish(Long id, Long rowVersion, String actor) {
        var old = found(mapper.lockVersion(id));
        com.ruoyi.knowledge.service.impl.KnowledgeRules.version(rowVersion, old.getRowVersion());
        if (!"DRAFT".equals(old.getStatus())) throw new ServiceException("只能发布草稿版本", 409);
        var v = version(id);
        prepare(v);
        dict.validate("kb_experience_version_status", "PUBLISHED", "版本状态");
        v.setPublishBy(actor);
        changed(mapper.publishVersion(v));
        return version(id);
    }

    @Transactional
    public void deleteVersion(Long id) {
        found(mapper.lockVersion(id));
        unreferenced(mapper.versionJobs(id), "模板版本仍被生成任务引用");
        mapper.deleteVersionAssets(id);
        mapper.deleteVersion(id);
    }
}
