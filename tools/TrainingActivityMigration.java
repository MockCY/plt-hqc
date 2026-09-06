import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import org.yaml.snakeyaml.Yaml;

public class TrainingActivityMigration {
    public static void main(String[] args) throws Exception {
        Map<?, ?> config;
        try (var input = Files.newInputStream(Path.of("src/main/resources/application.yml"))) {
            config = new Yaml().load(input);
        }
        Map<?, ?> source = (Map<?, ?>) ((Map<?, ?>) config.get("spring")).get("datasource");
        DriverManager.setLoginTimeout(15);
        try (Connection connection = DriverManager.getConnection(
                setting(source, "url", "DB_URL"), setting(source, "username", "DB_USERNAME"),
                setting(source, "password", "DB_PASSWORD"));
             Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(30);
            System.out.println("database=" + connection.getCatalog());
            if (args.length == 1 && args[0].equals("--apply")) {
                statement.execute(Files.readString(Path.of("database/27-training-activity.sql")));
                System.out.println("migrationExecuted=true");
            }
            Map<String, String> columns = new LinkedHashMap<>();
            try (ResultSet rows = statement.executeQuery("SHOW COLUMNS FROM training_activity")) {
                while (rows.next()) columns.put(rows.getString("Field"), rows.getString("Type"));
            }
            if (!columns.keySet().equals(new LinkedHashSet<>(List.of(
                    "user_id", "activity_type", "item_id", "started_at", "training_date", "active_seconds")))) {
                throw new IllegalStateException("Unexpected training activity columns: " + columns);
            }
            System.out.println("columns=" + columns);
            var primaryKey = new TreeMap<Short, String>();
            try (ResultSet rows = connection.getMetaData().getPrimaryKeys(connection.getCatalog(), null, "training_activity")) {
                while (rows.next()) primaryKey.put(rows.getShort("KEY_SEQ"), rows.getString("COLUMN_NAME"));
            }
            if (!new ArrayList<>(primaryKey.values()).equals(List.of("user_id", "activity_type", "item_id", "started_at", "training_date"))) {
                throw new IllegalStateException("Unexpected primary key: " + primaryKey);
            }
            boolean userForeignKey = false;
            try (ResultSet rows = connection.getMetaData().getImportedKeys(connection.getCatalog(), null, "training_activity")) {
                while (rows.next()) {
                    userForeignKey |= "user_id".equals(rows.getString("FKCOLUMN_NAME"))
                        && "users".equals(rows.getString("PKTABLE_NAME"))
                        && "id".equals(rows.getString("PKCOLUMN_NAME"))
                        && rows.getShort("DELETE_RULE") == DatabaseMetaData.importedKeyCascade;
                }
            }
            if (!userForeignKey) throw new IllegalStateException("Expected user foreign key was not found");
            System.out.println("primaryKeyVerified=true; userForeignKeyVerified=true");
            System.out.println("trainingActivitySchemaReady=true");
        }
    }

    private static String setting(Map<?, ?> source, String key, String env) {
        String override = System.getenv(env);
        if (override != null && !override.isBlank()) return override;
        String value = Objects.toString(source.get(key), "");
        String prefix = "${" + env + ":";
        if (value.startsWith(prefix) && value.endsWith("}")) return value.substring(prefix.length(), value.length() - 1);
        if (value.isBlank() || value.startsWith("${")) throw new IllegalStateException("Missing datasource " + key);
        return value;
    }
}
