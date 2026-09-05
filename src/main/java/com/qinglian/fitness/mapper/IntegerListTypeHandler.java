package com.qinglian.fitness.mapper;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.sql.*;
import java.util.List;

public class IntegerListTypeHandler extends BaseTypeHandler<List<Integer>> {
    private static final ObjectMapper JSON = new ObjectMapper();
    private List<Integer> read(String value) throws SQLException {
        try { return value == null ? List.of() : JSON.readValue(value, new TypeReference<List<Integer>>() {}); }
        catch (RuntimeException exception) { throw new SQLException("Invalid integer list", exception); }
    }
    @Override public void setNonNullParameter(PreparedStatement statement, int index, List<Integer> value, JdbcType type) throws SQLException {
        statement.setString(index, JSON.writeValueAsString(value));
    }
    @Override public List<Integer> getNullableResult(ResultSet result, String column) throws SQLException { return read(result.getString(column)); }
    @Override public List<Integer> getNullableResult(ResultSet result, int column) throws SQLException { return read(result.getString(column)); }
    @Override public List<Integer> getNullableResult(CallableStatement statement, int column) throws SQLException { return read(statement.getString(column)); }
}
