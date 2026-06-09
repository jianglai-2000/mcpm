package io.mcpm.registry.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mcpm.spi.McpmPackage;
import io.mcpm.spi.RegistryProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Reads package metadata from a static JSON registry file.
 * <p>
 * The file can be:
 * <ul>
 *   <li>Bundled in the classpath ({@code /io/mcpm/registry/builtin/registry.json})</li>
 *   <li>Remote URL (fetched and cached)</li>
 *   <li>Local file path</li>
 * </ul>
 *
 * The default source is the bundled classpath file, which contains a curated
 * set of well-known MCP servers. Users can override via environment variable
 * {@code MCPM_REGISTRY_URL}.
 */
public class BuiltinRegistryProvider implements RegistryProvider {

    private static final Logger log = LoggerFactory.getLogger(BuiltinRegistryProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String CLASSPATH_RESOURCE = "/io/mcpm/registry/builtin/registry.json";
    private static final String ENV_OVERRIDE = "MCPM_REGISTRY_URL";

    private final String registryUrl;
    private final HttpClient httpClient;

    private Map<String, McpmPackage> packages;
    private long lastRefreshMs;
    private static final long CACHE_TTL_MS = 300_000; // 5 minutes

    public BuiltinRegistryProvider() {
        this(System.getenv(ENV_OVERRIDE));
    }

    BuiltinRegistryProvider(String registryUrl) {
        this.registryUrl = registryUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.packages = Collections.emptyMap();
    }

    @Override
    public String name() {
        return "builtin";
    }

    @Override
    public List<McpmPackage> search(String query) {
        ensureLoaded();
        if (query == null || query.isBlank()) {
            return List.copyOf(packages.values());
        }

        String lowerQuery = query.toLowerCase(Locale.ROOT);
        return packages.values().stream()
                .filter(pkg -> matches(pkg, lowerQuery))
                .collect(Collectors.toList());
    }

    @Override
    public McpmPackage getPackage(String name) {
        ensureLoaded();
        return packages.get(name);
    }

    @Override
    public void refresh() {
        packages = Collections.emptyMap();
        lastRefreshMs = 0;
        ensureLoaded();
    }

    private void ensureLoaded() {
        if (!packages.isEmpty() && (System.currentTimeMillis() - lastRefreshMs) < CACHE_TTL_MS) {
            return;
        }

        try {
            JsonNode root;

            if (registryUrl != null && !registryUrl.isBlank()) {
                if (registryUrl.startsWith("http://") || registryUrl.startsWith("https://")) {
                    root = fetchRemote(registryUrl);
                } else {
                    root = MAPPER.readTree(Path.of(registryUrl).toFile());
                }
            } else {
                InputStream is = getClass().getResourceAsStream(CLASSPATH_RESOURCE);
                if (is == null) {
                    log.warn("Builtin registry file not found on classpath: {}", CLASSPATH_RESOURCE);
                    packages = Collections.emptyMap();
                    lastRefreshMs = System.currentTimeMillis();
                    return;
                }
                root = MAPPER.readTree(is);
            }

            Map<String, McpmPackage> parsed = new LinkedHashMap<>();
            if (root != null && root.isArray()) {
                for (JsonNode node : root) {
                    try {
                        McpmPackage pkg = parsePackage(node);
                        parsed.put(pkg.name(), pkg);
                    } catch (Exception e) {
                        log.warn("Skipping invalid registry entry: {}", e.getMessage());
                    }
                }
            }

            packages = parsed;
            lastRefreshMs = System.currentTimeMillis();
            log.info("Loaded {} packages from builtin registry", packages.size());

        } catch (Exception e) {
            log.error("Failed to load builtin registry", e);
            if (packages.isEmpty()) {
                packages = Collections.emptyMap();
            }
        }
    }

    private JsonNode fetchRemote(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build();
        HttpResponse<String> resp = httpClient.send(req,
                HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("Registry fetch failed: HTTP " + resp.statusCode());
        }
        return MAPPER.readTree(resp.body());
    }

    private boolean matches(McpmPackage pkg, String lowerQuery) {
        if (pkg.name().toLowerCase(Locale.ROOT).contains(lowerQuery)) return true;
        if (pkg.description() != null
                && pkg.description().toLowerCase(Locale.ROOT).contains(lowerQuery)) return true;
        if (pkg.type() != null
                && pkg.type().toLowerCase(Locale.ROOT).contains(lowerQuery)) return true;
        return false;
    }

    private McpmPackage parsePackage(JsonNode node) {
        String name = node.get("name").asText();
        String description = node.has("description") ? node.get("description").asText() : null;
        String type = node.has("type") ? node.get("type").asText() : "npx";
        String latestVersion = node.has("latestVersion") ? node.get("latestVersion").asText() : "latest";
        String license = node.has("license") ? node.get("license").asText() : null;
        String homepage = node.has("homepage") ? node.get("homepage").asText() : null;
        String repository = node.has("repository") ? node.get("repository").asText() : null;

        List<String> authors = new ArrayList<>();
        if (node.has("authors") && node.get("authors").isArray()) {
            node.get("authors").forEach(a -> authors.add(a.asText()));
        }

        Map<String, McpmPackage.VersionEntry> versions = new LinkedHashMap<>();
        if (node.has("versions") && node.get("versions").isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.get("versions").fields();
            while (fields.hasNext()) {
                var field = fields.next();
                versions.put(field.getKey(), parseVersionEntry(field.getKey(), field.getValue()));
            }
        }

        return new McpmPackage(name, description, type, latestVersion,
                versions, authors, license, homepage, repository);
    }

    private McpmPackage.VersionEntry parseVersionEntry(String version, JsonNode node) {
        String downloadUrl = node.has("downloadUrl") ? node.get("downloadUrl").asText() : null;
        String checksum = node.has("checksum") ? node.get("checksum").asText() : null;

        Map<String, Object> handlerArgs = new LinkedHashMap<>();
        if (node.has("handlerArgs") && node.get("handlerArgs").isObject()) {
            node.get("handlerArgs").fields().forEachRemaining(
                    f -> handlerArgs.put(f.getKey(), fromJsonNode(f.getValue())));
        }

        Map<String, String> defaultEnv = new LinkedHashMap<>();
        if (node.has("defaultEnv") && node.get("defaultEnv").isObject()) {
            node.get("defaultEnv").fields().forEachRemaining(
                    f -> defaultEnv.put(f.getKey(), f.getValue().asText()));
        }

        return new McpmPackage.VersionEntry(version, downloadUrl, checksum, handlerArgs, defaultEnv);
    }

    private Object fromJsonNode(JsonNode node) {
        if (node.isTextual()) return node.asText();
        if (node.isInt()) return node.asInt();
        if (node.isLong()) return node.asLong();
        if (node.isDouble()) return node.asDouble();
        if (node.isBoolean()) return node.asBoolean();
        if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            for (JsonNode child : node) {
                list.add(fromJsonNode(child));
            }
            return list;
        }
        if (node.isObject()) {
            Map<String, Object> map = new LinkedHashMap<>();
            node.fieldNames().forEachRemaining(f -> map.put(f, fromJsonNode(node.get(f))));
            return map;
        }
        return node.asText();
    }
}
