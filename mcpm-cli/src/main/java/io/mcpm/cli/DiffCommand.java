package io.mcpm.cli;

import io.mcpm.core.config.ConfigHelper;
import io.mcpm.core.registry.RegistryAggregator;
import io.mcpm.core.state.StateManager;
import io.mcpm.spi.McpConfig;
import io.mcpm.spi.McpmPackage;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "diff",
        description = "Compare installed version with the latest version in the registry.",
        mixinStandardHelpOptions = true
)
public class DiffCommand implements Callable<Integer> {

    @CommandLine.Parameters(paramLabel = "PACKAGE", description = "Package name")
    String packageName;

    @CommandLine.Option(names = {"-c", "--config"}, description = "Path to mcp.json (auto-detected)")
    String configPath;

    @Override
    public Integer call() throws Exception {
        StateManager state = new StateManager();
        RegistryAggregator registry = new RegistryAggregator();
        ConfigHelper configHelper = new ConfigHelper();

        // 1. Check state
        String installedVersion = null;
        if (state.isInstalled(packageName)) {
            installedVersion = state.installedVersion(packageName).orElse(null);
        }

        // 2. Check config
        Path configFile = configHelper.resolveConfigPath(configPath);
        McpConfig.ServerEntry entry = null;
        if (configFile.toFile().exists()) {
            McpConfig config = configHelper.readOrEmpty(configFile);
            entry = config.getServer(packageName);
        }

        // 3. Check registry
        Optional<McpmPackage> pkgOpt = registry.getPackage(packageName);
        if (pkgOpt.isEmpty()) {
            System.err.println("Package not found in registry: " + packageName);
            if (installedVersion != null) {
                System.out.println("  Installed: " + installedVersion + " (not in registry, may be removed)");
            }
            return 1;
        }

        McpmPackage pkg = pkgOpt.get();
        String latestVersion = pkg.latestVersion();

        System.out.println("Package: " + packageName + "\n");

        if (installedVersion != null) {
            System.out.println("  Installed:  " + installedVersion);
            System.out.println("  Registry:   " + latestVersion);
            System.out.println();
            if (installedVersion.equals(latestVersion)) {
                System.out.println("✔ Up to date (" + latestVersion + ")");
            } else {
                System.out.println("⬆ Update available: " + installedVersion + " → " + latestVersion);
                System.out.println("  Run: mcpm update " + packageName);
            }
        } else {
            System.out.println("  Registry:   " + latestVersion);
            System.out.println("  (not installed via mcpm — run 'mcpm install " + packageName + "')");
        }

        // Show config entry if exists
        if (entry != null) {
            System.out.println();
            System.out.println("  Config entry (" + configFile.getFileName() + "):");
            if (entry.isStdio()) {
                System.out.println("    command: " + entry.command());
                if (!entry.args().isEmpty()) {
                    System.out.println("    args: " + String.join(" ", entry.args()));
                }
            } else {
                System.out.println("    type: " + entry.transportType());
                System.out.println("    url: " + entry.transportUrl());
            }
        }

        return 0;
    }
}
