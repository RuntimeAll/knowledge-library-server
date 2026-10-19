package com.ruoyi.knowledge.layout;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "knowledge.render")
public class KbRenderProperties {
    private boolean enabled = true;
    private String nodeExecutable = "node";
    private String script = "../tools/layout-renderer/render.mjs";
    private String workDirectory = "../.runtime/render-jobs";
    private int timeoutSeconds = 1200;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean v) {
        enabled = v;
    }

    public String getNodeExecutable() {
        return nodeExecutable;
    }

    public void setNodeExecutable(String v) {
        nodeExecutable = v;
    }

    public String getScript() {
        return script;
    }

    public void setScript(String v) {
        script = v;
    }

    public String getWorkDirectory() {
        return workDirectory;
    }

    public void setWorkDirectory(String v) {
        workDirectory = v;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int v) {
        timeoutSeconds = v;
    }
}
