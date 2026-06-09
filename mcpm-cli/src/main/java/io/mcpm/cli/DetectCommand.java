package io.mcpm.cli;

import io.mcpm.core.config.ConfigHelper;
import picocli.CommandLine;

import java.util.List;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "detect",
        description = "@|cyan Detect|@ MCP configuration files on this machine.",
        mixinStandardHelpOptions = true
)
public class DetectCommand implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        ConfigHelper configHelper = new ConfigHelper();
        List<ConfigHelper.DiscoveredConfig> configs = configHelper.discoverConfigs();

        if (configs.isEmpty()) {
            System.out.println("No existing MCP configuration files found.");
            System.out.println();
            System.out.println("Default locations checked:");
            System.out.println("  ./mcp.json");
            System.out.println("  ./claude_desktop_config.json");
            System.out.println("  ./.cursor/mcp.json");
            System.out.println("  ~/.cursor/mcp.json");
            System.out.println("  ~/AppData/Roaming/Claude/claude_desktop_config.json (Windows)");
            System.out.println("  ~/Library/Application Support/Claude/claude_desktop_config.json (macOS)");
            System.out.println("  ~/.config/Claude/claude_desktop_config.json (Linux)");
            System.out.println();
            System.out.println("Run 'mcpm install <package>' to create a new mcp.json automatically.");
            return 0;
        }

        System.out.println("Detected MCP configuration files:\n");
        for (ConfigHelper.DiscoveredConfig dc : configs) {
            System.out.println(dc);
        }
        System.out.println();
        System.out.println("Auto-selected: " + configHelper.resolveConfigPath(null));

        return 0;
    }
}
