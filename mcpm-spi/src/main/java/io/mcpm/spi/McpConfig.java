package io.mcpm.spi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents the content of an AI client's MCP server configuration file
 * (e.g. the {@code mcpServers} block in {@code mcp.json}).
 * <p>
 * Provides a client-format-agnostic view of installed servers.
 */
public final class McpConfig {

    private final Map<String, ServerEntry> servers;

    public McpConfig() {
        this.servers = new LinkedHashMap<>();
    }

    public McpConfig(Map<String, ServerEntry> servers) {
        this.servers = new LinkedHashMap<>(Objects.requireNonNull(servers));
    }

    public Map<String, ServerEntry> servers() {
        return Collections.unmodifiableMap(servers);
    }

    public void addServer(String name, ServerEntry entry) {
        servers.put(name, entry);
    }

    public void removeServer(String name) {
        servers.remove(name);
    }

    public ServerEntry getServer(String name) {
        return servers.get(name);
    }

    public boolean hasServer(String name) {
        return servers.containsKey(name);
    }

    public int size() {
        return servers.size();
    }

    /**
     * A single MCP server entry in the configuration.
     */
    public static final class ServerEntry {
        private final String command;
        private final List<String> args;
        private final Map<String, String> env;
        private final String transportType;  // null for stdio, "sse" or "streamable-http" for remote
        private final String transportUrl;   // the URL for remote transports

        public ServerEntry(String command, List<String> args, Map<String, String> env) {
            this(command, args, env, null, null);
        }

        public ServerEntry(String command, List<String> args, Map<String, String> env,
                           String transportType, String transportUrl) {
            this.command = command;
            this.args = args == null ? List.of() : List.copyOf(args);
            this.env = env == null ? Map.of() : Map.copyOf(env);
            this.transportType = transportType;
            this.transportUrl = transportUrl;
        }

        public String command() { return command; }
        public List<String> args() { return args; }
        public Map<String, String> env() { return env; }
        public String transportType() { return transportType; }
        public String transportUrl() { return transportUrl; }

        public boolean isStdio() { return transportType == null; }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            if (command != null) map.put("command", command);
            if (!args.isEmpty()) map.put("args", new ArrayList<>(args));
            if (!env.isEmpty()) map.put("env", new LinkedHashMap<>(env));
            if (transportType != null) map.put("type", transportType);
            if (transportUrl != null) map.put("url", transportUrl);
            return map;
        }
    }
}
