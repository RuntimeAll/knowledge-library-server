package com.ruoyi.knowledge.service.impl;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.knowledge.domain.*;
import com.ruoyi.knowledge.dto.KbAgentProductRequest;
import com.ruoyi.knowledge.dto.KbAgentSourceRequest;
import com.ruoyi.knowledge.mapper.KbAgentSubmissionMapper;
import com.ruoyi.knowledge.service.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class KbAgentIngestionServiceImpl implements IKbAgentIngestionService {
    private final KbAgentSubmissionMapper submissions;
    private final IKbSourceService sources;
    private final IKbProductService products;

    public KbAgentIngestionServiceImpl(
            KbAgentSubmissionMapper submissions,
            IKbSourceService sources,
            IKbProductService products) {
        this.submissions = submissions;
        this.sources = sources;
        this.products = products;
    }

    public KbSource findSource(String key, String actor) {
        KnowledgeRules.text(key, "稳定外部标识", 160);
        Long id = submissions.findResource(actor, "SOURCE", key);
        return id == null ? null : sources.get(id);
    }

    public KbProduct findProduct(String key, String actor) {
        KnowledgeRules.text(key, "稳定外部标识", 160);
        Long id = submissions.findResource(actor, "PRODUCT", key);
        return id == null ? null : products.get(id);
    }

    @Transactional
    public KbSource saveSource(KbAgentSourceRequest request, String actor) {
        if (request == null || request.source() == null) throw new ServiceException("缺少来源内容", 400);
        KbSource input = request.source();
        if (request.profile() != null) input.setProfile(request.profile());
        if (request.outlineScopes() != null) input.setOutlineScopes(request.outlineScopes());
        if (request.declaredKpIds() != null) input.setDeclaredKpIds(request.declaredKpIds());
        if (request.regions() != null) input.setRegions(request.regions());
        if (request.examDetail() != null) input.setExamDetail(request.examDetail());
        if (input.getId() != null) throw new ServiceException("此入口用于登记来源，修改请使用带版本的来源维护接口", 400);
        KbAgentSubmission prior = claim(actor, "SOURCE", request.externalKey(), input);
        if (prior.getResourceId() != null) return sources.get(prior.getResourceId());
        input.setCreateBy(actor);
        KbSource saved = sources.create(input);
        KnowledgeRules.changed(
                submissions.complete(actor, "SOURCE", request.externalKey(), saved.getId()));
        return saved;
    }

    @Transactional
    public KbProduct saveProduct(KbAgentProductRequest request, String actor) {
        if (request == null || request.product() == null) throw new ServiceException("缺少成品内容", 400);
        KbProduct input = request.product();
        if (request.profile() != null) input.setProfile(request.profile());
        if (request.outlineScopes() != null) input.setOutlineScopes(request.outlineScopes());
        if (request.declaredKpIds() != null) input.setDeclaredKpIds(request.declaredKpIds());
        if (request.regions() != null) input.setRegions(request.regions());
        if (request.questionRefs() != null) input.setQuestionRefs(request.questionRefs());
        if (request.referenceSources() != null)
            input.setReferenceSources(request.referenceSources());
        if (request.expectedMaterialRowVersion() != null)
            input.setExpectedMaterialRowVersion(request.expectedMaterialRowVersion());
        if (input.getId() != null) throw new ServiceException("新成品或新版本不能指定已有成品ID", 400);
        KbAgentSubmission prior = claim(actor, "PRODUCT", request.externalKey(), input);
        if (prior.getResourceId() != null) return products.get(prior.getResourceId());
        input.setCreateBy(actor);
        KbProduct saved = products.create(input);
        KnowledgeRules.changed(
                submissions.complete(actor, "PRODUCT", request.externalKey(), saved.getId()));
        return saved;
    }

    private KbAgentSubmission claim(String actor, String operation, String key, Object payload) {
        String clean = KnowledgeRules.text(key, "稳定外部标识", 160);
        if (!clean.equals(key)) throw new ServiceException("外部标识不能包含首尾空格", 400);
        String hash;
        try {
            hash =
                    HexFormat.of()
                            .formatHex(
                                    MessageDigest.getInstance("SHA-256")
                                            .digest(
                                                    JSON.toJSONString(payload)
                                                            .getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
        submissions.claim(actor, operation, key, hash);
        KbAgentSubmission prior = submissions.lock(actor, operation, key);
        if (prior == null || !hash.equals(prior.getRequestHash()))
            throw new ServiceException("同一外部标识的内容已变化；新版本请使用新的标识", 409);
        return prior;
    }
}
