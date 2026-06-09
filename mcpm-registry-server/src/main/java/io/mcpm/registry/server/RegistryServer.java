package io.mcpm.registry.server;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Executors;

/**
 * Lightweight HTTP registry server for mcpm packages.
 * <p>
 * Stores packages in a {@code registry.json} file on disk.
 * Supports package submission (POST) and search/retrieval (GET).
 *
 * <h2>API</h2>
 * <ul>
 *   <li>{@code GET  /api/v1/packages} — list all packages</li>
 *   <li>{@code GET  /api/v1/search?q=...} — search packages</li>
 *   <li>{@code GET  /api/v1/packages/{name}} — get package details</li>
 *   <li>{@code POST /api/v1/packages} — submit a new package</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>
 * java io.mcpm.registry.server.RegistryServer
 *   --port 8080
 *   --data /path/to/registry.json
 * </pre>
 */
public class RegistryServer {

    private static final Logger log = LoggerFactory.getLogger(RegistryServer.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpServer server;
    private final Path dataFile;

    public RegistryServer(int port, Path dataFile) throws IOException {
        this.dataFile = dataFile;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.server.setExecutor(Executors.newFixedThreadPool(4));

        // Register handlers
        server.createContext("/api/v1/packages", this::handlePackages);
        server.createContext("/api/v1/search", this::handleSearch);
        server.createContext("/health", this::handleHealth);

        log.info("Registry server starting on port {}", port);
        log.info("Data file: {}", dataFile.toAbsolutePath());
    }

    public void start() {
        server.start();
        log.info("Registry server ready at http://localhost:" + server.getAddress().getPort());
    }

    public void stop() {
        server.stop(2);
        log.info("Registry server stopped");
    }

    // ---- Handlers ----

    private void handlePackages(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod().toUpperCase();
            String path = exchange.getRequestURI().getPath();

            switch (method) {
                case "GET" -> {
                    // GET /api/v1/packages → list all
                    // GET /api/v1/packages/{name} → get one
                    String name = extractName(path);
                    if (name != null) {
                        handleGetPackage(exchange, name);
                    } else {
                        handleListPackages(exchange);
                    }
                }
                case "POST" -> handleSubmitPackage(exchange);
                case "OPTIONS" -> sendJson(exchange, 204, "{}");
                default -> sendJson(exchange, 405, Map.of("error", "Method not allowed"));
            }
        } catch (Exception e) {
            log.error("Error handling request", e);
            sendJson(exchange, 500, Map.of("error", "Internal server error"));
        }
    }

    private void handleSearch(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        try {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, Map.of("error", "Method not allowed"));
                return;
            }

            String query = extractQueryParam(exchange.getRequestURI(), "q");
            if (query == null || query.isBlank()) {
                sendJson(exchange, 400, Map.of("error", "Query parameter 'q' is required"));
                return;
            }

            JsonNode registry = loadRegistry();
            ArrayNode matches = MAPPER.createArrayNode();
            if (registry != null && registry.isArray()) {
                String lowerQuery = query.toLowerCase(Locale.ROOT);
                for (JsonNode node : registry) {
                    String name = node.has("name") ? node.get("name").asText("") : "";
                    String desc = node.has("description") ? node.get("description").asText("") : "";
                    if (name.toLowerCase(Locale.ROOT).contains(lowerQuery)
                            || desc.toLowerCase(Locale.ROOT).contains(lowerQuery)) {
                        matches.add(node);
                    }
                }
            }

