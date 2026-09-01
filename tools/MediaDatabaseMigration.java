import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MediaDatabaseMigration {
    public static void main(String[] args) throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/application.yml"));
        String url = setting(yaml, "url", "DB_URL");
        String username = setting(yaml, "username", "DB_USERNAME");
        String password = setting(yaml, "password", "DB_PASSWORD");
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            addColumn(connection, "video_url", "VARCHAR(500) NULL");
            addColumn(connection, "video_cover_image", "VARCHAR(500) NULL");
            addColumn(connection, "video_duration_seconds", "INT NULL");
            try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE courses SET cover_image = REPLACE(cover_image, '/static/fitness/', '/media/images/fitness/') WHERE cover_image LIKE '/static/fitness/%'")) {
                System.out.println("updatedCoverRows=" + statement.executeUpdate());
            }
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery(
                     "SELECT COUNT(*) column_count FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='courses' AND column_name IN ('video_url','video_cover_image','video_duration_seconds')")) {
                result.next();
                System.out.println("verifiedVideoColumns=" + result.getInt(1));
            }
        }
    }

    private static void addColumn(Connection connection, String name, String definition) throws Exception {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet columns = metadata.getColumns(connection.getCatalog(), null, "courses", name)) {
            if (columns.next()) {
                System.out.println(name + "=already_exists");
                return;
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE courses ADD COLUMN " + name + " " + definition);
            System.out.println(name + "=added");
        }
    }

    private static String setting(String yaml, String property, String environmentName) {
        String environment = System.getenv(environmentName);
        if (environment != null && !environment.isBlank()) return environment;
        Matcher matcher = Pattern.compile("(?m)^\\s{4}" + property + ":\\s*\\$\\{" + environmentName + ":([^}]+)}").matcher(yaml);
        if (!matcher.find()) throw new IllegalStateException("Missing " + property);
        return matcher.group(1);
    }
}
