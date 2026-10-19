package com.ruoyi.knowledge.layout;

import com.alibaba.fastjson2.*;
import com.ruoyi.knowledge.catalog.*;
import com.ruoyi.knowledge.domain.*;

import java.util.*;

public class KbRendererManifest {
    private String schemaVersion;
    private String rendererFingerprint;
    private String inputHash;
    private List<KbRendererOutput> outputs;

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(String value) {
        schemaVersion = value;
    }

    public String getRendererFingerprint() {
        return rendererFingerprint;
    }

    public void setRendererFingerprint(String value) {
        rendererFingerprint = value;
    }

    public String getInputHash() {
        return inputHash;
    }

    public void setInputHash(String value) {
        inputHash = value;
    }

    public List<KbRendererOutput> getOutputs() {
        return outputs;
    }

    public void setOutputs(List<KbRendererOutput> value) {
        outputs = value;
    }
}
