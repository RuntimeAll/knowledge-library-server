package com.ruoyi.knowledge.mapper;

import com.ruoyi.knowledge.dto.KbKpOutlineRow;
import com.ruoyi.knowledge.dto.KbQuestionQuery;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.resultset.DefaultResultSetHandler;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KbQuestionBatchMapperTest {
    private Configuration configuration;

    @BeforeEach
    void parse() throws Exception {
        configuration = new Configuration();
        for (String name : List.of("Review", "Question", "Kp", "Occurrence")) {
            String resource = "mapper/knowledge/Kb" + name + "Mapper.xml";
            try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(input);
                new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
    }

    @Test
    void questionListExamIdentityRemainsUnscopedAndRequiresCurrentVerifiedStem() {
        KbQuestionQuery query = new KbQuestionQuery(); query.setExamSourceId(7L); query.setWrongSourceId(8L);
        String sql = statement("KbQuestionMapper.selectList").getBoundSql(query).getSql().replaceAll("\\s+", " ");
        String identity = sql.substring(0, sql.indexOf(") AS is_real_exam"));
        assertTrue(identity.contains("kb_verified_exam_source"));
        assertTrue(identity.contains("exam_qs.matched_stem_hash=kb_question.stem_hash"));
        assertTrue(identity.contains("kb_question.content_kind='EXERCISE'"));
        assertTrue(identity.contains("exam_s.source_kind='EXAM'"));
        assertFalse(identity.contains("?"), "List scope must not change the global real-exam identity");
        assertTrue(sql.contains("vqs.source_id=?"));
        assertTrue(sql.contains("wqs.source_id=?"));
    }

    @Test
    void relationQueriesAreBoundedToPageIdsAndHaveStableOrder() {
        Map<String, Object> input = Map.of("ids", List.of(9L, 3L));
        String points = statement("KbQuestionMapper.selectKpsForQuestions").getBoundSql(input).getSql().replaceAll("\\s+", " ");
        assertTrue(points.contains("WHERE question_id IN"));
        assertTrue(points.contains("ORDER BY question_id,kp_id"));
        assertEquals(2, statement("KbQuestionMapper.selectKpsForQuestions").getBoundSql(input).getParameterMappings().size());
        String bindings = statement("KbKpMapper.selectOutlineRowsForKps").getBoundSql(input).getSql().replaceAll("\\s+", " ");
        assertTrue(bindings.contains("WHERE b.kp_id IN"));
        assertTrue(bindings.contains("ORDER BY b.kp_id,b.outline_id,n.sort_order,n.id"));
    }

    @Test
    void nameLookupOnlyReadsIdAndNameForExplicitBoundIds() {
        var mapped = statement("KbKpMapper.selectNames");
        var bound = mapped.getBoundSql(Map.of("ids", List.of(9L, 3L)));
        assertEquals("SELECT id,name FROM kb_kp WHERE id IN ( ? , ? )",
                bound.getSql().replaceAll("\\s+", " ").trim());
        assertEquals(2, bound.getParameterMappings().size());
        assertEquals(com.ruoyi.knowledge.dto.KbKpName.class, mapped.getResultMaps().get(0).getType());
    }

    @Test
    void nestedMappingDoesNotCollapseTwoChaptersOfTheSameTextbook() throws Exception {
        // Run the actual MyBatis nested result mapper, not a hand-built DTO substitute.
        // The chapter id must participate in the outer row identity or MyBatis collapses it.
        RowSetMetaDataImpl metadata = new RowSetMetaDataImpl();
        String[] columns = {"kp_id", "outline_id", "node_id", "binding_outline_id", "outline_title",
                "subject", "textbook_version", "grade", "term", "edition", "node_title"};
        metadata.setColumnCount(columns.length);
        for (int i = 0; i < columns.length; i++) {
            metadata.setColumnName(i + 1, columns[i]); metadata.setColumnLabel(i + 1, columns[i]);
            metadata.setColumnType(i + 1, i < 4 ? Types.BIGINT : Types.VARCHAR);
            metadata.setNullable(i + 1, ResultSetMetaData.columnNullable);
        }
        CachedRowSet rows = RowSetProvider.newFactory().createCachedRowSet(); rows.setMetaData(metadata);
        for (long node : List.of(32L, 31L)) {
            rows.moveToInsertRow();
            for (int column = 1; column <= columns.length; column++) rows.updateNull(column);
            rows.updateLong(1, 4L); rows.updateLong(2, 16L); rows.updateLong(3, node); rows.updateLong(4, 16L);
            rows.updateString(5, "Textbook"); rows.updateString(6, "MATH");
            rows.updateString(11, "Chapter " + node); rows.insertRow(); rows.moveToCurrentRow();
        }
        rows.beforeFirst();
        // CachedRowSet implements JDBC data reads but does not implement isClosed().
        ResultSet resultSet = mock(ResultSet.class, org.mockito.AdditionalAnswers.delegatesTo(rows));
        doReturn(false).when(resultSet).isClosed();
        Statement jdbc = mock(Statement.class);
        when(jdbc.getResultSet()).thenReturn(resultSet); when(jdbc.getUpdateCount()).thenReturn(-1);
        MappedStatement mapped = statement("KbKpMapper.selectOutlineRowsForKps");
        var handler = new DefaultResultSetHandler(mock(Executor.class), mapped, null, null,
                mapped.getBoundSql(Map.of("ids", List.of(4L))), RowBounds.DEFAULT);
        List<Object> actual = handler.handleResultSets(jdbc);
        assertEquals(2, actual.size());
        List<KbKpOutlineRow> bindings = actual.stream().map(KbKpOutlineRow.class::cast).toList();
        assertEquals(List.of(31L, 32L), bindings.stream().map(row -> row.getBinding().getNodeId()).sorted().toList());
        assertTrue(bindings.stream().allMatch(row -> row.getKpId().equals(4L) && row.getOutlineId().equals(16L)));
    }

    private MappedStatement statement(String name) {
        return configuration.getMappedStatement("com.ruoyi.knowledge.mapper." + name);
    }
}
