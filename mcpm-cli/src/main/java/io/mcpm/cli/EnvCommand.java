package io.mcpm.cli;

import io.mcpm.core.config.ConfigHelper;
import io.mcpm.core.settings.SettingsManager;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

@CommandLine.Command(
        name = "env",
        description = "Manage multiple MCP environments (dev, staging, production).",
        mixinStandardHelpOptions = true,
        subcommands = {
                EnvCommand.List.class,
                EnvCommand.Create.class,
                EnvCommand.Switch.class,
                EnvCommand.Delete.class
        }
)
public class EnvCommand implements Runnable {

    static final Path ENV_DIR = Path.of(System.getProperty("user.home"), ".mcpm", "envs");

    @CommandLine.Spec CommandLine.Model.CommandSpec spec;

    @Override
    public void run() { spec.commandLine().usage(System.out); }

    static Path envFile(String name) {
        return ENV_DIR.resolve("mcp-" + name + ".json");
    }

    static String currentEnv() {
        return new SettingsManager().get("env", "default");
    }

    @CommandLine.Command(name = "list", aliases = {"ls"}, description = "List all environments.")
    static class List implements Callable<Integer> {
        @Override
        public Integer call() throws IOException {
            String current = currentEnv();
            System.out.println("Environments:\n");
            if (Files.exists(ENV_DIR)) {
                try (Stream<Path> files = Files.list(ENV_DIR)) {
                    files.filter(f -> f.getFileName().toString().startsWith("mcp-"))
                            .forEach(f -> {
                                String name = f.getFileName().toString()
                                        .replace("mcp-", "").replace(".json", "");
                                String mark = name.equals(current) ? " ← active" : "";
                                System.out.println("  " + name + mark);
                            });
                }
            } else {
                System.out.println("  default (no custom envs created)");
            }
            return 0;
        }
    }

    @CommandLine.Command(name = "create", description = "Create a new environment.")
    static class Create implements Callable<Integer> {
        @CommandLine.Parameters(paramLabel = "NAME", description = "Environment name (e.g. staging)")
        String name;

        @Override
        public Integer call() throws IOException {
            Files.createDirectories(ENV_DIR);
            Path file = envFile(name);
            if (Files.exists(file)) {
                System.err.println("Environment '" + name + "' already exists.");
                return 1;
            }
            Files.writeString(file, "{\"mcpServers\":{}}");
            System.out.println("✔ Created environment '" + name + "'.");
            System.out.println("  Config: " + file);
            System.out.println("  Run 'mcpm env switch " + name + "' to activate.");
            return 0;
        }
    }

    @CommandLine.Command(name = "switch", description = "Switch active environment.")
    static class Switch implements Callable<Integer> {
        @CommandLine.Parameters(paramLabel = "NAME", description = "Environment name")
        String name;

        @Override
        public Integer call() {
            Path file = envFile(name);
            if (!name.equals("default") && !Files.exists(file)) {
                System.err.println("Environment '" + name + "' not found.");
                System.err.println("  Create it first: mcpm env create " + name);
                return 1;
            }
            new SettingsManager().set("env", name);
            System.out.println("✔ Switched to environment: " + name);
            return 0;
        }
    }

    @CommandLine.Command(name = "delete", aliases = {"rm"}, description = "Delete an environment.")
    static class Delete implements Callable<Integer> {
        @CommandLine.Parameters(paramLabel = "NAME", description = "Environment name")
        String name;

        @Override
        public Integer call() throws IOException {
            if ("default".equals(name)) {
                System.err.println("Cannot delete 'default' environment.");
                return 1;
            }
            Path file = envFile(name);
            if (!Files.exists(file)) {
                System.err.println("Environment '" + name + "' not found.");
                return 1;
            }
            Files.delete(file);
            if (currentEnv().equals(name)) {
                new SettingsManager().set("env", "default");
                System.out.println("✔ Deleted '" + name + "'. Switched back to 'default'.");
            } else {
                System.out.println("✔ Deleted environment '" + name + "'.");
            }
            return 0;
        }
    }
}
