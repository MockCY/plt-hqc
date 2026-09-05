package com.qinglian.fitness.mapper;

import com.qinglian.fitness.catalog.TrainingSet;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.sql.*;
import java.util.List;

public class TrainingSetListTypeHandler extends BaseTypeHandler<List<TrainingSet>> {
    private static final ObjectMapper JSON = new ObjectMapper();

    private List<TrainingSet> read(String value) throws SQLException {
        try { return value == null ? List.of() : JSON.readValue(value, new TypeReference<List<TrainingSet>>() {}); }
        catch (RuntimeException exception) { throw new SQLException("Invalid training sets", exception); }
    }

    @Override public void setNonNullParameter(PreparedStatement statement, int index, List<TrainingSet> value, JdbcType type) throws SQLException {
        statement.setString(index, JSON.writeValueAsString(value));
    }
    @Override public List<TrainingSet> getNullableResult(ResultSet result, String column) throws SQLException { return read(result.getString(column)); }
    @Override public List<TrainingSet> getNullableResult(ResultSet result, int column) throws SQLException { return read(result.getString(column)); }
    @Override public List<TrainingSet> getNullableResult(CallableStatement statement, int column) throws SQLException { return read(statement.getString(column)); }
}
