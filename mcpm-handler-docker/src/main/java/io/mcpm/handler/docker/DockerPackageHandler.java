package io.mcpm.handler.docker;

import io.mcpm.spi.PackageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles MCP servers distributed as Docker images.
 * <p>
 * Runs the server via {@code docker run} with appropriate arguments.
 * The registry entry specifies the Docker image via
 * {@code handlerArgs: { "image": "mcp/server:latest" }}.
 * <p>
 * If no image is specified, the package name is used as the image name.
 * <p>
 * Example mcp.json entry:
 * <pre>
 * "server-name": {
 *   "command": "docker",
 *   "args": ["run", "-i", "--rm", "-e", "KEY=val", "mcp/server:latest"]
 * }
 * </pre>
 */
public class DockerPackageHandler implements PackageHandler {

    private static final Logger log = LoggerFactory.getLogger(DockerPackageHandler.class);

    @Override
    public String supportedType() {
        return "docker";
    }

    @Override
    public InstallResult install(InstallRequest request) {
        String image = resolveImage(request);
        String pkg = request.packageName();

        // docker run -i --rm [env...] <image> [args...]
        List<String> args = new ArrayList<>();
        args.add("run");
        args.add("-i");
        args.add("--rm");

        // Apply default env as -e flags
        if (request.env() != null) {
            for (var entry : request.env().entrySet()) {
                args.add("-e");
                args.add(entry.getKey() + "=" + entry.getValue());
            }
        }

        // Apply handler-specific env (stored in handlerArgs)
        if (request.args() != null) {
            @SuppressWarnings("unchecked")
            var dockerEnv = (List<String>) request.args().get("env");
            if (dockerEnv != null) {
                for (String e : dockerEnv) {
                    args.add("-e");
                    args.add(e);
                }
            }

            // Mount volumes
            @SuppressWarnings("unchecked")
            var volumes = (List<String>) request.args().get("volumes");
            if (volumes != null) {
                for (String v : volumes) {
                    args.add("-v");
                    args.add(v);
                }
            }

            // Container port mappings
            @SuppressWarnings("unchecked")
            var ports = (List<String>) request.args().get("ports");
            if (ports != null) {
                for (String p : ports) {
                    args.add("-p");
                    args.add(p);
                }
            }
        }

        args.add(image);

        // Append extra handler args (excluding known docker keys)
        if (request.args() != null) {
            for (var entry : request.args().entrySet()) {
                String key = entry.getKey();
                if ("image".equals(key) || "env".equals(key)
                        || "volumes".equals(key) || "ports".equals(key)) {
                    continue;
                }
                args.add("--" + key);
                args.add(String.valueOf(entry.getValue()));
            }
        }

        log.info("Installing docker package: {} → image: {}", pkg, image);
        return InstallResult.success(
                "Installed " + pkg + " (docker image: " + image + ")",
                "docker",
                List.copyOf(args),
                request.env()
        );
    }

    @Override
    public UninstallResult uninstall(UninstallRequest request) {
        log.info("Uninstalling docker package: {}", request.packageName());
        return UninstallResult.success(
                "Removed " + request.packageName()
                        + " from config (docker image not removed; use 'docker rmi' to clean up)");
    }

    private String resolveImage(InstallRequest request) {
        if (request.args() != null && request.args().containsKey("image")) {
            return String.valueOf(request.args().get("image"));
        }
        return request.packageName() + ":latest";
    }
}
