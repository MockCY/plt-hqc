import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import org.yaml.snakeyaml.Yaml;

public class MultipleDeviceMigration {
    public static void main(String[] args) throws Exception {
        if (args.length > 1 || (args.length == 1 && !args[0].equals("--apply"))) {
            throw new IllegalArgumentException("Usage: MultipleDeviceMigration.java [--apply]");
        }
        boolean apply = args.length == 1;
        Map<?, ?> config;
        try (var input = Files.newInputStream(Path.of("src/main/resources/application.yml"))) {
            config = new Yaml().load(input);
        }
        Map<?, ?> source = (Map<?, ?>) ((Map<?, ?>) config.get("spring")).get("datasource");
        DriverManager.setLoginTimeout(15);
        try (Connection connection = DriverManager.getConnection(setting(source, "url", "DB_URL"),
                setting(source, "username", "DB_USERNAME"), setting(source, "password", "DB_PASSWORD"));
             Statement sql = connection.createStatement()) {
            sql.setQueryTimeout(30);
            sql.execute("SET SESSION lock_wait_timeout = 10");
            var indexes = uniqueIndexes(connection);
            if (!indexes.containsValue(List.of("device_id"))) throw new SQLException("Missing unique device ownership constraint");
            var primary = indexes.get("PRIMARY");
            boolean ready = List.of("user_id", "device_id").equals(primary);
            if (!ready && !List.of("user_id").equals(primary)) throw new SQLException("Unexpected binding primary key: " + primary);
            var before = bindings(sql);
            System.out.println("database=" + connection.getCatalog() + ", bindingCount=" + before.size());
            System.out.println("primaryKey=" + primary + ", uniqueDeviceOwnership=verified");
            if (!ready && apply) {
                sql.execute(Files.readString(Path.of("database/29-multiple-user-devices.sql")));
                var after = uniqueIndexes(connection);
                if (!List.of("user_id", "device_id").equals(after.get("PRIMARY")) || !after.containsValue(List.of("device_id"))) {
                    throw new SQLException("Binding constraints did not match the migration target");
                }
                if (!before.equals(bindings(sql))) throw new SQLException("Bindings changed during migration; check concurrent writes");
                System.out.println("existingBindingsAndTimestamps=preserved");
                ready = true;
            }
            System.out.println("multipleDeviceSchemaReady=" + ready);
        }
    }

    private static Map<String, List<String>> uniqueIndexes(Connection connection) throws SQLException {
        var ordered = new HashMap<String, SortedMap<Short, String>>();
        try (var rows = connection.getMetaData().getIndexInfo(connection.getCatalog(), null, "user_device_selections", true, false)) {
            while (rows.next()) {
                String name = rows.getString("INDEX_NAME"), column = rows.getString("COLUMN_NAME");
                if (name != null && column != null) ordered.computeIfAbsent(name, key -> new TreeMap<>()).put(rows.getShort("ORDINAL_POSITION"), column);
            }
        }
        var result = new HashMap<String, List<String>>();
        ordered.forEach((name, columns) -> result.put(name, List.copyOf(columns.values())));
        return result;
    }

    private static List<String> bindings(Statement sql) throws SQLException {
        var result = new ArrayList<String>();
        try (var rows = sql.executeQuery("select user_id, device_id, selected_at from user_device_selections order by user_id, device_id")) {
            while (rows.next()) result.add(rows.getLong(1) + ":" + rows.getLong(2) + ":" + rows.getTimestamp(3));
        }
        return result;
    }

    private static String setting(Map<?, ?> source, String key, String env) {
        for (String name : List.of("SPRING_DATASOURCE_" + key.toUpperCase(Locale.ROOT), env)) {
            String override = System.getenv(name);
            if (override != null && !override.isBlank()) return override;
        }
        String value = Objects.toString(source.get(key), "");
        String prefix = "${" + env + ":";
        if (value.startsWith(prefix) && value.endsWith("}")) return value.substring(prefix.length(), value.length() - 1);
        if (value.isBlank() || value.startsWith("${")) throw new IllegalStateException("Missing datasource " + key);
        return value;
    }
}
