package io.mcpm.core.discovery;

import java.util.List;

/**
 * Represents a discovered MCP service on the local system.
 *
 * @param source       How it was found: "process", "port", "config"
 * @param name         Service name (from process name or port number)
 * @param type         "stdio" or "sse"
 * @param transport    Transport details (pid:port or command line)
 * @param status       Current status: "running", "stopped", "unknown"
 * @param command      The command string (for stdio servers)
 * @param url          The URL (for SSE servers)
 * @param lastSeen     When it was last confirmed running
 */
public record DiscoveredService(
        String source,
        String name,
        String type,
        String transport,
        String status,
        String command,
        String url,
        String lastSeen) {

    public String displayName() {
        return name != null ? name : (url != null ? url : "unknown");
    }
}
