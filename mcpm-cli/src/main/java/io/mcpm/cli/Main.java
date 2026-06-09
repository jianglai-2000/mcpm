package io.mcpm.cli;

import picocli.CommandLine;

@CommandLine.Command(
        name = "mcpm",
        subcommands = {
                SearchCommand.class,
                InstallCommand.class,
                UninstallCommand.class,
                ListCommand.class,
                InfoCommand.class,
                DetectCommand.class,
                UpdateCommand.class,
                InitCommand.class,
                PublishCommand.class,
                CompletionCommand.class,
                ConfigCommand.class,
                CommandLine.HelpCommand.class
        },
        description = "MCP Package Manager — discover, install, and manage MCP servers.",
        mixinStandardHelpOptions = true,
        version = "@|bold mcpm 0.1.0|@",
        usageHelpAutoWidth = true,
        footer = "@|cyan Report issues at https://github.com/mcpm/mcpm/issues|@"
)
public class Main implements Runnable {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public void run() {
        // No subcommand given → show usage
        spec.commandLine().usage(System.out);
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }
}
