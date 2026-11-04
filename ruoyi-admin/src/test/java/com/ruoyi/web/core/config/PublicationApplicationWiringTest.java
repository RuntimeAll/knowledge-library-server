package com.ruoyi.web.core.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import com.ruoyi.framework.config.ApplicationConfig;
import com.ruoyi.knowledge.mapper.PublicationMapper;
import com.ruoyi.knowledge.publication.PublicationCheckService;
import com.ruoyi.knowledge.publication.PublicationGraph;
import com.ruoyi.knowledge.publication.PublicationProperties;
import com.ruoyi.knowledge.publication.PublicationPublicService;
import com.ruoyi.knowledge.publication.PublicationReadGate;
import com.ruoyi.knowledge.publication.PublicationReviewValidator;
import com.ruoyi.knowledge.publication.PublicationSaleService;
import com.ruoyi.knowledge.publication.PublicationService;
import com.ruoyi.knowledge.publication.PublicationShareService;
import com.ruoyi.knowledge.publication.PublicationSupport;
import com.ruoyi.knowledge.review.KbPublicationReviewBridge;
import com.ruoyi.knowledge.review.KbReviewGate;
import com.ruoyi.knowledge.review.KbReviewService;
import com.ruoyi.knowledge.storage.IKbAssetService;
import com.ruoyi.web.controller.knowledge.PublicationController;
import com.ruoyi.web.controller.knowledge.PublicationPublicController;
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

/** The real production MapperScan must supply every publication mapper, without a test-only registration. */
class PublicationApplicationWiringTest {
    @Test void productionScanWiresBothControllersAndReviewValidator() {
        DataSource source = mock(DataSource.class);
        Configuration configuration = new Configuration(new Environment("scan-only", new JdbcTransactionFactory(), source));
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(ApplicationConfig.class, PublicationProperties.class, PublicationSupport.class,
                    PublicationService.class, PublicationGraph.class, PublicationCheckService.class, PublicationSaleService.class,
                    PublicationShareService.class, com.ruoyi.knowledge.publication.PublicationBundleService.class, com.ruoyi.knowledge.publication.PublicationDownloadService.class, PublicationReadGate.class, PublicationPublicService.class, PublicationReviewValidator.class,
                    PublicationController.class, PublicationPublicController.class);
            context.registerBean(SqlSessionFactory.class, () -> new DefaultSqlSessionFactory(configuration));
            context.registerBean(PlatformTransactionManager.class, () -> new DataSourceTransactionManager(source));
            context.registerBean(KbReviewGate.class, KbReviewGate::new);
            context.registerBean(KbPublicationReviewBridge.class, () -> mock(KbPublicationReviewBridge.class));
            context.registerBean(KbReviewService.class, () -> mock(KbReviewService.class));
            context.registerBean(IKbAssetService.class, () -> mock(IKbAssetService.class));
            context.refresh();
            assertNotNull(context.getBean(PublicationMapper.class));
            assertNotNull(context.getBean(PublicationController.class));
            assertNotNull(context.getBean(PublicationPublicController.class));
            assertNotNull(context.getBean(PublicationReviewValidator.class));
        }
    }
}
