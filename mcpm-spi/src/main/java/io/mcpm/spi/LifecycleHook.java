package io.mcpm.spi;

/**
 * Lifecycle hooks that fire before and after install/uninstall operations.
 * <p>
 * Useful for cross-cutting concerns: analytics, security scanning,
 * telemetry, version locking, audit logging.
 * <p>
 * Discovered via {@link java.util.ServiceLoader} from
 * {@code META-INF/services/io.mcpm.spi.LifecycleHook}.
 */
public interface LifecycleHook {

    /**
     * Called before any install operation begins.
     *
     * @param request  the install request about to be processed
     * @param context  contextual information (package metadata, etc.)
     */
    default void beforeInstall(PackageHandler.InstallRequest request, HookContext context) {}

    /**
     * Called after an install operation completes.
     */
    default void afterInstall(PackageHandler.InstallResult result, HookContext context) {}

    /**
     * Called before any uninstall operation begins.
     */
    default void beforeUninstall(PackageHandler.UninstallRequest request, HookContext context) {}

    /**
     * Called after an uninstall operation completes.
     */
    default void afterUninstall(PackageHandler.UninstallResult result, HookContext context) {}

    /**
     * Context provided to hooks at runtime.
     */
    final class HookContext {
        private final String packageName;
        private final String version;

        public HookContext(String packageName, String version) {
            this.packageName = packageName;
            this.version = version;
        }

        public String packageName() { return packageName; }
        public String version() { return version; }
    }
}
