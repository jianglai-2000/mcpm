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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles MCP servers distributed as native binaries.
 * <p>
 * Downloads the binary to {@code ~/.mcpm/bin/} and creates a
 * direct executable entry in mcp.json.
 * <p>
 * Platform-aware: appends {@code .exe} on Windows automatically.
 * <p>
 * Example mcp.json entry:
 * <pre>
 * "server-name": {
 *   "command": "/home/user/.mcpm/bin/server-name-v1.0"
 *   "args": ["--port", "8080"]
 * }
 * </pre>
 */
public class BinaryPackageHandler implements PackageHandler {

    private static final Logger log = LoggerFactory.getLogger(BinaryPackageHandler.class);

    private static final boolean IS_WINDOWS =
            System.getProperty("os.name").toLowerCase().contains("win");

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
        String downloadUrl = (String) request.args().get("downloadUrl");
        if (downloadUrl == null) {
            return InstallResult.failure(
                    "Binary handler requires 'downloadUrl' in package args");
        }

        String binaryName = binaryName(request.packageName(), request.version());
        Path binaryPath = storageDir.resolve(binaryName);

        try {
            Files.createDirectories(storageDir);

            if (Files.notExists(binaryPath)) {
                log.info("Downloading binary {} → {}", downloadUrl, binaryPath);
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(downloadUrl))
                        .GET()
                        .timeout(Duration.ofSeconds(60))
                        .build();
                HttpResponse<Path> response = httpClient.send(req,
                        HttpResponse.BodyHandlers.ofFile(binaryPath));

                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    Files.deleteIfExists(binaryPath);
                    return InstallResult.failure(
                            "Download failed with HTTP " + response.statusCode());
                }

                // Make executable (no-op on Windows)
                if (!IS_WINDOWS) {
                    binaryPath.toFile().setExecutable(true, true);
                }
            } else {
                log.info("Binary already cached: {}", binaryPath);
            }

            // Build args: path to binary + handler-specific args
            List<String> args = new ArrayList<>();
            if (request.args() != null) {
                for (var entry : request.args().entrySet()) {
                    if ("downloadUrl".equals(entry.getKey())
                            || "checksum".equals(entry.getKey())
                            || "platform".equals(entry.getKey())
                            || "architecture".equals(entry.getKey())) {
                        continue; // internal fields
                    }
                    args.add("--" + entry.getKey());
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
            log.error("Failed to install binary package {}", request.packageName(), e);
            return InstallResult.failure("Download error: " + e.getMessage());
        }
    }

    @Override
    public UninstallResult uninstall(UninstallRequest request) {
        String binaryName = binaryName(request.packageName(), request.version());
        Path binaryPath = storageDir.resolve(binaryName);

        try {
            boolean deleted = Files.deleteIfExists(binaryPath);
            if (deleted) {
                log.info("Deleted binary: {}", binaryPath);
                return UninstallResult.success("Deleted " + binaryPath.getFileName());
            } else {
                return UninstallResult.success(
                        "Binary not found in cache: " + binaryPath.getFileName());
            }
        } catch (IOException e) {
            log.error("Failed to delete binary {}", binaryPath, e);
            return UninstallResult.failure("Failed to delete: " + e.getMessage());
        }
    }

    private String binaryName(String packageName, String version) {
        String name = packageName.replace('/', '-');
        String ext = IS_WINDOWS ? ".exe" : "";
        return name + "-" + version + ext;
    }
}
