package com.ruoyi.knowledge.review;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.knowledge.importing.dto.KbRecordsRequest;
import com.ruoyi.knowledge.importing.mapper.KbImportMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Authenticated fixed-origin candidate proxy; browser never receives the pipeline credential. */
@Component
public class KbCandidateProxy {
    public record SourceLocator(String physicalPage, String printedPage, String questionNo, String note) { }
    public record ArtifactRef(String artifactId, String sha256, String mediaType, Long byteSize) { }
    public record Candidate(String schemaVersion, String runId, String groupKey, String role,
            String ownerUsername, String instanceId, String candidateKey, Integer candidateRevision,
            String clientKey, String candidateHash, String canonicalCandidateJson,
            KbRecordsRequest.Record record, String sourceId, SourceLocator sourceLocator,
            List<ArtifactRef> evidenceRefs, String createdAt, String workerKind, Boolean current,
            ReviewModels.GranularityException granularityExceptionReceipt) { }
    private final String baseUrl;
    private final String tokenFile;
    private final KbImportMapper imports;
    private final String writerUsername;
    private final String writerUserId;
    private final String ownerUsername;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    public KbCandidateProxy(@Value("${knowledge.review.pipelineUrl:http://127.0.0.1:18083}") String baseUrl,
            @Value("${knowledge.review.pipelineTokenFile:}") String tokenFile, KbImportMapper imports,
            @Value("${knowledge.review.writerUsername:}") String writerUsername,
            @Value("${knowledge.review.writerUserId:}") String writerUserId,
            @Value("${knowledge.review.ownerUsername:}") String ownerUsername) {
        this.baseUrl = baseUrl;
        this.tokenFile = tokenFile;
        this.imports = imports;
        this.writerUsername=writerUsername; this.writerUserId=writerUserId; this.ownerUsername=ownerUsername;
    }

    public Candidate get(String runId, String candidateKey) {
        if (!safeKey(runId) || !safeKey(candidateKey)) { throw new ServiceException("候选身份无效", 400); }
        URI base = URI.create(baseUrl);
        if (!"http".equals(base.getScheme()) || !"127.0.0.1".equals(base.getHost()) || base.getRawUserInfo() != null
                || base.getPort() < 1 || !List.of("", "/").contains(base.getPath()) || tokenFile.isBlank())
            { throw new ServiceException("候选服务尚未受控配置", 503); }
        try {
            String token = Files.readString(Path.of(tokenFile), StandardCharsets.UTF_8).trim();
            if (token.isBlank() || token.length() > 4096 || token.contains("\n")) { throw new ServiceException("候选服务凭据不可用", 503); }
            HttpRequest request = HttpRequest.newBuilder(base.resolve("/pipeline/runs/" + runId + "/candidates/" + candidateKey))
                    .timeout(Duration.ofSeconds(10)).header("Authorization", "Bearer " + token)
                    .header("X-Knowledge-User-Id", SecurityUtils.getUserId().toString())
                    .header("X-Knowledge-Username", SecurityUtils.getUsername()).GET().build();
            HttpResponse<java.io.InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            byte[] bytes;
            try (var body = response.body()) { bytes = body.readNBytes(2 * 1024 * 1024 + 1); }
            if (bytes.length > 2 * 1024 * 1024) { throw new ServiceException("候选快照超过2MiB", 409); }
            if (response.statusCode() != 200) { throw new ServiceException("候选服务读取失败，请检查权限、身份或快照状态", response.statusCode() == 403 ? 403 : 503); }
            strictJson(bytes);
            JSONObject envelope = JSON.parseObject(bytes);
            if (!"OK".equals(envelope.getString("code"))) { throw new ServiceException("候选服务未返回成功事实", 503); }
            JSONObject data = envelope.getJSONObject("data");
            Candidate candidate = typed(data);
            validate(candidate, data, runId, candidateKey);
            return candidate;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ServiceException("候选读取已中断", 503);
        } catch (java.io.IOException | com.alibaba.fastjson2.JSONException exception) {
            throw new ServiceException("候选快照无法读取或解析", 503);
        }
    }

    private void validate(Candidate candidate, JSONObject data, String runId, String candidateKey) {
        if (candidate == null || !"pipeline-candidate/v1".equals(candidate.schemaVersion())
                || !runId.equals(candidate.runId()) || !candidateKey.equals(candidate.candidateKey())
                || !canReadOwner(candidate.ownerUsername())
                || !Objects.equals(imports.selectInstanceId(), candidate.instanceId())
                || candidate.record() == null || !Objects.equals(candidate.clientKey(), candidate.record().clientKey())
                || candidate.canonicalCandidateJson() == null
                || !Objects.equals(candidate.candidateHash(), KbReviewLedger.hash(candidate.canonicalCandidateJson().getBytes(StandardCharsets.UTF_8))))
            { throw new ServiceException("候选身份或完整快照哈希不匹配", 409); }
        strictJson(candidate.canonicalCandidateJson().getBytes(StandardCharsets.UTF_8));
        JSONObject canonical = JSON.parseObject(candidate.canonicalCandidateJson());
        List<String> keys = List.of("schemaVersion", "runId", "groupKey", "role", "ownerUsername", "instanceId",
                "candidateKey", "candidateRevision", "clientKey", "record", "sourceId", "sourceLocator", "evidenceRefs");
        if (!canonical.keySet().equals(new java.util.HashSet<>(keys))) { throw new ServiceException("候选规范快照字段不完整", 409); }
        for (String key : keys) { if (!Objects.equals(canonical.get(key), data.get(key)))
            { throw new ServiceException("候选显示数据与冻结快照不一致：" + key, 409); } }
    }

    private boolean canReadOwner(String candidateOwner) {
        if (SecurityUtils.getUsername().equals(candidateOwner)) { return true; }
        return !writerUsername.isBlank() && !writerUserId.isBlank() && !ownerUsername.isBlank()
                && writerUsername.equals(SecurityUtils.getUsername()) && writerUserId.equals(SecurityUtils.getUserId().toString())
                && ownerUsername.equals(candidateOwner);
    }

    private static Candidate typed(JSONObject data) {
        try { return tools.jackson.databind.json.JsonMapper.builder().build().readValue(JSON.toJSONBytes(data),Candidate.class); }
        catch (tools.jackson.core.JacksonException error) { throw new ServiceException("Invalid candidate schema",409); }
    }

    private static void strictJson(byte[] bytes) {
        try {
            tools.jackson.databind.json.JsonMapper.builder().enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .build().readTree(bytes);
        } catch (tools.jackson.core.JacksonException exception) { throw new ServiceException("候选JSON含重复字段或无法解析", 409); }
    }

    private static boolean safeKey(String value) { return value != null && value.matches("[A-Za-z0-9._:-]{1,160}"); }
}
