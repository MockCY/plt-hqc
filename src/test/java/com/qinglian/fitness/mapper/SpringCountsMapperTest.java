package com.qinglian.fitness.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.executor.resultset.DefaultResultSetHandler;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.session.AutoMappingBehavior;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Exercises the real XML constructor mappings without opening a database connection. */
class SpringCountsMapperTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @ParameterizedTest
    @ValueSource(strings = {
        "com.qinglian.fitness.mapper.AdminMapper.findExercise",
        "com.qinglian.fitness.mapper.CatalogMapper.findExercises",
        "com.qinglian.fitness.mapper.CatalogMapper.findCourseExercises"
    })
    void readsColorCountsLegacyAndExplicitZeroFromEveryExerciseProjection(String statementId) throws Exception {
        var configuration = new Configuration();
        configuration.setAutoMappingBehavior(AutoMappingBehavior.NONE);
        for (String mapper : List.of("mappers/AdminMapper.xml", "mappers/CatalogMapper.xml")) {
            try (var stream = Resources.getResourceAsStream(mapper)) {
                new XMLMapperBuilder(stream, configuration, mapper, configuration.getSqlFragments()).parse();
            }
        }

        var colored = JSON.valueToTree(readRow(configuration, statementId, "{\"red\":2,\"green\":1,\"yellow\":0,\"blue\":3}"));
        assertThat(colored.path("springCounts")).isEqualTo(JSON.readTree("{\"red\":2,\"green\":1,\"yellow\":0,\"blue\":3}"));
        var legacy = JSON.valueToTree(readRow(configuration, statementId, null));
        assertThat(legacy.path("springCounts").isNull()).isTrue();
        assertThat(legacy.path("springSets")).isEqualTo(JSON.readTree("[2,3]"));
        var zeros = JSON.valueToTree(readRow(configuration, statementId, "{\"red\":0,\"green\":0,\"yellow\":0,\"blue\":0}"));
        assertThat(zeros.path("springCounts").isObject()).isTrue();
        assertThat(zeros.path("springCounts")).isEqualTo(JSON.readTree("{\"red\":0,\"green\":0,\"yellow\":0,\"blue\":0}"));
    }

    private Object readRow(Configuration configuration, String statementId, String springCounts) throws Exception {
        var mapped = configuration.getMappedStatement(statementId);
        List<ResultMapping> columns = mapped.getResultMaps().getFirst().getConstructorResultMappings();
        var metadata = mock(ResultSetMetaData.class);
        when(metadata.getColumnCount()).thenReturn(columns.size());
        for (int index = 0; index < columns.size(); index++) {
            when(metadata.getColumnLabel(index + 1)).thenReturn(columns.get(index).getColumn());
            when(metadata.getColumnType(index + 1)).thenReturn(Types.VARCHAR);
            when(metadata.getColumnClassName(index + 1)).thenReturn(String.class.getName());
        }
        var result = mock(ResultSet.class);
        when(result.getMetaData()).thenReturn(metadata);
        when(result.next()).thenReturn(true, false);
        when(result.getLong("id")).thenReturn(42L);
        when(result.getString("name")).thenReturn("测试动作");
        when(result.getString("body_part")).thenReturn("核心训练");
        when(result.getString("spring_counts")).thenReturn(springCounts);
        when(result.getString("spring_sets")).thenReturn("[2,3]");
        when(result.getString("training_sets")).thenReturn("[]");
        var statement = mock(Statement.class);
        when(statement.getResultSet()).thenReturn(result);
        when(statement.getUpdateCount()).thenReturn(-1);
        var connection = mock(Connection.class);
        when(statement.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(mock(DatabaseMetaData.class));

        var handler = new DefaultResultSetHandler(null, mapped, null, null,
            mapped.getBoundSql(Map.of("id", 42L, "courseId", 1L)), RowBounds.DEFAULT);
        var rows = handler.handleResultSets(statement);
        assertThat(rows).hasSize(1);
        return rows.getFirst();
    }
}
