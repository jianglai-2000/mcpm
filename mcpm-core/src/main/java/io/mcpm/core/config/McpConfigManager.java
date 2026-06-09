package io.mcpm.core.config;

import io.mcpm.spi.ConfigFormatProvider;
import io.mcpm.spi.McpConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Manages reading and writing of AI client MCP configuration files.
 * <p>
 * Discovers {@link ConfigFormatProvider} implementations via {@link ServiceLoader}
 * and delegates to the appropriate provider based on filename.
 */
public class McpConfigManager {

    private static final Logger log = LoggerFactory.getLogger(McpConfigManager.class);

    private final List<ConfigFormatProvider> providers;

    public McpConfigManager() {
        this(ServiceLoader.load(ConfigFormatProvider.class));
    }

    public McpConfigManager(Iterable<ConfigFormatProvider> providers) {
        this.providers = new ArrayList<>();
        providers.forEach(this.providers::add);
    }

    /**
     * Read an MCP config file, auto-detecting the format from the file path.
     *
     * @param configPath path to the config file (e.g. ./mcp.json, ~/.claude/claude_desktop_config.json)
     * @return parsed config
     * @throws IllegalArgumentException if no provider can handle the file
     */
    public McpConfig read(Path configPath) {
        String pathStr = configPath.toString();
        ConfigFormatProvider provider = findProvider(pathStr);
        return provider.read(pathStr);
    }

    /**
     * Write an MCP config file.
     */
    public void write(Path configPath, McpConfig config) {
        String pathStr = configPath.toString();
        ConfigFormatProvider provider = findProvider(pathStr);
        provider.write(pathStr, config);
    }

    private ConfigFormatProvider findProvider(String path) {
        for (ConfigFormatProvider p : providers) {
            if (p.canHandle(path)) {
                return p;
            }
        }
        throw new IllegalArgumentException(
                "No ConfigFormatProvider can handle: " + path);
    }

    /** All registered config format providers. */
    public List<ConfigFormatProvider> providers() {
        return List.copyOf(providers);
    }
}