            sendJson(exchange, 200, matches);

        } catch (Exception e) {
            log.error("Search error", e);
            sendJson(exchange, 500, Map.of("error", "Search failed"));
        }
    }

    private void handleHealth(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        sendJson(exchange, 200, Map.of(
                "status", "ok",
                "packages", countPackages()));
    }

    // ---- Package operations ----

    private void handleListPackages(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        JsonNode registry = loadRegistry();
        if (registry == null) {
            sendJson(exchange, 200, MAPPER.createArrayNode());
        } else {
            sendJson(exchange, 200, registry);
        }
    }

    private void handleGetPackage(com.sun.net.httpserver.HttpExchange exchange, String name) throws IOException {
        JsonNode registry = loadRegistry();
        if (registry != null && registry.isArray()) {
            for (JsonNode node : registry) {
                if (node.has("name") && name.equals(node.get("name").asText())) {
                    sendJson(exchange, 200, node);
                    return;
                }
            }
        }
        sendJson(exchange, 404, Map.of("error", "Package not found: " + name));
    }

    private synchronized void handleSubmitPackage(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        // Read body
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

        JsonNode submitted;
        try {
            submitted = MAPPER.readTree(body);
        } catch (Exception e) {
            sendJson(exchange, 400, Map.of("error", "Invalid JSON: " + e.getMessage()));
            return;
        }

        // Validate basic fields
        if (!submitted.has("name") || !submitted.has("type") || !submitted.has("latestVersion")) {
            sendJson(exchange, 400, Map.of(
                    "error", "Missing required fields: name, type, latestVersion"));
            return;
        }

        String name = submitted.get("name").asText();

        // Check for duplicate
        JsonNode registry = loadRegistry();
        ArrayNode packages = registry != null && registry.isArray()
                ? (ArrayNode) registry
                : MAPPER.createArrayNode();

        for (JsonNode existing : packages) {
            if (existing.has("name") && name.equals(existing.get("name").asText())) {
                sendJson(exchange, 409, Map.of(
                        "error", "Package already exists: " + name,
                        "hint", "Use PUT to update, or choose a different name"));
                return;
            }
        }

        // Add to registry
        packages.add(submitted);
        saveRegistry(packages);

        log.info("Package published: {} v{}", name, submitted.get("latestVersion").asText());

        sendJson(exchange, 201, Map.of(
                "message", "Package published",
                "name", name,
                "version", submitted.get("latestVersion").asText()));
    }

    // ---- Persistence ----

    private JsonNode loadRegistry() throws IOException {
        if (!Files.exists(dataFile)) {
            return null;
        }
        return MAPPER.readTree(dataFile.toFile());
    }

    private void saveRegistry(ArrayNode packages) throws IOException {
        Files.createDirectories(dataFile.getParent());
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter()
                .withObjectIndenter(new DefaultIndenter("  ", "\n"))
                .withArrayIndenter(new DefaultIndenter("  ", "\n"));
        MAPPER.writer(printer).writeValue(dataFile.toFile(), packages);
    }

    private int countPackages() throws IOException {
        JsonNode registry = loadRegistry();
        return registry != null && registry.isArray() ? registry.size() : 0;
    }

    // ---- Utilities ----

    private String extractName(String path) {
        // /api/v1/packages/ → null
        // /api/v1/packages/my-pkg → "my-pkg"
        // /api/v1/packages/ → null
        String prefix = "/api/v1/packages/";
        if (path.startsWith(prefix)) {
            String rest = path.substring(prefix.length());
            if (!rest.isEmpty() && !rest.equals("/")) {
                return URLDecoder.decode(rest, StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private String extractQueryParam(URI uri, String param) {
        String query = uri.getQuery();
        if (query == null) return null;
        for (String part : query.split("&")) {
            int idx = part.indexOf('=');
            if (idx > 0 && param.equals(part.substring(0, idx))) {
                return URLDecoder.decode(part.substring(idx + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private void sendJson(com.sun.net.httpserver.HttpExchange exchange, int status, Object data) throws IOException {
        byte[] json = MAPPER.writeValueAsBytes(data);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.sendResponseHeaders(status, json.length);
        exchange.getResponseBody().write(json);
        exchange.getResponseBody().close();
    }

    private void sendJson(com.sun.net.httpserver.HttpExchange exchange, int status, JsonNode data) throws IOException {
        byte[] json = MAPPER.writeValueAsBytes(data);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, json.length);
        exchange.getResponseBody().write(json);
        exchange.getResponseBody().close();
    }

    // ---- Main ----

    public static void main(String[] args) throws Exception {
        int port = 8080;
        Path dataFile = Path.of("registry.json");

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--data" -> dataFile = Path.of(args[++i]);
                case "--help" -> {
                    System.out.println("Usage: RegistryServer --port <port> --data <path>");
                    return;
                }
            }
        }

        RegistryServer server = new RegistryServer(port, dataFile);
        server.start();

        // Keep alive
        System.out.println("Press Ctrl+C to stop.");
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        Thread.currentThread().join();
    }
}
