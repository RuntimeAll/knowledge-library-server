package com.ruoyi.knowledge.mapper;

import com.ruoyi.knowledge.catalog.KbCatalogBatch;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.resultset.DefaultResultSetHandler;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Types;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KbCatalogBatchMapperTest {
    private Configuration configuration;
    @BeforeEach
    void parse() throws Exception {
        configuration = new Configuration();
        String resource = "mapper/knowledge/KbCatalogMapper.xml";
        try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input);
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
    }
    @Test
    void nestedScopesGroupNodesWithinOwnerAndKeepEmptyWholeBookNodes() throws Exception {
        List<KbCatalogBatch> rows = mapped("batchScopes",
                new String[]{"owner_id", "scope_id", "outline_id", "scope_kind", "node_id"},
                new Object[][]{{1L, 11L, 7L, "CHAPTERS", 5L}, {1L, 11L, 7L, "CHAPTERS", 6L},
                        {1L, 12L, 8L, "WHOLE_BOOK", null}, {2L, 13L, 7L, "CHAPTERS", 5L}});
        assertEquals(2, rows.size()); assertEquals(1L, rows.get(0).getOwnerId());
        assertEquals(2, rows.get(0).getOutlineScopes().size());
        assertEquals(List.of(5L, 6L), rows.get(0).getOutlineScopes().get(0).getNodeIds());
        assertEquals(List.of(), rows.get(0).getOutlineScopes().get(1).getNodeIds());
        assertEquals(List.of(5L), rows.get(1).getOutlineScopes().get(0).getNodeIds());
    }
    @Test
    void declaredAndActualKpsRemainSeparateAcrossNullUnionColumns() throws Exception {
        List<KbCatalogBatch> rows = mapped("batchKps", new String[]{"owner_id", "declared_kp_id", "actual_kp_id"},
                new Object[][]{{1L, null, 3L}, {1L, null, 9L}, {1L, 3L, null}, {1L, 7L, null}, {2L, null, 3L}});
        assertEquals(List.of(3L, 7L), rows.get(0).getDeclaredKpIds());
        assertEquals(List.of(3L, 9L), rows.get(0).getActualKpIds());
        assertEquals(List.of(), rows.get(1).getDeclaredKpIds());
        assertEquals(List.of(3L), rows.get(1).getActualKpIds());
    }
    @Test
    void batchRelationshipsNeverUseNestedSelectsAndAlwaysBindCurrentPageIds() {
        for (String kind : List.of("SOURCE", "PRODUCT")) {
            for (String statement : List.of("batchProfiles", "batchScopes", "batchKps", "batchRegions",
                    "batchExams", "batchProductQuestions", "batchProductSources", "batchCollections")) {
                MappedStatement ms = statement(statement);
                var sql = ms.getBoundSql(Map.of("ownerKind", kind, "ownerIds", List.of(7L, 8L)));
                assertTrue(sql.getSql().contains("IN"));
                assertTrue(sql.getParameterMappings().size() >= 2);
            }
        }
        for (var resultMap : configuration.getResultMaps())
            if (resultMap.getId().contains("batch"))
                resultMap.getResultMappings().forEach(mapping -> assertNull(mapping.getNestedQueryId()));
    }
    private MappedStatement statement(String name) {
        return configuration.getMappedStatement("com.ruoyi.knowledge.mapper.KbCatalogMapper." + name);
    }
    @SuppressWarnings("unchecked")
    private List<KbCatalogBatch> mapped(String name, String[] columns, Object[][] values) throws Exception {
        var rowSet = RowSetProvider.newFactory().createCachedRowSet();
        var metadata = new RowSetMetaDataImpl(); metadata.setColumnCount(columns.length);
        for (int i = 0; i < columns.length; i++) {
            metadata.setColumnName(i + 1, columns[i]); metadata.setColumnLabel(i + 1, columns[i]);
            metadata.setColumnType(i + 1, columns[i].equals("scope_kind") ? Types.VARCHAR : Types.BIGINT);
            metadata.setNullable(i + 1, ResultSetMetaData.columnNullable);
        }
        rowSet.setMetaData(metadata);
        for (int row = values.length - 1; row >= 0; row--) {
            rowSet.moveToInsertRow();
            for (int column = 1; column <= columns.length; column++) rowSet.updateNull(column);
            for (int column = 0; column < columns.length; column++) rowSet.updateObject(column + 1, values[row][column]);
            rowSet.insertRow(); rowSet.moveToCurrentRow();
        }
        rowSet.beforeFirst();
        // CachedRowSet supports JDBC reads but not isClosed(), which MyBatis checks.
        ResultSet resultSet = mock(ResultSet.class, org.mockito.AdditionalAnswers.delegatesTo(rowSet));
        doReturn(false).when(resultSet).isClosed();
        Statement statement = mock(Statement.class); when(statement.getResultSet()).thenReturn(resultSet);
        when(statement.getUpdateCount()).thenReturn(-1);
        MappedStatement ms = statement(name);
        var bound = ms.getBoundSql(Map.of("ownerKind", "SOURCE", "ownerIds", List.of(1L, 2L)));
        var handler = new DefaultResultSetHandler(mock(Executor.class), ms, null, null, bound, RowBounds.DEFAULT);
        return (List<KbCatalogBatch>) (List<?>) handler.handleResultSets(statement);
    }
}
