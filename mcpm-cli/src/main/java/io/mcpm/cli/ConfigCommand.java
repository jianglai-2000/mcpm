package io.mcpm.cli;

import io.mcpm.core.settings.SettingsManager;
import picocli.CommandLine;

import java.util.Map;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "config",
        description = "View and modify mcpm global settings.",
        mixinStandardHelpOptions = true,
        subcommands = {
                ConfigCommand.GetCommand.class,
                ConfigCommand.SetCommand.class,
                ConfigCommand.ListCommand.class,
                ConfigCommand.UnsetCommand.class
        }
)
public class ConfigCommand implements Runnable {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(System.out);
    }

    // ---- get ----

    @CommandLine.Command(
            name = "get",
            description = "Get a setting value.",
            mixinStandardHelpOptions = true
    )
    static class GetCommand implements Callable<Integer> {
        @CommandLine.Parameters(paramLabel = "KEY", description = "Setting key (e.g. registry.url)")
        String key;

        @Override
        public Integer call() {
            SettingsManager settings = new SettingsManager();
            String value = settings.get(key);
            if (value == null) {
                System.err.println("Setting not found: " + key);
                return 1;
            }
            System.out.println(value);
            return 0;
        }
    }

    // ---- set ----

    @CommandLine.Command(
            name = "set",
            description = "Set a setting value.",
            mixinStandardHelpOptions = true
    )
    static class SetCommand implements Callable<Integer> {
        @CommandLine.Parameters(paramLabel = "KEY", description = "Setting key")
        String key;

        @CommandLine.Parameters(paramLabel = "VALUE", description = "Setting value")
        String value;

        @Override
        public Integer call() {
            new SettingsManager().set(key, value);
            System.out.println("✓ " + key + " = " + value);
            return 0;
        }
    }

    // ---- list ----

    @CommandLine.Command(
            name = "list",
            description = "List all settings.",
            aliases = {"ls", "show"},
            mixinStandardHelpOptions = true
    )
    static class ListCommand implements Callable<Integer> {
        @Override
        public Integer call() {
            SettingsManager settings = new SettingsManager();
            Map<String, String> all = settings.all();

            if (all.isEmpty()) {
                System.out.println("No settings configured.");
                return 0;
            }

            System.out.println("Settings:\n");
            for (Map.Entry<String, String> entry : all.entrySet()) {
                if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                    System.out.println("  " + entry.getKey() + " = " + entry.getValue());
                } else {
                    System.out.println("  " + entry.getKey() + " = (not set)");
                }
            }
            return 0;
        }
    }

    // ---- unset ----

    @CommandLine.Command(
            name = "unset",
            description = "Remove a setting.",
            aliases = {"rm", "delete"},
            mixinStandardHelpOptions = true
    )
    static class UnsetCommand implements Callable<Integer> {
        @CommandLine.Parameters(paramLabel = "KEY", description = "Setting key to remove")
        String key;

        @Override
        public Integer call() {
            new SettingsManager().unset(key);
            System.out.println("✓ Removed " + key);
            return 0;
        }
    }
}
