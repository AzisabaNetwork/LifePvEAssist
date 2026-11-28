package net.azisaba.lifepveassist.database;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * LifeCore 準拠の database: セクション設定モデル。
 */
public record DatabaseConfig(
        String scheme,
        String hostname,
        int port,
        String name,
        String username,
        String password,
        Map<String, String> properties
) {

    public static DatabaseConfig fromSection(ConfigurationSection section) {
        if (section == null) {
            return new DatabaseConfig(
                    "jdbc:mariadb", "localhost", 3306, "lifepveassist", "lifepveassist", "", Map.of()
            );
        }
        String scheme = section.getString("scheme", "jdbc:mariadb");
        String hostname = section.getString("hostname", "localhost");
        int port = section.getInt("port", 3306);
        String name = section.getString("name", "lifepveassist");
        String username = section.getString("username", "lifepveassist");
        String password = section.getString("password", "");

        Map<String, String> props = new LinkedHashMap<>();
        ConfigurationSection propSec = section.getConfigurationSection("properties");
        if (propSec != null) {
            for (String key : propSec.getKeys(false)) {
                Object val = propSec.get(key);
                if (val != null) {
                    props.put(key, String.valueOf(val));
                }
            }
        }
        return new DatabaseConfig(
                scheme, hostname, port, name, username, password,
                Collections.unmodifiableMap(props)
        );
    }

    public String toJdbcUrl() {
        return scheme + "://" + hostname + ":" + port + "/" + name;
    }

    public Properties toProperties() {
        Properties p = new Properties();
        p.putAll(properties);
        return p;
    }
}
