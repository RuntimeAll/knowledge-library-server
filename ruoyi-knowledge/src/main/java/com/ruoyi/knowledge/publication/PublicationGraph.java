package com.ruoyi.knowledge.publication;

import com.ruoyi.knowledge.mapper.PublicationMapper;
import static com.ruoyi.knowledge.publication.PublicationSupport.fail;
import com.ruoyi.knowledge.publication.PublicationModels.Input;
import com.ruoyi.knowledge.publication.PublicationModels.ProductInput;
import com.ruoyi.knowledge.publication.PublicationModels.QuestionInput;
import com.ruoyi.knowledge.publication.PublicationModels.ReferenceInput;
import com.ruoyi.knowledge.publication.PublicationModels.ImageInput;
import com.ruoyi.knowledge.publication.PublicationModels.ShareInput;
import com.ruoyi.knowledge.publication.PublicationModels.FileInput;
import com.ruoyi.knowledge.publication.PublicationModels.ProductFact;
import com.ruoyi.knowledge.publication.PublicationModels.FrozenQuestion;
import com.ruoyi.knowledge.publication.PublicationRows.Publication;
import com.ruoyi.knowledge.publication.PublicationRows.PublicationProduct;
import com.ruoyi.knowledge.publication.PublicationRows.PublicationQuestion;
import com.ruoyi.knowledge.publication.PublicationRows.PublicationReferenceSource;
import com.ruoyi.knowledge.publication.PublicationRows.PublicationImage;
import com.ruoyi.knowledge.publication.PublicationRows.PublicationBaiduShare;
import com.ruoyi.knowledge.publication.PublicationRows.PublicationBaiduFile;
import com.ruoyi.knowledge.storage.IKbAssetService;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Reads/writes only publication references. Existing product/source/question content is immutable here. */
@Component
public class PublicationGraph {
    private final PublicationMapper mapper;
    private final PublicationSupport support;
    private final IKbAssetService assets;
    public PublicationGraph(PublicationMapper mapper, PublicationSupport support, IKbAssetService assets) {
        this.mapper = mapper; this.support = support; this.assets = assets;
    }
    public Input read(Publication row) {
        Input out = support.json.convertValue(row, Input.class);
        List<PublicationQuestion> questions = mapper.questions(row.id);
        out.products = new ArrayList<>();
        for (PublicationProduct p : mapper.products(row.id)) {
            ProductInput value = support.json.convertValue(p, ProductInput.class);
            value.questions = questions.stream().filter(q -> q.publicationProductId.equals(p.id))
                    .map(q -> support.json.convertValue(q, QuestionInput.class)).toList();
            out.products.add(value);
        }
        out.referenceSources = mapper.sources(row.id).stream()
                .map(x -> support.json.convertValue(x, ReferenceInput.class)).toList();
        out.images = mapper.images(row.id).stream().map(x -> support.json.convertValue(x, ImageInput.class)).toList();
        List<PublicationBaiduFile> files = mapper.files(row.id);
        out.baiduShares = new ArrayList<>();
        List<PublicationProduct> products = mapper.products(row.id);
        for (PublicationBaiduShare share : mapper.shares(row.id)) {
            ShareInput item = support.json.convertValue(share, ShareInput.class);
            item.files = new ArrayList<>();
            for (PublicationBaiduFile file : files) {
                if (!file.baiduShareId.equals(share.id)) { continue; }
                FileInput f = support.json.convertValue(file, FileInput.class);
                f.productId = products.stream().filter(p -> p.id.equals(file.publicationProductId))
                        .findFirst().orElseThrow().productId;
                item.files.add(f);
            }
            out.baiduShares.add(item);
        }
        return out;
    }
    public void validate(Input input) {
        if (input == null || input.title == null || input.title.isBlank() || input.products == null
                || input.images == null || input.referenceSources == null || input.baiduShares == null) {
            throw fail(400, "INVALID_REQUEST", "资料名称和全部关联数组必填");
        }
        Set<Long> products = new HashSet<>(); Set<Integer> order = new HashSet<>();
        for (ProductInput p : input.products) {
            positive(p.productId); sequence(p.sortOrder, order);
            if (!products.add(p.productId) || p.questions == null) { invalid("成品重复或题单数组缺失"); }
            oneOf(p.questionListStatus, "UNKNOWN", "VERIFIED", "NOT_APPLICABLE");
            Set<Integer> seq = new HashSet<>();
            for (QuestionInput q : p.questions) {
                positive(q.questionId); sequence(q.seq, seq);
                if (q.observedQuestionVersion == null || q.observedQuestionVersion < 0
                        || q.observedContentHash == null || !q.observedContentHash.matches("[a-f0-9]{64}")) {
                    invalid("题目受检版本或完整摘要无效");
                }
                oneOf(q.answerAvailability, "UNKNOWN", "PROVIDED", "NOT_PROVIDED");
            }
            consecutive(seq);
            if ("VERIFIED".equals(p.questionListStatus) && (p.expectedQuestionCount == null
                    || p.expectedQuestionCount < 1 || p.expectedQuestionCount != p.questions.size()
                    || (p.evidenceAssetId == null && (p.note == null || p.note.isBlank())))) {
                invalid("核实题单需完整题数及核实依据");
            }
            if ("NOT_APPLICABLE".equals(p.questionListStatus) && (!withoutQuestions(p)
                    || (p.evidenceAssetId == null && (p.note == null || p.note.isBlank())))) {
                invalid("课件无需题单须显式填写零题数、空题单及课件核实依据");
            }
            if (p.evidenceAssetId != null) { ready(p.evidenceAssetId); }
        }
        consecutive(order);
        validateProducts(products, input.products);
        Set<Integer> imageOrder = new HashSet<>(); Set<Long> imageIds = new HashSet<>();
        for (ImageInput image : input.images) {
            positive(image.assetId); sequence(image.sortOrder, imageOrder);
            if (!imageIds.add(image.assetId)) { invalid("发布图片重复"); }
            if (!ready(image.assetId).getContentType().startsWith("image/")) { invalid("发布图片资产不是图片"); }
        }
        consecutive(imageOrder);
        Set<Integer> sourceOrder = new HashSet<>(); Set<Long> sourceIds = new HashSet<>();
        for (ReferenceInput source : input.referenceSources) {
            positive(source.sourceId); sequence(source.sortOrder, sourceOrder);
            if (!sourceIds.add(source.sourceId)) { invalid("参考来源重复"); }
        }
        consecutive(sourceOrder);
        if (!sourceIds.isEmpty() && mapper.countSources(new ArrayList<>(sourceIds)) != sourceIds.size()) { invalid("参考来源不存在"); }
        Set<Integer> shareOrder = new HashSet<>(); Set<String> shareUrls = new HashSet<>();
        for (ShareInput share : input.baiduShares) {
            validateShare(share, products); sequence(share.sortOrder, shareOrder);
            if (!shareUrls.add(share.shareUrl.trim())) { invalid("网盘分享链接重复"); }
        }
        consecutive(shareOrder);
    }
    private void validateProducts(Set<Long> ids, List<ProductInput> input) {
        if (ids.isEmpty()) { return; }
        List<ProductFact> facts = mapper.productFacts(new ArrayList<>(ids));
        if (facts.size() != ids.size()) { invalid("引用的成品不存在"); }
        facts.forEach(p -> ready(p.assetId));
        List<FrozenQuestion> frozen = mapper.frozenQuestions(new ArrayList<>(ids));
        for (ProductInput p : input) {
            List<FrozenQuestion> list = frozen.stream().filter(f -> f.productId.equals(p.productId)).toList();
            ProductFact fact = facts.stream().filter(f -> f.id.equals(p.productId)).findFirst().orElseThrow();
            if ("NOT_APPLICABLE".equals(p.questionListStatus)
                    && (!"COURSEWARE".equals(fact.resourceType) || !list.isEmpty())) {
                invalid("仅已确认为课件且没有冻结题单的成品可声明无需题单");
            }
            if (!list.isEmpty() && (list.size() != p.questions.size() || list.stream().anyMatch(f ->
                    p.questions.stream().noneMatch(q -> Objects.equals(q.seq, f.seq) && Objects.equals(q.questionId, f.questionId))))) {
                invalid("发布题单与成品原始冻结题单不一致");
            }
        }
    }
    private void validateShare(ShareInput share, Set<Long> products) {
        if (share.shareUrl == null || share.shareUrl.isBlank()) { invalid("网盘分享地址必填"); }
        share.shareUrl = share.shareUrl.trim();
        try {
            URI uri = URI.create(share.shareUrl);
            if (!"https".equals(uri.getScheme()) || !"pan.baidu.com".equalsIgnoreCase(uri.getHost())
                    || uri.getUserInfo() != null || uri.getPort() != -1 || !uri.getPath().startsWith("/s/")) { invalid("请填写真实百度网盘HTTPS分享地址"); }
        } catch (IllegalArgumentException e) { invalid("网盘分享地址无效"); }
        if (share.files == null || share.sortOrder == null || share.sortOrder < 1) { invalid("网盘映射或顺序缺失"); }
        Set<Long> seen = new HashSet<>();
        for (FileInput file : share.files) {
            if (!products.contains(file.productId) || !seen.add(file.productId)) { invalid("网盘文件必须对应本资料的不同成品"); }
            if (file.remoteFileName == null || file.remoteFileName.isBlank() || (file.remoteFileSize != null && file.remoteFileSize < 0)) {
                invalid("网盘文件名必填，文件字节数须为非负数");
            }
            oneOf(file.mappingStatus, "PENDING", "VERIFIED");
            if ("VERIFIED".equals(file.mappingStatus) && (file.verificationNote == null || file.verificationNote.isBlank())) { invalid("网盘映射核实依据必填"); }
        }
    }
    public void replace(Long id, Input input) {
        mapper.deleteFiles(id); mapper.deleteQuestions(id); mapper.deleteProducts(id);
        mapper.deleteSources(id); mapper.deleteImages(id); mapper.deleteShares(id);
        List<PublicationProduct> products = writeProducts(id, input.products);
        for (ReferenceInput i : input.referenceSources) {
            PublicationReferenceSource r = support.stamp(support.json.convertValue(i, PublicationReferenceSource.class));
            r.publicationId = id; mapper.insertPublicationReferenceSource(r);
        }
        for (ImageInput i : input.images) {
            PublicationImage r = support.stamp(support.json.convertValue(i, PublicationImage.class));
            r.publicationId = id; mapper.insertPublicationImage(r);
        }
        for (ShareInput i : input.baiduShares) { writeShare(id, i, products); }
    }
    private List<PublicationProduct> writeProducts(Long id, List<ProductInput> input) {
        List<PublicationProduct> results = new ArrayList<>();
        for (ProductInput i : input) {
            PublicationProduct row = support.stamp(support.json.convertValue(i, PublicationProduct.class));
            row.publicationId = id;
            if (List.of("VERIFIED", "NOT_APPLICABLE").contains(row.questionListStatus)) {
                row.verifiedBy = support.actor(); row.verifiedAt = new java.util.Date();
            }
            mapper.insertPublicationProduct(row); results.add(row);
            for (QuestionInput q : i.questions) {
                PublicationQuestion qr = support.stamp(support.json.convertValue(q, PublicationQuestion.class));
                qr.publicationProductId = row.id; mapper.insertPublicationQuestion(qr);
            }
        }
        return results;
    }
    private void writeShare(Long id, ShareInput input, List<PublicationProduct> products) {
        PublicationBaiduShare row = support.stamp(support.json.convertValue(input, PublicationBaiduShare.class));
        row.publicationId = id; row.shareUrlHash = PublicationSupport.digest(input.shareUrl.trim());
        mapper.insertPublicationBaiduShare(row);
        for (FileInput f : input.files) {
            PublicationBaiduFile file = support.stamp(support.json.convertValue(f, PublicationBaiduFile.class));
            file.publicationId = id; file.baiduShareId = row.id;
            file.publicationProductId = products.stream().filter(p -> p.productId.equals(f.productId)).findFirst().orElseThrow().id;
            if ("VERIFIED".equals(f.mappingStatus)) { file.verifiedBy = support.actor(); file.verifiedAt = new java.util.Date(); }
            mapper.insertPublicationBaiduFile(file);
        }
    }
    private com.ruoyi.knowledge.storage.domain.KbAsset ready(Long assetId) {
        try { return assets.requireReady(assetId); }
        catch (com.ruoyi.common.exception.ServiceException e) { throw fail(400, "INVALID_REFERENCE", "引用文件不存在或不可用：" + assetId); }
    }
    static boolean withoutQuestions(ProductInput input) {
        return "NOT_APPLICABLE".equals(input.questionListStatus) && Integer.valueOf(0).equals(input.expectedQuestionCount)
                && input.questions != null && input.questions.isEmpty();
    }
    private static void positive(Long id) { if (id == null || id <= 0) { invalid("关联ID无效"); } }
    private static void sequence(Integer value, Set<Integer> seen) {
        if (value == null || value < 1 || !seen.add(value)) { invalid("排序必须为不重复的正整数"); }
    }
    private static void consecutive(Set<Integer> seq) {
        for (int i = 1; i <= seq.size(); i++) { if (!seq.contains(i)) { invalid("排序必须从1连续"); } }
    }
    private static void oneOf(String value, String... allowed) {
        if (value == null || !List.of(allowed).contains(value)) { invalid("枚举值无效"); }
    }
    private static void invalid(String message) { throw fail(400, "INVALID_REFERENCE", message); }
}
