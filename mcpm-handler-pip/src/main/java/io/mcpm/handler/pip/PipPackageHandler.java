package io.mcpm.handler.pip;

import io.mcpm.spi.PackageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles MCP servers that are Python packages installed via pip.
 * <p>
 * The user must {@code pip install} the package first. The handler
 * generates the {@code python -m <module>} entry for mcp.json.
 * <p>
 * Example mcp.json entry:
 * <pre>
 * "server-name": {
 *   "command": "python",
 *   "args": ["-m", "mcp_server_module", "--option", "value"]
 * }
 * </pre>
 * <p>
 * Registry entries for pip packages should provide the Python module name
 * via {@code handlerArgs: { "module": "mcp_server_module" }}.
 * If not specified, the package name is converted (hyphens → underscores).
 */
public class PipPackageHandler implements PackageHandler {

    private static final Logger log = LoggerFactory.getLogger(PipPackageHandler.class);

    @Override
    public String supportedType() {
        return "pip";
    }

    @Override
    public InstallResult install(InstallRequest request) {
        String module = resolveModule(request);
        String pkg = request.packageName();

        List<String> args = new ArrayList<>();
        args.add("-m");
        args.add(module);

        if (request.args() != null) {
            for (var entry : request.args().entrySet()) {
                if ("module".equals(entry.getKey())) continue; // internal
                args.add("--" + entry.getKey());
                args.add(String.valueOf(entry.getValue()));
            }
        }

        log.info("Installing pip package: {} (module: {})", pkg, module);
        return InstallResult.success(
                "Installed " + pkg + " (pip — remember to 'pip install " + pkg + "')",
                "python",
                List.copyOf(args),
                request.env()
        );
    }

    @Override
    public UninstallResult uninstall(UninstallRequest request) {
        log.info("Uninstalling pip package: {}", request.packageName());
        return UninstallResult.success(
                "Removed " + request.packageName() + " from config (pip package not uninstalled)");
    }

    private String resolveModule(PackageHandler.InstallRequest request) {
        // Check for explicit module name in args
        if (request.args() != null && request.args().containsKey("module")) {
            return String.valueOf(request.args().get("module"));
        }
        // Convert package name to Python module convention
        String pkg = request.packageName();
        String name = pkg;
        if (name.contains("/")) {
            name = name.substring(name.lastIndexOf('/') + 1);
        }
        return name.replace("-", "_").replace(".", "_");
    }
}
