package com.ruoyi.knowledge.layout;

import java.util.List;

/** Private process boundary; never accepted from a public HTTP client. */
public record KbRendererInput(
        String schemaVersion,
        String jobId,
        KbExperienceVersion template,
        KbRenderRequest config,
        List<KbRenderItem> items,
        List<KbLayoutAsset> assets) {}
