package io.mcpm.handler.npx;

import io.mcpm.spi.PackageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Handles MCP servers that are npm packages run via {@code npx}.
 * <p>
 * Example mcp.json entry produced:
 * <pre>
 * "server-name": {
 *   "command": "npx",
 *   "args": ["-y", "@org/server-name", "--option", "value"]
 * }
 * </pre>
 */
public class NpxPackageHandler implements PackageHandler {

    private static final Logger log = LoggerFactory.getLogger(NpxPackageHandler.class);

    @Override
    public String supportedType() {
        return "npx";
    }

    @Override
    public InstallResult install(InstallRequest request) {
        String pkg = request.packageName();

        // Build the args: npx -y <package> [handler_args...]
        List<String> args = new ArrayList<>();
        args.add("-y");
        args.add(pkg);

        // Append handler-specific args (e.g. "--port", "8080")
        if (request.args() != null) {
            for (var entry : request.args().entrySet()) {
                args.add("--" + entry.getKey());
                args.add(String.valueOf(entry.getValue()));
            }
        }

        log.info("Installing npx package: {} v{}", pkg, request.version());
        return InstallResult.success(
                "Installed " + pkg + " (npx)",
                "npx",
                List.copyOf(args),
                request.env()
        );
    }

    @Override
    public UninstallResult uninstall(UninstallRequest request) {
        log.info("Uninstalling npx package: {} v{}", request.packageName(), request.version());
        // NPX packages don't require explicit uninstall — npx handles caching.
        // A future version might do `npm uninstall -g` or clean npx cache.
        return UninstallResult.success(
                "Removed " + request.packageName() + " from config (npx cache not cleared)");
    }
}
