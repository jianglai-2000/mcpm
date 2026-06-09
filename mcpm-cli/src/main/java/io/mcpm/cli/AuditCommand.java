package io.mcpm.cli;

import io.mcpm.core.config.ConfigHelper;
import io.mcpm.core.registry.RegistryAggregator;
import io.mcpm.core.state.StateManager;
import io.mcpm.spi.McpConfig;
import io.mcpm.spi.McpmPackage;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "audit",
        description = "Check installed MCP servers for security issues, outdated versions, and config problems.",
        mixinStandardHelpOptions = true
)
public class AuditCommand implements Callable<Integer> {

    @CommandLine.Option(names = {"-c", "--config"}, description = "Path to mcp.json (auto-detected)")
    String configPath;

    @CommandLine.Option(names = {"-f", "--fix"}, description = "Attempt to fix issues automatically")
    boolean fix;

    @Override
    public Integer call() throws Exception {
        StateManager state = new StateManager();
        RegistryAggregator registry = new RegistryAggregator();
        ConfigHelper configHelper = new ConfigHelper();

        List<String> issues = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> infos = new ArrayList<>();

        System.out.println("🔍 MCPM Audit\n");

        // 1. Check outdated packages
        if (state.count() > 0) {
            infos.add("Checking " + state.count() + " installed package(s) for updates...");
            for (var entry : state.allInstalled().values()) {
                Optional<McpmPackage> pkgOpt = registry.getPackage(entry.name());
                if (pkgOpt.isPresent()) {
                    McpmPackage pkg = pkgOpt.get();
                    if (!pkg.latestVersion().equals(entry.version())) {
                        issues.add("  ⚠ " + entry.name()
                                + " (" + entry.version() + " → " + pkg.latestVersion() + ")"
                                + " — run 'mcpm update " + entry.name() + "'");
                    }
                } else {
                    warnings.add("  ? " + entry.name() + " — not found in registry, may be removed");
                }
            }
        } else {
            infos.add("No version-tracked packages (re-install with 'mcpm install' to enable)");
        }

        // 2. Check config file
        Path configFile = configHelper.resolveConfigPath(configPath);
        if (configFile.toFile().exists()) {
            infos.add("Config: " + configFile);

            // Check file permissions (Unix only)
            if (!System.getProperty("os.name").toLowerCase().contains("win")) {
                if (configFile.toFile().canWrite() && configFile.toFile().canRead()) {
                    infos.add("  ✓ permissions OK");
                }
            }

            McpConfig config = configHelper.readOrEmpty(configFile);

            // Check for common issues
            for (Map.Entry<String, McpConfig.ServerEntry> entry : config.servers().entrySet()) {
                String name = entry.getKey();
                McpConfig.ServerEntry server = entry.getValue();

                // Check for plaintext secrets in env
                if (server.env() != null) {
                    for (String envKey : server.env().keySet()) {
                        String val = server.env().get(envKey);
                        if (isLikelySecret(envKey, val)) {
                            warnings.add("  ⚠ " + name + ": env variable '" + envKey
                                    + "' contains a plaintext secret");
                        }
                    }
                }

                // Check command existence
                if (server.isStdio() && server.command() != null) {
                    String cmd = server.command();
                    if (cmd.equals("npx") || cmd.equals("uvx") || cmd.equals("docker")) {
                        infos.add("  ✓ " + name + ": uses " + cmd + " (managed)");
                    } else if (!cmd.contains("/") && !cmd.contains("\\")) {
                        // Just a bare command name, check if on PATH
                        infos.add("  ? " + name + ": command '" + cmd + "' (assumed on PATH)");
                    } else {
                        // Full path, check existence
                        if (!Files.exists(Path.of(cmd))) {
                            issues.add("  ✘ " + name + ": command not found at '" + cmd + "'");
                        }
                    }
                }
            }
        } else {
            infos.add("No config file found at default locations");
        }

        // 3. Output
        if (!issues.isEmpty()) {
            System.out.println("Issues (" + issues.size() + "):");
            issues.forEach(System.out::println);
            System.out.println();
        }
        if (!warnings.isEmpty()) {
            System.out.println("Warnings (" + warnings.size() + "):");
            warnings.forEach(System.out::println);
            System.out.println();
        }
        if (!infos.isEmpty()) {
            System.out.println("Info:");
            infos.forEach(System.out::println);
            System.out.println();
        }

        int total = issues.size() + warnings.size();
        if (total == 0) {
            System.out.println("✔ All clear! No issues found.");
        } else {
            System.out.println("Found " + issues.size() + " issue(s) and "
                    + warnings.size() + " warning(s).");
            if (fix && !issues.isEmpty()) {
                System.out.println("\nAuto-fix not yet implemented. Use the suggested commands above.");
            }
        }

        return issues.isEmpty() ? 0 : 1;
    }

    private boolean isLikelySecret(String key, String value) {
        if (value == null || value.isEmpty() || value.contains("***")) return false;
        String lowerKey = key.toLowerCase();
        return (lowerKey.contains("key") || lowerKey.contains("secret")
                || lowerKey.contains("token") || lowerKey.contains("password")
                || lowerKey.contains("api"));
    }
}
