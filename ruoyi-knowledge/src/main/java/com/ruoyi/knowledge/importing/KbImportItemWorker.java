package com.ruoyi.knowledge.importing;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.knowledge.domain.KbOccurrence;
import com.ruoyi.knowledge.domain.KbQuestion;
import com.ruoyi.knowledge.html.HtmlMetadata;
import com.ruoyi.knowledge.html.HtmlQuestionDocument;
import com.ruoyi.knowledge.html.KbHtmlCodec;
import com.ruoyi.knowledge.importing.domain.KbImportBatch;
import com.ruoyi.knowledge.importing.domain.KbImportIdentity;
import com.ruoyi.knowledge.importing.domain.KbImportItem;
import com.ruoyi.knowledge.importing.domain.KbImportResource;
import com.ruoyi.knowledge.importing.mapper.KbImportMapper;
import com.ruoyi.knowledge.service.IKbQuestionService;
import com.ruoyi.knowledge.service.IKbSourceService;
import com.ruoyi.knowledge.storage.KbAssetServiceImpl;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** One question, its provenance and result receipt commit together. */
@Service
public class KbImportItemWorker {
    private final IKbQuestionService questions;
    private final IKbSourceService sources;
    private final KbImportMapper mapper;
    private final KbHtmlCodec codec;

    public KbImportItemWorker(IKbQuestionService questions, IKbSourceService sources, KbImportMapper mapper, KbHtmlCodec codec) {
        this.questions = questions;
        this.sources = sources;
        this.mapper = mapper;
        this.codec = codec;
    }

    @com.ruoyi.knowledge.review.KbQualityWrite(dependency = "NONE")
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public KbImportItem write(KbImportBatch batch, int order, ResolvedArticle resolved) {
        guard(batch);
        KbImportItem previous = mapper.selectItem(batch.getBatchId(), order);
        if (previous != null) return previous;
        HtmlQuestionDocument document = resolved.document();
        KbImportIdentity identity = null;
        if (!"update".equals(document.intent())) {
            KbImportIdentity existing = mapper.selectIdentity(batch.getCreateBy(), document.clientKey());
            if (existing != null) { return reused(batch, order, document, resolved.fingerprint(), existing); }
            identity = new KbImportIdentity();
            identity.setClientKey(document.clientKey());
            identity.setCreateBy(batch.getCreateBy());
            identity.setRequestFingerprint(resolved.fingerprint());
            identity.setQuestionId(0L);
            // Unique key claims the external identity before a new question is created.
            mapper.insertIdentity(identity);
        }

        KbQuestion question = "update".equals(document.intent()) ? questions.get(document.questionId()) : new KbQuestion();
        if ("update".equals(document.intent())) {
            if (!document.rowVersion().equals(question.getRowVersion())) { throw new ServiceException("题目已被修改，请重新导出后再提交"); }
        } else {
            question.setStatus("DRAFT");
            question.setKpIds(List.of());
            question.setAssetIds(List.of());
            question.setCreationKind("derive".equals(document.intent()) ? "DERIVED" : "EXTRACTED");
        }
        HtmlMetadata meta = document.metadata();
        question.setStemHtml(resolved.stemHtml());
        question.setStemText(codec.text(resolved.stemHtml()));
        question.setStemHash(KbAssetServiceImpl.sha256(resolved.stemHtml().getBytes(StandardCharsets.UTF_8)));
        if (resolved.answerHtml() != null) { question.setAnswerHtml(resolved.answerHtml()); }
        if (resolved.analysisHtml() != null) { question.setAnalysisHtml(resolved.analysisHtml()); }
        if (meta.title() != null) { question.setTitle(meta.title()); }
        if (question.getTitle() == null || question.getTitle().isBlank()) {
            question.setTitle(question.getStemText().isBlank() ? "图形题" : question.getStemText().substring(0, Math.min(120, question.getStemText().length())));
        }
        if (meta.subject() != null) { question.setSubject(meta.subject()); }
        if (meta.contentKind() != null) { question.setContentKind(meta.contentKind()); }
        if (meta.layoutMode() != null) { question.setLayoutMode(meta.layoutMode()); }
        if (meta.unitKind() != null) { question.setUnitKind(meta.unitKind()); }
        if (meta.paginationMode() != null) { question.setPaginationMode(meta.paginationMode()); }
        if (meta.layoutNote() != null) { question.setLayoutNote(meta.layoutNote()); }
        if (meta.questionType() != null) { question.setQuestionType(meta.questionType()); }
        if (meta.difficulty() != null) { question.setDifficulty(meta.difficulty()); }
        if (meta.kpIds() != null) { question.setKpIds(meta.kpIds()); }
        if (meta.sourceNote() != null) { question.setSourceNote(meta.sourceNote()); }
        LinkedHashSet<Long> imageIds = new LinkedHashSet<>();
        // Derive references from the final merged content, including omitted regions kept from the old version.
        for (String content : new String[]{question.getStemHtml(), question.getAnswerHtml(), question.getAnalysisHtml()}) {
            for (Element image : Jsoup.parseBodyFragment(content == null ? "" : content).select("img[data-asset-id]")) {
                imageIds.add(Long.valueOf(image.attr("data-asset-id")));
            }
        }
        question.setAssetIds(List.copyOf(imageIds));
        question.setExpectedParentContentHash(document.expectedParentContentHash());
        question.setExpectedParentRowVersion(document.expectedParentRowVersion());
        question.setExpectedParentStemHash(document.expectedParentStemHash());
        question.setGranularityExceptionId(document.granularityExceptionId());
        question.setImportClientKey(document.clientKey());
        question.setCandidateArticleHash(document.candidateArticleHash());
        question.setImportSourceId(batch.getSourceId());
        question.setCreateBy(batch.getCreateBy());
        question.setUpdateBy(batch.getCreateBy());
        KbQuestion saved = switch (document.intent()) {
            case "create" -> questions.createImportedQuestion(question);
            case "update" -> questions.updateImportedQuestion(question);
            case "derive" -> questions.deriveImportedQuestion(document.parentId(), question,
                    meta.auxiliaryParentIds() == null ? List.of() : meta.auxiliaryParentIds(),
                    meta.operation(), meta.changeNote(), meta.preservedNote());
            default -> throw new ServiceException("不支持的导入意图");
        };
        if (identity != null) {
            identity.setQuestionId(saved.getId());
            mapper.updateIdentity(identity);
        }
        ensureSource(batch, saved.getId());
        questions.bindReviewImport(saved, question, batch.getCreateBy(), resolved.fingerprint());
        KbImportItem item = item(batch, order, document, "SUCCESS", saved.getId(), "导入成功");
        mapper.insertItem(item);
        return item;
    }

