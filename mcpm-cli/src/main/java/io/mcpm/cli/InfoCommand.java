package io.mcpm.cli;

import io.mcpm.core.registry.RegistryAggregator;
import io.mcpm.spi.McpmPackage;
import picocli.CommandLine;

import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "info",
        description = "Show detailed information about a package.",
        aliases = {"view", "show"},
        mixinStandardHelpOptions = true
)
public class InfoCommand implements Callable<Integer> {

    @CommandLine.Parameters(paramLabel = "PACKAGE", description = "Package name")
    String packageName;

    @Override
    public Integer call() {
        RegistryAggregator registry = new RegistryAggregator();
        Optional<McpmPackage> pkgOpt = registry.getPackage(packageName);

        if (pkgOpt.isEmpty()) {
            System.err.println("Package not found: " + packageName);
            return 1;
        }

        McpmPackage pkg = pkgOpt.get();

        System.out.println(pkg.name() + "@" + pkg.latestVersion() + "  |  " + pkg.type());
        System.out.println();
        if (pkg.description() != null) {
            System.out.println("  " + pkg.description());
            System.out.println();
        }

        System.out.println("  Type:      " + pkg.type());
        System.out.println("  Latest:    " + pkg.latestVersion());
        System.out.println("  License:   " + (pkg.license() != null ? pkg.license() : "N/A"));
        System.out.println("  Authors:   " + (pkg.authors() != null ? String.join(", ", pkg.authors()) : "N/A"));

        if (pkg.homepage() != null) System.out.println("  Homepage:  " + pkg.homepage());
        if (pkg.repository() != null) System.out.println("  Repository: " + pkg.repository());

        System.out.println();
        System.out.println("Versions:");
        pkg.versions().keySet().stream()
                .sorted(Comparator.reverseOrder())
                .forEach(v -> {
                    var entry = pkg.versions().get(v);
                    String suffix = v.equals(pkg.latestVersion()) ? " (latest)" : "";
                    String dl = entry.downloadUrl() != null ? "  ↓ " + entry.downloadUrl() : "";
                    System.out.println("  " + v + suffix + dl);
                });

        System.out.println();
        System.out.println("Install: mcpm install " + pkg.name());
        System.out.println("Info:    mcpm info " + pkg.name());
        System.out.println("Docs:    mcpm docs " + pkg.name());

        return 0;
    }
}
