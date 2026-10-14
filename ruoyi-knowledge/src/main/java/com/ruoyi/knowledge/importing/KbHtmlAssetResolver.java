package com.ruoyi.knowledge.importing;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.knowledge.html.HtmlQuestionDocument;
import com.ruoyi.knowledge.html.KbHtmlCodec;
import com.ruoyi.knowledge.storage.IKbAssetService;
import com.ruoyi.knowledge.storage.KbAssetServiceImpl;
import com.ruoyi.knowledge.storage.domain.KbAsset;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

@Component
public class KbHtmlAssetResolver {
    private final IKbAssetService assets;
    private final KbHtmlCodec codec;

    public KbHtmlAssetResolver(IKbAssetService assets, KbHtmlCodec codec) { this.assets = assets; this.codec = codec; }

    public ResolvedArticle resolve(HtmlQuestionDocument document, List<ImportResource> resources, String runId) {
        String fingerprint = KbImportServiceImpl.articleFingerprint(document, resources);
        Set<Long> ids = new LinkedHashSet<>();
        List<Element> parts = new ArrayList<>();
        parts.add(document.stem());
        if (document.answer() != null) { parts.add(document.answer()); }
        if (document.analysis() != null) { parts.add(document.analysis()); }
        for (Element part : parts) {
            for (Element img : part.select("img")) {
                KbAsset asset;
                if (img.hasAttr("data-asset-id")) {
                    try { asset = assets.requireReady(Long.valueOf(img.attr("data-asset-id"))); }
                    catch (NumberFormatException e) { throw new ServiceException("题图assetId无效"); }
                    if (!"QUESTION_IMAGE".equals(asset.getPurpose())) { throw new ServiceException("题内图片只能引用题图资产"); }
                } else if (img.attr("src").startsWith("data:")) {
                    String src = img.attr("src");
                    int comma = src.indexOf(',');
                    if (comma < 0 || !src.substring(0, comma).matches("data:image/(png|jpeg|gif|webp|svg\\+xml);base64")) {
                        throw new ServiceException("内嵌题图必须使用支持格式的base64 data URI");
                    }
                    byte[] bytes;
                    try { bytes = Base64.getDecoder().decode(src.substring(comma + 1)); }
                    catch (IllegalArgumentException e) { throw new ServiceException("内嵌题图base64无效"); }
                    asset = assets.uploadBytes(bytes, "embedded." + imageExtension(bytes), "application/octet-stream", "QUESTION_IMAGE", runId);
                } else {
                    String src = img.attr("src").replace('\\', '/');
                    if (src.isBlank() || src.startsWith("/") || src.contains(":") || List.of(src.split("/")).contains("..")) {
                        throw new ServiceException("题图必须为本次资源相对路径或已登记assetId，禁止抓取远程URL");
                    }
                    ImportResource resource = resources.stream().filter(r -> r.resourceKey().equals(src)).findFirst().orElse(null);
                    if (resource == null) { throw new ServiceException("缺少题图资源：" + src); }
                    asset = assets.uploadBytes(resource.bytes(), resource.resourceKey(), resource.contentType(), "QUESTION_IMAGE", runId);
                }
                ids.add(asset.getAssetId());
                img.attr("data-asset-id", asset.getAssetId().toString());
                img.attr("src", "kb-asset:" + asset.getAssetId());
            }
        }
        String stem = codec.sanitize(document.stem());
        String answer = codec.sanitize(document.answer());
        String analysis = codec.sanitize(document.analysis());
        if (codec.text(stem).isBlank() && !stem.contains("<img") && !stem.contains("<svg")) {
            throw new ServiceException("题干不能为空");
        }
        return new ResolvedArticle(document, stem, answer, analysis, List.copyOf(ids), fingerprint);
    }

    private String imageExtension(byte[] bytes) {
        return switch (KbAssetServiceImpl.imageType(bytes)) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            default -> "svg";
        };
    }
}
