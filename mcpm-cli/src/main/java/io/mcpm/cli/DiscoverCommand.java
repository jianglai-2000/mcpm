package io.mcpm.cli;

import io.mcpm.core.config.ConfigHelper;
import io.mcpm.core.discovery.DiscoveredService;
import io.mcpm.core.discovery.ServiceDiscovery;
import io.mcpm.spi.McpConfig;
import picocli.CommandLine;

import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "discover",
        description = "Discover, add, and start MCP services on this machine.",
        mixinStandardHelpOptions = true
)
public class DiscoverCommand implements Callable<Integer> {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    @CommandLine.Option(names = {"-w", "--watch"}, description = "Watch mode — continuous monitoring")
    boolean watch;

    @CommandLine.Option(names = {"-i", "--interval"}, description = "Watch interval in seconds (default: 10)")
    int interval = 10;

    @CommandLine.Option(names = {"-p", "--process"}, description = "Scan running processes only")
    boolean processOnly;

    @CommandLine.Option(names = {"--port-only"}, description = "Scan ports only")
    boolean portOnly;

    @CommandLine.Option(names = {"-a", "--add"}, description = "Add a discovered service to mcp.json")
    String addName;

    @CommandLine.Option(names = {"-s", "--start"}, description = "Start a configured service by name")
    String startName;

    @CommandLine.Option(names = {"-c", "--config"}, description = "Path to mcp.json (auto-detected)")
    String configPath;

    @Override
    public Integer call() throws Exception {
        ServiceDiscovery discovery = new ServiceDiscovery();
        ConfigHelper configHelper = new ConfigHelper();

        // --add: add discovered service to config
        if (addName != null) {
            return addToConfig(discovery, configHelper);
        }

        // --start: launch a configured service
        if (startName != null) {
            return startService(configHelper);
        }

        // Default: scan
        if (watch) {
            return watchMode(discovery);
        }
        return singleScan(discovery);
    }

    // ---- Scan ----

    private int watchMode(ServiceDiscovery discovery) throws Exception {
        System.out.println("🔍 Watching for MCP services (Ctrl+C to stop)\n");
        while (true) {
            System.out.print("[" + LocalTime.now().format(TIME) + "] ");
            singleScan(discovery);
            System.out.println();
            Thread.sleep(interval * 1000L);
        }
    }

    private int singleScan(ServiceDiscovery discovery) {
        List<DiscoveredService> services;
        if (processOnly) {
            services = discovery.scanProcesses();
        } else if (portOnly) {
            services = discovery.scanPorts();
        } else {
            services = discovery.scan();
        }

        int total = services.size();
        if (!watch) {
            System.out.println("🔍 MCP Service Discovery\n");
        }

        if (total == 0) {
            if (!watch) System.out.println("  No MCP services found.");
            return 0;
        }

        for (var svc : services) {
            String icon = switch (svc.source()) {
                case "process" -> "⚡";
                case "port" -> "🌐";
                case "config" -> "📄";
                default -> "❓";
            };

            String alive = "-";
            try { alive = discovery.checkAlive(svc) ? "●" : "○"; } catch (Exception ignored) {}

            if (!watch) {
                System.out.printf("  %s %s %s%n", alive, icon, svc.displayName());
                if (svc.type() != null) System.out.printf("     Type:   %s%n", svc.type());
                if (svc.transport() != null) System.out.printf("     Addr:   %s%n", svc.transport());
                System.out.println();
            }
        }

        if (!watch) {
            long procs = services.stream().filter(s -> "process".equals(s.source())).count();
            long ports = services.stream().filter(s -> "port".equals(s.source())).count();
            long configs = services.stream().filter(s -> "config".equals(s.source())).count();
            System.out.printf("Found %d service(s): %d process, %d port, %d config%n",
                    total, procs, ports, configs);
            System.out.println("  Add to config: mcpm discover --add <name>");
            System.out.println("  Start service: mcpm discover --start <name>");
        }

        return 0;
    }

    // ---- Add ----

    private int addToConfig(ServiceDiscovery discovery, ConfigHelper configHelper) throws Exception {
        // Re-scan to get current state
        discovery.scan();

        // Find the service by name (exact or partial match)
        DiscoveredService target = null;
        for (var svc : discovery.all()) {
            if (svc.displayName().equals(addName)) {
                target = svc;
                break;
            }
        }

        if (target == null) {
            System.err.println("Service not found: " + addName);
            System.err.println("  Run 'mcpm discover' to see available services.");
            return 1;
        }

        Path configFile = configHelper.resolveConfigPath(configPath);
        McpConfig config = configHelper.readOrEmpty(configFile);

        String name = addName.replaceAll("[^a-zA-Z0-9@/_-]", "-");
        McpConfig.ServerEntry entry;

        if ("sse".equals(target.type()) && target.url() != null) {
            entry = new McpConfig.ServerEntry(null, List.of(), Map.of(),
                    "sse", target.url());
        } else if (target.command() != null) {
            String[] parts = target.command().split("\\s+", 2);
            String cmd = parts[0];
            List<String> args = parts.length > 1
                    ? List.of(parts[1].split("\\s+"))
                    : List.of();
            entry = new McpConfig.ServerEntry(cmd, args, Map.of());
        } else {
            entry = new McpConfig.ServerEntry(target.command(), List.of(), Map.of());
        }

        config.addServer(name, entry);
        configHelper.writeWithBackup(configFile, config);

        System.out.println("✔ Added '" + name + "' to " + configFile.getFileName());
        return 0;
    }

    // ---- Start ----

    private int startService(ConfigHelper configHelper) throws Exception {
        Path configFile = configHelper.resolveConfigPath(configPath);
        McpConfig config = configHelper.readOrEmpty(configFile);

        var entry = config.getServer(startName);
        if (entry == null) {
            System.err.println("Service not found in config: " + startName);
            System.err.println("  Run 'mcpm list' to see configured services.");
            return 1;
        }

        if (!entry.isStdio()) {
            System.err.println("Cannot start remote service (SSE). Connect your AI client to: " + entry.transportUrl());
            return 1;
        }

        String cmd = entry.command();
        if (cmd == null || cmd.isBlank()) {
            System.err.println("Service '" + startName + "' has no command to run.");
            return 1;
        }

        System.out.println("Starting '" + startName + "'...");

        var commandList = new java.util.ArrayList<String>();
        commandList.add(cmd);
        commandList.addAll(entry.args());

        var pb = new ProcessBuilder(commandList);

        // Set env vars
        if (entry.env() != null) {
            pb.environment().putAll(entry.env());
        }

        // Inherit IO so the service can communicate via stdio
        pb.inheritIO();

        try {
            Process process = pb.start();
            System.out.println("✔ Started " + startName + " (PID: " + process.pid() + ")");
            System.out.println("  Command: " + cmd + " " + String.join(" ", entry.args()));
        } catch (Exception e) {
            System.err.println("✘ Failed to start: " + e.getMessage());
            return 1;
        }

        return 0;
    }
}
