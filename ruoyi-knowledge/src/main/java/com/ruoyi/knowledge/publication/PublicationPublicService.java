package com.ruoyi.knowledge.publication;

import static com.ruoyi.knowledge.publication.PublicationSupport.fail;
import static com.ruoyi.knowledge.publication.PublicationSupport.id;
import com.ruoyi.knowledge.publication.PublicationModels.Input;
import com.ruoyi.knowledge.publication.PublicationModels.Page;
import com.ruoyi.knowledge.publication.PublicationModels.CheckResult;
import com.ruoyi.knowledge.publication.PublicationModels.QuestionCheck;
import com.ruoyi.knowledge.publication.PublicationModels.SourceEvidence;
import com.ruoyi.knowledge.publication.PublicationModels.Snapshot;
import com.ruoyi.knowledge.publication.PublicationModels.RelationQuery;
import com.ruoyi.knowledge.publication.PublicationModels.Relation;
import com.ruoyi.knowledge.publication.PublicationModels.RelationFile;
import com.ruoyi.knowledge.publication.PublicationModels.OutlineLocation;
import com.ruoyi.knowledge.publication.PublicationModels.RelationLink;
import com.ruoyi.knowledge.publication.PublicationModels.RelationCounts;
import com.ruoyi.knowledge.publication.PublicationModels.PublicImage;
import com.ruoyi.knowledge.publication.PublicationModels.PublicProduct;
import com.ruoyi.knowledge.publication.PublicationModels.PublicFile;
import com.ruoyi.knowledge.publication.PublicationModels.PublicShare;
import com.ruoyi.knowledge.publication.PublicationModels.PublicPublication;
import com.ruoyi.knowledge.publication.PublicationModels.Materials;
import com.ruoyi.knowledge.publication.PublicationRows.Publication;
import com.ruoyi.knowledge.publication.PublicationRows.PublicationOperation;
import com.ruoyi.knowledge.storage.IKbAssetService;
import com.ruoyi.knowledge.storage.domain.KbAsset;
import com.ruoyi.knowledge.storage.mapper.KbAssetMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;

