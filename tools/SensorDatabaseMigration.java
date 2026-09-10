import java.nio.file.*;
import java.net.URI;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.yaml.snakeyaml.Yaml;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

public class SensorDatabaseMigration {
    static final Map<String, Integer> TABLES = Map.of(
        "sensor_devices", 11, "sensor_device_bindings", 8,
        "sensor_binding_challenges", 7, "sensor_latest_readings", 11,
        "sensor_boots", 3, "sensor_workout_sessions", 29);

    public static void main(String[] args) throws Exception {
        boolean apply = args.length == 1 && args[0].equals("--apply");
        boolean verify = args.length == 1 && args[0].equals("--verify");
        if (args.length > 0 && !apply && !verify) throw new IllegalArgumentException("Usage: SensorDatabaseMigration.java [--apply|--verify]");
        Map<?, ?> config;
        try (var input = Files.newInputStream(Path.of("src/main/resources/application.yml"))) {
            config = new Yaml().load(input);
        }
        Map<?, ?> source = (Map<?, ?>) ((Map<?, ?>) config.get("spring")).get("datasource");
        String url = setting(source, "url", "DB_URL");
        String user = setting(source, "username", "DB_USERNAME");
        String password = setting(source, "password", "DB_PASSWORD");
        DriverManager.setLoginTimeout(15);
        try (Connection db = DriverManager.getConnection(url, user, password); Statement sql = db.createStatement()) {
            sql.setQueryTimeout(30);
            sql.execute("SET SESSION lock_wait_timeout=10");
            System.out.println("database=" + db.getCatalog() + ", serverVersion=" + db.getMetaData().getDatabaseProductVersion());
            try (var row = sql.executeQuery("select count(*) from information_schema.tables where table_schema=database()")) {
                row.next(); System.out.println("existingTableCount=" + row.getInt(1));
            }
            for (String table : new TreeSet<>(TABLES.keySet())) {
                try (var rows = db.getMetaData().getTables(db.getCatalog(), null, table, new String[]{"TABLE"})) {
                    System.out.println(table + "=" + (rows.next() ? "exists" : "missing"));
                }
            }
            try (var rows = sql.executeQuery("select column_type from information_schema.columns where table_schema=database() and table_name='devices' and column_name='id'")) {
                if (!rows.next() || !rows.getString(1).equals("bigint unsigned")) throw new SQLException("Unexpected devices.id type");
            }
            if (!apply && !verify) return;

            if (apply) {
            URI endpoint = URI.create(url.substring(5));
            Path backup = Path.of("../.runtime/database-backups", "hqc-plt-before-sensor-" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".sql").toAbsolutePath().normalize();
            Files.createDirectories(backup.getParent());
            Path errors = Path.of(backup + ".stderr.log");
            ProcessBuilder dump = new ProcessBuilder("D:/javaStudy/mysql-8.0.31-winx64/bin/mysqldump.exe",
                "--host=" + endpoint.getHost(), "--port=" + (endpoint.getPort() < 0 ? 3306 : endpoint.getPort()),
                "--user=" + user, "--single-transaction", "--skip-lock-tables", "--no-tablespaces",
                "--set-gtid-purged=OFF", "--column-statistics=0", "--default-character-set=utf8mb4",
                "--result-file=" + backup, db.getCatalog());
            dump.environment().put("MYSQL_PWD", password);
            dump.redirectError(errors.toFile());
            if (dump.start().waitFor() != 0 || !Files.exists(backup) || Files.size(backup) == 0)
                throw new IllegalStateException("Database backup failed; migration was not run. Check " + errors);
            System.out.println("backup=" + backup + ", bytes=" + Files.size(backup));

            ScriptUtils.executeSqlScript(db, new FileSystemResource("database/30-sensor-iot.sql"));
            ScriptUtils.executeSqlScript(db, new FileSystemResource("database/31-sensor-keyless.sql"));
            try (var columns=db.getMetaData().getColumns(db.getCatalog(),null,"sensor_workout_sessions","schema_version")) {
                if (!columns.next()) ScriptUtils.executeSqlScript(db,new FileSystemResource("database/33-sensor-v4.sql"));
            }
            }
            for (String table : new TreeSet<>(TABLES.keySet())) {
                int columns = 0;
                try (var rows = db.getMetaData().getColumns(db.getCatalog(), null, table, null)) {
                    while (rows.next()) columns++;
                }
                if (columns != TABLES.get(table)) throw new SQLException("Unexpected column count: " + table + "=" + columns);
                try (var rows = sql.executeQuery("show create table " + table)) {
                    rows.next();
                    String ddl = rows.getString(2);
                    if (table.equals("sensor_device_bindings") &&
                        (!ddl.contains("uk_sensor_active") || !ddl.contains("uk_bed_active") || !ddl.contains("FOREIGN KEY")))
                        throw new SQLException("Missing binding constraints");
                    if (table.equals("sensor_workout_sessions") &&
                        (!ddl.contains("uk_workout_active") || !ddl.contains("idx_workout_expiry") || !ddl.contains("uk_workout_device_session")))
                        throw new SQLException("Missing workout constraints");
                }
                System.out.println(table + "=verified, columns=" + columns);
            }
            for (String column : List.of("key_hash", "claim_hash")) {
                try (var rows = db.getMetaData().getColumns(db.getCatalog(), null, "sensor_devices", column)) {
                    if (!rows.next() || rows.getInt("NULLABLE") != DatabaseMetaData.columnNullable)
                        throw new SQLException("Keyless registration requires nullable " + column);
                }
            }
            System.out.println("keylessRegistrationSchema=verified");
            System.out.println("sensorMigration=complete");
        }
    }

    private static String setting(Map<?, ?> source, String key, String env) {
        for (String name : List.of("SPRING_DATASOURCE_" + key.toUpperCase(Locale.ROOT), env)) {
            String value = System.getenv(name);
            if (value != null && !value.isBlank()) return value;
        }
        String value = Objects.toString(source.get(key), "");
        String prefix = "${" + env + ":";
        if (value.startsWith(prefix) && value.endsWith("}")) return value.substring(prefix.length(), value.length()-1);
        if (value.isBlank() || value.startsWith("${")) throw new IllegalStateException("Missing datasource " + key);
        return value;
    }
}
