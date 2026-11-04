package com.ruoyi.knowledge.publication;

import static com.ruoyi.knowledge.publication.PublicationSupport.fail;
import static com.ruoyi.knowledge.publication.PublicationSupport.id;
import com.ruoyi.knowledge.html.KbSvgSafety;
import com.ruoyi.knowledge.publication.PublicationModels.Input;
import com.ruoyi.knowledge.publication.PublicationModels.Snapshot;
import com.ruoyi.knowledge.publication.PublicationRows.PublicationOperation;
import com.ruoyi.knowledge.storage.IKbAssetService;
import com.ruoyi.knowledge.storage.KbAssetServiceImpl;
import com.ruoyi.knowledge.storage.domain.KbAsset;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Service;

/** Frozen, deterministic download export. No live API or signed URLs are embedded in the package. */
@Service
public class PublicationBundleService {
    static final int MAX_BYTES = 100 * 1024 * 1024;
    private final PublicationService service;
    private final IKbAssetService assets;
    public PublicationBundleService(PublicationService service, IKbAssetService assets) {
        this.service = service; this.assets = assets;
    }
    record FileFact(String path, String assetId, long byteSize, String sha256, String caption) { }
    record RelationFact(String objectType, String objectId, String rowVersion, String name) { }
    record Manifest(String schemaVersion, String publicationId, String contentVersion, String dependencyHash,
            List<FileFact> files, List<RelationFact> relations) { }
    public KbAsset create(Long publicationId, PublicationOperation operation) {
        Snapshot snapshot = service.support.decode(operation.snapshotJson, Snapshot.class);
        byte[] bytes = build(publicationId, operation.contentVersion, snapshot);
        return assets.uploadBytes(bytes, "publication-" + publicationId + "-v" + operation.contentVersion + ".zip",
                "application/zip", "IMPORT_RESOURCE", null, "publication-bundle-" + operation.id);
    }
    byte[] build(Long publicationId, Long version, Snapshot snapshot) {
        Input input = snapshot.publication();
        List<RelationFact> relations = service.support.gated(() -> service.mapper.relationRefs(
                PublicationService.scope(publicationId, snapshot.check())).stream()
                .map(r -> new RelationFact(r.objectType, id(r.id), id(r.rowVersion), r.name))
                .sorted(Comparator.comparing(RelationFact::objectType).thenComparing(RelationFact::objectId)).toList());
        List<FileFact> files = new ArrayList<>();
        try (var output = new ByteArrayOutputStream(); var zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            long total = 0;
            for (var image : input.images.stream().sorted(Comparator.comparing(i -> i.sortOrder)).toList()) {
                KbAsset asset = assets.requireReady(image.assetId);
                if (asset.getFileSize() == null || asset.getFileSize() <= 0 || asset.getFileSize() > MAX_BYTES - total) {
                    throw fail(409, "BUNDLE_TOO_LARGE", "发布下载包超过100MiB限制");
                }
                String path = "images/" + String.format(java.util.Locale.ROOT, "%03d", image.sortOrder) + extension(asset.getContentType());
                byte[] bytes;
                try (InputStream stream = assets.openContent(image.assetId)) { bytes = stream.readNBytes(asset.getFileSize().intValue() + 1); }
                if (bytes.length != asset.getFileSize() || !KbAssetServiceImpl.sha256(bytes).equals(asset.getSha256())) {
                    throw fail(409, "DEPENDENCY_CHANGED", "发布图片与登记摘要不一致");
                }
                if ("image/svg+xml".equals(asset.getContentType())) { KbSvgSafety.validateBytes(bytes); }
                total += bytes.length; add(zip, output, path, bytes);
                files.add(new FileFact(path, id(image.assetId), bytes.length, asset.getSha256(), image.caption));
            }
            total += addText(zip, output, files, "标题.txt", text(input.postTitle));
            total += addText(zip, output, files, "正文.txt", text(PublicationCopyText.body(input.postBody)));
            total += addText(zip, output, files, "商品上架描述.txt", text(input.listingDescription));
            total += addText(zip, output, files, "百度网盘信息.txt", baidu(input));
            total += addText(zip, output, files, "index.html", PublicationOfflinePage.render(input, files));
            String manifest = service.support.encode(new Manifest("publication-download/v1", id(publicationId), id(version),
                    snapshot.check().dependencyHash, files, relations));
            byte[] manifestBytes = manifest.getBytes(StandardCharsets.UTF_8);
            if (total + manifestBytes.length > MAX_BYTES) { throw fail(409, "BUNDLE_TOO_LARGE", "发布下载包超过100MiB限制"); }
            add(zip, output, "manifest.json", manifestBytes); zip.finish();
            if (output.size() > MAX_BYTES) { throw fail(409, "BUNDLE_TOO_LARGE", "发布下载包超过100MiB限制"); }
            return output.toByteArray();
        } catch (IOException e) { throw fail(503, "GENERATION_FAILED", "发布下载包生成失败，请按原操作重试"); }
    }
    private static int addText(ZipOutputStream zip, ByteArrayOutputStream output, List<FileFact> files, String path, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8); add(zip, output, path, bytes);
        files.add(new FileFact(path, null, bytes.length, KbAssetServiceImpl.sha256(bytes), null)); return bytes.length;
    }
    private static void add(ZipOutputStream zip, ByteArrayOutputStream output, String path, byte[] bytes) throws IOException {
        if ((long) output.size() + bytes.length + 512 > MAX_BYTES) { throw fail(409, "BUNDLE_TOO_LARGE", "发布下载包超过100MiB限制"); }
        ZipEntry entry = new ZipEntry(path); entry.setTime(0); entry.setMethod(ZipEntry.STORED);
        entry.setSize(bytes.length); CRC32 crc = new CRC32(); crc.update(bytes); entry.setCrc(crc.getValue());
        zip.putNextEntry(entry); zip.write(bytes); zip.closeEntry();
    }
    static String baidu(Input input) {
        return input.baiduShares.stream().map(s -> PublicationCopyText.baidu(s.shareUrl, s.extractionCode,
                s.files.stream().map(f -> f.remoteFileName).toList())).collect(java.util.stream.Collectors.joining("\n\n"));
    }

    static String text(String value) { return value == null ? "" : value; }
    private static String extension(String type) {
        return switch (type) { case "image/png" -> ".png"; case "image/jpeg" -> ".jpg";
            case "image/webp" -> ".webp"; case "image/gif" -> ".gif"; case "image/svg+xml" -> ".svg";
            default -> throw fail(409, "INVALID_IMAGE_TYPE", "下载包仅支持PNG/JPEG/WebP/GIF或安全静态SVG图片"); };
    }
}
