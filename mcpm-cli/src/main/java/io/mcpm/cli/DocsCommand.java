package io.mcpm.cli;

import io.mcpm.core.registry.RegistryAggregator;
import io.mcpm.spi.McpmPackage;
import picocli.CommandLine;

import java.awt.Desktop;
import java.net.URI;
import java.util.Optional;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "docs",
        description = "Open the documentation or homepage for a package.",
        mixinStandardHelpOptions = true
)
public class DocsCommand implements Callable<Integer> {

    @CommandLine.Parameters(paramLabel = "PACKAGE", description = "Package name")
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
        String url = pkg.homepage() != null ? pkg.homepage() : pkg.repository();

        if (url == null) {
            System.err.println("No homepage or repository URL for " + packageName);
            return 1;
        }

        System.out.println("Opening: " + url);

        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(new URI(url));
        } else {
            System.out.println("Desktop browsing not supported. Visit manually:");
            System.out.println("  " + url);
        }

        return 0;
    }
}
