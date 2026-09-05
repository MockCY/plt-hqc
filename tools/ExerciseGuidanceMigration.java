import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.regex.Pattern;

public class ExerciseGuidanceMigration {
    public static void main(String[] args) throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/application.yml"));
        try (Connection connection = DriverManager.getConnection(setting(yaml, "url", "DB_URL"), setting(yaml, "username", "DB_USERNAME"), setting(yaml, "password", "DB_PASSWORD"));
             Statement statement = connection.createStatement()) {
            boolean exists;
            try (ResultSet columns = connection.getMetaData().getColumns(connection.getCatalog(), null, "exercises", "instruction_audio_url")) { exists = columns.next(); }
            if (!exists && args.length == 1 && args[0].equals("--apply")) {
                statement.execute(Files.readString(Path.of("database/24-exercise-guidance.sql")));
                exists = true;
            }
            System.out.println("exerciseGuidanceColumnsReady=" + exists);
        }
    }
    private static String setting(String yaml, String key, String env) {
        String value = System.getenv(env);
        if (value != null && !value.isBlank()) return value;
        var matcher = Pattern.compile("(?m)^\\s{4}" + key + ":\\s*\\$\\{" + env + ":([^}]+)}").matcher(yaml);
        if (!matcher.find()) throw new IllegalStateException("Missing " + key);
        return matcher.group(1);
    }
}
