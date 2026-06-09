package io.mcpm.handler.deno;

import io.mcpm.spi.PackageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles MCP servers distributed as Deno modules, run via {@code deno run}.
 */
public class DenoPackageHandler implements PackageHandler {
    private static final Logger log = LoggerFactory.getLogger(DenoPackageHandler.class);

    @Override public String supportedType() { return "deno"; }

    @Override
    public InstallResult install(InstallRequest request) {
        List<String> args = new ArrayList<>();
        args.add("run");
        args.add("--allow-all");
        args.add(request.packageName());
        if (request.args() != null) {
            request.args().forEach((k, v) -> { args.add("--" + k); args.add(String.valueOf(v)); });
        }
        log.info("Installing Deno package: {}", request.packageName());
        return InstallResult.success("Installed " + request.packageName() + " (deno run)", "deno", List.copyOf(args), request.env());
    }

    @Override
    public UninstallResult uninstall(UninstallRequest request) {
        return UninstallResult.success("Removed " + request.packageName() + " from config");
    }
}
