package io.mcpm.spi;

import java.util.List;
import java.util.Map;

/**
 * Handles a specific type of MCP server package (npx, pip, jar, binary, docker, etc.).
 * <p>
 * Each implementation is discovered via {@link java.util.ServiceLoader}.
 * Register by placing a file at
 * {@code META-INF/services/io.mcpm.spi.PackageHandler}
 * containing the fully qualified class name of your implementation.
 */
public interface PackageHandler {

    /**
     * Unique type identifier, e.g. {@code "npx"}, {@code "jar"}, {@code "binary"}, {@code "pip"}, {@code "docker"}.
     * Used to match packages in the registry index to their handler.
     */
    String supportedType();

    /**
     * Install the package. Called after the package artifact has been
     * resolved (e.g. downloaded, or confirmed available on PATH).
     *
     * @param request installation details
     * @return result describing what happened
     */
    InstallResult install(InstallRequest request);

    /**
     * Uninstall the package. The orchestrator guarantees this is only called
     * for packages whose {@link #supportedType()} matches.
     */
    UninstallResult uninstall(UninstallRequest request);

    // ---- data classes ----

    /**
     * Details needed to install a package with this handler.
     */
    final class InstallRequest {
        private final String packageName;
        private final String version;
        private final Map<String, String> env;
        private final Map<String, Object> args;

        public InstallRequest(String packageName, String version,
                              Map<String, String> env, Map<String, Object> args) {
            this.packageName = packageName;
            this.version = version;
            this.env = env == null ? Map.of() : Map.copyOf(env);
            this.args = args == null ? Map.of() : Map.copyOf(args);
        }

        public String packageName() { return packageName; }
        public String version() { return version; }
        public Map<String, String> env() { return env; }
        public Map<String, Object> args() { return args; }
    }

    /**
     * Result of an install operation.
     */
    final class InstallResult {
        private final boolean success;
        private final String message;
        private final String entryCommand;       // the "command" value for mcp.json
        private final List<String> entryArgs;    // the "args" value for mcp.json
        private final Map<String, String> entryEnv;    // the "env" value for mcp.json (merged)

        public InstallResult(boolean success, String message,
                             String entryCommand, List<String> entryArgs,
                             Map<String, String> entryEnv) {
            this.success = success;
            this.message = message;
            this.entryCommand = entryCommand;
            this.entryArgs = entryArgs == null ? List.of() : List.copyOf(entryArgs);
            this.entryEnv = entryEnv == null ? Map.of() : Map.copyOf(entryEnv);
        }

        public boolean success() { return success; }
        public String message() { return message; }
        public String entryCommand() { return entryCommand; }
        public List<String> entryArgs() { return entryArgs; }
        public Map<String, String> entryEnv() { return entryEnv; }

        public static InstallResult failure(String message) {
            return new InstallResult(false, message, null, List.of(), Map.of());
        }

        public static InstallResult success(String message, String command,
                                            List<String> args, Map<String, String> env) {
            return new InstallResult(true, message, command, args, env);
        }
    }

    /**
     * Details needed to uninstall a package. Enough for the handler to clean up.
     */
    final class UninstallRequest {
        private final String packageName;
        private final String version;

        public UninstallRequest(String packageName, String version) {
            this.packageName = packageName;
            this.version = version;
        }

        public String packageName() { return packageName; }
        public String version() { return version; }
    }

    /**
     * Result of an uninstall operation.
     */
    final class UninstallResult {
        private final boolean success;
        private final String message;

        public UninstallResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean success() { return success; }
        public String message() { return message; }

        public static UninstallResult failure(String message) {
            return new UninstallResult(false, message);
        }

        public static UninstallResult success(String message) {
            return new UninstallResult(true, message);
        }
    }
}
