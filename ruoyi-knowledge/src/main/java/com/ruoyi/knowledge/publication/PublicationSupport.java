package com.ruoyi.knowledge.publication;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.knowledge.review.KbReviewGate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HexFormat;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class PublicationSupport {
    final ObjectMapper json;
    final TransactionTemplate tx;
    final KbReviewGate gate;
    private final PublicationProperties properties;
    public PublicationSupport(PlatformTransactionManager manager,
            KbReviewGate gate, PublicationProperties properties) {
        this.json = new ObjectMapper().configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        this.tx = new TransactionTemplate(manager);
        this.gate = gate;
        this.properties = properties;
    }
    public <T> T gated(Supplier<T> action) {
        gate.enter();
        try { return action.get(); } finally { gate.leave(); }
    }
    public <T> T transaction(Supplier<T> action) { return tx.execute(s -> action.get()); }
    public void enabled() {
        if (!properties.isEnabled()) { throw fail(503, "DEPENDENCY_UNAVAILABLE", "发布台尚未启用"); }
    }
    public void permission(String action) {
        enabled();
        if (!SecurityUtils.hasPermi("knowledge:publication:" + action)) {
            throw fail(403, "FORBIDDEN", "无此发布台操作权限");
        }
    }
    public boolean has(String action) { return SecurityUtils.hasPermi("knowledge:publication:" + action); }
    public String actor() { return SecurityUtils.getUsername(); }
    public boolean sameActor(PublicationRows.PublicationOperation operation) {
        return Objects.equals(operation.actorId, SecurityUtils.getUserId()) && Objects.equals(operation.actorName, actor());
    }
    public boolean registrationOnly() { return !SecurityUtils.isAdmin() && !has("all"); }
    public void own(PublicationRows.Publication row) {
        // A dedicated registration account cannot browse another creator's records.
        if (registrationOnly() && !Objects.equals(row.createBy, actor())) {
            throw fail(403, "FORBIDDEN", "此账号无权管理该发布资料");
        }
    }
    public void versions(PublicationRows.Publication row, PublicationModels.Command c) {
        if (!Objects.equals(row.rowVersion, c.expectedRowVersion)
                || !Objects.equals(row.contentVersion, c.contentVersion)) {
            throw fail(409, "VERSION_CONFLICT", "资料版本已变化，请刷新后重检");
        }
    }
    public <T extends PublicationRows.BaseRow> T stamp(T row) {
        row.rowVersion = 0L; row.createBy = actor(); row.updateBy = actor();
        row.createTime = new Date(); row.updateTime = new Date();
        return row;
    }
    public void updated(PublicationRows.BaseRow row) { row.updateBy = actor(); row.updateTime = new Date(); }
    public String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("发布数据不能序列化", e); }
    }
    public <T> T decode(String text, Class<T> type) {
        if (text == null) { return null; }
        try { return json.readValue(text, type); }
        catch (JsonProcessingException e) { throw fail(503, "DEPENDENCY_UNAVAILABLE", "发布回执无法核对"); }
    }
    public String hash(Object value) { return digest(encode(value)); }
    public static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public String deliveryMode() { return properties.getDeliveryMode().name(); }
    public String publicUrl(String code) {
        if (properties.getDeliveryMode() != PublicationProperties.DeliveryMode.PUBLIC_PAGE) { return null; }
        return publicOrigin() + "/p/" + code;
    }
    private String publicOrigin() {
        try {
            String base = properties.getPublicBaseUrl();
            java.net.URI uri = new java.net.URI(base == null ? "" : base);
            boolean protocol = "https".equals(uri.getScheme())
                    || (properties.isAllowHttp() && "http".equals(uri.getScheme()));
            if (!protocol || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || !(uri.getRawPath().isEmpty() || "/".equals(uri.getRawPath()))
                    || uri.getPort() == 0 || uri.getPort() > 65535) {
                throw new java.net.URISyntaxException(base == null ? "" : base, "invalid origin");
            }
            return uri.getScheme() + "://" + uri.getRawAuthority();
        } catch (java.net.URISyntaxException e) {
            throw fail(503, "GENERATION_FAILED", "请配置有效在线入口；HTTP入口需显式启用allow-http");
        }
    }

    public static PublicationException fail(int code, String key, String message) {
        return new PublicationException(code, key, message);
    }
    public static String id(Long value) { return value == null ? null : value.toString(); }
}
