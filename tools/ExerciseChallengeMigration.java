import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import org.yaml.snakeyaml.Yaml;

public class ExerciseChallengeMigration {
    public static void main(String[] args) throws Exception {
        if (args.length > 1 || (args.length == 1 && !args[0].equals("--apply"))) {
            throw new IllegalArgumentException("Usage: ExerciseChallengeMigration.java [--apply]");
        }
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
            statement.execute("SET SESSION innodb_lock_wait_timeout = 10");
            connection.setAutoCommit(false);
            try {
                if (args.length == 1) {
                    System.out.println("updatedExercises=" + statement.executeUpdate(
                        "UPDATE exercises SET level = '\u6311\u6218' WHERE level = '\u62c9\u4f38'"));
                }
                try (ResultSet rows = statement.executeQuery(
                        "SELECT COUNT(*) FROM exercises WHERE level = '\u62c9\u4f38'")) {
                    rows.next();
                    long remaining = rows.getLong(1);
                    System.out.println("remainingLegacyLevels=" + remaining);
                    if (args.length == 1 && remaining != 0) throw new SQLException("Legacy levels remain");
                }
                connection.commit();
            } catch (Exception error) {
                connection.rollback();
                throw error;
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
