package com.ruoyi.knowledge.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInterceptor;
import com.ruoyi.knowledge.mapper.KbLayoutMapper;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Exact readback uses the real mapper and the isolated fixture, never the HTTP/production schemas. */
@EnabledIfEnvironmentVariable(named="KB_REVIEW_TEST_JDBC_URL",matches=".+")
class KbRenderLookupMysqlTest {
    @Test void exactActorAndKeyLookupReturnsOneReceiptWithAccurateTotalWithoutWriting() throws Exception {
        String url=System.getenv("KB_REVIEW_TEST_JDBC_URL");
        assertTrue(url.matches("jdbc:mysql://127\\.0\\.0\\.1:13308/prd010_review_test(?:\\?.*)?"));
        var source=new DriverManagerDataSource(url,"root","");var jdbc=new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE IF NOT EXISTS kb_render_job (id BIGINT PRIMARY KEY,row_version BIGINT,template_version_id BIGINT,actor_user_id BIGINT,idempotency_key VARCHAR(128),request_hash CHAR(64),material_id BIGINT,material_row_version BIGINT,renderer_fingerprint VARCHAR(64),status VARCHAR(16),retry_count INT,error_code VARCHAR(64),error_message TEXT,create_by VARCHAR(64),create_time DATETIME,update_time DATETIME,UNIQUE KEY fixture_render_request(actor_user_id,idempotency_key)) ENGINE=InnoDB");
        long id=System.nanoTime();String key="lookup-"+id;
        jdbc.update("INSERT INTO kb_render_job(id,row_version,actor_user_id,idempotency_key,request_hash,status) VALUES(?,0,42,?,?,'SUCCEEDED'),(?,0,77,?,?,'QUEUED'),(?,0,42,?,?,'FAILED')",
                id,key,"a".repeat(64),id+1,key,"b".repeat(64),id+2,key+"-other","c".repeat(64));
        var factory=new SqlSessionFactoryBean();factory.setDataSource(source);
        factory.setMapperLocations(new ClassPathResource("mapper/knowledge/KbLayoutMapper.xml"));
        var interceptor=new PageInterceptor();var properties=new Properties();properties.setProperty("helperDialect","mysql");
        interceptor.setProperties(properties);factory.setPlugins(interceptor);
        var mapper=new SqlSessionTemplate(factory.getObject()).getMapper(KbLayoutMapper.class);
        var query=new KbRenderJob();query.setActorUserId(42L);query.setIdempotencyKey(key);
        try {
            PageHelper.startPage(1,10);
            var found=mapper.jobs(query);
            assertEquals(1,found.size());assertEquals(1,((Page<?>)found).getTotal());
            assertEquals(id,found.get(0).getId());assertEquals(key,found.get(0).getIdempotencyKey());
            assertEquals("a".repeat(64),found.get(0).getRequestHash());assertEquals("SUCCEEDED",found.get(0).getStatus());
            query.setIdempotencyKey(key+"-missing");PageHelper.startPage(1,10);
            var absent=mapper.jobs(query);assertTrue(absent.isEmpty());assertEquals(0,((Page<?>)absent).getTotal());
            assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM kb_render_job WHERE id IN (?,?,?)",Integer.class,id,id+1,id+2));
        } finally { PageHelper.clearPage(); }
    }
}
