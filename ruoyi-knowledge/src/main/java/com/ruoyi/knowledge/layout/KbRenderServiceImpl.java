package com.ruoyi.knowledge.layout;

import static com.ruoyi.knowledge.service.impl.KnowledgeRules.*;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.knowledge.catalog.*;
import com.ruoyi.knowledge.domain.*;
import com.ruoyi.knowledge.mapper.*;
import com.ruoyi.knowledge.service.*;
import com.ruoyi.knowledge.storage.IKbAssetService;

import org.jsoup.Jsoup;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** Owns the short submit transaction: current records become immutable generation inputs. */
@Service
public class KbRenderServiceImpl implements IKbRenderService {
    private final KbLayoutMapper mapper;
    private final IKbExperienceService experiences;
    private final IKbQuestionService questions;
    private final KbQuestionMapper questionMapper;
    private final KbMaterialMapper materials;
    private final IKbMaterialService materialService;
    private final KbOccurrenceMapper occurrences;
    private final IKbCatalogService catalog;
    private final KbProductMapper products;
    private final IKbProductService productService;
    private final IKbAssetService assets;
    private final KbKpMapper points;
    private final IKbSourceService sources;

    public KbRenderServiceImpl(
            KbLayoutMapper mapper,
            IKbExperienceService experiences,
            IKbQuestionService questions,
            KbQuestionMapper questionMapper,
            KbMaterialMapper materials,
            IKbMaterialService materialService,
            KbOccurrenceMapper occurrences,
            IKbCatalogService catalog,
            IKbAssetService assets,
            KbKpMapper points,
            IKbSourceService sources,
            KbProductMapper products,
            IKbProductService productService) {
        this.mapper = mapper;
        this.experiences = experiences;
        this.questions = questions;
        this.questionMapper = questionMapper;
        this.materials = materials;
        this.materialService = materialService;
        this.occurrences = occurrences;
        this.catalog = catalog;
        this.assets = assets;
        this.points = points;
        this.sources = sources;
        this.products = products;
        this.productService = productService;
    }