    @com.ruoyi.knowledge.review.KbQualityWrite(dependency = "NONE")
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public KbImportItem reuse(KbImportBatch batch, int order, HtmlQuestionDocument document, String fingerprint) {
        guard(batch);
        KbImportItem previous = mapper.selectItem(batch.getBatchId(), order);
        if (previous != null) return previous;
        KbImportIdentity identity = mapper.selectIdentity(batch.getCreateBy(), document.clientKey());
        if (identity == null) { return null; }
        return reused(batch, order, document, fingerprint, identity);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void recordFailure(KbImportBatch batch, KbImportItem failure) {
        guard(batch);
        if (mapper.selectItem(batch.getBatchId(), failure.getItemOrder()) == null) {
            mapper.insertItem(failure);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void saveResource(KbImportBatch batch, KbImportResource resource) {
        guard(batch);
        KbImportResource old = mapper.selectResources(batch.getBatchId()).stream()
                .filter(value -> value.getResourceKey().equals(resource.getResourceKey()))
                .findFirst().orElse(null);
        if (old == null) mapper.insertResource(resource);
        else if (!old.getSha256().equals(resource.getSha256()) || !old.getAssetId().equals(resource.getAssetId())) {
            throw new ServiceException("已接收资源清单与原请求不一致", 409);
        }
    }

    private void guard(KbImportBatch batch) {
        // Held until this REQUIRES_NEW transaction commits. A new claim cannot
        // pass this row lock while question changes and the receipt are in flight.
        if (mapper.lockOwnedBatch(batch.getBatchId(), batch.getClaimToken()) == null) {
            throw new ServiceException("批次执行权已变化，旧执行者不能写入", 409);
        }
        if (mapper.renewClaim(batch.getBatchId(), batch.getClaimToken()) != 1) {
            throw new ServiceException("批次执行租约已失效", 409);
        }
    }

    private KbImportItem reused(KbImportBatch batch, int order, HtmlQuestionDocument document,
                                 String fingerprint, KbImportIdentity identity) {
        if (!fingerprint.equals(identity.getRequestFingerprint())) {
            throw new ServiceException("稳定外部键已对应其他内容；修改请使用update，变式请使用新的clientKey");
        }
        try {
            questions.get(identity.getQuestionId());
        } catch (ServiceException missing) {
            throw new ServiceException("该外部键原题已删除，不能通过重试复活；新建请使用新clientKey", 409);
        }
        ensureSource(batch, identity.getQuestionId());
        KbImportItem item = item(batch, order, document, "REUSED", identity.getQuestionId(), "已存在，复用原题");
        mapper.insertItem(item);
        return item;
    }

    private void ensureSource(KbImportBatch batch, Long questionId) {
        if (batch.getSourceId() != null && sources.occurrences(questionId, batch.getSourceId()).isEmpty()) {
            KbOccurrence occurrence = new KbOccurrence();
            occurrence.setSourceId(batch.getSourceId());
            occurrence.setQuestionId(questionId);
            occurrence.setMatchStatus("UNVERIFIED");
            occurrence.setCreateBy(batch.getCreateBy());
            sources.createOccurrence(occurrence);
        }
    }

    private KbImportItem item(KbImportBatch batch, int order, HtmlQuestionDocument document,
                              String status, Long questionId, String message) {
        KbImportItem item = new KbImportItem();
        item.setBatchId(batch.getBatchId());
        item.setItemOrder(order);
        item.setClientKey(document.clientKey());
        item.setIntent(document.intent());
        item.setStatus(status);
        item.setQuestionId(questionId);
        item.setMessage(message);
        return item;
    }
}
