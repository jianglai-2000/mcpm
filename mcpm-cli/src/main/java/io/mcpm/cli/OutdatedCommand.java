package io.mcpm.cli;

import io.mcpm.core.registry.RegistryAggregator;
import io.mcpm.core.state.StateManager;
import io.mcpm.spi.McpmPackage;
import picocli.CommandLine;

import java.util.*;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "outdated",
        description = "List installed packages that have newer versions available.",
        mixinStandardHelpOptions = true
)
public class OutdatedCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        StateManager state = new StateManager();
        RegistryAggregator registry = new RegistryAggregator();

        if (state.count() == 0) {
            System.out.println("No packages tracked. Install packages with 'mcpm install' first.");
            return 0;
        }

        List<String[]> outdated = new ArrayList<>();
        int upToDate = 0;
        int notFound = 0;

        for (var entry : state.allInstalled().values()) {
            Optional<McpmPackage> pkgOpt = registry.getPackage(entry.name());
            if (pkgOpt.isEmpty()) {
                notFound++;
                continue;
            }
            String latest = pkgOpt.get().latestVersion();
            if (!latest.equals(entry.version())) {
                outdated.add(new String[]{entry.name(), entry.version(), latest, entry.type()});
            } else {
                upToDate++;
            }
        }

        if (outdated.isEmpty()) {
            System.out.println("All " + upToDate + " package(s) up to date. ✓");
            return 0;
        }

        System.out.println("Outdated packages:\n");
        System.out.printf("%-35s %-15s %-15s %s%n", "PACKAGE", "INSTALLED", "LATEST", "TYPE");
        System.out.println("-".repeat(80));
        for (String[] row : outdated) {
            System.out.printf("%-35s %-15s %-15s %s%n", row[0], row[1], row[2], row[3]);
        }
        System.out.println();
        System.out.println(outdated.size() + " outdated, " + upToDate + " up to date"
                + (notFound > 0 ? ", " + notFound + " not in registry" : ""));
        System.out.println();
        System.out.println("Run 'mcpm update' to upgrade all packages.");
        return outdated.isEmpty() ? 0 : 1;
    }
}