    private record Prepared(
            KbRenderRequest request,
            KbExperienceVersion template,
            KbProduct product,
            List<KbRenderItem> items,
            List<KbLayoutAsset> assets) {}

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public KbRenderValidation validate(KbRenderRequest request) {
        var p = prepare(request);
        var v = new KbRenderValidation();
        v.setValid(true);
        v.setPositionCount(p.items.size());
        v.setUniqueQuestionCount(
                (int) p.items.stream().map(KbRenderItem::getQuestionId).distinct().count());
        v.setAssetCount(p.assets.size());
        v.setWarnings(
                p.request.getOutlineScopes().stream()
                        .filter(x -> !"CONFIRMED".equals(x.getOutlineStatus()))
                        .map(x -> "教材大纲 " + x.getOutlineId() + " 仍待核实；生成不代表教材核实")
                        .toList());
        v.setNormalizedRequest(p.request);
        return v;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public KbRenderJob submit(KbRenderRequest request) {
        String key = text(request.getIdempotencyKey(), "幂等键", 128);
        String hash = KbLayoutProtocol.hash(request);
        Long actor = SecurityUtils.getUserId();
        var prior = mapper.ownKey(actor, key);
        if (prior != null) return repeat(prior, hash);
        var p = prepare(request);
        var job = new KbRenderJob();
        job.setTemplateVersionId(p.template.getId());
        job.setActorUserId(actor);
        job.setIdempotencyKey(key);
        job.setRequestHash(hash);
        job.setMaterialId(p.request.getMaterialId());
        job.setMaterialRowVersion(p.request.getMaterialRowVersion());
        job.setCreateBy(SecurityUtils.getUsername());
        var frozen = new KbRenderFrozenConfig();
        frozen.setRequest(p.request);
        frozen.setProductCatalog(p.product);
        job.setConfigSnapshotJson(KbLayoutProtocol.json(frozen));
        job.setTemplateSnapshotJson(KbLayoutProtocol.json(p.template));
        try {
            mapper.insertJob(job);
        } catch (DuplicateKeyException e) {
            prior = mapper.lockOwnKey(actor, key);
            if (prior == null) throw e;
            return repeat(prior, hash);
        }
        for (var item : p.items) {
            item.setJobId(job.getId());
            mapper.insertItem(item);
        }
        for (var a : p.assets) {
            a.setJobId(job.getId());
            mapper.insertJobAsset(a);
        }
        return get(job.getId());
    }

    private KbRenderJob repeat(KbRenderJob old, String hash) {
        if (!hash.equals(old.getRequestHash())) throw new ServiceException("生成幂等键已用于不同配置或题单", 409);
        return get(old.getId());
    }

    private Prepared prepare(KbRenderRequest original) {
        KbRenderRequest r =
                JSON.parseObject(KbLayoutProtocol.json(original), KbRenderRequest.class);
        if (r.getTemplateVersionId() == null)
            throw new ServiceException("缺少templateVersionId", 400);
        found(mapper.lockVersion(r.getTemplateVersionId()));
        var template = experiences.usableVersion(r.getTemplateVersionId());
        r.setParameters(KbLayoutProtocol.parameters(template, r.getParameters(), true));
        if (r.getParameters().getString("title") == null
                || r.getParameters().getString("title").isBlank())
            throw new ServiceException("缺少成品标题title", 400);
        text(r.getParameters().getString("title"), "成品标题", 260);
        assets.validateTestRunId(r.getTestRunId());
        if (KbLayoutProtocol.list(r.getOutputRoles()).isEmpty()
                || r.getOutputRoles().size() > 3
                || new HashSet<>(r.getOutputRoles()).size() != r.getOutputRoles().size())
            throw new ServiceException("输出角色不能为空或重复", 400);
        for (String role : r.getOutputRoles())
            choice(role, null, "STUDENT", "INLINE_ANSWER", "ANSWER_ONLY");
        Set<String> previousRoles = new HashSet<>();
        for (var previous : KbLayoutProtocol.list(r.getPreviousOutputs())) {
            if (previous.productId() == null
                    || !r.getOutputRoles().contains(previous.outputKey())
                    || !previousRoles.add(previous.outputKey()))
                throw new ServiceException("成品版本关联无效或重复", 400);
            found(products.lockById(previous.productId()));
            var old = productService.get(previous.productId());
            version(previous.rowVersion(), old.getRowVersion());
            if (!Objects.equals(previous.outputKey(), old.getProfile().getAudienceRole()))
                throw new ServiceException("学生版和答案版必须分别维护版本链", 400);
        }
        if (KbLayoutProtocol.list(r.getOutlineScopes()).isEmpty())
            throw new ServiceException("生成成品必须声明教材范围", 400);
        KbMaterial material = null;
        if (r.getMaterialId() != null) {
            if (!KbLayoutProtocol.list(r.getSections()).isEmpty())
                throw new ServiceException("专项题单与sections只能选一种", 400);
            var locked = found(materials.lockById(r.getMaterialId()));
            version(r.getMaterialRowVersion(), locked.getRowVersion());
            material = materialService.get(r.getMaterialId());
            if (!Boolean.TRUE.equals(material.getSelectionValid()))
                throw new ServiceException("专项选题真实性条件已失效，请先核对出处", 409);
            var section = new KbRenderSection();
            section.setKey("material");
            section.setTitle(material.getTitle());
            List<KbRenderQuestionRef> refs = new ArrayList<>();
            for (var q : material.getQuestions()) {
                var ref = new KbRenderQuestionRef();
                ref.setQuestionId(q.getId());
                ref.setRowVersion(q.getRowVersion());
                refs.add(ref);
            }
            section.setQuestionRefs(refs);
            r.setSections(List.of(section));
        } else if (r.getMaterialRowVersion() != null)
            throw new ServiceException("materialRowVersion须与materialId一起提交", 400);
        Set<String> sections = new HashSet<>();
        List<KbProductQuestionRef> refs = new ArrayList<>();
        List<KbRenderItem> items = new ArrayList<>();
        TreeSet<Long> sourceIds = new TreeSet<>();
        TreeSet<Long> kpIds = new TreeSet<>();
        for (var section : KbLayoutProtocol.list(r.getSections())) {
            if (!sections.add(KbLayoutProtocol.key(section.getKey(), 64)))
                throw new ServiceException("栏目key重复", 400);
            KbLayoutProtocol.limit(section.getTitle(), 300, "栏目标题");
            if (section.getDayKey() != null) KbLayoutProtocol.key(section.getDayKey(), 64);
            if (KbLayoutProtocol.list(section.getQuestionRefs()).isEmpty())
                throw new ServiceException("栏目题单不能为空", 400);
            for (var ref : section.getQuestionRefs()) {
                if (ref.getQuestionId() == null || ref.getRowVersion() == null)
                    throw new ServiceException("题目必须携带ID及版本", 400);
                var productRef = new KbProductQuestionRef();
                productRef.setSeq(refs.size() + 1);
                productRef.setQuestionId(ref.getQuestionId());
                productRef.setQuestionRowVersion(ref.getRowVersion());
                productRef.setItemLocator(section.getKey() + ":" + (refs.size() + 1));
                refs.add(productRef);
            }
        }
        if (refs.isEmpty() || refs.size() > 500)
            throw new ServiceException("每次生成须为1至500个内容位置", 400);
        // Occurrence writes lock source -> question. Discover IDs without taking question locks,
        // then follow that same order and verify the discovery again at READ_COMMITTED.
        List<Long> questionIds =
                refs.stream().map(KbProductQuestionRef::getQuestionId).distinct().sorted().toList();
        Set<Long> lockedSources = new TreeSet<>(mapper.questionSourceIds(questionIds));
        if (material != null)
            material.getReferenceSources().forEach(s -> lockedSources.add(s.getSourceId()));
        for (Long id : lockedSources) found(mapper.lockSource(id));
        for (Long id : questionIds) found(questionMapper.lockById(id));
        if (!lockedSources.containsAll(mapper.questionSourceIds(questionIds)))
            throw new ServiceException("冻结题单期间新增了出处，请按原请求重试", 409);
        Map<Long, KbQuestion> loaded = new HashMap<>();
        Map<Long, List<KbOccurrence>> occurred = new HashMap<>();
        String subject = null;
        for (var ref : refs) {
            var q = loaded.computeIfAbsent(ref.getQuestionId(), questions::get);
            version(ref.getQuestionRowVersion(), q.getRowVersion());
            if (!"READY".equals(q.getStatus()))
                throw new ServiceException("题目 " + q.getId() + " 尚未就绪", 400);
            if (subject == null) subject = q.getSubject();
            if (!Objects.equals(subject, q.getSubject()))
                throw new ServiceException("一次生成只支持同学科题单", 400);
            ref.setStemHash(q.getStemHash());
            ref.setQuestionTitle(q.getTitle());
            kpIds.addAll(KbLayoutProtocol.list(q.getKpIds()));
            if (!occurred.containsKey(q.getId())) {
                var filter = new KbOccurrence();
                filter.setQuestionId(q.getId());
                occurred.put(q.getId(), occurrences.selectList(filter));
            }
            occurred.get(q.getId()).stream().map(KbOccurrence::getSourceId).forEach(sourceIds::add);
        }
        if (material != null)
            material.getReferenceSources().forEach(s -> sourceIds.add(s.getSourceId()));
        var product = new KbProduct();
        product.setTitle(r.getParameters().getString("title"));
        product.setMaterialId(r.getMaterialId());
        product.setQuestionRefs(refs);
        var profile = new KbCatalogProfile();
        profile.setSubject(subject);
        r.setResourceType(r.getResourceType() == null ? "WORKSHEET" : r.getResourceType());
        if ("UNKNOWN".equals(r.getResourceType())) throw new ServiceException("生成成品必须明确资料类型", 400);
        profile.setResourceType(r.getResourceType());
        profile.setCatalogStatus("DRAFT");
        profile.setOriginKind("AUTHORED");
        profile.setAudienceRole(r.getOutputRoles().get(0));
        profile.setProvenanceStatus("TRACEABLE");
        profile.setDescription("由已发布排版经验装配的知识库成品");
        product.setProfile(profile);
        product.setOutlineScopes(r.getOutlineScopes());
        product.setDeclaredKpIds(new ArrayList<>(kpIds));
        List<KbProductReferenceSource> sourceRefs = new ArrayList<>();
        for (Long id : sourceIds) {
            var ref = new KbProductReferenceSource();
            ref.setSourceId(id);
            ref.setNote("提交任务时冻结的题目出处或专项参考来源");
            sourceRefs.add(ref);
        }
        product.setReferenceSources(sourceRefs);
        catalog.prepareProduct(product, null);
        r.setOutlineScopes(product.getOutlineScopes());
        Map<Long, KbSource> sourceSnapshots = new HashMap<>();
        for (Long id : sourceIds) sourceSnapshots.put(id, sources.get(id));
        for (var ref : sourceRefs)
            ref.setSourceTitle(sourceSnapshots.get(ref.getSourceId()).getTitle());
        LinkedHashMap<String, KbLayoutAsset> taskAssets = new LinkedHashMap<>();
        for (var source : sourceSnapshots.values())
            for (Long id : KbLayoutProtocol.list(source.getAssetIds()))
                addAsset(taskAssets, "source:" + id, id, "SOURCE_ORIGINAL");
        for (var a : KbLayoutProtocol.list(template.getAssets()))
            addAsset(taskAssets, a.getResourceKey(), a.getAssetId(), "TEMPLATE_IMAGE");
        for (var section : r.getSections())
            for (var ref : section.getQuestionRefs()) {
                var q = loaded.get(ref.getQuestionId());
                validateBodyAssets(q);
                for (Long id : KbLayoutProtocol.list(q.getAssetIds()))
                    addAsset(taskAssets, "question:" + id, id, "QUESTION_IMAGE");
                var snapshot = new KbRenderQuestionSnapshot();
                snapshot.setQuestion(q);
                snapshot.setOccurrences(occurred.get(q.getId()));
                snapshot.setKnowledgePoints(
                        KbLayoutProtocol.list(q.getKpIds()).stream()
                                .map(points::selectById)
                                .toList());
                snapshot.setSources(
                        occurred.get(q.getId()).stream()
                                .map(KbOccurrence::getSourceId)
                                .distinct()
                                .map(sourceSnapshots::get)
                                .toList());
                var item = new KbRenderItem();
                item.setSeq(items.size() + 1);
                item.setSectionKey(section.getKey());
                item.setDayKey(section.getDayKey());
                item.setQuestionId(q.getId());
                item.setQuestionRowVersion(q.getRowVersion());
                item.setSnapshotJson(KbLayoutProtocol.json(snapshot));
                item.setContentHash(
                        KbLayoutProtocol.hash(
                                item.getSnapshotJson()
                                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                items.add(item);
            }
        return new Prepared(r, template, product, items, new ArrayList<>(taskAssets.values()));
    }

    private void validateBodyAssets(KbQuestion q) {
        Set<Long> declared = new HashSet<>(KbLayoutProtocol.list(q.getAssetIds()));
        for (String body :
                List.of(
                        Objects.toString(q.getStemHtml(), ""),
                        Objects.toString(q.getAnswerHtml(), ""),
                        Objects.toString(q.getAnalysisHtml(), "")))
            for (var image : Jsoup.parseBodyFragment(body).select("img")) {
                try {
                    if (!declared.contains(Long.valueOf(image.attr("data-asset-id"))))
                        throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    throw new ServiceException("题目 " + q.getId() + " 存在未登记图片", 400);
                }
            }
    }

    private void addAsset(Map<String, KbLayoutAsset> all, String key, Long id, String role) {
        var stored = assets.requireReady(id);
        if (!"SOURCE_ORIGINAL".equals(role) && !stored.getContentType().startsWith("image/"))
            throw new ServiceException("题目或模板资源必须为图片", 400);
        var a = new KbLayoutAsset();
        a.setResourceKey(key);
        a.setAssetId(id);
        a.setAssetRole(role);
        a.setSha256(stored.getSha256());
        a.setMimeType(stored.getContentType());
        all.put(key, a);
    }

    private void own(KbRenderJob job) {
        if (!SecurityUtils.isAdmin(SecurityUtils.getUserId())
                && !Objects.equals(job.getActorUserId(), SecurityUtils.getUserId()))
            throw new ServiceException("无权读取他人的生成任务", 403);
    }

    public List<KbRenderJob> list(KbRenderJob query) {
        if (!SecurityUtils.isAdmin(SecurityUtils.getUserId()))
            query.setActorUserId(SecurityUtils.getUserId());
        return mapper.jobs(query);
    }

    public KbRenderJob get(Long id) {
        var job = found(mapper.job(id));
        own(job);
        return hydrate(job);
    }

    public KbRenderJob hydrate(KbRenderJob job) {
        var f = JSON.parseObject(job.getConfigSnapshotJson(), KbRenderFrozenConfig.class);
        job.setConfigSnapshot(f.getRequest());
        job.setTemplateSnapshot(
                JSON.parseObject(job.getTemplateSnapshotJson(), KbExperienceVersion.class));
        var items = mapper.items(job.getId());
        for (var item : items) {
            var s = JSON.parseObject(item.getSnapshotJson(), KbRenderQuestionSnapshot.class);
            item.setSnapshot(s.getQuestion());
            item.setOccurrences(s.getOccurrences());
            item.setKnowledgePoints(s.getKnowledgePoints());
            item.setSources(s.getSources());
        }
        job.setItems(items);
        job.setAssets(mapper.jobAssets(job.getId()));
        job.setOutputs(mapper.outputs(job.getId()));
        return job;
    }

    @Transactional
    public KbRenderJob retry(Long id) {
        var job = found(mapper.lockJob(id));
        own(job);
        if ("SUCCEEDED".equals(job.getStatus())) return get(id);
        if (!"FAILED".equals(job.getStatus()))
            throw new ServiceException("任务尚未失败，处理中任务将按租约自动恢复", 409);
        changed(mapper.retry(job));
        return get(id);
    }

    @Transactional
    public void delete(Long id) {
        var job = found(mapper.lockJob(id));
        own(job);
        if (!Set.of("FAILED", "SUCCEEDED").contains(job.getStatus()))
            throw new ServiceException("运行中的任务不能删除", 409);
        unreferenced(mapper.outputs(id).size(), "请先删除生成成品及后续版本");
        mapper.deleteJobAssets(id);
        mapper.deleteItems(id);
        mapper.deleteJob(id);
    }

    public Long downloadAsset(Long id, String outputKey, String kind) {
        var job = get(id);
        if (!"SUCCEEDED".equals(job.getStatus())) throw new ServiceException("任务尚未完整成功", 409);
        var output =
                job.getOutputs().stream()
                        .filter(x -> x.getOutputKey().equals(outputKey))
                        .findFirst()
                        .orElseThrow(() -> new ServiceException("输出不存在", 404));
        return switch (kind) {
            case "pdf" -> output.getAssetId();
            case "package" -> output.getPackageAssetId();
            default -> throw new ServiceException("kind须为pdf或package", 400);
        };
    }

    public void assertAssetDownload(Long assetId) {
        var jobs = mapper.assetJobs(assetId);
        if (jobs.isEmpty()) return;
        var permissions = SecurityUtils.getLoginUser().getPermissions();
        if (!SecurityUtils.isAdmin(SecurityUtils.getUserId())
                && !permissions.contains("*:*:*")
                && !permissions.contains("knowledge:render:download"))
            throw new ServiceException("缺少生成产物下载权限", 403);
        for (var job : jobs) {
            own(job);
            if (!"SUCCEEDED".equals(job.getStatus())) throw new ServiceException("任务尚未完整成功", 409);
        }
    }
}
