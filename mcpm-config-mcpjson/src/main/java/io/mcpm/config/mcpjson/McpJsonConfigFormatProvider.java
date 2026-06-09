package io.mcpm.config.mcpjson;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mcpm.spi.ConfigFormatProvider;
import io.mcpm.spi.McpConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Reads and writes {@code mcp.json} — the standard MCP configuration format
 * used by Claude Desktop, Cursor, VS Code (via Continue), and other clients.
 * <p>
 * Expected structure:
 * <pre>
 * {
 *   "mcpServers": {
 *     "server-name": {
 *       "command": "npx",
 *       "args": ["-y", "@org/server"],
 *       "env": { "KEY": "value" }
 *     }
 *   }
 * }
 * </pre>
 */
public class McpJsonConfigFormatProvider implements ConfigFormatProvider {

    private static final Logger log = LoggerFactory.getLogger(McpJsonConfigFormatProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> HANDLED_NAMES = Set.of(
            "mcp.json",
            "claude_desktop_config.json");

    @Override
    public String formatName() {
        return "mcp.json/claude_desktop_config.json";
    }

    @Override
    public boolean canHandle(String configFilePath) {
        if (configFilePath == null) return false;
        String name = new File(configFilePath).getName();
        return HANDLED_NAMES.contains(name);
    }

    @Override
    public McpConfig read(String configFilePath) {
        try {
            File file = new File(configFilePath);
            if (!file.exists()) {
                return new McpConfig();
            }

            JsonNode root = MAPPER.readTree(file);
            JsonNode serversNode = root.get("mcpServers");

            if (serversNode == null || !serversNode.isObject()) {
                log.warn("mcp.json has no 'mcpServers' object: {}", configFilePath);
                return new McpConfig();
            }

            McpConfig config = new McpConfig();
            Iterator<Map.Entry<String, JsonNode>> fields = serversNode.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                String name = field.getKey();
                JsonNode entry = field.getValue();

                config.addServer(name, parseEntry(entry));
            }

            return config;

        } catch (IOException e) {
            log.error("Failed to read mcp.json: {}", configFilePath, e);
            throw new RuntimeException("Failed to read config: " + configFilePath, e);
        }
    }

    @Override
    public void write(String configFilePath, McpConfig config) {
        try {
            // Preserve existing fields (important for claude_desktop_config.json
            // which has globalShortcut, etc. alongside mcpServers)
            ObjectNode root;
            File file = new File(configFilePath);
            if (file.exists()) {
                JsonNode existing = MAPPER.readTree(file);
                root = existing.isObject() ? (ObjectNode) existing : MAPPER.createObjectNode();
            } else {
                root = MAPPER.createObjectNode();
            }

            ObjectNode serversNode = MAPPER.createObjectNode();

            Map<String, McpConfig.ServerEntry> servers = config.servers();
            for (Map.Entry<String, McpConfig.ServerEntry> entry : servers.entrySet()) {
                serversNode.set(entry.getKey(), toJson(entry.getValue()));
            }

            root.set("mcpServers", serversNode);

            // Pretty-print with 2-space indent
            DefaultPrettyPrinter printer = new DefaultPrettyPrinter()
                    .withObjectIndenter(new DefaultIndenter("  ", "\n"))
                    .withArrayIndenter(new DefaultIndenter("  ", "\n"));

            MAPPER.writer(printer).writeValue(new File(configFilePath), root);

        } catch (IOException e) {
            log.error("Failed to write mcp.json: {}", configFilePath, e);
            throw new RuntimeException("Failed to write config: " + configFilePath, e);
        }
    }

    private McpConfig.ServerEntry parseEntry(JsonNode node) {
        String command = null;
        if (node.has("command")) {
            command = node.get("command").asText();
        }

        List<String> args = new ArrayList<>();
        if (node.has("args") && node.get("args").isArray()) {
            for (JsonNode arg : node.get("args")) {
                args.add(arg.asText());
            }
        }

        Map<String, String> env = new LinkedHashMap<>();
        if (node.has("env") && node.get("env").isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.get("env").fields();
            while (fields.hasNext()) {
                var field = fields.next();
                env.put(field.getKey(), field.getValue().asText());
            }
        }

        // Check for remote transport (type + url)
        String transportType = null;
        String transportUrl = null;
        if (node.has("type")) {
            transportType = node.get("type").asText();
        }
        if (node.has("url")) {
            transportUrl = node.get("url").asText();
        }

        return new McpConfig.ServerEntry(command, args, env, transportType, transportUrl);
    }

    private ObjectNode toJson(McpConfig.ServerEntry entry) {
        ObjectNode node = MAPPER.createObjectNode();

        if (entry.isStdio()) {
            node.put("command", entry.command());
            if (!entry.args().isEmpty()) {
                ArrayNode argsNode = node.putArray("args");
                entry.args().forEach(argsNode::add);
            }
        } else {
            node.put("type", entry.transportType());
            node.put("url", entry.transportUrl());
        }

        if (!entry.env().isEmpty()) {
            ObjectNode envNode = node.putObject("env");
            entry.env().forEach(envNode::put);
        }

        return node;
    }
}
