package io.mcpm.cli;

import io.mcpm.core.config.ConfigHelper;
import io.mcpm.spi.McpConfig;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "init",
        description = "Create an empty MCP configuration file in the current directory.",
        mixinStandardHelpOptions = true
)
public class InitCommand implements Callable<Integer> {

    @CommandLine.Option(names = {"-f", "--format"}, description = "Config format: mcp.json (default) or claude_desktop_config.json")
    String format;

    @CommandLine.Option(names = {"-y", "--overwrite"}, description = "Overwrite existing file without confirmation")
    boolean overwrite;

    @CommandLine.Option(names = {"-p", "--path"}, description = "Target directory (default: current directory)")
    String targetPath;

    @CommandLine.Option(names = {"-e", "--example"}, description = "Include an example server entry")
    boolean withExample;

    @Override
    public Integer call() throws Exception {
        // Determine file name
        String fileName;
        if ("claude_desktop_config.json".equals(format)) {
            fileName = "claude_desktop_config.json";
        } else {
            fileName = "mcp.json";
        }

        // Determine target path
        Path targetDir;
        if (targetPath != null) {
            targetDir = Path.of(targetPath);
            if (!Files.isDirectory(targetDir)) {
                Files.createDirectories(targetDir);
            }
        } else {
            targetDir = Path.of(".").toAbsolutePath().normalize();
        }

        Path configFile = targetDir.resolve(fileName).toAbsolutePath().normalize();

        // Check if exists
        if (Files.exists(configFile) && !overwrite) {
            System.err.println("✘ " + configFile.getFileName() + " already exists at " + configFile.getParent());
            System.err.println("  Use --overwrite to replace it.");
            return 1;
        }

        // Build config content
        McpConfig config = new McpConfig();
        if (withExample) {
            config.addServer("_example", new McpConfig.ServerEntry(
                    "npx",
                    java.util.List.of("-y", "@org/my-mcp-server"),
                    java.util.Map.of("API_KEY", "your-key-here")));
        }

        // Write
        ConfigHelper configHelper = new ConfigHelper();
        configHelper.writeWithBackup(configFile, config);

        // Summary
        System.out.println("✔ Created " + fileName + " at " + configFile);

        if (withExample) {
            System.out.println();
            System.out.println("  Includes an example entry 'hello-mcp'. Edit or remove it,");
            System.out.println("  then run 'mcpm install <package>' to add real servers.");
        } else {
            System.out.println();
            System.out.println("  Next step: run 'mcpm install <package>' to add an MCP server.");
            System.out.println("  Or:        run 'mcpm search <query>' to find available servers.");
        }

        return 0;
    }
}
