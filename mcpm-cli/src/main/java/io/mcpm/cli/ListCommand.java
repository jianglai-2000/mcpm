package io.mcpm.cli;

import io.mcpm.core.config.ConfigHelper;
import io.mcpm.spi.McpConfig;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "list",
        description = "List installed MCP servers from the configuration file.",
        mixinStandardHelpOptions = true
)
public class ListCommand implements Callable<Integer> {

    @CommandLine.Option(names = {"-c", "--config"}, description = "Path to mcp.json or directory (auto-detected)")
    String configPath;

    @Override
    public Integer call() throws Exception {
        ConfigHelper configHelper = new ConfigHelper();
        Path configFile = configHelper.resolveConfigPath(configPath);

        if (!configFile.toFile().exists()) {
            System.out.println("No MCP config found at " + configFile.toAbsolutePath());
            return 0;
        }

        McpConfig config = configHelper.readOrEmpty(configFile);

        if (config.size() == 0) {
            System.out.println("No MCP servers configured in " + configFile.toAbsolutePath());
            return 0;
        }

        System.out.println("Installed MCP servers (" + configFile.toAbsolutePath() + "):\n");

        for (Map.Entry<String, McpConfig.ServerEntry> entry : config.servers().entrySet()) {
            String name = entry.getKey();
            McpConfig.ServerEntry server = entry.getValue();

            System.out.println("  " + name + ":");
            if (server.isStdio()) {
                System.out.println("    command: " + server.command());
                if (!server.args().isEmpty()) {
                    System.out.println("    args: " + String.join(" ", server.args()));
                }
            } else {
                System.out.println("    type: " + server.transportType());
                System.out.println("    url: " + server.transportUrl());
            }
            if (!server.env().isEmpty()) {
                System.out.println("    env: " + server.env());
            }
            System.out.println();
        }

        System.out.println("Total: " + config.size() + " server(s)");
        return 0;
    }
}
