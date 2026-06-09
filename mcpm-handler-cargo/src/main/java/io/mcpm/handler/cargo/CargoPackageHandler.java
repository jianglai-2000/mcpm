package io.mcpm.handler.cargo;

import io.mcpm.spi.PackageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles MCP servers distributed as Rust crates, run via {@code cargo run}.
 */
public class CargoPackageHandler implements PackageHandler {
    private static final Logger log = LoggerFactory.getLogger(CargoPackageHandler.class);

    @Override public String supportedType() { return "cargo"; }

    @Override
    public InstallResult install(InstallRequest request) {
        List<String> args = new ArrayList<>(List.of("run", "--package", request.packageName()));
        if (request.args() != null) {
            request.args().forEach((k, v) -> { args.add("--" + k); args.add(String.valueOf(v)); });
        }
        log.info("Installing Cargo package: {}", request.packageName());
        return InstallResult.success("Installed " + request.packageName() + " (cargo run)", "cargo", List.copyOf(args), request.env());
    }

    @Override
    public UninstallResult uninstall(UninstallRequest request) {
        return UninstallResult.success("Removed " + request.packageName() + " from config (Cargo package not uninstalled)");
    }
}
