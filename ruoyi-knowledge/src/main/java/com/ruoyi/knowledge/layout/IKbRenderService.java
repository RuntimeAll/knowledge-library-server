package com.ruoyi.knowledge.layout;

import java.util.List;

public interface IKbRenderService {
    KbRenderValidation validate(KbRenderRequest request);

    KbRenderJob submit(KbRenderRequest request);

    List<KbRenderJob> list(KbRenderJob query);

    KbRenderJob get(Long id);

    KbRenderJob retry(Long id);

    void delete(Long id);

    Long downloadAsset(Long id, String outputKey, String kind);

    void assertAssetDownload(Long assetId);
}
