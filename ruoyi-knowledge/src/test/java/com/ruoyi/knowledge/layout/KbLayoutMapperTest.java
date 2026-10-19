package com.ruoyi.knowledge.layout;

import static org.junit.jupiter.api.Assertions.*;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.util.Map;

class KbLayoutMapperTest {
    @Test
    void concurrentSubmitAndSourceDiscoveryBypassSessionCache() throws Exception {
        Configuration c = new Configuration();
        String resource = "mapper/knowledge/KbLayoutMapper.xml";
        try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, c, resource, c.getSqlFragments()).parse();
        }
        var own = c.getMappedStatement("com.ruoyi.knowledge.mapper.KbLayoutMapper.lockOwnKey");
        assertTrue(own.isFlushCacheRequired());
        assertFalse(own.isUseCache());
        assertTrue(
                own.getBoundSql(Map.of("actorUserId", 42L, "key", "key"))
                        .getSql()
                        .contains("FOR UPDATE"));
        var sources =
                c.getMappedStatement("com.ruoyi.knowledge.mapper.KbLayoutMapper.questionSourceIds");
        assertTrue(sources.isFlushCacheRequired());
        assertFalse(sources.isUseCache());
        assertTrue(
                sources.getBoundSql(Map.of("questionIds", java.util.List.of(7L)))
                        .getSql()
                        .contains("kb_question_source"));
        for (String method : java.util.List.of("submit", "validate")) {
            var transaction =
                    KbRenderServiceImpl.class
                            .getMethod(method, KbRenderRequest.class)
                            .getAnnotation(
                                    org.springframework.transaction.annotation.Transactional.class);
            assertEquals(
                    org.springframework.transaction.annotation.Isolation.READ_COMMITTED,
                    transaction.isolation());
        }
    }

    @Test
    void leaseMutationsAndFencedArchiveUseDatabaseClock() throws Exception {
        Configuration c = new Configuration();
        String resource = "mapper/knowledge/KbLayoutMapper.xml";
        try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, c, resource, c.getSqlFragments()).parse();
        }
        String own =
                c.getMappedStatement("com.ruoyi.knowledge.mapper.KbLayoutMapper.lockOwnedJob")
                        .getBoundSql(Map.of("id", 1, "token", "t"))
                        .getSql();
        assertTrue(own.contains("lease_token=?"));
        assertTrue(own.contains("lease_until > NOW()"));
        assertTrue(own.contains("FOR UPDATE"));
        var job = new KbRenderJob();
        job.setId(1L);
        job.setLeaseToken("t");
        job.setStatus("SUCCEEDED");
        String finish =
                c.getMappedStatement("com.ruoyi.knowledge.mapper.KbLayoutMapper.finish")
                        .getBoundSql(job)
                        .getSql();
        assertTrue(finish.contains("status='RUNNING' AND lease_token=?"));
    }
}
