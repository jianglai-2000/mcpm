package io.mcpm.cli;

import io.mcpm.core.registry.RegistryAggregator;
import io.mcpm.spi.McpmPackage;
import picocli.CommandLine;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "search",
        description = "Search for MCP server packages in the registry.",
        mixinStandardHelpOptions = true
)
public class SearchCommand implements Callable<Integer> {

    @CommandLine.Parameters(paramLabel = "QUERY", description = "Search query (package name or keywords)", arity = "0..1")
    String query;

    @CommandLine.Option(names = {"-n", "--count"}, description = "Maximum results (default: 20)")
    int count = 20;

    @CommandLine.Option(names = {"-s", "--sort"}, description = "Sort by: name, version (default: relevance)")
    String sort;

    @CommandLine.Option(names = {"-t", "--type"}, description = "Filter by type: npx, pip, uvx, jar, binary, docker...")
    String typeFilter;

    @Override
    public Integer call() {
        RegistryAggregator registry = new RegistryAggregator();

        // Search with empty string returns all
        String searchQuery = query != null ? query : "";
        List<McpmPackage> results = registry.search(searchQuery);

        // Filter by type
        if (typeFilter != null && !typeFilter.isBlank()) {
            results = results.stream()
                    .filter(p -> typeFilter.equals(p.type()))
                    .toList();
        }

        // Sort
        if ("name".equals(sort)) {
            results = results.stream()
                    .sorted(Comparator.comparing(McpmPackage::name))
                    .toList();
        } else if ("version".equals(sort)) {
            results = results.stream()
                    .sorted(Comparator.<McpmPackage, String>comparing(McpmPackage::latestVersion).reversed())
                    .toList();
        }

        if (results.isEmpty()) {
            System.out.println("No packages found" + (searchQuery.isEmpty() ? "." : " for: " + searchQuery));
            if (typeFilter != null) {
                System.out.println("  Filter: type=" + typeFilter);
            }
            return 0;
        }

        int limit = Math.min(results.size(), count);
        System.out.printf("Found %d package(s) (showing %d):%n%n", results.size(), limit);

        System.out.printf("%-30s %-12s %-8s %s%n", "NAME", "VERSION", "TYPE", "DESCRIPTION");
        System.out.println("-".repeat(Math.min(120, 70)));

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
