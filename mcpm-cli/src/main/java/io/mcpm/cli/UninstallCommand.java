package io.mcpm.cli;

import io.mcpm.core.config.ConfigHelper;
import io.mcpm.core.install.InstallOrchestrator;
import io.mcpm.core.registry.RegistryAggregator;
import io.mcpm.core.state.StateManager;
import io.mcpm.spi.McpConfig;
import io.mcpm.spi.McpmPackage;
import io.mcpm.spi.PackageHandler;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "uninstall",
        description = "@|red Uninstall|@ an MCP server package and remove it from your config.",
        mixinStandardHelpOptions = true
)
public class UninstallCommand implements Callable<Integer> {

    @CommandLine.Parameters(paramLabel = "PACKAGE", description = "Package or alias name to uninstall")
    String packageName;

    @CommandLine.Option(names = {"-v", "--version"}, description = "Version to uninstall (default: latest)")
    String version;

    @CommandLine.Option(names = {"-c", "--config"}, description = "Path to mcp.json (default: auto-detect)")
    String configPath;

    @CommandLine.Option(names = {"-y", "--yes"}, description = "Skip confirmation prompts")
    boolean yes;

    @CommandLine.Option(names = {"--purge"}, description = "Remove cached package files (JAR/binary) as well")
    boolean purge;

    @Override
    public Integer call() throws Exception {
        ConfigHelper configHelper = new ConfigHelper();
        Path configFile = configHelper.resolveConfigPath(configPath);

        // ---- Step 1: Remove from state ----
        new StateManager().recordUninstall(packageName);

        // ---- Step 2: Remove from config file ----
        System.out.println("Config: " + configFile);

        McpConfig.ServerEntry removed = configHelper.removeServer(configFile, packageName);
        if (removed != null) {
            System.out.println("✓ Removed '" + packageName + "' from " + configFile.getFileName());
        } else if (configFile.toFile().exists()) {
            System.out.println("  Server '" + packageName + "' not found in " + configFile.getFileName());
            System.out.println("  Run 'mcpm list' to see installed servers.");
        } else {
            System.out.println("  No config file found at " + configFile);
        }

        // ---- Step 2: Run handler-level uninstall ----
        RegistryAggregator registry = new RegistryAggregator();
        Optional<McpmPackage> pkgOpt = registry.getPackage(packageName);

        if (pkgOpt.isPresent()) {
            McpmPackage pkg = pkgOpt.get();
            String ver = version != null ? version : pkg.latestVersion();

            System.out.print("Cleaning up package... ");
            System.out.flush();

            InstallOrchestrator orchestrator = new InstallOrchestrator();
            PackageHandler.UninstallResult result = orchestrator.uninstall(pkg, ver);

            if (result.success()) {
                System.out.println("✓");
                System.out.println("  " + result.message());
            } else {
                System.out.println();
                System.out.println("  Warning: " + result.message());
            }

            // Purge cached files
            if (purge) {
                purgeCachedFiles(packageName, ver);
            }
        } else {
            System.out.println("  (Package metadata not found in registry — config-only cleanup done)");
            if (purge) {
                purgeCachedFiles(packageName, null);
            }
        }

        System.out.println();
        System.out.println("✔ Uninstall complete.");
        System.out.println("  Restart your AI client to apply the change.");

        return 0;
    }

    private void purgeCachedFiles(String name, String ver) {
        java.nio.file.Path cacheDir = java.nio.file.Path.of(
                System.getProperty("user.home"), ".mcpm", "cache");
        java.nio.file.Path binDir = java.nio.file.Path.of(
                System.getProperty("user.home"), ".mcpm", "bin");
        java.nio.file.Path jarDir = java.nio.file.Path.of(
                System.getProperty("user.home"), ".mcpm", "jars");

        String safe = name.replace('/', '-');
        for (java.nio.file.Path dir : java.util.List.of(cacheDir, binDir, jarDir)) {
            if (!java.nio.file.Files.exists(dir)) continue;
            try (var files = java.nio.file.Files.list(dir)) {
                files.filter(f -> f.getFileName().toString().startsWith(safe))
                        .forEach(f -> {
                            try {
                                java.nio.file.Files.deleteIfExists(f);
                                System.out.println("  ✓ Purged cached: " + f.getFileName());
                            } catch (Exception ignored) {}
                        });
            } catch (Exception ignored) {}
        }
    }
}
