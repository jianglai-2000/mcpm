package io.mcpm.handler.bun;

import io.mcpm.spi.PackageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles MCP servers run via {@code bun run}.
 */
public class BunPackageHandler implements PackageHandler {
    private static final Logger log = LoggerFactory.getLogger(BunPackageHandler.class);

    @Override public String supportedType() { return "bun"; }

    @Override
    public InstallResult install(InstallRequest request) {
        List<String> args = new ArrayList<>(List.of("run", request.packageName()));
        if (request.args() != null) {
            request.args().forEach((k, v) -> { args.add("--" + k); args.add(String.valueOf(v)); });
        }
        log.info("Installing Bun package: {}", request.packageName());
        return InstallResult.success("Installed " + request.packageName() + " (bun run)", "bun", List.copyOf(args), request.env());
    }

    @Override
    public UninstallResult uninstall(UninstallRequest request) {
        return UninstallResult.success("Removed " + request.packageName() + " from config");
    }
}
