import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DeviceDatabaseMigration {
    public static void main(String[] args) throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/application.yml"));
        try (Connection connection = DriverManager.getConnection(
            setting(yaml, "url", "DB_URL"), setting(yaml, "username", "DB_USERNAME"), setting(yaml, "password", "DB_PASSWORD"));
             Statement statement = connection.createStatement()) {
            String sql = Files.readString(Path.of("database/06-devices.sql"));
            for (String command : sql.replaceFirst("(?i)USE\\s+hqc_plt\\s*;", "").split(";")) {
                if (!command.isBlank()) statement.execute(command.trim());
            }
            try (ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM devices WHERE active=true")) {
                result.next();
                System.out.println("activeDevices=" + result.getInt(1));
            }
            try (ResultSet result = statement.executeQuery("SELECT code,name,connected FROM devices ORDER BY sort_order,id")) {
                while (result.next()) {
                    System.out.println(result.getString(1) + "|" + result.getString(2) + "|connected=" + result.getBoolean(3));
                }
            }
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
