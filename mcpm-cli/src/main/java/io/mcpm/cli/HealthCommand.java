package io.mcpm.cli;

import io.mcpm.core.config.ConfigHelper;
import io.mcpm.spi.McpConfig;
import picocli.CommandLine;

import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "health",
        description = "Check if configured MCP servers are responsive.",
        mixinStandardHelpOptions = true
)
public class HealthCommand implements Callable<Integer> {

    @CommandLine.Option(names = {"-c", "--config"}, description = "Path to mcp.json (auto-detected)")
    String configPath;

    @Override
    public Integer call() throws Exception {
        ConfigHelper configHelper = new ConfigHelper();
        Path configFile = configHelper.resolveConfigPath(configPath);

        if (!configFile.toFile().exists()) {
            System.out.println("No MCP config found.");
            return 0;
        }

        McpConfig config = configHelper.readOrEmpty(configFile);
        if (config.size() == 0) {
            System.out.println("No MCP servers configured in " + configFile);
            return 0;
        }

        int passed = 0;
        int failed = 0;

        System.out.println("Health check (" + configFile.getFileName() + "):\n");

        for (var entry : config.servers().entrySet()) {
            String name = entry.getKey();
            McpConfig.ServerEntry server = entry.getValue();

            System.out.print("  " + name + " → ");

            if (server.isStdio()) {
                // Check if the command exists on PATH
                String cmd = server.command();
                if (cmd == null || cmd.isBlank()) {
                    System.out.println("✘ no command specified");
                    failed++;
                } else if (cmd.contains("/") || cmd.contains("\\")) {
                    // Full path — check file exists
                    if (Files.exists(Path.of(cmd))) {
                        System.out.println("✓ binary exists");
                        passed++;
                    } else {
                        System.out.println("✘ binary not found: " + cmd);
                        failed++;
                    }
                } else {
                    // Just check if it's a known runner
                    System.out.println("? using " + cmd + " (assumed on PATH)");
                    passed++;
                }
            } else {
                // Remote transport — try TCP connection
                String url = server.transportUrl();
                if (url != null && url.startsWith("http")) {
                    try {
                        String host = java.net.URI.create(url).getHost();
                        int port = java.net.URI.create(url).getPort();
                        if (port == -1) port = url.startsWith("https") ? 443 : 80;
                        try (var s = new Socket(host, port)) {
                            System.out.println("✓ " + host + ":" + port + " reachable");
                            passed++;
                        }
                    } catch (Exception e) {
                        System.out.println("✘ connection failed: " + e.getMessage());
                        failed++;
                    }
                } else {
                    System.out.println("? no URL to check");
                    passed++;
                }
            }
        }

        System.out.println();
        System.out.println("Result: " + passed + " passed, " + failed + " failed"
                + (failed == 0 ? " ✓" : ""));
        return failed > 0 ? 1 : 0;
    }
}
