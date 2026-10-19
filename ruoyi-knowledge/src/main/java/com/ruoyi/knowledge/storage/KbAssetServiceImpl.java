package com.ruoyi.knowledge.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.knowledge.storage.domain.KbAsset;
import com.ruoyi.knowledge.storage.mapper.KbAssetMapper;
import com.ruoyi.knowledge.html.KbSvgSafety;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class KbAssetServiceImpl implements IKbAssetService {
    private static final Set<String> PURPOSES = Set.of("SOURCE_ORIGINAL", "PRODUCT_ORIGINAL", "QUESTION_IMAGE", "IMPORT_HTML", "IMPORT_RESOURCE");
    private final KbAssetMapper mapper;
    private final ObjectStorage storage;
    private final KbStorageProperties properties;
    private final TransactionTemplate transactions;

    public KbAssetServiceImpl(KbAssetMapper mapper, ObjectStorage storage, KbStorageProperties properties,
                             PlatformTransactionManager transactionManager) {
        this.mapper = mapper;
        this.storage = storage;
        this.properties = properties;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public KbAsset getAsset(Long assetId) {
        KbAsset asset = assetId == null ? null : mapper.selectById(assetId);
        if (asset == null) { throw new ServiceException("文件不存在"); }
        return asset;
    }

    @Override
    public KbAsset requireReady(Long assetId) {
        KbAsset asset = mapper.selectByIdForUpdate(assetId);
        if (asset == null) { throw new ServiceException("文件不存在"); }
        if (!"READY".equals(asset.getStatus())) { throw new ServiceException("文件尚未就绪或已删除"); }
        return asset;
    }

    @Override
    public KbAsset requireOriginal(Long assetId) {
        KbAsset asset = requireReady(assetId);
        if (!Set.of("SOURCE_ORIGINAL", "PRODUCT_ORIGINAL").contains(asset.getPurpose()))
            throw new ServiceException("来源和成品须引用原件资产，题图或导入中间文件不能作为原件", 400);
        return asset;
    }

    @Override
    public KbAsset upload(MultipartFile file, String purpose, String testRunId) {
        return upload(file, purpose, testRunId, null);
    }

    @Override
    public KbAsset upload(MultipartFile file, String purpose, String testRunId, String idempotencyKey) {
        if (file == null || file.isEmpty()) { throw new ServiceException("上传文件不能为空"); }
        if (file.getSize() > properties.getMaxFileSize()) { throw new ServiceException("文件超过知识库上传大小限制"); }
        try {
            return uploadBytes(file.getBytes(), file.getOriginalFilename(), file.getContentType(), purpose, testRunId, idempotencyKey);
        } catch (IOException e) {
            throw new ServiceException("读取上传文件失败");
        }
    }

    @Override
    public KbAsset findOwnUpload(String externalKey) {
        if (externalKey == null || externalKey.isBlank() || externalKey.length() > 128)
            throw new ServiceException("文件幂等键长度必须为1至128字符", 400);
        return mapper.selectByExternalKey(SecurityUtils.getUsername(), externalKey);
    }

    @Override
    public KbAsset uploadBytes(byte[] bytes, String originalName, String contentType, String purpose, String testRunId) {
        return uploadBytes(bytes, originalName, contentType, purpose, testRunId, null);
    }

    @Override
    public KbAsset uploadBytes(byte[] bytes, String originalName, String contentType, String purpose, String testRunId, String externalKey) {
        if (bytes == null || bytes.length == 0 || bytes.length > properties.getMaxFileSize()) {
            throw new ServiceException("文件为空或超过知识库上传大小限制");
        }
        if (!PURPOSES.contains(purpose)) { throw new ServiceException("文件用途无效"); }
        String runId = validateTestRunId(testRunId);
        String name = cleanName(originalName);
        String type = contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType;
        if ("QUESTION_IMAGE".equals(purpose)) { type = imageType(bytes); }
        if (properties.getBucket() == null || properties.getBucket().isBlank()) { throw new ServiceException("知识库OSS未配置"); }
        KbAsset asset = new KbAsset();
        asset.setPurpose(purpose);
        asset.setStorageProvider("ALIYUN_OSS");
        asset.setBucketName(properties.getBucket());
        asset.setObjectKey(objectKey(purpose, name, runId));
        asset.setOriginalName(name);
        asset.setContentType(type);
        asset.setFileSize((long) bytes.length);
        asset.setSha256(sha256(bytes));
        asset.setStatus("UPLOADING");
        asset.setUploadToken(UUID.randomUUID().toString());
        asset.setTestRunId(runId);
        asset.setCreateBy(SecurityUtils.getUsername());
        if (externalKey != null && (externalKey.isBlank() || externalKey.length() > 128)) { throw new ServiceException("文件幂等键长度必须为1至128字符"); }
        asset.setExternalKey(externalKey);
        KbAsset existing = externalKey == null ? null : mapper.selectByExternalKey(asset.getCreateBy(), externalKey);
        if (existing == null) {
            try { mapper.insert(asset); }
            catch (DuplicateKeyException e) {
                if (externalKey == null) { throw e; }
                existing = mapper.selectByExternalKey(asset.getCreateBy(), externalKey);
                if (existing == null) { throw e; }
            }
        }
        boolean retry = existing != null;
        if (existing != null) {
            if (!asset.getSha256().equals(existing.getSha256()) || !purpose.equals(existing.getPurpose())
                    || !name.equals(existing.getOriginalName()) || !java.util.Objects.equals(runId, existing.getTestRunId())) {
                throw new ServiceException("文件幂等键已用于不同内容或用途");
            }
            if ("READY".equals(existing.getStatus())) { return existing; }
            String uploadToken = asset.getUploadToken();
            if (mapper.claimRetry(existing.getAssetId(), uploadToken) != 1) {
                throw new ServiceException("文件正在处理或已删除，请按原幂等键查询；上传中断超过10分钟可用原请求恢复", 409);
            }
            asset = existing;
            asset.setUploadToken(uploadToken);
        }
        try {
            ObjectStorage.StoredObject remote = retry ? storage.stat(asset.getBucketName(), asset.getObjectKey()) : null;
            if (remote != null) {
                if (!asset.getSha256().equals(remote.sha256()) || remote.contentLength() != bytes.length) {
                    throw new ServiceException("OSS对象与原请求不一致，拒绝覆盖");
                }
                asset.setOssVersionId(remote.versionId());
            } else {
                ObjectStorage.PutResult result = storage.put(asset.getBucketName(), asset.getObjectKey(), bytes, type);
                asset.setOssVersionId(result.versionId());
            }
            asset.setStatus("READY");
            asset.setFailureMessage(null);
            asset.setUpdateBy(asset.getCreateBy());
            if (mapper.finishUpload(asset) != 1)
                throw new ServiceException("上传处理权已转交恢复请求，请按原幂等键查询结果", 409);
            return asset;
        } catch (RuntimeException e) {
            // An upload can have succeeded remotely before a timeout. Keep the exact key for reconciliation.
            asset.setStatus("FAILED");
            asset.setFailureMessage("OSS上传或文件登记失败，请按文件编号重试清理");
            mapper.finishUpload(asset);
            throw new ServiceException("文件上传失败，文件编号：" + asset.getAssetId());
        }
    }

    @Override
    public InputStream openContent(Long assetId) {
        KbAsset asset = requireReady(assetId);
        try { return storage.open(asset.getBucketName(), asset.getObjectKey(), asset.getOssVersionId()); }
        catch (RuntimeException e) { throw new ServiceException("OSS文件读取失败，请稍后重试"); }
    }

    @Override
    public Preview preview(Long assetId) {
        KbAsset asset = requireReady(assetId);
        if (!("QUESTION_IMAGE".equals(asset.getPurpose()) && "image/svg+xml".equals(asset.getContentType()))
                && !Set.of("image/png", "image/jpeg", "image/gif", "image/webp", "application/pdf").contains(asset.getContentType())) {
            throw new ServiceException("此文件请下载原件查看");
        }
        return new Preview(storage.previewUrl(asset.getBucketName(), asset.getObjectKey(), asset.getOssVersionId(), 600),
                600, asset.getContentType());
    }

    @Override
    public void delete(Long assetId) {
        KbAsset asset = transactions.execute(status -> {
            KbAsset locked = mapper.selectByIdForUpdate(assetId);
            if (locked == null) { throw new ServiceException("文件不存在"); }
            if ("DELETED".equals(locked.getStatus())) { return locked; }
            if (mapper.countReferences(assetId) != 0) { throw new ServiceException("文件仍被题目、来源或成品版本引用，不能删除"); }
            if (!locked.getObjectKey().startsWith(normalizedPrefix() + "/") || !properties.getBucket().equals(locked.getBucketName())) {
                throw new ServiceException("文件不属于本知识库，不能删除");
            }
            if (mapper.markDeletePending(assetId, SecurityUtils.getUsername()) != 1) { throw new ServiceException("文件状态不允许删除"); }
            return locked;
        });
        if (asset == null) { throw new ServiceException("文件不存在"); }
        if ("DELETED".equals(asset.getStatus())) { return; }
        try {
            boolean remoteExists = true;
            if (asset.getOssVersionId() == null || asset.getOssVersionId().isBlank()) {
                ObjectStorage.StoredObject remote = storage.stat(asset.getBucketName(), asset.getObjectKey());
                if (remote != null && !asset.getSha256().equals(remote.sha256())) {
                    throw new ServiceException("OSS对象与登记哈希不符，拒绝删除");
                }
                if (remote != null) { asset.setOssVersionId(remote.versionId()); }
                remoteExists = remote != null;
            }
            if (remoteExists) {
                storage.delete(asset.getBucketName(), asset.getObjectKey(), asset.getOssVersionId());
            }
            asset.setStatus("DELETED");
            asset.setFailureMessage(null);
            mapper.updateState(asset);
        } catch (RuntimeException e) {
            asset.setStatus("DELETE_PENDING");
            asset.setFailureMessage("OSS删除失败，可按同一文件编号重试");
            mapper.updateState(asset);
            throw new ServiceException("OSS删除失败，文件保留在待清理状态");
        }
    }

    @Override
    public String validateTestRunId(String testRunId) {
        if (testRunId == null || testRunId.isBlank()) { return null; }
        if (!properties.isTestMode() || !testRunId.matches("[a-zA-Z0-9_-]{1,64}")) {
            throw new ServiceException("测试批次头仅在测试模式有效且必须为安全标识");
        }
        return testRunId;
    }

    private String objectKey(String purpose, String name, String runId) {
        String extension = name.contains(".") ? name.substring(name.lastIndexOf('.')).toLowerCase(Locale.ROOT) : "";
        if (!extension.matches("\\.[a-z0-9]{1,12}")) { extension = ""; }
        String prefix = normalizedPrefix() + (runId == null ? "" : "/e2e/" + runId);
        return prefix + "/" + purpose.toLowerCase(Locale.ROOT) + "/"
                + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd")) + "/" + UUID.randomUUID() + extension;
    }

    private String normalizedPrefix() {
        String prefix = properties.getPrefix();
        if (prefix == null || !prefix.matches("knowledge-library(?:/[a-zA-Z0-9_-]+)*")) {
            throw new ServiceException("OSS目录必须位于knowledge-library之下");
        }
        return prefix;
    }

    private static String cleanName(String name) {
        if (name == null || name.isBlank()) { throw new ServiceException("文件名不能为空"); }
        String clean = name.replace('\\', '/');
        clean = clean.substring(clean.lastIndexOf('/') + 1).replaceAll("[\\r\\n\\x00]", "");
        if (clean.isBlank() || clean.length() > 255) { throw new ServiceException("文件名长度无效"); }
        return clean;
    }

    public static String imageType(byte[] data) {
        String beginning = new String(data, 0, Math.min(data.length, 1024), StandardCharsets.UTF_8).stripLeading();
        if (beginning.startsWith("<svg") || (beginning.startsWith("<?xml") && beginning.contains("<svg"))) {
            KbSvgSafety.validateBytes(data);
            return "image/svg+xml";
        }
        if (data.length >= 8 && data[0] == (byte) 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') { return "image/png"; }
        if (data.length >= 3 && data[0] == (byte) 0xff && data[1] == (byte) 0xd8 && data[2] == (byte) 0xff) { return "image/jpeg"; }
        if (data.length >= 6 && new String(data, 0, 6, StandardCharsets.US_ASCII).matches("GIF8[79]a")) { return "image/gif"; }
        if (data.length >= 12 && new String(data, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                && new String(data, 8, 4, StandardCharsets.US_ASCII).equals("WEBP")) { return "image/webp"; }
        throw new ServiceException("题图仅支持内容有效的PNG、JPEG、GIF或WebP文件");
    }

    public static String sha256(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
