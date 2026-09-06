import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import org.yaml.snakeyaml.Yaml;

public class CourseTrainingTagsMigration {
    public static void main(String[] args) throws Exception {
        if (args.length > 1 || (args.length == 1 && !args[0].equals("--apply"))) {
            throw new IllegalArgumentException("Usage: CourseTrainingTagsMigration.java [--apply]");
        }
        boolean apply = args.length == 1;
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
            System.out.println("database=" + connection.getCatalog());
            statement.setQueryTimeout(30);
            statement.execute("SET SESSION lock_wait_timeout = 10");
            // Equivalent to migration 28, without client-only DELIMITER commands.
            boolean tags = ensureColumn(connection, statement, apply, "courses", "training_tags", "varchar", 200,
                "ALTER TABLE courses ADD COLUMN training_tags VARCHAR(200) NULL");
            boolean advice = ensureColumn(connection, statement, apply, "course_exercises", "recommended_plays", "int", null,
                "ALTER TABLE course_exercises ADD COLUMN recommended_plays INT NULL");
            if (tags && advice) {
                try (ResultSet rows = statement.executeQuery("SELECT id, training_tags FROM courses WHERE status = 'PUBLISHED' ORDER BY id LIMIT 1")) {
                    if (rows.next()) System.out.println("publishedCourseId=" + rows.getLong("id"));
                }
                try (ResultSet rows = statement.executeQuery("SELECT course_id, recommended_plays FROM course_exercises LIMIT 1")) {
                    System.out.println("courseExerciseQuery=passed");
                }
            }
            System.out.println("courseTrainingTagsSchemaReady=" + (tags && advice));
        }
    }

    private static boolean ensureColumn(Connection connection, Statement statement, boolean apply,
            String table, String column, String type, Integer length, String ddl) throws SQLException {
        if (!columnReady(connection, table, column, type, length)) {
            if (!apply) { System.out.println(table + "." + column + "=missing"); return false; }
            statement.execute(ddl);
            System.out.println("added=" + table + "." + column);
        }
        if (!columnReady(connection, table, column, type, length)) throw new SQLException("Column still missing: " + table + "." + column);
        System.out.println(table + "." + column + "=verified");
        return true;
    }

    private static boolean columnReady(Connection connection, String table, String column, String type, Integer length) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?")) {
            query.setQueryTimeout(30);
            query.setString(1, table); query.setString(2, column);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) return false;
                if (!type.equals(row.getString("DATA_TYPE")) || !"YES".equals(row.getString("IS_NULLABLE"))
                        || (length != null && row.getInt("CHARACTER_MAXIMUM_LENGTH") != length)) {
                    throw new SQLException("Unexpected existing column definition: " + table + "." + column);
                }
                return true;
            }
        }
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
