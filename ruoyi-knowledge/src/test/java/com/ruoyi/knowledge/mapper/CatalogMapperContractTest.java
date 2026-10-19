package com.ruoyi.knowledge.mapper;

import static org.junit.jupiter.api.Assertions.*;

import com.ruoyi.knowledge.catalog.*;
import com.ruoyi.knowledge.dto.KbQuestionQuery;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.*;

import java.util.*;

class CatalogMapperContractTest {
    Configuration c;

    @BeforeEach
    void parse() throws Exception {
        c = new Configuration();
        for (String name :
                List.of("Catalog", "Question", "Source", "Product", "Occurrence", "Material")) {
            String resource = "mapper/knowledge/Kb" + name + "Mapper.xml";
            try (var in = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(in);
                new XMLMapperBuilder(in, c, resource, c.getSqlFragments()).parse();
            }
        }
    }

    String sql(String name, Object input) {
        return c.getMappedStatement("com.ruoyi.knowledge.mapper." + name)
                .getBoundSql(input)
                .getSql()
                .replaceAll("\\s+", " ");
    }

    @Test
    void completeOutlineIdentityLivesInsideOneScopeExists() {
        KbCatalogQuery query = new KbCatalogQuery();
        query.setGrade("G7");
        query.setTerm("FIRST");
        query.setTextbookVersion("ZHEJIAO");
        query.setOutlineNodeId(9L);
        query.setOutlineId(16L);
        query.setChapterAncestorIds(List.of(9L, 8L));
        query.setChapterTargetIds(List.of(9L, 10L));
        query.setChapterMatch("SUBTREE");
        for (String type : List.of("Source", "Product")) {
            String s = sql("Kb" + type + "Mapper.selectList", query);
            assertTrue(s.contains("sc.outline_id=?"));
            assertTrue(s.contains("o.textbook_version=?"));
            assertTrue(s.contains("o.grade=?"));
            assertTrue(s.contains("o.term=?"));
            assertTrue(s.contains("sc.scope_kind='WHOLE_BOOK'"));
            assertTrue(s.contains("sc.scope_kind='CHAPTERS'"));
            assertFalse(s.contains("kb_question_kp"));
            assertFalse(s.contains("JOIN kb_" + type.toLowerCase() + "_outline_scope sc ON"));
        }
    }

    @Test
    void actualCoverageAndDeclaredCoverageStaySeparate() {
        KbCatalogQuery q = new KbCatalogQuery();
        q.setKpId(9L);
        q.setCoverageMode("DECLARED");
        assertTrue(sql("KbSourceMapper.selectList", q).contains("kb_source_kp dk"));
        assertFalse(sql("KbSourceMapper.selectList", q).contains("kb_question_kp qk"));
        q.setCoverageMode("ACTUAL");
        assertTrue(
                sql("KbSourceMapper.selectList", q)
                        .contains("kb_question_source l JOIN kb_question_kp qk"));
        assertTrue(
                sql("KbProductMapper.selectList", q)
                        .contains("kb_product_question l JOIN kb_question_kp qk"));
    }

    @Test
    void effectiveExamAndWrongScopesAreIndependentExistsPredicates() {
        KbQuestionQuery q = new KbQuestionQuery();
        q.setVerifiedOnly(true);
        q.setExamSourceId(1L);
        q.setExamPurpose("MOCK");
        q.setExamYear(2024);
        q.setWrongOnly(true);
        q.setWrongSourceId(2L);
        String s = sql("KbQuestionMapper.selectList", q);
        assertTrue(s.contains("kb_verified_exam_source"));
        assertTrue(s.contains("vqs.source_id=?"));
        assertTrue(s.contains("ve.exam_type=?"));
        assertTrue(s.contains("ve.exam_year=?"));
        assertTrue(s.contains("wqs.source_id=?"));
        assertTrue(s.contains("wc.collection_kind='WRONG_QUESTION'"));
        assertFalse(s.contains("SELECT DISTINCT"));
    }

    @Test
    void latestAndAnyNeverMixFileVersions() {
        KbCatalogQuery q = new KbCatalogQuery();
        q.setHistoryMode("LATEST");
        assertTrue(
                sql("KbProductMapper.selectList", q).contains("newer.version_no > r.version_no"));
        q.setHistoryMode("ANY");
        assertFalse(sql("KbProductMapper.selectList", q).contains("newer.version_no"));
    }

    @Test
    void yearAndRegionUseExplicitColumnsAndRoles() {
        KbCatalogQuery q = new KbCatalogQuery();
        q.setYearField("preparedYear");
        q.setYearFrom(2024);
        q.setYearTo(2025);
        q.setRegionRole("ORIGIN");
        q.setRegionCodes(List.of("CN"));
        String s = sql("KbSourceMapper.selectList", q);
        assertTrue(s.contains("p.prepared_year >= ?"));
        assertTrue(s.contains("p.prepared_year <= ?"));
        assertTrue(s.contains("rg.region_role=?"));
        assertFalse(s.contains("YEAR(r.create_time)"));
    }

    @Test
    void typedOwnerMappingCannotSelectAnArbitraryTable() {
        for (String owner : List.of("SOURCE", "PRODUCT")) {
            String s = sql("KbCatalogMapper.profile", Map.of("ownerKind", owner, "ownerId", 1L));
            assertTrue(s.contains("kb_" + owner.toLowerCase() + "_profile"));
            assertFalse(s.contains("${"));
        }
    }

    @Test
    void allExamEligibilityReadsUseTheSharedVerifiedView() {
        assertTrue(
                sql("KbQuestionMapper.isRealExam", Map.of("id", 1L))
                        .contains("kb_verified_exam_source"));
        for (String name : List.of("realInSources", "selectVerifiedOccurrenceIdForUpdate"))
            assertTrue(
                    sql(
                                    "KbMaterialMapper." + name,
                                    Map.of("questionId", 1L, "sourceIds", List.of(2L)))
                            .contains("kb_verified_exam_source"));
    }
}
