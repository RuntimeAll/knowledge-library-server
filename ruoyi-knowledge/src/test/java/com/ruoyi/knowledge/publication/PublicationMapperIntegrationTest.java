package com.ruoyi.knowledge.publication;

import com.ruoyi.knowledge.mapper.PublicationMapper;
import static org.junit.jupiter.api.Assertions.*;
import com.ruoyi.knowledge.publication.PublicationModels.*;
import com.ruoyi.knowledge.publication.PublicationRows.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Date;
import java.util.UUID;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** All mutations roll back and are restricted to the PRD-012 isolated schema. */
@EnabledIfEnvironmentVariable(named = "KB_PUBLICATION_TEST_JDBC_URL", matches = ".+")
class PublicationMapperIntegrationTest {
    @Test void crudPaginationGraphQueriesAndCompositeForeignKeys() throws Exception {
        String url = System.getenv("KB_PUBLICATION_TEST_JDBC_URL");
        assertTrue(url.startsWith("jdbc:mysql://127.0.0.1:13308/prd012_publication_"));
        var data = new DriverManagerDataSource(url, "root", "");
        Configuration configuration = new Configuration(new Environment("publication-test", new JdbcTransactionFactory(), data));
        configuration.setMapUnderscoreToCamelCase(false);
        com.github.pagehelper.PageInterceptor pageHelper = new com.github.pagehelper.PageInterceptor();
        java.util.Properties pageProperties = new java.util.Properties(); pageProperties.setProperty("helperDialect", "mysql");
        pageProperties.setProperty("supportMethodsArguments", "true"); pageProperties.setProperty("reasonable", "true");
        pageProperties.setProperty("params", "count=countSql"); pageHelper.setProperties(pageProperties);
        configuration.addInterceptor(pageHelper);
        try (var xml = getClass().getResourceAsStream("/mapper/knowledge/publication/PublicationMapper.xml")) {
            new XMLMapperBuilder(xml, configuration, "publication.xml", configuration.getSqlFragments()).parse();
        }
        try (SqlSession session = new SqlSessionFactoryBuilder().build(configuration).openSession(false)) {
            PublicationMapper mapper = session.getMapper(PublicationMapper.class);
            String name = "publication-test-" + UUID.randomUUID();
            Publication first = publication(name), second = publication(name + "-2");
            mapper.insertPublication(first); mapper.insertPublication(second);
            assertEquals("PREPARING", mapper.get(first.id).status);
            assertEquals(first.id, mapper.lock(first.id).id);
            ListQuery query = new ListQuery(); query.keyword = name; query.pageSize = 1;
            assertEquals(2, mapper.count(query)); assertEquals(1, mapper.list(query).size());
            first.postTitle = "title"; assertEquals(1, mapper.updatePublication(first));
            assertEquals(0, mapper.updatePublication(first));
            assertEquals(1L, mapper.get(first.id).rowVersion);
            assertTrue(mapper.products(first.id).isEmpty()); assertTrue(mapper.questions(first.id).isEmpty());
            assertTrue(mapper.sources(first.id).isEmpty()); assertTrue(mapper.images(first.id).isEmpty());
            assertTrue(mapper.shares(first.id).isEmpty()); assertTrue(mapper.files(first.id).isEmpty());
            for (String type : java.util.List.of("PRODUCT", "QUESTION", "SOURCE", "KP", "OUTLINE")) {
                RelationQuery r = new RelationQuery(); r.publicationId = first.id; r.objectType = type;
                assertEquals(0, mapper.relationCount(r)); assertTrue(mapper.relationPage(r).isEmpty());
                r.pageIds = java.util.List.of(first.id); assertTrue(mapper.relationFiles(r).isEmpty());
                if (java.util.List.of("KP", "OUTLINE").contains(type)) {
                    assertTrue(mapper.relationNodes(r).isEmpty());
                }
            }
            RelationQuery scope = PublicationService.scope(first.id, null);
            assertTrue(mapper.allowedAssets(scope).isEmpty()); assertTrue(mapper.dependencyFacts(scope).isEmpty());
            assertTrue(mapper.relationLinks(scope).isEmpty());
            // Production leaves underscore mapping disabled: verify non-empty child graphs explicitly.
            long productId, questionId;
            try (var statement = session.getConnection().createStatement(); var rs = statement.executeQuery("SELECT id FROM kb_product ORDER BY id LIMIT 1")) {
                assertTrue(rs.next(), "isolated fixture must contain a product"); productId = rs.getLong(1);
            }
            try (var statement = session.getConnection().createStatement(); var rs = statement.executeQuery("SELECT id FROM kb_question ORDER BY id LIMIT 1")) {
                assertTrue(rs.next(), "isolated fixture must contain a question"); questionId = rs.getLong(1);
            }
            var productAsset = mapper.productFacts(java.util.List.of(productId)).get(0);
            first.rowVersion = mapper.get(first.id).rowVersion;
            first.shareAssetId = productAsset.assetId;
            assertEquals(1, mapper.updatePublication(first));
            assertNotNull(mapper.get(first.id).shareContentType, "ready artifact type is read without a detail N+1");
            query.pageSize = 2;
            var summary = mapper.list(query).stream().filter(item -> item.id.equals(first.id)).findFirst().orElseThrow();
            assertEquals(mapper.get(first.id).shareContentType, summary.shareContentType);
            // Verify the real profile join, including its conservative fallback, inside this rollback-only fixture.
            try (var statement = session.getConnection().prepareStatement("UPDATE kb_product_profile SET resource_type = 'COURSEWARE' WHERE product_id = ?")) {
                statement.setLong(1, productId);
                assertEquals(1, statement.executeUpdate(), "isolated fixture must contain its product profile");
            }
            assertEquals("COURSEWARE", mapper.productFacts(java.util.List.of(productId)).get(0).resourceType);
            PublicationProduct product = stamp(new PublicationProduct()); product.publicationId = first.id;
            product.productId = productId; product.sortOrder = 1; product.questionListStatus = "UNKNOWN";
            mapper.insertPublicationProduct(product);
            PublicationQuestion question = stamp(new PublicationQuestion()); question.publicationProductId = product.id;
            question.questionId = questionId; question.seq = 1; question.observedQuestionVersion = 0L;
            question.observedContentHash = "b".repeat(64); question.answerAvailability = "UNKNOWN";
            mapper.insertPublicationQuestion(question);
            var loaded = mapper.questions(first.id).get(0);
            assertEquals(product.id, loaded.publicationProductId); assertEquals(questionId, loaded.questionId);
            assertEquals(question.observedContentHash, loaded.observedContentHash); assertEquals(0L, loaded.observedQuestionVersion);
            PublicationCheck check = stamp(new PublicationCheck()); check.publicationId = first.id;
            check.contentVersion = 1L; check.policyVersion = "PRD012-QUALITY-1"; check.status = "ERROR";
            check.questionCount = 0; check.blockingCount = 0; check.noticeCount = 0;
            mapper.insertPublicationCheck(check); assertNull(mapper.getCheck(first.id, check.id).dependencyHash);
            PublicationOperation invalid = stamp(new PublicationOperation()); invalid.publicationId = second.id;
            invalid.operationKey = name; invalid.action = "SALE"; invalid.requestHash = "a".repeat(64);
            invalid.contentVersion = 1L; invalid.expectedRowVersion = 0L; invalid.actorName = "publication-test";
            invalid.checkId = check.id; invalid.status = "PENDING"; invalid.attemptCount = 0; invalid.snapshotJson = "{}";
            assertThrows(org.apache.ibatis.exceptions.PersistenceException.class, () -> mapper.insertPublicationOperation(invalid));
            session.rollback();
        }
    }
    private static Publication publication(String title) {
        Publication p = stamp(new Publication()); p.title = title; p.status = "PREPARING";
        p.contentVersion = 1L; p.shareState = "NOT_CREATED"; return p;
    }
    private static <T extends BaseRow> T stamp(T row) {
        row.rowVersion = 0L; row.createBy = "publication-test"; row.updateBy = "publication-test";
        row.createTime = new Date(); row.updateTime = new Date(); return row;
    }
}
