package io.mcpm.cli;

import io.mcpm.core.config.ConfigHelper;
import io.mcpm.core.state.StateManager;
import io.mcpm.spi.McpConfig;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "backup",
        description = "Backup or restore MCP configuration and state.",
        mixinStandardHelpOptions = true,
        subcommands = {
                BackupCommand.BackupCreate.class,
                BackupCommand.BackupRestore.class,
                BackupCommand.BackupList.class
        }
)
public class BackupCommand implements Runnable {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(System.out);
    }

    static final Path BACKUP_DIR = Path.of(System.getProperty("user.home"), ".mcpm", "backups");
    static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    @CommandLine.Command(name = "create", description = "Create a backup of config and state.")
    static class BackupCreate implements Callable<Integer> {
        @CommandLine.Option(names = {"-c", "--config"}, description = "Path to mcp.json (auto-detected)")
        String configPath;

        @Override
        public Integer call() throws Exception {
            Files.createDirectories(BACKUP_DIR);
            String ts = LocalDateTime.now().format(STAMP);
            Path backup = BACKUP_DIR.resolve("mcpm-backup-" + ts);
            Files.createDirectories(backup);

            // Backup config files
            ConfigHelper configHelper = new ConfigHelper();
            Path cfg = configHelper.resolveConfigPath(configPath);
            if (cfg.toFile().exists()) {
                Files.copy(cfg, backup.resolve(cfg.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                System.out.println("  ✓ Config: " + cfg.getFileName());
            }

            // Backup state
            Path stateFile = Path.of(System.getProperty("user.home"), ".mcpm", "state.json");
            if (stateFile.toFile().exists()) {
                Files.copy(stateFile, backup.resolve("state.json"), StandardCopyOption.REPLACE_EXISTING);
                System.out.println("  ✓ State:  state.json");
            }

            // Backup settings
            Path settingsFile = Path.of(System.getProperty("user.home"), ".mcpm", "config.json");
            if (settingsFile.toFile().exists()) {
                Files.copy(settingsFile, backup.resolve("config.json"), StandardCopyOption.REPLACE_EXISTING);
                System.out.println("  ✓ Settings: config.json");
            }

            System.out.println("\n✔ Backup created at: " + backup);
            return 0;
        }
    }

    @CommandLine.Command(name = "restore", description = "Restore from a backup.")
    static class BackupRestore implements Callable<Integer> {
        @CommandLine.Parameters(paramLabel = "BACKUP", description = "Backup directory or name (e.g. mcpm-backup-20260609-...)")
        String backupName;

        @Override
        public Integer call() throws Exception {
            Path backupDir = backupName.contains("/") || backupName.contains("\\")
                    ? Path.of(backupName)
                    : BACKUP_DIR.resolve(backupName);

            if (!Files.isDirectory(backupDir)) {
                System.err.println("Backup not found: " + backupDir);
                return 1;
            }

            // Restore each file
            try (var files = Files.list(backupDir)) {
                files.filter(Files::isRegularFile).forEach(f -> {
                    try {
                        Path target = switch (f.getFileName().toString()) {
                            case "state.json" -> Path.of(System.getProperty("user.home"), ".mcpm", "state.json");
                            case "config.json" -> Path.of(System.getProperty("user.home"), ".mcpm", "config.json");
                            default -> Path.of(".").resolve(f.getFileName()); // mcp.json etc.
                        };
                        Files.createDirectories(target.getParent());
                        Files.copy(f, target, StandardCopyOption.REPLACE_EXISTING);
                        System.out.println("  ✓ Restored: " + f.getFileName() + " → " + target);
                    } catch (IOException e) {
                        System.err.println("  ✘ Failed: " + f.getFileName() + " — " + e.getMessage());
                    }
                });
            }

            System.out.println("\n✔ Restore complete.");
            return 0;
        }
    }

    @CommandLine.Command(name = "list", description = "List available backups.", aliases = {"ls"})
    static class BackupList implements Callable<Integer> {
        @Override
        public Integer call() throws Exception {
            if (!Files.isDirectory(BACKUP_DIR)) {
                System.out.println("No backups found.");
                return 0;
            }

            System.out.println("Backups:\n");
            try (var dirs = Files.list(BACKUP_DIR)) {
                dirs.filter(Files::isDirectory)
                        .sorted()
                        .forEach(d -> {
                            long count = 0;
                            try (var files = Files.list(d)) { count = files.count(); } catch (IOException ignored) {}
                            System.out.println("  " + d.getFileName() + "  (" + count + " files)");
                        });
            }
            return 0;
        }
    }
}
