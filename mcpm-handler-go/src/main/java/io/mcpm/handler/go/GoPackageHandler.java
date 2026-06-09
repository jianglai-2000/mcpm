package io.mcpm.handler.go;

import io.mcpm.spi.PackageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles MCP servers distributed as Go modules, run via {@code go run}.
 */
public class GoPackageHandler implements PackageHandler {
    private static final Logger log = LoggerFactory.getLogger(GoPackageHandler.class);

    @Override public String supportedType() { return "go"; }

    @Override
    public InstallResult install(InstallRequest request) {
        List<String> args = new ArrayList<>(List.of("run", request.packageName() + "@" + request.version()));
        if (request.args() != null) {
            request.args().forEach((k, v) -> { if (!"module".equals(k)) { args.add("--" + k); args.add(String.valueOf(v)); } });
        }
        log.info("Installing Go package: {}", request.packageName());
        return InstallResult.success("Installed " + request.packageName() + " (go run)", "go", List.copyOf(args), request.env());
    }

    @Override
    public UninstallResult uninstall(UninstallRequest request) {
        return UninstallResult.success("Removed " + request.packageName() + " from config (Go module not uninstalled)");
    }
}
