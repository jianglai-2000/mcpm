package io.mcpm.core.discovery;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Scans local system for running MCP services via:
 * <ul>
 *   <li>Process scanning (Java 9+ ProcessHandle API)</li>
 *   <li>Port scanning (localhost TCP ports)</li>
 *   <li>Config file scanning (mcp.json entries)</li>
 * </ul>
 */
public class ServiceDiscovery {

    private static final Logger log = LoggerFactory.getLogger(ServiceDiscovery.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final List<Integer> COMMON_PORTS = List.of(
            3000, 4000, 5000, 6274, 6277, 8000, 8080, 8888, 9000, 30000);

    private static final Set<String> MCP_KEYWORDS = Set.of(
            "mcp-server", "mcp-", "npx", "uvx", "mcp-java",
            "claude", "cursor", "continue");

    private final Path cacheFile;

    /** Cache of discovered services (name → service). */
    private final Map<String, DiscoveredService> discovered = new ConcurrentHashMap<>();

    public ServiceDiscovery() {
        this(Path.of(System.getProperty("user.home"), ".mcpm", "discovered.json"));
    }

    ServiceDiscovery(Path cacheFile) {
        this.cacheFile = cacheFile;
        loadCache();
    }

    /**
     * Run a full discovery scan.
     * @return fresh list of discovered services
     */
    public List<DiscoveredService> scan() {
        List<DiscoveredService> results = new ArrayList<>();
        results.addAll(scanProcesses());
        results.addAll(scanPorts());
        results.addAll(scanConfig());

        // Merge with cache
        for (var svc : results) {
            discovered.put(svc.displayName(), svc);
        }
        saveCache();

        return List.copyOf(results);
    }

    /**
     * Scan running processes via {@link ProcessHandle}.
     */
    List<DiscoveredService> scanProcesses() {
        List<DiscoveredService> results = new ArrayList<>();
        String now = LocalDateTime.now().format(TS);

        try {
            ProcessHandle.allProcesses().forEach(ph -> {
                String cmd = ph.info().command().orElse("");
                String args = ph.info().arguments()
                        .map(a -> String.join(" ", a)).orElse("");

                if (isMcpProcess(cmd, args)) {
                    String name = extractName(cmd, args);
                    long pid = ph.pid();
                    results.add(new DiscoveredService(
                            "process", name, "stdio",
                            "PID " + pid, "running",
                            cmd + " " + args, null, now));
                }
            });
        } catch (Exception e) {
            log.warn("Process scan failed: {}", e.getMessage());
        }

        return results;
    }

    /**
     * Scan localhost ports for MCP SSE endpoints.
     */
    List<DiscoveredService> scanPorts() {
        List<DiscoveredService> results = new ArrayList<>();
        String now = LocalDateTime.now().format(TS);

        for (int port : COMMON_PORTS) {
            try (Socket s = new Socket("127.0.0.1", port)) {
                // Port is open — it might be an MCP server
                String name = "localhost:" + port;
                results.add(new DiscoveredService(
                        "port", name, "sse",
                        "TCP " + port, "running",
                        null, "http://localhost:" + port, now));
            } catch (IOException ignored) {
                // Port not reachable
            }
        }

        return results;
    }

    /**
     * Read discovered services from mcp.json config.
     */
    List<DiscoveredService> scanConfig() {
        List<DiscoveredService> results = new ArrayList<>();
        String now = LocalDateTime.now().format(TS);

        try {
            Path cfg = Path.of("./mcp.json").toAbsolutePath().normalize();
            if (!Files.exists(cfg)) return results;

            var root = MAPPER.readTree(cfg.toFile());
            var servers = root.get("mcpServers");
            if (servers == null || !servers.isObject()) return results;

            var fields = servers.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                String name = entry.getKey();
                var node = entry.getValue();

                String cmd = node.has("command") ? node.get("command").asText() : "";
                String url = node.has("url") ? node.get("url").asText() : "";
                String type = node.has("type") ? node.get("type").asText() : "stdio";

                results.add(new DiscoveredService(
                        "config", name, type,
                        type.equals("stdio") ? cmd : url,
                        "configured", cmd, url, now));
            }
        } catch (Exception e) {
            log.warn("Config scan failed: {}", e.getMessage());
        }

        return results;
    }

