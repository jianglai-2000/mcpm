package io.mcpm.cli;

import io.mcpm.core.registry.RegistryAggregator;
import io.mcpm.spi.McpmPackage;
import picocli.CommandLine;

import java.util.List;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "search",
        description = "Search for MCP server packages in the registry.",
        mixinStandardHelpOptions = true
)
public class SearchCommand implements Callable<Integer> {

    @CommandLine.Parameters(description = "Search query (package name or keywords)")
    String query;

    @CommandLine.Option(names = {"-n", "--count"}, description = "Maximum results (default: 20)")
    int count = 20;

    @Override
    public Integer call() {
        RegistryAggregator registry = new RegistryAggregator();
        List<McpmPackage> results = registry.search(query);

        if (results.isEmpty()) {
            System.out.println("No packages found for: " + query);
            return 0;
        }

        int limit = Math.min(results.size(), count);
        System.out.printf("Found %d package(s) (showing %d):%n%n", results.size(), limit);

        // Table header
        System.out.printf("%-30s %-12s %-8s %s%n", "NAME", "VERSION", "TYPE", "DESCRIPTION");
        System.out.println("-".repeat(Math.min(120, 70 + query.length())));

        for (int i = 0; i < limit; i++) {
            McpmPackage pkg = results.get(i);
            String desc = pkg.description();
            if (desc != null && desc.length() > 50) {
                desc = desc.substring(0, 47) + "...";
            }
            System.out.printf("%-30s %-12s %-8s %s%n",
                    pkg.name(), pkg.latestVersion(), pkg.type(),
                    desc != null ? desc : "");
        }

        return 0;
    }
}
