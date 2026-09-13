package com.qinglian.fitness.mapper;

import com.qinglian.fitness.catalog.SpringCounts;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringCountsTypeHandlerTest {
    private final SpringCountsTypeHandler handler = new SpringCountsTypeHandler();

    @Test
    void roundTripsAllFourCountsThroughJdbcJson() throws Exception {
        var counts = new SpringCounts(2, 1, 0, 3);
        var statement = mock(PreparedStatement.class);
        handler.setParameter(statement, 1, counts, JdbcType.VARCHAR);
        var json = ArgumentCaptor.forClass(String.class);
        verify(statement).setString(org.mockito.ArgumentMatchers.eq(1), json.capture());

        var result = mock(ResultSet.class);
        when(result.getString("spring_counts")).thenReturn(json.getValue());
        when(result.getString(1)).thenReturn(json.getValue());
        var callable = mock(CallableStatement.class);
        when(callable.getString(1)).thenReturn(json.getValue());
        assertThat(handler.getNullableResult(result, "spring_counts")).isEqualTo(counts);
        assertThat(handler.getNullableResult(result, 1)).isEqualTo(counts);
        assertThat(handler.getNullableResult(callable, 1)).isEqualTo(counts);
    }

    @Test
    void retainsSqlNullForUnconfiguredLegacyRecordsAndClearingConfiguration() throws Exception {
        assertThat(handler.getNullableResult(mock(ResultSet.class), "spring_counts")).isNull();
        var statement = mock(PreparedStatement.class);
        handler.setParameter(statement, 1, null, JdbcType.VARCHAR);
        verify(statement).setNull(1, java.sql.Types.VARCHAR);
    }

    @Test
    void rejectsLegacyNumberArraysInTheColorColumn() throws Exception {
        var result = mock(ResultSet.class);
        when(result.getString("spring_counts")).thenReturn("[2,3]");
        assertThatThrownBy(() -> handler.getNullableResult(result, "spring_counts"))
            .isInstanceOf(SQLException.class).hasMessage("Invalid spring counts");
    }
}
