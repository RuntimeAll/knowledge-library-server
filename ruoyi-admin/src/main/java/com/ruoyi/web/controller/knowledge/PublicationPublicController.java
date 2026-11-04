package com.ruoyi.web.controller.knowledge;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.utils.file.FileUtils;
import com.ruoyi.knowledge.publication.PublicationPublicService;
import com.ruoyi.knowledge.publication.PublicationModels.Page;
import com.ruoyi.knowledge.publication.PublicationModels.RelationQuery;
import com.ruoyi.knowledge.publication.PublicationModels.Relation;
import com.ruoyi.knowledge.publication.PublicationModels.PublicPublication;
import com.ruoyi.knowledge.publication.PublicationModels.Materials;
import com.ruoyi.knowledge.storage.IKbAssetService;
import com.ruoyi.knowledge.storage.domain.KbAsset;
import com.ruoyi.web.core.docs.CoreApi;
import jakarta.servlet.http.HttpServletResponse;
import java.io.InputStream;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
public class PublicationPublicController {
    private final PublicationPublicService publications;
    private final IKbAssetService assets;
    public PublicationPublicController(PublicationPublicService publications, IKbAssetService assets) {
        this.publications = publications; this.assets = assets;
    }
    @InitBinder
    public void bind(org.springframework.web.bind.WebDataBinder binder) { binder.initDirectFieldAccess(); }
    @GetMapping("/public/publications/{shareCode}")
    @CoreApi(value = "publication.publicGet", published = false, anonymous = true, response = PublicPublication.class)
    public AjaxResult get(@PathVariable String shareCode, HttpServletResponse response) {
        noCache(response); return AjaxResult.success(publications.get(shareCode));
    }
    @GetMapping("/public/publications/{shareCode}/materials")
    @CoreApi(value = "publication.publicMaterials", published = false, anonymous = true, response = Materials.class)
    public AjaxResult materials(@PathVariable String shareCode, @RequestParam String kind,
            @RequestParam(required = false) String scope, @RequestParam(required = false) Long assetId,
            HttpServletResponse response) {
        noCache(response); return AjaxResult.success(publications.materials(shareCode, kind, scope, assetId));
    }
    @GetMapping("/public/publications/{shareCode}/relations")
    @CoreApi(value = "publication.publicRelations", published = false, anonymous = true, response = Relation.class, shape = CoreApi.Shape.PAGE,
            queryFields = {"objectType", "objectId", "pageNum", "pageSize"})
    public TableDataInfo relations(@PathVariable String shareCode, RelationQuery query, HttpServletResponse response) {
        noCache(response); Page<Relation> page = publications.relations(shareCode, query);
        TableDataInfo result = new TableDataInfo(page.rows(), page.total());
        result.setCode(200); result.setMsg("查询成功"); return result;
    }
    @GetMapping("/public/publications/{shareCode}/assets/{assetId}/content")
    @CoreApi(value = "publication.publicAsset", published = false, anonymous = true, response = byte[].class,
            shape = CoreApi.Shape.BINARY, mediaType = "application/octet-stream")
    public void asset(@PathVariable String shareCode, @PathVariable Long assetId,
            @RequestParam Long contentVersion, HttpServletResponse response) throws Exception {
        KbAsset asset = publications.asset(shareCode, assetId, contentVersion);
        noCache(response); response.setHeader("X-Content-Type-Options", "nosniff");
        if (asset.getContentType().startsWith("image/") && !asset.getContentType().contains("svg")) {
            response.setContentType(asset.getContentType());
        } else {
            response.setContentType(asset.getContentType()); FileUtils.setAttachmentResponseHeader(response, asset.getOriginalName());
            response.setHeader("Content-Security-Policy", "sandbox; default-src 'none'");
        }
        response.setContentLengthLong(asset.getFileSize());
        try (InputStream input = assets.openContent(assetId)) { input.transferTo(response.getOutputStream()); }
    }
    @GetMapping("/p/{shareCode}")
    @CoreApi(value = "publication.publicPage", published = false, anonymous = true, response = byte[].class,
            shape = CoreApi.Shape.BINARY, mediaType = "text/html")
    public void page(@PathVariable String shareCode, HttpServletResponse response) throws Exception {
        KbAsset page = publications.page(shareCode); noCache(response);
        response.setContentType("text/html;charset=UTF-8"); response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' blob:; connect-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'");
        response.setContentLengthLong(page.getFileSize());
        try (InputStream input = assets.openContent(page.getAssetId())) { input.transferTo(response.getOutputStream()); }
    }
    private static void noCache(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store"); response.setHeader("Pragma", "no-cache");
        response.setHeader("Referrer-Policy", "no-referrer");
    }
}
