package com.ruoyi.knowledge.mapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ruoyi.knowledge.domain.KbMaterial;
import com.ruoyi.knowledge.domain.KbSource;
import com.ruoyi.knowledge.dto.KbComparisonQuery;
import com.ruoyi.knowledge.dto.KbPointMatch;
import com.ruoyi.knowledge.dto.KbQuestionQuery;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

class KnowledgeMapperContractTest {
    private Configuration configuration;

    @BeforeEach
    void parseBusinessMappers() throws Exception {
        configuration = new Configuration();
        for (String name :
                List.of(
                        "Question",
                        "Kp",
                        "Source",
                        "Occurrence",
                        "Outline",
                        "OutlineNode",
                        "Material",
                        "Product")) {
            String resource = "mapper/knowledge/Kb" + name + "Mapper.xml";
            try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resource)) {
                new XMLMapperBuilder(
                                stream, configuration, resource, configuration.getSqlFragments())
                        .parse();
            }
        }
    }

    @Test
    void emptyCandidateRangeCannotBecomeAnUnfilteredQuery() {
        KbQuestionQuery query = new KbQuestionQuery();
        query.setSourceIds(List.of());
        assertTrue(sql("KbQuestionMapper.selectList", query).contains("WHERE 1=0"));
    }

    @Test
    void comparisonIsPagedInSqlAndEmptyReferenceRemainsEmpty() {
        KbComparisonQuery query = new KbComparisonQuery();
        query.setSourceIds(List.of());
        query.setPointIds(List.of(1L));
        query.setPointMatches(List.of(new KbPointMatch(1L, "考点", List.of(2L))));
        query.page(2, 10);
        String matches = sql("KbMaterialMapper.selectComparisonMatches", query);
        assertTrue(matches.contains("AND 1=0"));
        assertTrue(matches.contains("LIMIT ? OFFSET ?"));
        assertTrue(
                sql("KbMaterialMapper.selectComparisonCoverage", query)
                        .contains("LIMIT ? OFFSET ?"));
        assertTrue(
                sql("KbMaterialMapper.selectComparisonPoints", query).contains("LIMIT ? OFFSET ?"));
    }

    @Test
    void comparisonWithoutTrainingBasisHasNoFacts() {
        KbComparisonQuery query = new KbComparisonQuery();
        query.setPointIds(List.of());
        query.setPointMatches(List.of());
        query.page(1, 20);
        assertTrue(sql("KbMaterialMapper.countComparisonMatches", query).contains("WHERE 1=0"));
    }

    private String sql(String statement, Object value) {
        return configuration
                .getMappedStatement("com.ruoyi.knowledge.mapper." + statement)
                .getBoundSql(value)
                .getSql()
                .replaceAll("\\s+", " ");
    }

    @Test
    void materialListCountsItemsAndSourceListFiltersVerificationStatus() {
        assertTrue(
                sql("KbMaterialMapper.selectList", new KbMaterial()).contains("AS question_count"));
        KbSource source = new KbSource();
        source.setAuthenticityStatus("VERIFIED");
        String sourceSql = sql("KbSourceMapper.selectList", source);
        assertTrue(sourceSql.contains("e.authenticity_status=?"));
        assertTrue(sourceSql.contains("s.source_kind='EXAM'"));
    }

    @Test
    void eligibilityAndVersionAllocationUseCurrentReadsAfterBusinessLocks() {
        String eligibility =
                sql(
                        "KbMaterialMapper.selectVerifiedOccurrenceIdForUpdate",
                        Map.of("questionId", 1L, "sourceIds", List.of(2L)));
        assertTrue(eligibility.contains("FOR UPDATE"));
        assertTrue(eligibility.contains("s.source_kind='EXAM'"));
        assertTrue(sql("KbProductMapper.nextVersion", Map.of("rootId", 1L)).contains("FOR UPDATE"));
        assertTrue(
                sql("KbQuestionMapper.isRealExam", Map.of("id", 1L))
                        .contains("s.source_kind='EXAM'"));
    }

    @Test
    void automaticSourceProgressIncludesAuditAndOptimisticVersion() {
        String update =
                sql("KbSourceMapper.advanceExtraction", Map.of("id", 1L, "actor", "reviewer"));
        assertTrue(update.contains("row_version=row_version+1"));
        assertTrue(update.contains("update_by=?"));
        assertTrue(update.contains("extraction_status='ORIGINAL_ONLY'"));
    }
}
