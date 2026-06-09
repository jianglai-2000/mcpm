package io.mcpm.core.settings;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manages mcpm global settings stored in {@code ~/.mcpm/config.json}.
 * <p>
 * Configuration keys:
 * <ul>
 *   <li>{@code registry.url} — default registry URL for publish</li>
 *   <li>{@code color.enabled} — enable/disable ANSI colors (default: auto)</li>
 *   <li>{@code config.path} — default mcp.json path (default: auto-detect)</li>
 *   <li>{@code state.path} — state file path (default: ~/.mcpm/state.json)</li>
 * </ul>
 */
public class SettingsManager {

    private static final Logger log = LoggerFactory.getLogger(SettingsManager.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int SETTINGS_VERSION = 1;

    private final Path settingsFile;
    private final Map<String, String> settings;

    public SettingsManager() {
        this(Path.of(System.getProperty("user.home"), ".mcpm", "config.json"));
    }

    SettingsManager(Path settingsFile) {
        this.settingsFile = settingsFile;
        this.settings = new LinkedHashMap<>();
        load();
        initDefaults();
    }

    /**
     * Get a setting value.
     *
     * @return the value, or {@code null} if not set
     */
    public String get(String key) {
        return settings.get(key);
    }

    /**
     * Get a setting with a default fallback.
     */
    public String get(String key, String defaultValue) {
        return settings.getOrDefault(key, defaultValue);
    }

    /**
     * Set a setting value and persist.
     */
    public void set(String key, String value) {
        settings.put(key, value);
        save();
    }

    /**
     * Unset a setting.
     */
    public void unset(String key) {
        settings.remove(key);
        save();
    }

    /**
     * Check if a key is set.
     */
    public boolean has(String key) {
        return settings.containsKey(key);
    }

    /**
     * Return all settings as an unmodifiable map.
     */
    public Map<String, String> all() {
        return Map.copyOf(settings);
    }

    // ---- Persistence ----

    private void load() {
        File file = settingsFile.toFile();
        if (!file.exists()) return;
        try {
            var root = MAPPER.readTree(file);
            if (root.has("settings") && root.get("settings").isObject()) {
                settings.clear();
                root.get("settings").fields().forEachRemaining(
                        f -> settings.put(f.getKey(), f.getValue().asText("")));
            }
        } catch (IOException e) {
            log.warn("Failed to load settings, starting fresh: {}", e.getMessage());
            settings.clear();
        }
    }

    private synchronized void save() {
        try {
            Files.createDirectories(settingsFile.getParent());
            securePermissions(settingsFile.getParent());
            ObjectNode root = MAPPER.createObjectNode();
            root.put("version", SETTINGS_VERSION);
            ObjectNode settingsNode = root.putObject("settings");
            settings.forEach(settingsNode::put);

            var printer = new DefaultPrettyPrinter()
                    .withObjectIndenter(new DefaultIndenter("  ", "\n"))
                    .withArrayIndenter(new DefaultIndenter("  ", "\n"));
            MAPPER.writer(printer).writeValue(settingsFile.toFile(), root);
        } catch (IOException e) {
            log.error("Failed to save settings: {}", settingsFile, e);
        }
    }

    private void initDefaults() {
        boolean dirty = false;
        if (!settings.containsKey("color.enabled")) {
            settings.put("color.enabled", "auto");
            dirty = true;
        }
        if (!settings.containsKey("registry.url")) {
            settings.put("registry.url", "");
            dirty = true;
        }
        if (dirty) save();
    }

    /** Restrict directory permissions to owner-only on Unix systems. */
    private static void securePermissions(java.nio.file.Path dir) {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) return;
        try {
            var perms = java.util.EnumSet.of(
                    java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                    java.nio.file.attribute.PosixFilePermission.OWNER_WRITE,
                    java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE,
                    java.nio.file.attribute.PosixFilePermission.GROUP_READ);
            java.nio.file.Files.setPosixFilePermissions(dir, perms);
        } catch (Exception ignored) {}
    }
}
