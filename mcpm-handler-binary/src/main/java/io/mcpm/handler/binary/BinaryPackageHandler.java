package io.mcpm.handler.binary;

import io.mcpm.spi.PackageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Handles MCP servers distributed as native binaries.
 * <p>
 * Automatically selects the right binary for the current platform
 * (linux-amd64, darwin-arm64, win-x64, etc.).
 * <p>
 * Supports two registry formats:
 * <ol>
 *   <li>{@code handlerArgs.downloadUrl} — single URL (legacy)</li>
 *   <li>{@code handlerArgs.downloadUrls} — map of platform → URL (modern)</li>
 * </ol>
 * <p>
 * Validates SHA256 checksum if {@code handlerArgs.checksum} is provided.
 */
public class BinaryPackageHandler implements PackageHandler {

    private static final Logger log = LoggerFactory.getLogger(BinaryPackageHandler.class);

    private static final String OS_NAME = System.getProperty("os.name").toLowerCase();
    private static final String OS_ARCH = System.getProperty("os.arch").toLowerCase();

    private static final boolean IS_WINDOWS = OS_NAME.contains("win");

    private final Path storageDir;
    private final HttpClient httpClient;

    public BinaryPackageHandler() {
        this(Path.of(System.getProperty("user.home"), ".mcpm", "bin"));
    }

    BinaryPackageHandler(Path storageDir) {
        this.storageDir = storageDir;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public String supportedType() {
        return "binary";
    }

    @Override
    public InstallResult install(InstallRequest request) {
        // Resolve download URL
        String downloadUrl = resolveDownloadUrl(request);
        if (downloadUrl == null) {
            return InstallResult.failure(
                    "No download URL for platform " + currentPlatform()
                            + ". Supported platforms may include: linux-amd64, darwin-arm64, win-x64.");
        }

        String expectedChecksum = request.args() != null
                ? (String) request.args().get("checksum")
                : null;

        String binaryName = binaryName(request.packageName(), request.version());
        Path binaryPath = storageDir.resolve(binaryName);

        try {
            Files.createDirectories(storageDir);

            if (Files.notExists(binaryPath)) {
                log.info("Downloading {} → {}", downloadUrl, binaryPath);
                download(downloadUrl, binaryPath, expectedChecksum);

                // Make executable (no-op on Windows)
                if (!IS_WINDOWS) {
                    binaryPath.toFile().setExecutable(true, true);
                }
            } else {
                log.info("Binary already cached: {}", binaryPath);
            }

            // Build args
            List<String> args = new ArrayList<>();
            if (request.args() != null) {
                for (var entry : request.args().entrySet()) {
                    String key = entry.getKey();
                    if ("downloadUrl".equals(key) || "checksum".equals(key)
                            || "platform".equals(key) || "architecture".equals(key)
                            || "downloadUrls".equals(key)) {
                        continue;
                    }
                    args.add("--" + key);
                    args.add(String.valueOf(entry.getValue()));
                }
            }

            return InstallResult.success(
                    "Downloaded binary to " + binaryPath,
                    binaryPath.toAbsolutePath().toString(),
                    List.copyOf(args),
                    request.env());

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Failed to install binary {}", request.packageName(), e);
            return InstallResult.failure("Download error: " + e.getMessage());
        }
    }

    @Override
    public UninstallResult uninstall(UninstallRequest request) {
        String binaryName = binaryName(request.packageName(), request.version());
        Path binaryPath = storageDir.resolve(binaryName);

        try {
            boolean deleted = Files.deleteIfExists(binaryPath);
            return deleted
                    ? UninstallResult.success("Deleted " + binaryPath.getFileName())
                    : UninstallResult.success("Binary not found in cache: " + binaryPath.getFileName());
        } catch (IOException e) {
            log.error("Failed to delete binary {}", binaryPath, e);
            return UninstallResult.failure("Failed to delete: " + e.getMessage());
        }
    }

    // ---- Platform detection ----

    /** e.g. "linux-amd64", "darwin-arm64", "win-x64" */
    static String currentPlatform() {
        String os;
        if (IS_WINDOWS) os = "win";
        else if (OS_NAME.contains("mac") || OS_NAME.contains("darwin")) os = "darwin";
        else if (OS_NAME.contains("linux")) os = "linux";
        else os = OS_NAME.replaceAll("[^a-z0-9]", "");

        String arch;
        if (OS_ARCH.contains("amd64") || OS_ARCH.contains("x86_64")) arch = "x64";
        else if (OS_ARCH.contains("aarch64") || OS_ARCH.contains("arm64")) arch = "arm64";
        else arch = OS_ARCH.replaceAll("[^a-z0-9]", "");

        return os + "-" + arch;
    }

    static String platformExtension() {
        return IS_WINDOWS ? ".exe" : "";
    }

    // ---- URL resolution ----

    @SuppressWarnings("unchecked")
    private String resolveDownloadUrl(InstallRequest request) {
        Map<String, Object> args = request.args();
        if (args == null) return null;

        // Check for multi-platform URLs
        if (args.containsKey("downloadUrls")) {
            Object urlsObj = args.get("downloadUrls");
            if (urlsObj instanceof Map) {
                Map<String, Object> urls = (Map<String, Object>) urlsObj;
                String platform = currentPlatform();
                Object url = urls.get(platform);
                if (url != null) return String.valueOf(url);
                // Try with extension for Windows
                if (IS_WINDOWS) {
                    url = urls.get(platform + ".exe");
                    if (url != null) return String.valueOf(url);
                }
            }
        }

        // Legacy single URL
        return (String) args.get("downloadUrl");
    }

    // ---- Download ----

    private void download(String url, Path target, String expectedChecksum)
            throws IOException, InterruptedException {

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(120))
                .build();

        // Download to temp file first
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            HttpResponse<Path> response = httpClient.send(req,
                    HttpResponse.BodyHandlers.ofFile(tmp));

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                Files.deleteIfExists(tmp);
                throw new IOException("HTTP " + response.statusCode());
            }

            // Verify checksum
            if (expectedChecksum != null && !expectedChecksum.isEmpty()) {
                String actual = sha256(tmp);
                if (!actual.equalsIgnoreCase(expectedChecksum)) {
                    Files.deleteIfExists(tmp);
                    throw new IOException(
                            "Checksum mismatch: expected " + expectedChecksum
                                    + ", got " + actual);
                }
                log.info("Checksum verified: {}", actual);
            }

            // Atomically move to final location
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);

        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    // ---- Checksum ----

    private String sha256(Path file) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] data = Files.readAllBytes(file);
            byte[] hash = md.digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IOException("Failed to compute SHA-256", e);
        }
    }

    // ---- Naming ----

    private String binaryName(String packageName, String version) {
        String name = packageName.replace('/', '-');
        return name + "-" + version + platformExtension();
    }
}
