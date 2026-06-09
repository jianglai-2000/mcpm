package io.mcpm.cli;

import io.mcpm.core.discovery.DiscoveredService;
import io.mcpm.core.discovery.ServiceDiscovery;
import picocli.CommandLine;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "discover",
        description = "Discover MCP services running on this machine.",
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

    @CommandLine.Option(names = {"--port"}, description = "Scan ports only")
    boolean portOnly;

    @Override
    public Integer call() throws Exception {
        if (watch) {
            return watchMode();
        }
        return singleScan();
    }

    private int watchMode() throws Exception {
        System.out.println("🔍 Watching for MCP services (Ctrl+C to stop)\n");
        while (true) {
            System.out.print("[" + LocalTime.now().format(TIME) + "] ");
            int result = singleScan();
            System.out.println();
            Thread.sleep(interval * 1000L);
        }
    }

    private int singleScan() {
        ServiceDiscovery discovery = new ServiceDiscovery();
        List<DiscoveredService> services = discovery.scan();

        int procs = 0, ports = 0, configs = 0;

        if (!watch) {
            System.out.println("🔍 MCP Service Discovery\n");
        }

        for (var svc : services) {
            String icon = switch (svc.source()) {
                case "process" -> { procs++; yield "⚡"; }
                case "port" -> { ports++; yield "🌐"; }
                case "config" -> { configs++; yield "📄"; }
                default -> "❓";
            };

            boolean alive = discovery.checkAlive(svc);

            if (!watch) {
                String statusIcon = alive ? "●" : "○";
                System.out.printf("  %s %s %s%n", statusIcon, icon, svc.displayName());
                if (svc.type() != null) System.out.printf("     Type:   %s%n", svc.type());
                if (svc.transport() != null) System.out.printf("     Addr:   %s%n", svc.transport());
                if (svc.source() != null) System.out.printf("     Source: %s%n", svc.source());
                System.out.println();
            }
        }

        int total = services.size();
        if (total == 0 && !watch) {
            System.out.println("  No MCP services found.");
            System.out.println("  (Scanning processes, ports, and config)");
        }

        if (!watch) {
            System.out.printf("Found %d service(s): %d process, %d port, %d config%n",
                    total, procs, ports, configs);
        }

        return 0;
    }
}
