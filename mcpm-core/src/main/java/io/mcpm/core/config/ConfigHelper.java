package io.mcpm.core.config;

import io.mcpm.spi.McpConfig;
import io.mcpm.spi.McpmPackage;
import io.mcpm.spi.PackageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * High-level helper for reading, writing, and backing up MCP config files.
 * <p>
 * Wraps {@link McpConfigManager} with production-ready behavior:
 * auto-backup before write, conflict detection, config auto-discovery,
 * and friendly error messages.
 */
public class ConfigHelper {

    private static final Logger log = LoggerFactory.getLogger(ConfigHelper.class);
    private static final DateTimeFormatter BACKUP_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final McpConfigManager configManager;

    public ConfigHelper() {
        this(new McpConfigManager());
    }

    public ConfigHelper(McpConfigManager configManager) {
        this.configManager = configManager;
    }

    // ---- Config discovery ----

    /**
     * Well-known MCP config file names.
     */
    private static final String MCP_JSON = "mcp.json";
    private static final String CLAUDE_DESKTOP_CONFIG = "claude_desktop_config.json";

    /**
     * Search for the best existing config file to use.
     * <p>
     * Resolution order:
     * <ol>
     *   <li>User-specified {@code --config} path</li>
     *   <li>{@code ./mcp.json} in current working directory</li>
     *   <li>{@code .cursor/mcp.json} in current working directory</li>
     *   <li>Cursor global: {@code ~/.cursor/mcp.json}</li>
     *   <li>Claude Desktop: platform-specific default location</li>
     *   <li>Fallback: {@code ./mcp.json} (will be created)</li>
     * </ol>
     */
    public Path resolveConfigPath(String userSpecifiedPath) {
        if (userSpecifiedPath != null) {
            Path p = Path.of(userSpecifiedPath);
            if (Files.isDirectory(p)) {
                // Look inside for known config files
                Path found = findFirstExisting(p,
                        MCP_JSON,
                        ".cursor/" + MCP_JSON,
                        CLAUDE_DESKTOP_CONFIG);
                if (found != null) return found.toAbsolutePath().normalize();
                // Nothing found — default to dir/mcp.json
                return p.resolve(MCP_JSON).toAbsolutePath().normalize();
            }
            return p.toAbsolutePath().normalize();
        }

        // Check active environment
        String env = currentEnv();
        if (!"default".equals(env)) {
            Path envConfig = Path.of("./mcp-" + env + ".json").toAbsolutePath().normalize();
            if (Files.exists(envConfig)) {
                log.info("Using env config: mcp-{}.json", env);
                return envConfig;
            }
            return envConfig;
        }

        // Scan well-known locations
        Path home = Path.of(System.getProperty("user.home"));

        // Priority-ordered scan list
        List<Path> candidates = new ArrayList<>();

        // 1) Project-level mcp.json
        candidates.add(Path.of("./" + MCP_JSON).toAbsolutePath().normalize());

        // 2) Project-level claude_desktop_config.json
        candidates.add(Path.of("./" + CLAUDE_DESKTOP_CONFIG).toAbsolutePath().normalize());

        // 3) Cursor project-level
        candidates.add(Path.of("./.cursor/" + MCP_JSON).toAbsolutePath().normalize());

        // 4) Cursor user-level
        candidates.add(home.resolve(".cursor/" + MCP_JSON));

        // 5) Claude Desktop (platform-specific)
        Path claudePath = claudeDesktopConfigPath();
        if (claudePath != null) {
            candidates.add(claudePath);
        }

        // Return first existing, or the project-level mcp.json as fallback
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                log.info("Detected config: {}", candidate);
                return candidate;
            }
        }

        // Fallback: project-level mcp.json (will be created)
        return candidates.get(0);
    }

    /**
     * Return all discovered config files, ordered by priority.
     */
    public List<DiscoveredConfig> discoverConfigs() {
        List<DiscoveredConfig> result = new ArrayList<>();

        Path home = Path.of(System.getProperty("user.home"));
        String os = System.getProperty("os.name").toLowerCase();

        // Project mcp.json
        addIfExists(result, Path.of("./" + MCP_JSON), "Project", MCP_JSON);

        // Project claude_desktop_config.json
        addIfExists(result, Path.of("./" + CLAUDE_DESKTOP_CONFIG), "Project (Claude Desktop)", CLAUDE_DESKTOP_CONFIG);

        // Cursor project
        addIfExists(result, Path.of("./.cursor/" + MCP_JSON), "Cursor (project)", MCP_JSON);

        // Cursor user
        addIfExists(result, home.resolve(".cursor/" + MCP_JSON), "Cursor (user)", MCP_JSON);

        // Claude Desktop
        Path claudePath = claudeDesktopConfigPath();
        if (claudePath != null) {
            addIfExists(result, claudePath, "Claude Desktop", CLAUDE_DESKTOP_CONFIG);
        }

        return result;
    }

    /**
     * A discovered config file with a human-readable label.
     */
    public record DiscoveredConfig(Path path, String label, String format) {
        @Override
        public String toString() {
            return String.format("  %-22s %s", label + ":", path);
        }
    }

    private void addIfExists(List<DiscoveredConfig> list, Path path, String label, String format) {
        if (Files.exists(path)) {
            list.add(new DiscoveredConfig(path.toAbsolutePath().normalize(), label, format));
        }
    }

    /** Read current environment from settings, defaulting to 'default'. */
    static String currentEnv() {
        try {
            var settings = new io.mcpm.core.settings.SettingsManager();
            return settings.get("env", "default");
        } catch (Exception e) {
            return "default";
        }
    }

    /**
     * Platform-dependent Claude Desktop config path.
     */
    private static Path claudeDesktopConfigPath() {
        String os = System.getProperty("os.name").toLowerCase();
        Path home = Path.of(System.getProperty("user.home"));

        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null) {
                return Path.of(appData, "Claude", CLAUDE_DESKTOP_CONFIG);
            }
            return home.resolve("AppData/Roaming/Claude/" + CLAUDE_DESKTOP_CONFIG);
        } else if (os.contains("mac")) {
            return home.resolve("Library/Application Support/Claude/" + CLAUDE_DESKTOP_CONFIG);
        } else {
            // Linux
            return home.resolve(".config/Claude/" + CLAUDE_DESKTOP_CONFIG);
        }
    }

    private static Path findFirstExisting(Path dir, String... names) {
        for (String name : names) {
            Path p = dir.resolve(name);
            if (Files.exists(p)) return p;
        }
        return null;
    }

    // ---- Read / Write ----

    /**
     * Read existing config or return an empty one if the file doesn't exist.
     */
    public McpConfig readOrEmpty(Path configFile) {
        if (Files.exists(configFile)) {
            return configManager.read(configFile);
        }
        return new McpConfig();
    }

    /**
     * Write config, creating a timestamped backup of the previous file first.
     *
     * @return the backup path, or null if no backup was made
     */
    public Path writeWithBackup(Path configFile, McpConfig config) throws IOException {
        Path backupPath = null;
        if (Files.exists(configFile)) {
            backupPath = configFile.resolveSibling(
                    configFile.getFileName() + ".bak." + BACKUP_STAMP.format(LocalDateTime.now()));
            Files.copy(configFile, backupPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("Backed up existing config to {}", backupPath);
        }
        configManager.write(configFile, config);
        return backupPath;
    }

    /**
     * Check if a server with the given name already exists in the config.
     *
     * @return a descriptive message, or null if no conflict
     */
    public String checkConflict(McpConfig config, String serverName) {
        if (config.hasServer(serverName)) {
            McpConfig.ServerEntry existing = config.getServer(serverName);
            return "Server '" + serverName + "' already exists in config.\n"
                    + "  Existing: " + describeEntry(existing) + "\n"
                    + "  Use --yes to overwrite.";
        }
        return null;
    }

    /**
     * Remove a server from the config and save.
     *
     * @return the removed entry, or null if not found
     */
    public McpConfig.ServerEntry removeServer(Path configFile, String serverName) throws IOException {
        if (!Files.exists(configFile)) {
            return null;
        }
        McpConfig config = configManager.read(configFile);
        McpConfig.ServerEntry removed = config.getServer(serverName);
        if (removed == null) {
            return null;
        }
        config.removeServer(serverName);
        writeWithBackup(configFile, config);
        return removed;
    }

    // ---- Descriptions ----

    /**
     * Build a human-readable description of a server entry (from install result).
     */
    public static String describeEntry(McpmPackage pkg, PackageHandler.InstallResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("type: ").append(pkg.type());
        sb.append(", command: ").append(result.entryCommand());
        if (!result.entryArgs().isEmpty()) {
            sb.append(", args: ").append(result.entryArgs());
        }
        if (!result.entryEnv().isEmpty()) {
            sb.append(", env: ").append(result.entryEnv().keySet());
        }
        return sb.toString();
    }

    private static String describeEntry(McpConfig.ServerEntry entry) {
        StringBuilder sb = new StringBuilder();
        if (entry.isStdio()) {
            sb.append("command: ").append(entry.command());
            if (!entry.args().isEmpty()) {
                sb.append(", args: ").append(entry.args());
            }
        } else {
            sb.append("type: ").append(entry.transportType());
            sb.append(", url: ").append(entry.transportUrl());
        }
        return sb.toString();
    }
}
