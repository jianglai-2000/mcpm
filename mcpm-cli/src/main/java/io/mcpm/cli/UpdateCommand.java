package io.mcpm.cli;

import io.mcpm.core.config.ConfigHelper;
import io.mcpm.core.install.InstallOrchestrator;
import io.mcpm.core.registry.RegistryAggregator;
import io.mcpm.core.state.StateManager;
import io.mcpm.spi.McpConfig;
import io.mcpm.spi.McpmPackage;
import io.mcpm.spi.PackageHandler;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

import static io.mcpm.core.config.ConfigHelper.describeEntry;

@CommandLine.Command(
        name = "update",
        description = "@|yellow Update|@ installed MCP servers to the latest version.",
        mixinStandardHelpOptions = true
)
public class UpdateCommand implements Callable<Integer> {

    @CommandLine.Parameters(paramLabel = "PACKAGE", description = "Package name to update (optional — updates all if omitted)", arity = "0..1")
    String packageName;

    @CommandLine.Option(names = {"-c", "--config"}, description = "Path to mcp.json or directory (auto-detected)")
    String configPath;

    @CommandLine.Option(names = {"-y", "--yes"}, description = "Skip confirmation prompts")
    boolean yes;

    @Override
    public Integer call() throws Exception {
        StateManager state = new StateManager();

        if (state.count() == 0) {
            System.out.println("No installed packages tracked in state.");
            System.out.println("Re-install packages with 'mcpm install' to enable update tracking.");
            return 0;
        }

        // Determine which packages to update
        List<StateManager.InstalledEntry> toUpdate;
        if (packageName != null) {
            if (!state.isInstalled(packageName)) {
                System.err.println("Package not found in installed state: " + packageName);
                System.err.println("Installed packages:");
                state.allInstalled().keySet().forEach(p -> System.err.println("  " + p));
                return 1;
            }
            toUpdate = List.of(state.allInstalled().get(packageName));
        } else {
            toUpdate = new ArrayList<>(state.allInstalled().values());
        }

        // Resolve config
        ConfigHelper configHelper = new ConfigHelper();
        Path configFile = configHelper.resolveConfigPath(configPath);
        boolean configExists = configFile.toFile().exists();

        RegistryAggregator registry = new RegistryAggregator();
        InstallOrchestrator orchestrator = new InstallOrchestrator();

        int updated = 0;
        int skipped = 0;
        int failed = 0;

        System.out.println("Checking " + toUpdate.size() + " package(s) for updates...\n");

        for (StateManager.InstalledEntry entry : toUpdate) {
            System.out.print("  " + entry.name() + " (" + entry.version() + ") → ");

            // Look up latest version in registry
            Optional<McpmPackage> pkgOpt = registry.getPackage(entry.name());
            if (pkgOpt.isEmpty()) {
                System.out.println("⚠  not in registry, skipping");
                skipped++;
                continue;
            }

            McpmPackage pkg = pkgOpt.get();
            String latest = pkg.latestVersion();

            if (latest.equals(entry.version())) {
                System.out.println("✓ already at latest (" + latest + ")");
                skipped++;
                continue;
            }

            // Update needed
            System.out.println(latest + "  ");

            // Re-install with latest version (preserve existing env from config)
            Map<String, String> existingEnv = null;
            if (configExists) {
                McpConfig config = configHelper.readOrEmpty(configFile);
                McpConfig.ServerEntry existing = config.getServer(entry.name());
                if (existing != null && !existing.env().isEmpty()) {
                    existingEnv = existing.env();
                }
            }

            // Uninstall old version (handler cleanup)
            PackageHandler.UninstallResult uninstallResult = orchestrator.uninstall(pkg, entry.version());
            if (!uninstallResult.success()) {
                System.out.println("      ⚠  cleanup warning: " + uninstallResult.message());
            }

            // Install new version
            PackageHandler.InstallResult installResult = orchestrator.install(pkg, latest, existingEnv);
            if (!installResult.success()) {
                System.out.println("      ✘ update failed: " + installResult.message());
                failed++;
                continue;
            }

            // Update mcp.json
            if (configExists) {
                McpConfig config = configHelper.readOrEmpty(configFile);
                McpConfig.ServerEntry newEntry = new McpConfig.ServerEntry(
                        installResult.entryCommand(),
                        installResult.entryArgs(),
                        installResult.entryEnv());
                config.addServer(entry.name(), newEntry);
                configHelper.writeWithBackup(configFile, config);
            }

            // Update state
            state.recordUpdate(entry.name(), latest);

            System.out.println("      ✓ updated");
            updated++;
        }

        // Summary
        System.out.println();
        if (updated > 0) {
            System.out.println("✔ " + updated + " package(s) updated.");
        }
        if (skipped > 0) {
            System.out.println("  " + skipped + " package(s) already up to date.");
        }
        if (failed > 0) {
            System.out.println("  " + failed + " package(s) failed.");
        }

        if (updated > 0) {
            System.out.println("\n  Restart your AI client to use the updated servers.");
        }

        return failed > 0 ? 1 : 0;
    }
}
