package com.qinglian.fitness.mapper;

import com.qinglian.fitness.catalog.SpringCounts;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import tools.jackson.databind.ObjectMapper;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class SpringCountsTypeHandler extends BaseTypeHandler<SpringCounts> {
    private static final ObjectMapper JSON = new ObjectMapper();

    private SpringCounts read(String value) throws SQLException {
        try {
            return value == null ? null : JSON.readValue(value, SpringCounts.class);
        } catch (RuntimeException exception) {
            throw new SQLException("Invalid spring counts", exception);
        }
    }

    @Override
    public void setNonNullParameter(PreparedStatement statement, int index, SpringCounts value, JdbcType type) throws SQLException {
        statement.setString(index, JSON.writeValueAsString(value));
    }

    @Override
    public SpringCounts getNullableResult(ResultSet result, String column) throws SQLException {
        return read(result.getString(column));
    }

    @Override
    public SpringCounts getNullableResult(ResultSet result, int column) throws SQLException {
        return read(result.getString(column));
    }

    @Override
    public SpringCounts getNullableResult(CallableStatement statement, int column) throws SQLException {
        return read(statement.getString(column));
    }
}