@Service
public class PublicationPublicService {
    private final PublicationService service;
    private final PublicationReadGate checks;
    private final IKbAssetService assets;
    private final KbAssetMapper assetMapper;
    public PublicationPublicService(PublicationService service, PublicationReadGate checks,
            IKbAssetService assets, KbAssetMapper assetMapper) {
        this.service = service; this.checks = checks; this.assets = assets; this.assetMapper = assetMapper;
    }
    public PublicPublication get(String code) {
        return service.support.gated(() -> publicView(require(code)));
    }
    public Materials materials(String code, String kind, String scope, Long assetId) {
        PublicPublication page = get(code);
        if ("IMAGES".equals(kind)) {
            if (scope == null || !List.of("ALL", "ONE").contains(scope) || ("ONE".equals(scope) && assetId == null)) { throw fail(400, "INVALID_REQUEST", "图片范围无效"); }
            List<PublicImage> selected = "ALL".equals(scope) ? page.images()
                    : page.images().stream().filter(i -> Objects.equals(i.assetId(), id(assetId))).toList();
            if (selected.isEmpty()) { throw fail(404, "SHARE_UNAVAILABLE", "发布图片不可用"); }
            return new Materials(kind, page.contentVersion(), selected.size(), selected, null);
        }
        if (kind == null) { throw fail(400, "INVALID_REQUEST", "文本类别必填"); }
        String text = switch (kind) {
            case "TITLE" -> page.postTitle(); case "BODY" -> page.postBody(); case "LISTING_DESCRIPTION" -> page.listingDescription();
            case "BAIDU" -> page.baiduShares().stream().map(s -> PublicationCopyText.baidu(s.shareUrl(), s.extractionCode(),
                    s.files().stream().map(PublicFile::remoteFileName).toList())).collect(Collectors.joining("\n\n"));
            default -> throw fail(400, "INVALID_REQUEST", "文本类别无效");
        };
        return new Materials(kind, page.contentVersion(), null, null, text == null ? "" : text);
    }
    public Page<Relation> relations(String code, RelationQuery query) {
        return service.support.gated(() -> {
            Publication row = require(code);
            if (query.objectType == null || !List.of("PRODUCT", "QUESTION", "SOURCE", "KP", "OUTLINE").contains(query.objectType)
                    || query.pageNum < 1 || query.pageSize < 1 || query.pageSize > 100 || query.pageNum > 1000000) {
                throw fail(400, "INVALID_REQUEST", "关联范围或分页无效");
            }
            query.publicationId = row.id; query.frozenSourceIds = scope(row).frozenSourceIds; query.offset = (query.pageNum - 1) * query.pageSize;
            List<Relation> items = service.mapper.relationPage(query);
            if (query.objectId != null && items.isEmpty()) { throw unavailable(); }
            List<Long> pageIds = items.stream().map(i -> i.id).toList(); query.pageIds = pageIds;
            List<RelationFile> files = pageIds.isEmpty() ? List.of() : service.mapper.relationFiles(query);
            List<OutlineLocation> nodes = pageIds.isEmpty() || !List.of("KP", "OUTLINE").contains(query.objectType)
                    ? List.of() : service.mapper.relationNodes(query);
            for (RelationFile file : files) { file.contentUrl = assetUrl(row, file.assetId); }
            List<RelationLink> links = new java.util.ArrayList<>(service.mapper.relationLinks(query));
            Snapshot frozen = service.support.decode(service.mapper.getOperation(row.id, row.lastSaleOperationId).snapshotJson, Snapshot.class);
            for (QuestionCheck item : frozen.check().items) {
                for (SourceEvidence evidence : item.sourceEvidence) {
                    if ("FROZEN_LINEAGE".equals(evidence.kind) && evidence.sourceId != null) {
                        links.add(new RelationLink("FROZEN_LINEAGE", "QUESTION", id(item.questionId), "SOURCE", id(evidence.sourceId)));
                    }
                }
            }
            Set<Long> hiddenProducts = hiddenProductAssets(row);
            Set<Long> allowed = service.mapper.allowedAssets(scope(row)).stream().filter(id -> !hiddenProducts.contains(id))
                    .collect(Collectors.toUnmodifiableSet());
            for (Relation item : items) {
                item.links = links.stream().filter(l -> (l.fromType().equals(item.objectType) && l.fromId().equals(id(item.id)))
                        || (l.toType().equals(item.objectType) && l.toId().equals(id(item.id)))).toList();
                item.relationKinds = item.links.stream().map(RelationLink::type).distinct().toList();
                item.stemHtml = sanitize(item.stemHtml, row, allowed); item.answerHtml = sanitize(item.answerHtml, row, allowed);
                item.files = files.stream().filter(f -> f.objectId.equals(item.id) && f.objectType.equals(item.objectType) && !hiddenProducts.contains(f.assetId)).toList();
                item.nodes = nodes.stream().filter(n -> "KP".equals(item.objectType) ? n.kpId.equals(item.id) : n.outlineId.equals(item.id)).toList();
                item.contentUrl = item.assetId == null || hiddenProducts.contains(item.assetId) ? null : assetUrl(row, item.assetId);
            }
            return new Page<>(items, service.mapper.relationCount(query));
        });
    }
    public KbAsset asset(String code, Long assetId, Long contentVersion) {
        return service.support.gated(() -> {
            Publication row = require(code);
            if (!Objects.equals(row.contentVersion, contentVersion) || hiddenProductAssets(row).contains(assetId) || !service.mapper.allowedAssets(scope(row)).contains(assetId)) { throw unavailable(); }
            return assets.requireReady(assetId);
        });
    }
    public KbAsset page(String code) {
        return service.support.gated(() -> {
            Publication row = require(code);
            if (row.shareAssetId == null) { throw unavailable(); }
            KbAsset page = assets.requireReady(row.shareAssetId);
            if (!"text/html".equals(page.getContentType())) { throw unavailable(); }
            return page;
        });
    }
    public Publication require(String code) {
        service.support.enabled();
        if (!"PUBLIC_PAGE".equals(service.support.deliveryMode())) { throw unavailable(); }
        if (code == null || !code.matches("[a-f0-9]{32}")) { throw unavailable(); }
        Publication row = service.mapper.byCode(code);
        if (row == null || !"ON_SALE".equals(row.status) || !"READY".equals(row.shareState)
                || !Objects.equals(row.contentVersion, row.sharedContentVersion)) { throw unavailable(); }
        PublicationOperation sale = service.mapper.getOperation(row.id, row.lastSaleOperationId);
        if (sale == null || !"SUCCEEDED".equals(sale.status) || !"SALE".equals(sale.action)) { throw unavailable(); }
        try {
            CheckResult current = checks.evaluate(row, sale);
            Snapshot snapshot = service.support.decode(sale.snapshotJson, Snapshot.class);
            if (!"PASSED".equals(current.status) || !Objects.equals(current.dependencyHash, snapshot.check().dependencyHash)) {
                service.support.transaction(() -> {
                    Publication locked = service.lock(row.id);
                    if (Objects.equals(locked.lastSaleOperationId, sale.id) && "READY".equals(locked.shareState)) {
                        locked.shareState = "DISABLED"; locked.updateBy = "publication-check"; locked.updateTime = new java.util.Date();
                        PublicationService.changed(service.mapper.updatePublication(locked));
                    }
                    return null;
                });
                throw unavailable();
            }
        } catch (PublicationException e) { throw e; }
        catch (RuntimeException e) { throw fail(503, "DEPENDENCY_UNAVAILABLE", "资料暂时无法核对，请稍后重试"); }
        if (row.shareAssetId == null || !"text/html".equals(assets.requireReady(row.shareAssetId).getContentType())) { throw unavailable(); }
        return row;
    }
    private PublicPublication publicView(Publication row) {
        Input input = service.graph.read(row);
        List<Long> imageIds = input.images.stream().map(i -> i.assetId).toList();
        Map<Long, KbAsset> metadata = imageIds.isEmpty() ? Map.of() : assetMapper.selectByIds(imageIds).stream()
                .collect(Collectors.toMap(KbAsset::getAssetId, Function.identity()));
        List<Long> previewIds = imageIds.stream().filter(id -> {
            KbAsset a = metadata.get(id);
            return a != null && a.getFileSize() != null && a.getFileSize() <= 20L * 1024 * 1024
                    && Set.of("image/png", "image/jpeg", "image/webp", "image/gif").contains(a.getContentType());
        }).toList();
        Map<Long, IKbAssetService.Preview> previews = previewIds.isEmpty() ? Map.of() : assets.publicationImagePreviews(previewIds);
        List<PublicImage> images = input.images.stream().map(i -> {
            KbAsset a = metadata.get(i.assetId);
            if (a == null || !"READY".equals(a.getStatus())) { throw unavailable(); }
            IKbAssetService.Preview preview = previews.get(i.assetId);
            return new PublicImage(id(i.assetId), i.sortOrder, i.caption, a.getContentType(), null, null, id(a.getFileSize()), assetUrl(row, i.assetId),
                    preview == null ? null : preview.url(), preview == null ? null : preview.expiresInSeconds());
        }).toList();
        List<Long> productIds = input.products.stream().map(p -> p.productId).toList();
        List<PublicProduct> products = productIds.isEmpty() || !service.support.productDownloadsEnabled() ? List.of() : service.mapper.productFacts(productIds).stream()
                .map(p -> new PublicProduct(id(p.id), p.title, "第" + p.versionNo + "版", id(p.assetId), p.fileName, p.mimeType, assetUrl(row, p.assetId))).toList();
        List<PublicShare> baidu = input.baiduShares.stream().map(s -> new PublicShare(s.shareUrl, s.extractionCode,
                s.files.stream().map(f -> new PublicFile(id(f.productId), f.remoteFileName)).toList())).toList();
        return new PublicPublication(row.shareCode, id(row.contentVersion), row.title, "ON_SALE", row.postTitle, PublicationCopyText.body(row.postBody),
                row.listingDescription, images, products, baidu, counts(row));
    }
    private RelationCounts counts(Publication row) {
        long[] counts = new long[5]; String[] types = {"PRODUCT", "QUESTION", "SOURCE", "KP", "OUTLINE"};
        RelationQuery q = scope(row);
        for (int i = 0; i < types.length; i++) { q.objectType = types[i]; counts[i] = service.mapper.relationCount(q); }
        return new RelationCounts(counts[0], counts[1], counts[2], counts[3], counts[4]);
    }
    private RelationQuery scope(Publication row) {
        PublicationOperation sale = service.mapper.getOperation(row.id, row.lastSaleOperationId);
        Snapshot snapshot = service.support.decode(sale.snapshotJson, Snapshot.class);
        return PublicationService.scope(row.id, snapshot.check());
    }
    String sanitize(String html, Publication row, Set<Long> allowed) {
        if (html == null) { return null; }
        Document document = Jsoup.parseBodyFragment(html);
        for (Element image : document.select("img")) {
            String raw = image.attr("data-asset-id");
            if (raw.isBlank()) {
                java.util.regex.Matcher match = java.util.regex.Pattern.compile("(?:asset://|kb-asset:|/asset/)([0-9]+)").matcher(image.attr("src"));
                if (match.find()) { raw = match.group(1); }
            }
            if (!raw.matches("[0-9]+")) { image.remove(); continue; }
            try { Long id = Long.valueOf(raw); if (!allowed.contains(id)) { image.remove(); } else { image.attr("src", assetUrl(row, id)); } }
            catch (NumberFormatException e) { image.remove(); }
        }
        return Jsoup.clean(document.body().html(), "", Safelist.relaxed().removeTags("a").removeAttributes(":all", "style").preserveRelativeLinks(true).removeProtocols("img", "src", "http", "https")
                .addTags("math", "mi", "mo", "mn", "mrow", "mfrac", "msup", "msub").addAttributes("img", "src", "alt"),
                new Document.OutputSettings().prettyPrint(false));
    }
    private Set<Long> hiddenProductAssets(Publication row) {
        if (service.support.productDownloadsEnabled()) { return Set.of(); }
        List<Long> ids = service.mapper.products(row.id).stream().map(p -> p.productId).toList();
        return ids.isEmpty() ? Set.of() : service.mapper.productFacts(ids).stream().map(p -> p.assetId)
                .filter(Objects::nonNull).collect(Collectors.toUnmodifiableSet());
    }
    private static String assetUrl(Publication row, Long assetId) {
        return "/public/publications/" + row.shareCode + "/assets/" + assetId + "/content?contentVersion=" + row.contentVersion;
    }
    private static PublicationException unavailable() { return fail(404, "SHARE_UNAVAILABLE", "该分享资料不可用"); }
}
