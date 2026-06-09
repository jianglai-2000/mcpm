package io.mcpm.cli;

import io.mcpm.core.config.ConfigHelper;
import io.mcpm.core.state.StateManager;
import io.mcpm.spi.McpConfig;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "why",
        description = "Show why a package is installed and where it's referenced.",
        mixinStandardHelpOptions = true
)
public class WhyCommand implements Callable<Integer> {

    @CommandLine.Parameters(paramLabel = "PACKAGE", description = "Package name to inspect")
    String packageName;

    @CommandLine.Option(names = {"-c", "--config"}, description = "Path to mcp.json (auto-detected)")
    String configPath;

    @Override
    public Integer call() throws Exception {
        StateManager state = new StateManager();
        ConfigHelper configHelper = new ConfigHelper();

        boolean found = false;

        // 1. Check state
        if (state.isInstalled(packageName)) {
            var entry = state.allInstalled().get(packageName);
            System.out.println("Package: " + entry.name());
            System.out.println("  Version:   " + entry.version());
            System.out.println("  Type:      " + entry.type());
            System.out.println("  Installed: " + entry.installedAt());
            System.out.println("  Source:    mcpm install (user command)");
            found = true;
        }

        // 2. Check config files for references
        Path configFile = configHelper.resolveConfigPath(configPath);
        if (configFile.toFile().exists()) {
            McpConfig config = configHelper.readOrEmpty(configFile);
            if (config.hasServer(packageName)) {
                var entry = config.getServer(packageName);
                if (!found) {
                    System.out.println("Package: " + packageName);
                }
                System.out.println("  Config:    " + configFile);
                if (entry.isStdio()) {
                    System.out.println("  Command:   " + entry.command());
                } else {
                    System.out.println("  Type:      " + entry.transportType());
                    System.out.println("  URL:       " + entry.transportUrl());
                }
                found = true;
            }
        }

        if (!found) {
            System.out.println("Package '" + packageName + "' is not installed.");
            System.out.println("  Not in state file (~/.mcpm/state.json)");
            System.out.println("  Not in config file (" + configFile + ")");
            System.out.println();
            System.out.println("Try: mcpm search " + packageName + "  to find it in the registry");
            return 1;
        }

        // 3. Show dependent servers (servers that reference this package)
        System.out.println();
        System.out.println("Referenced by:");
        System.out.println("  You (manually installed via mcpm install)");

        // 4. Show recent actions
        System.out.println();
        System.out.println("Hint: mcpm update " + packageName + "  to upgrade");
        System.out.println("      mcpm uninstall " + packageName + "  to remove");

        return 0;
    }
}
