package com.ruoyi.knowledge.layout;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;

class KbRenderWorkerTest {
    @TempDir Path root;

    @Test
    void browserErrorKeepsQuestionAndCauseWithoutExposingStack() {
        String message =
                "page.evaluate: Error: 题目176公式越出正文区域\n"
                    + "    at eval (<anonymous>:88:3)\n"
                    + "    at UtilityScript.evaluate";
        String raw =
                "browser diagnostic\n"
                        + com.alibaba.fastjson2.JSONObject.of(
                                        "code", "RENDER_FAILED", "message", message)
                                .toJSONString()
                        + "\n";
        assertEquals("题目176公式越出正文区域", KbRenderWorker.errorMessage(raw));
        assertEquals(
                "题目7缺少图片资产12",
                KbRenderWorker.errorMessage(
                        com.alibaba.fastjson2.JSONObject.of("message", "题目7缺少图片资产12")
                                .toJSONString()));
    }

    @Test
    void outputCannotEscapeTaskDirectory() throws Exception {
        Path inside = Files.createDirectories(root.resolve("job"));
        Path outside = Files.writeString(root.resolve("secret"), "x");
        assertThrows(
                KbRenderWorker.RenderFailure.class,
                () -> KbRenderWorker.resolve(inside, outside.toString()));
        assertThrows(
                KbRenderWorker.RenderFailure.class,
                () -> KbRenderWorker.resolve(inside, "../secret"));
        Path safe = Files.writeString(inside.resolve("ok.pdf"), "%PDF-");
        assertEquals(safe.toRealPath(), KbRenderWorker.resolve(inside, "ok.pdf"));
    }
}
