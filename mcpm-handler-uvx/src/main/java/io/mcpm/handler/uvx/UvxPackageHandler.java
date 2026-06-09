package io.mcpm.handler.uvx;

import io.mcpm.spi.PackageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles MCP servers that are Python packages run via {@code uvx}.
 * <p>
 * Similar to npx — {@code uvx} automatically downloads and caches
 * Python packages without requiring an explicit install step.
 * <p>
 * Example mcp.json entry:
 * <pre>
 * "server-name": {
 *   "command": "uvx",
 *   "args": ["mcp-server-name", "--option", "value"]
 * }
 * </pre>
 */
public class UvxPackageHandler implements PackageHandler {

    private static final Logger log = LoggerFactory.getLogger(UvxPackageHandler.class);

    @Override
    public String supportedType() {
        return "uvx";
    }

    @Override
    public InstallResult install(InstallRequest request) {
        String pkg = request.packageName();

        List<String> args = new ArrayList<>();
        args.add(pkg);

        if (request.args() != null) {
            for (var entry : request.args().entrySet()) {
                args.add("--" + entry.getKey());
                args.add(String.valueOf(entry.getValue()));
            }
        }

        log.info("Installing uvx package: {} v{}", pkg, request.version());
        return InstallResult.success(
                "Installed " + pkg + " (uvx)",
                "uvx",
                List.copyOf(args),
                request.env()
        );
    }

    @Override
    public UninstallResult uninstall(UninstallRequest request) {
        log.info("Uninstalling uvx package: {}", request.packageName());
        return UninstallResult.success(
                "Removed " + request.packageName() + " from config (uvx cache not cleared)");
    }
}
