import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.regex.Pattern;

public class DeviceModelBrandMigration {
    public static void main(String[] args) throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/application.yml"));
        try (Connection connection = DriverManager.getConnection(setting(yaml, "url", "DB_URL"), setting(yaml, "username", "DB_USERNAME"), setting(yaml, "password", "DB_PASSWORD"));
             Statement statement = connection.createStatement()) {
            if (args.length == 1 && (args[0].equals("--device-binding-schema") || args[0].equals("--check-device-binding-schema"))) {
                boolean apply = args[0].equals("--device-binding-schema");
                boolean ready = true;
                for (String column : new String[] { "last_bound_at", "last_unbound_at" }) {
                    boolean exists;
                    try (PreparedStatement check = connection.prepareStatement(
                            "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'devices' AND column_name = ?")) {
                        check.setString(1, column);
                        try (ResultSet rows = check.executeQuery()) { rows.next(); exists = rows.getInt(1) == 1; }
                    }
                    if (!exists && apply) {
                        statement.execute("ALTER TABLE devices ADD COLUMN " + column + " DATETIME(3) NULL");
                        exists = true;
                    }
                    System.out.println(column + "=" + exists);
                    ready &= exists;
                }
                if (ready) {
                    if (apply) {
                        int updated = statement.executeUpdate("UPDATE devices d JOIN user_device_selections s ON s.device_id = d.id SET d.last_bound_at = s.selected_at WHERE d.last_bound_at IS NULL");
                        System.out.println("backfilledBindings=" + updated);
                    }
                    try (ResultSet rows = statement.executeQuery("SELECT d.*, COALESCE(uds.selected_at, d.last_bound_at) bound_at, uds.user_id IS NOT NULL bound, uds.user_id bound_user_id, COALESCE(NULLIF(u.nickname, ''), CONCAT('用户 #', u.id)) bound_user_name, u.phone bound_user_phone FROM devices d LEFT JOIN user_device_selections uds ON uds.device_id=d.id LEFT JOIN users u ON u.id=uds.user_id ORDER BY d.created_at DESC LIMIT 20 OFFSET 0")) {
                        int count = 0;
                        while (rows.next()) count++;
                        System.out.println("deviceListQuery=passed; rows=" + count);
                    }
                }
                return;
            }
            if (args.length == 1 && args[0].equals("--presence-schema")) {
                for (String command : Files.readString(Path.of("database/23-user-presence-history.sql")).replaceFirst("(?i)USE\\s+hqc_plt\\s*;", "").split(";")) {
                    if (!command.isBlank()) statement.execute(command);
                }
                System.out.println("presenceSchemaReady=true");
                return;
            }
            boolean exists;
            try (ResultSet columns = connection.getMetaData().getColumns(connection.getCatalog(), null, "device_models", "brand")) {
                exists = columns.next();
            }
            if (!exists && args.length == 1 && args[0].equals("--apply")) {
                for (String command : Files.readString(Path.of("database/22-device-model-brand.sql")).split(";")) {
                    if (!command.isBlank()) statement.execute(command);
                }
                exists = true;
            }
            System.out.println("brandColumn=" + exists);
            try (ResultSet rows = statement.executeQuery("SELECT m.name, m.sn_prefix, d.brand, COUNT(d.id) FROM device_models m LEFT JOIN devices d ON d.device_model=m.name GROUP BY m.id,m.name,m.sn_prefix,d.brand ORDER BY m.id")) {
                while (rows.next()) System.out.println(rows.getString(1) + " | " + rows.getString(2) + " | existingDeviceBrand=" + rows.getString(3) + " | count=" + rows.getLong(4));
            }
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
