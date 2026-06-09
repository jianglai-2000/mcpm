package io.mcpm.core.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;

/**
 * Local cache for registry data and downloaded packages.
 * <p>
 * Enables offline search and install of previously cached packages.
 * <p>
 * Cache location: {@code ~/.mcpm/cache/}
 */
public class CacheManager {

    private static final Logger log = LoggerFactory.getLogger(CacheManager.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Duration CACHE_TTL = Duration.ofHours(24);
    private static final String REGISTRY_CACHE = "registry.json";

    private final Path cacheDir;
    private final HttpClient httpClient;

    public CacheManager() {
        this(Path.of(System.getProperty("user.home"), ".mcpm", "cache"));
    }

    CacheManager(Path cacheDir) {
        this.cacheDir = cacheDir;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public boolean isOffline() {
        try {
            URI uri = URI.create("https://registry.mcpm.io/health");
            HttpRequest req = HttpRequest.newBuilder().uri(uri).GET()
                    .timeout(Duration.ofSeconds(3)).build();
            httpClient.send(req, HttpResponse.BodyHandlers.discarding());
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    // ---- Registry cache ----

    /**
     * Get the registry index, using cache if available and fresh,
     * or fetching from the network if needed.
     */
    public ArrayNode getRegistry(URI registryUrl) throws IOException {
        Path cacheFile = cacheDir.resolve(REGISTRY_CACHE);

        // Try to use cached version if fresh
        if (Files.exists(cacheFile) && isCacheFresh(cacheFile)) {
            try {
                log.debug("Using cached registry");
                return (ArrayNode) MAPPER.readTree(cacheFile.toFile());
            } catch (Exception e) {
                log.warn("Cached registry corrupted, re-fetching");
            }
        }

        // Fetch from network
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(registryUrl.resolve("api/v1/packages"))
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();
            HttpResponse<InputStream> resp = httpClient.send(req,
                    HttpResponse.BodyHandlers.ofInputStream());

            if (resp.statusCode() == 200) {
                Files.createDirectories(cacheDir);
                Files.copy(resp.body(), cacheFile, StandardCopyOption.REPLACE_EXISTING);
                log.info("Registry cached to {}", cacheFile);
                return (ArrayNode) MAPPER.readTree(cacheFile.toFile());
            }
        } catch (Exception e) {
            log.warn("Failed to fetch registry: {}", e.getMessage());
            // Fall back to stale cache if available
            if (Files.exists(cacheFile)) {
                log.info("Falling back to cached registry (may be stale)");
                return (ArrayNode) MAPPER.readTree(cacheFile.toFile());
            }
        }

        return MAPPER.createArrayNode();
    }

    /**
     * Cache a downloaded package binary.
     */
    public Path cachePackage(String packageName, String version, URI downloadUrl) throws IOException {
        Files.createDirectories(cacheDir);

        String cacheName = packageName.replace('/', '-') + "-" + version;
        Path cacheFile = cacheDir.resolve(cacheName);

        if (Files.exists(cacheFile)) {
            log.debug("Package already cached: {}", cacheFile);
            return cacheFile;
        }

        HttpRequest req = HttpRequest.newBuilder()
                .uri(downloadUrl)
                .GET()
                .timeout(Duration.ofSeconds(120))
                .build();
        try {
            HttpResponse<Path> resp = httpClient.send(req,
                    HttpResponse.BodyHandlers.ofFile(cacheFile));
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                Files.deleteIfExists(cacheFile);
                throw new IOException("HTTP " + resp.statusCode());
            }
            return cacheFile;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Download interrupted", e);
        }
    }

    /**
     * Check if a package is already cached.
     */
    public boolean isPackageCached(String packageName, String version) {
        String cacheName = packageName.replace('/', '-') + "-" + version;
        return Files.exists(cacheDir.resolve(cacheName));
    }

    /**
     * Clear all cached data.
     */
    public void clearCache() throws IOException {
        if (Files.exists(cacheDir)) {
            try (var files = Files.list(cacheDir)) {
                files.forEach(f -> {
                    try { Files.deleteIfExists(f); } catch (IOException ignored) {}
                });
            }
        }
    }

    private boolean isCacheFresh(Path cacheFile) {
        try {
            Instant modified = Files.getLastModifiedTime(cacheFile).toInstant();
            return modified.plus(CACHE_TTL).isAfter(Instant.now());
        } catch (IOException e) {
            return false;
        }
    }
}
