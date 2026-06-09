package io.mcpm.handler.jar;

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
import java.util.ArrayList;
import java.util.List;

/**
 * Handles MCP servers distributed as executable JAR files.
 * <p>
 * Downloads the JAR to {@code ~/.mcpm/jars/} and generates a
 * {@code java -jar} command entry for mcp.json.
 */
public class JarPackageHandler implements PackageHandler {

    private static final Logger log = LoggerFactory.getLogger(JarPackageHandler.class);

    private final Path storageDir;
    private final HttpClient httpClient;

    public JarPackageHandler() {
        this(Path.of(System.getProperty("user.home"), ".mcpm", "jars"));
    }

    JarPackageHandler(Path storageDir) {
        this.storageDir = storageDir;
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public String supportedType() {
        return "jar";
    }

    @Override
    public InstallResult install(InstallRequest request) {
        String downloadUrl = (String) request.args().get("downloadUrl");
        if (downloadUrl == null) {
            return InstallResult.failure(
                    "JAR handler requires 'downloadUrl' in package args");
        }

        try {
            Files.createDirectories(storageDir);

            String jarName = request.packageName().replace('/', '-')
                    + "-" + request.version() + ".jar";
            Path jarPath = storageDir.resolve(jarName);

            if (Files.notExists(jarPath)) {
                log.info("Downloading {} → {}", downloadUrl, jarPath);
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(downloadUrl))
                        .GET()
                        .build();
                HttpResponse<Path> response = httpClient.send(req,
                        HttpResponse.BodyHandlers.ofFile(jarPath));

                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    Files.deleteIfExists(jarPath);
                    return InstallResult.failure(
                            "Download failed with HTTP " + response.statusCode());
                }
            } else {
                log.info("JAR already cached: {}", jarPath);
            }

            // Build java -jar command
            List<String> args = new ArrayList<>();
            args.add("-jar");
            args.add(jarPath.toAbsolutePath().toString());

            // Append handler-specific args
            if (request.args() != null) {
                for (var entry : request.args().entrySet()) {
                    if ("downloadUrl".equals(entry.getKey())
                            || "checksum".equals(entry.getKey())) {
                        continue; // skip internal fields
                    }
                    args.add("--" + entry.getKey());
                    args.add(String.valueOf(entry.getValue()));
                }
            }

            return InstallResult.success(
                    "Downloaded and installed " + jarPath.getFileName(),
                    "java",
                    List.copyOf(args),
                    request.env());

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Failed to install JAR package {}", request.packageName(), e);
            return InstallResult.failure("Installation error: " + e.getMessage());
        }
    }

    @Override
    public UninstallResult uninstall(UninstallRequest request) {
        String jarName = request.packageName().replace('/', '-')
                + "-" + request.version() + ".jar";
        Path jarPath = storageDir.resolve(jarName);

        try {
            boolean deleted = Files.deleteIfExists(jarPath);
            if (deleted) {
                log.info("Deleted JAR: {}", jarPath);
                return UninstallResult.success(
                        "Deleted " + jarPath.getFileName());
            } else {
                return UninstallResult.success(
                        "JAR not found in cache: " + jarPath.getFileName());
            }
        } catch (IOException e) {
            log.error("Failed to delete JAR {}", jarPath, e);
            return UninstallResult.failure("Failed to delete: " + e.getMessage());
        }
    }
}