    /**
     * Check if a single service is still alive.
     */
    public boolean checkAlive(DiscoveredService svc) {
        try {
            if ("port".equals(svc.source()) || "sse".equals(svc.type())) {
                String url = svc.url();
                if (url != null) {
                    URI uri = URI.create(url);
                    int port = uri.getPort() == -1 ? 80 : uri.getPort();
                    try (Socket s = new Socket(uri.getHost(), port)) {
                        return s.isConnected();
                    }
                }
            }
            return "running".equals(svc.status());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Mark a service as stopped.
     */
    public void markStopped(String name) {
        var svc = discovered.get(name);
        if (svc != null) {
            discovered.put(name, new DiscoveredService(
                    svc.source(), svc.name(), svc.type(), svc.transport(),
                    "stopped", svc.command(), svc.url(),
                    LocalDateTime.now().format(TS)));
            saveCache();
        }
    }

    // ---- Helpers ----

    private boolean isMcpProcess(String cmd, String args) {
        String combined = (cmd + " " + args).toLowerCase();
        return MCP_KEYWORDS.stream().anyMatch(combined::contains);
    }

    private String extractName(String cmd, String args) {
        // Try to extract a meaningful name from the command line
        String combined = cmd + " " + args;
        for (String kw : MCP_KEYWORDS) {
            int idx = combined.indexOf(kw);
            if (idx >= 0) {
                // Extract the package name after the keyword
                String after = combined.substring(idx + kw.length()).trim();
                if (!after.isEmpty()) {
                    String[] parts = after.split("\\s+");
                    return kw + (parts.length > 0 ? "-" + parts[0] : "");
                }
                return kw;
            }
        }
        return "unknown-mcp";
    }

    // ---- Persistence ----

    private void loadCache() {
        if (!Files.exists(cacheFile)) return;
        try {
            var root = MAPPER.readTree(cacheFile.toFile());
            var arr = root.get("services");
            if (arr != null && arr.isArray()) {
                for (var node : arr) {
                    var svc = new DiscoveredService(
                            safeText(node, "source"),
                            safeText(node, "name"),
                            safeText(node, "type"),
                            safeText(node, "transport"),
                            safeText(node, "status"),
                            safeText(node, "command"),
                            safeText(node, "url"),
                            safeText(node, "lastSeen"));
                    discovered.put(svc.displayName(), svc);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load discovered services cache: {}", e.getMessage());
        }
    }

    private synchronized void saveCache() {
        try {
            Files.createDirectories(cacheFile.getParent());
            ObjectNode root = MAPPER.createObjectNode();
            root.put("version", 1);
            root.put("updated", LocalDateTime.now().format(TS));

            ArrayNode arr = root.putArray("services");
            for (var svc : discovered.values()) {
                var n = arr.addObject();
                n.put("source", svc.source());
                n.put("name", svc.name());
                n.put("type", svc.type());
                n.put("transport", svc.transport());
                n.put("status", svc.status());
                if (svc.command() != null) n.put("command", svc.command());
                if (svc.url() != null) n.put("url", svc.url());
                n.put("lastSeen", svc.lastSeen());
            }

            var printer = new DefaultPrettyPrinter()
                    .withObjectIndenter(new DefaultIndenter("  ", "\n"))
                    .withArrayIndenter(new DefaultIndenter("  ", "\n"));
            MAPPER.writer(printer).writeValue(cacheFile.toFile(), root);
        } catch (IOException e) {
            log.error("Failed to save discovered services: {}", e.getMessage());
        }
    }

    private String safeText(com.fasterxml.jackson.databind.JsonNode node, String field) {
        return node.has(field) ? node.get(field).asText() : "";
    }
}
