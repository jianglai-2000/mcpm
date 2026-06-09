package io.mcpm.cli;

import io.mcpm.core.config.ConfigHelper;
import io.mcpm.spi.McpConfig;
import picocli.CommandLine;

import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "health",
        description = "Check if configured MCP servers are responsive.",
        mixinStandardHelpOptions = true
)
public class HealthCommand implements Callable<Integer> {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    @CommandLine.Option(names = {"-c", "--config"}, description = "Path to mcp.json (auto-detected)")
    String configPath;

    @CommandLine.Option(names = {"-w", "--watch"}, description = "Watch mode: keep checking at interval")
    boolean watch;

    @CommandLine.Option(names = {"-i", "--interval"}, description = "Watch interval in seconds (default: 5)")
    int interval = 5;

    @Override
    public Integer call() throws Exception {
        if (watch) {
            return watchMode();
        }
        return singleCheck();
    }

    private int watchMode() throws Exception {
        System.out.println("Watch mode — checking every " + interval + "s (Ctrl+C to stop)\n");
        int exitCode = 0;
        while (true) {
            String ts = LocalTime.now().format(TIME_FMT);
            System.out.print("[" + ts + "] ");
            int result = singleCheck();
            if (result != 0) exitCode = result;
            Thread.sleep(interval * 1000L);
        }
    }

    private int singleCheck() throws Exception {
        ConfigHelper configHelper = new ConfigHelper();
        Path configFile = configHelper.resolveConfigPath(configPath);

        if (!configFile.toFile().exists()) {
            System.out.println("No MCP config found.");
            return 0;
        }

        McpConfig config = configHelper.readOrEmpty(configFile);
        if (config.size() == 0) {
            System.out.println("No MCP servers configured.");
            return 0;
        }

        int passed = 0;
        int failed = 0;
        boolean headerPrinted = false;

        for (var entry : config.servers().entrySet()) {
            String name = entry.getKey();
            McpConfig.ServerEntry server = entry.getValue();
            String status;

            if (server.isStdio()) {
                String cmd = server.command();
                if (cmd == null || cmd.isBlank()) {
                    status = "✘ no command";
                    failed++;
                } else if (cmd.contains("/") || cmd.contains("\\")) {
                    status = Files.exists(Path.of(cmd)) ? "✓ OK" : "✘ missing";
                    if (status.startsWith("✓")) passed++; else failed++;
                } else {
                    status = "? " + cmd;
                    passed++;
                }
            } else {
                String url = server.transportUrl();
                if (url != null && url.startsWith("http")) {
                    try {
                        URI uri = URI.create(url);
                        int port = uri.getPort() == -1 ? (url.startsWith("https") ? 443 : 80) : uri.getPort();
                        try (Socket s = new Socket(uri.getHost(), port)) {
                            status = "✓ " + uri.getHost() + ":" + port;
                            passed++;
                        }
                    } catch (Exception e) {
                        status = "✘ " + e.getMessage();
                        failed++;
                    }
                } else {
                    status = "? no URL";
                    passed++;
                }
            }

            // Print in compact format
            if (!headerPrinted && watch) {
                System.out.println(configFile.getFileName() + " (" + config.size() + " servers)");
                headerPrinted = true;
            }
            if (watch) {
                System.out.println("  " + name + ": " + status);
            } else {
                System.out.println("  " + name + ": " + status);
            }
        }

        if (!watch) {
            System.out.println("\n" + passed + " passed, " + failed + " failed"
                    + (failed == 0 ? " ✓" : ""));
        }
        return failed > 0 ? 1 : 0;
    }
}
