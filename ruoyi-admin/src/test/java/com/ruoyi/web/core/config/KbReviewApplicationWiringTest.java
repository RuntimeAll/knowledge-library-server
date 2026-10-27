package com.ruoyi.web.core.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import com.ruoyi.framework.config.ApplicationConfig;
import com.ruoyi.knowledge.mapper.KbReviewMapper;
import com.ruoyi.knowledge.review.KbReviewGate;
import com.ruoyi.knowledge.review.KbReviewLedger;
import com.ruoyi.knowledge.review.KbReviewProofs;
import com.ruoyi.knowledge.review.KbReviewService;
import com.ruoyi.knowledge.service.impl.KnowledgeDictionary;
import javax.sql.DataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/** Uses the unchanged production MapperScan, rather than manually registering the review mapper. */
class KbReviewApplicationWiringTest {
    @Test void productionMapperScanSuppliesReviewMapperToServiceConstructor() {
        DataSource source = mock(DataSource.class);
        Configuration configuration = new Configuration(new Environment("scan-only",new JdbcTransactionFactory(),source));
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(ApplicationConfig.class,KbReviewService.class);
            context.registerBean(SqlSessionFactory.class,() -> new DefaultSqlSessionFactory(configuration));
            context.registerBean(PlatformTransactionManager.class,() -> new DataSourceTransactionManager(source));
            context.registerBean(KbReviewLedger.class,() -> new KbReviewLedger(""));
            context.registerBean(KbReviewGate.class,KbReviewGate::new);
            context.registerBean(KbReviewProofs.class,() -> mock(KbReviewProofs.class));
            context.registerBean(KnowledgeDictionary.class,() -> mock(KnowledgeDictionary.class));
            context.refresh();
            assertNotNull(context.getBean(KbReviewMapper.class));
            assertFalse(context.getBean(KbReviewService.class).enabled());
        }
    }
}
