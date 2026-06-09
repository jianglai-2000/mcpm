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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

import static io.mcpm.core.config.ConfigHelper.describeEntry;

@CommandLine.Command(
        name = "install",
        description = "@|green Install|@ an MCP server package and add it to your MCP config.",
        mixinStandardHelpOptions = true
)
public class InstallCommand implements Callable<Integer> {

    @CommandLine.Parameters(paramLabel = "PACKAGE", description = "Package name to install (e.g. @anthropic/server-filesystem). Omit for interactive mode.", arity = "0..1")
    String packageName;

    @CommandLine.Option(names = {"-v", "--version"}, description = "Specific version to install (default: latest)")
    String version;

    @CommandLine.Option(names = {"-e", "--env"}, description = "Environment variables (key=value, repeatable)")
    Map<String, String> env;

    @CommandLine.Option(names = {"-c", "--config"}, description = "Path to mcp.json (default: auto-detect ./mcp.json)")
    String configPath;

    @CommandLine.Option(names = {"-y", "--yes"}, description = "Skip all confirmation prompts, overwrite if needed")
    boolean yes;

    @CommandLine.Option(names = {"-a", "--alias"}, description = "Alias name in mcp.json (default: package name)")
    String alias;

    @Override
    public Integer call() throws Exception {
        RegistryAggregator registry = new RegistryAggregator();

        // ---- Interactive mode: no package name ----
        if (packageName == null) {
            return interactiveInstall(registry);
        }

        // ---- Step 1: Resolve package from registry ----
        Optional<McpmPackage> pkgOpt = registry.getPackage(packageName);

        if (pkgOpt.isEmpty()) {
            System.err.println("✘ Package not found: " + packageName);
            System.err.println("  Run 'mcpm search " + packageName + "' to search, or check the name.");
            return 1;
        }
        McpmPackage pkg = pkgOpt.get();

        // Resolve version range (^1.0, >=2.0, etc.)
        String resolvedVersion = resolveVersion(pkg, version);
        if (resolvedVersion == null) {
            System.err.println("✘ No matching version for: " + (version != null ? version : "latest"));
            return 1;
        }

        System.out.println("Package:  " + pkg.name() + " v" + resolvedVersion
                + (version != null && !version.equals(resolvedVersion) ? " (requested: " + version + ")" : ""));
        System.out.println("Type:     " + pkg.type());
        System.out.println();

        // ---- Step 2: Resolve config file ----
        ConfigHelper configHelper = new ConfigHelper();
        Path configFile = configHelper.resolveConfigPath(configPath);
        System.out.println("Config:   " + configFile);

        // ---- Step 3: Check for conflicts ----
        McpConfig config = configHelper.readOrEmpty(configFile);
        String serverName = alias != null ? alias : pkg.name();
        String conflictMsg = configHelper.checkConflict(config, serverName);

        if (conflictMsg != null) {
            System.out.println("Conflict: " + conflictMsg);
            if (!yes) {
                System.err.println("  Use --yes to overwrite, or specify a different --alias.");
                return 1;
            }
            System.out.println("  → Overwriting (--yes)");
        }

        // ---- Step 4: Install via handler (with spinner) ----
        System.out.println();
        var spinner = new io.mcpm.core.util.Spinner("Installing " + pkg.name());
        spinner.start();

        InstallOrchestrator orchestrator = new InstallOrchestrator();
        PackageHandler.InstallResult result = orchestrator.install(pkg, version, env);

        if (!result.success()) {
            spinner.fail();
            System.err.println("  ✘ " + result.message());
            return 1;
        }

        spinner.success();

        // ---- Step 5: Record in state ----
        String installedVer = version != null ? version : pkg.latestVersion();
        new StateManager().recordInstall(pkg.name(), installedVer, pkg.type());

        // ---- Step 6: Write to config ----
        System.out.print("Writing config... ");
        System.out.flush();

        McpConfig.ServerEntry entry = new McpConfig.ServerEntry(
                result.entryCommand(),
                result.entryArgs(),
                result.entryEnv());

        config.addServer(serverName, entry);

        Path backupPath = configHelper.writeWithBackup(configFile, config);
        if (backupPath != null) {
            System.out.println("✓ (backup: " + configFile.getFileName() + ".bak.*" + ")");
        } else {
            System.out.println("✓");
        }

        // ---- Step 6: Summary ----
        System.out.println();
        System.out.println("✔ Installation complete!");
        System.out.println();
        System.out.println("  " + serverName + ":");
        System.out.println("    command: " + result.entryCommand());
        if (!result.entryArgs().isEmpty()) {
            System.out.println("    args:    " + String.join(" ", result.entryArgs()));
        }
        if (!result.entryEnv().isEmpty()) {
            System.out.println("    env:     " + result.entryEnv());
        }
        System.out.println();
        System.out.println("  Config: " + configFile);
        System.out.println("  Type:   " + describeEntry(pkg, result));
        System.out.println();
        System.out.println("  Restart your AI client to use the new server. 🚀");

        return 0;
    }

