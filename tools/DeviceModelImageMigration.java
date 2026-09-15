import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import org.yaml.snakeyaml.Yaml;

/** Applies only database/37-device-model-images.sql using the server datasource. */
public class DeviceModelImageMigration {
    public static void main(String[] args) throws Exception {
        if (args.length > 1 || (args.length == 1 && !args[0].equals("--apply"))) {
            throw new IllegalArgumentException("Usage: DeviceModelImageMigration.java [--apply]");
        }
        try {
            run(args.length == 1);
        } catch (SQLException exception) {
            // Avoid printing connection URLs, usernames, or credentials from driver errors.
            System.err.println("databaseOperationFailed=" + exception.getClass().getSimpleName()
                + "; sqlState=" + exception.getSQLState() + "; vendorCode=" + exception.getErrorCode());
            System.exit(1);
        }
    }

    private static void run(boolean apply) throws Exception {
        Map<?, ?> config;
        try (var input = Files.newInputStream(Path.of("src/main/resources/application.yml"))) {
            config = new Yaml().load(input);
        }
        Map<?, ?> source = (Map<?, ?>) ((Map<?, ?>) config.get("spring")).get("datasource");
        DriverManager.setLoginTimeout(15);
        try (Connection connection = DriverManager.getConnection(setting(source, "url", "DB_URL"),
                setting(source, "username", "DB_USERNAME"), setting(source, "password", "DB_PASSWORD"));
             Statement sql = connection.createStatement()) {
            if (!"hqc_plt".equals(connection.getCatalog())) throw new IllegalStateException("Configured database does not match migration target hqc_plt");
            sql.setQueryTimeout(30);
            sql.execute("SET SESSION lock_wait_timeout = 10");
            System.out.println("database=" + connection.getCatalog());
            boolean existed = imageColumnReady(sql);
            System.out.println("imageColumnBefore=" + existed);
            var models = snapshot(sql, "SELECT id, name, brand, sn_prefix, created_at, updated_at FROM device_models ORDER BY id");
            var devices = snapshot(sql, "SELECT id, device_model, brand, device_name, device_source, serial_number, created_at, updated_at FROM devices ORDER BY id");
            var bindings = snapshot(sql, "SELECT user_id, device_id, selected_at FROM user_device_selections ORDER BY user_id, device_id");
            System.out.println("modelCount=" + models.size() + "; deviceCount=" + devices.size() + "; bindingCount=" + bindings.size());
            if (apply) {
                String script = Files.readString(Path.of("database/37-device-model-images.sql"))
                    .replaceAll("(?m)^\\s*--[^\\r\\n]*", "");
                for (String command : script.split(";")) if (!command.isBlank()) sql.execute(command);
                if (!imageColumnReady(sql)) throw new IllegalStateException("image_url was not created");
                boolean unchanged = models.equals(snapshot(sql, "SELECT id, name, brand, sn_prefix, created_at, updated_at FROM device_models ORDER BY id"))
                    && devices.equals(snapshot(sql, "SELECT id, device_model, brand, device_name, device_source, serial_number, created_at, updated_at FROM devices ORDER BY id"))
                    && bindings.equals(snapshot(sql, "SELECT user_id, device_id, selected_at FROM user_device_selections ORDER BY user_id, device_id"));
                System.out.println("migration37Executed=true; existingModelsDevicesAndBindingsPreserved=" + unchanged);
                if (!unchanged) throw new IllegalStateException("Existing data changed during migration; check concurrent writes");
            }
            boolean ready = imageColumnReady(sql);
            System.out.println("deviceModelImageSchemaReady=" + ready);
            if (ready) {
                try (ResultSet rows = sql.executeQuery("SELECT COUNT(*), COUNT(image_url) FROM device_models")) {
                    rows.next();
                    long configured = rows.getLong(2);
                    System.out.println("modelCount=" + rows.getLong(1) + "; modelsWithImage=" + configured);
                    if (apply && !existed && configured != 0) throw new IllegalStateException("Unexpected non-null image values after adding the column");
                }
                try (ResultSet rows = sql.executeQuery("SELECT d.id, m.image_url FROM user_device_selections uds JOIN devices d ON d.id = uds.device_id LEFT JOIN device_models m ON d.device_source = 'OWN' AND m.brand = d.brand AND m.name = d.device_model")) {
                    int count = 0;
                    while (rows.next()) count++;
                    System.out.println("boundDeviceImageQuery=passed; rows=" + count);
                }
            }
        }
    }

    private static boolean imageColumnReady(Statement sql) throws SQLException {
        try (ResultSet rows = sql.executeQuery("SELECT DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE, COLUMN_DEFAULT FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'device_models' AND column_name = 'image_url'")) {
            if (!rows.next()) return false;
            if (!"varchar".equalsIgnoreCase(rows.getString(1)) || rows.getLong(2) != 500
                || !"YES".equals(rows.getString(3)) || rows.getObject(4) != null) {
                throw new IllegalStateException("Existing image_url definition does not match VARCHAR(500) NULL DEFAULT NULL");
            }
            return true;
        }
    }

    private static List<List<String>> snapshot(Statement sql, String query) throws SQLException {
        List<List<String>> result = new ArrayList<>();
        try (ResultSet rows = sql.executeQuery(query)) {
            int columns = rows.getMetaData().getColumnCount();
            while (rows.next()) {
                List<String> row = new ArrayList<>();
                for (int column = 1; column <= columns; column++) row.add(rows.getString(column));
                result.add(row);
            }
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
