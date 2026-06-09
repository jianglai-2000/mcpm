package io.mcpm.spi;

/**
 * Reads and writes AI client configuration files (e.g. mcp.json, claude_desktop_config.json).
 * <p>
 * Different AI clients use different config formats and locations. Implement this
 * interface to add support for a new client's configuration format,
 * allowing mcpm to install/uninstall servers in that client.
 * <p>
 * Discovered via {@link java.util.ServiceLoader} from
 * {@code META-INF/services/io.mcpm.spi.ConfigFormatProvider}.
 */
public interface ConfigFormatProvider {

    /**
     * Short format identifier, e.g. {@code "mcp.json"}, {@code "claude-desktop"}.
     */
    String formatName();

    /**
     * Determine if this provider can handle the given config file path
     * (by filename or content inspection).
     */
    boolean canHandle(String configFilePath);

    /**
     * Read the MCP server entries from a config file.
     *
     * @return a snapshot of installed servers
     */
    McpConfig read(String configFilePath);

    /**
     * Write the MCP server entries back to a config file.
     */
    void write(String configFilePath, McpConfig config);
}