    // ---- Version range resolution ----

    static String resolveVersion(McpmPackage pkg, String versionSpec) {
        if (versionSpec == null || versionSpec.isBlank()) {
            return pkg.latestVersion();
        }

        var versions = new java.util.ArrayList<>(pkg.versions().keySet());
        java.util.Collections.sort(versions, java.util.Comparator.reverseOrder());

        if (versionSpec.startsWith(">=")) {
            String min = versionSpec.substring(2);
            for (String v : versions) {
                if (compareVersions(v, min) >= 0) return v;
            }
            return null;
        }

        if (versionSpec.startsWith("^")) {
            // ^1.2.3 → >=1.2.3, <2.0.0
            String base = versionSpec.substring(1);
            String[] parts = base.split("\\.");
            if (parts.length > 0) {
                String major = parts[0];
                String upper = (Integer.parseInt(major) + 1) + ".0.0";
                for (String v : versions) {
                    if (compareVersions(v, base) >= 0 && compareVersions(v, upper) < 0) return v;
                }
            }
            return null;
        }

        if (versionSpec.startsWith("~")) {
            // ~1.2.3 → >=1.2.3, <1.3.0
            String base = versionSpec.substring(1);
            String[] parts = base.split("\\.");
            String upper = parts[0] + "." + (parts.length > 1 ? Integer.parseInt(parts[1]) + 1 : 1) + ".0";
            for (String v : versions) {
                if (compareVersions(v, base) >= 0 && compareVersions(v, upper) < 0) return v;
            }
            return null;
        }

        // Exact version match
        if (pkg.versions().containsKey(versionSpec)) {
            return versionSpec;
        }

        return null;
    }

    static int compareVersions(String a, String b) {
        String[] pa = a.split("[.-]");
        String[] pb = b.split("[.-]");
        for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
            int va = i < pa.length ? tryParseInt(pa[i]) : 0;
            int vb = i < pb.length ? tryParseInt(pb[i]) : 0;
            if (va != vb) return va - vb;
        }
        return 0;
    }

    private static int tryParseInt(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }

    // ---- Interactive mode ----

    private Integer interactiveInstall(RegistryAggregator registry) throws Exception {
        System.out.println("Interactive install — search for packages:");
        System.out.println("(Type your search query and press Enter, or Ctrl+C to exit)");
        System.out.println();

        java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(System.in));

        while (true) {
            System.out.print("Search: ");
            System.out.flush();
            String query = reader.readLine();
            if (query == null || query.isBlank()) continue;

            var results = registry.search(query);
            if (results.isEmpty()) {
                System.out.println("  No packages found for: " + query);
                System.out.println();
                continue;
            }

            System.out.println();
            for (int i = 0; i < results.size(); i++) {
                var p = results.get(i);
                System.out.printf("  %2d. %-30s %-8s %s%n",
                        i + 1, p.name(), p.type(),
                        p.description() != null ? p.description() : "");
            }
            System.out.println();
            System.out.print("Select package (1-%d) or 'q' to quit: ".formatted(results.size()));
            System.out.flush();

            String line = reader.readLine();
            if (line == null || line.trim().equalsIgnoreCase("q")) {
                System.out.println("Aborted.");
                return 0;
            }

            try {
                int idx = Integer.parseInt(line.trim()) - 1;
                if (idx >= 0 && idx < results.size()) {
                    packageName = results.get(idx).name();
                    // Fall through to the normal install flow
                    break;
                }
            } catch (NumberFormatException ignored) {}

            System.out.println("Invalid selection. Try again.");
            System.out.println();
        }

        // Re-run with the selected package name
        return call();
    }
}
