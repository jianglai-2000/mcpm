package io.mcpm.cli;

import io.mcpm.core.registry.RegistryAggregator;
import io.mcpm.spi.McpmPackage;
import picocli.CommandLine;

import java.util.Optional;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "info",
        description = "@|cyan Show|@ detailed information about a package.",
        mixinStandardHelpOptions = true
)
public class InfoCommand implements Callable<Integer> {

    @CommandLine.Parameters(description = "Package name")
    String packageName;

    @Override
    public Integer call() throws Exception {
        RegistryAggregator registry = new RegistryAggregator();
        Optional<McpmPackage> pkgOpt = registry.getPackage(packageName);

        if (pkgOpt.isEmpty()) {
            System.err.println("Package not found: " + packageName);
            return 1;
        }

        McpmPackage pkg = pkgOpt.get();

        System.out.println("Package:   " + pkg.name());
        System.out.println("Type:      " + pkg.type());
        System.out.println("Latest:    " + pkg.latestVersion());
        System.out.println("License:   " + (pkg.license() != null ? pkg.license() : "N/A"));
        System.out.println("Homepage:  " + (pkg.homepage() != null ? pkg.homepage() : "N/A"));
        System.out.println("Repository: " + (pkg.repository() != null ? pkg.repository() : "N/A"));
        System.out.println("Authors:   " + (pkg.authors() != null ? String.join(", ", pkg.authors()) : "N/A"));

        if (pkg.description() != null) {
            System.out.println("\nDescription:");
            System.out.println("  " + pkg.description());
        }

        System.out.println("\nAvailable versions: " + pkg.versions().size());
        pkg.versions().keySet().stream()
                .limit(10)
                .forEach(v -> System.out.println("  - " + v + (v.equals(pkg.latestVersion()) ? " (latest)" : "")));

        if (pkg.versions().size() > 10) {
            System.out.println("  ... and " + (pkg.versions().size() - 10) + " more");
        }

        return 0;
    }
}
