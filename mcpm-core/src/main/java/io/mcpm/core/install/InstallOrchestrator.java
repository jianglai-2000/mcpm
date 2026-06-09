package io.mcpm.core.install;

import io.mcpm.spi.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Central orchestrator for all install/uninstall operations.
 * <p>
 * Discovers {@link PackageHandler}, {@link LifecycleHook} implementations
 * via {@link ServiceLoader} and coordinates the full lifecycle.
 * <p>
 * This class is the main entry point for {@code mcpm-core} — the CLI
 * delegates to it, and any other consumer (GUI, CI script, etc.) can too.
 */
/**
 * Central orchestrator for all install/uninstall operations.
 * <p>
 * Discovers {@link PackageHandler} and {@link LifecycleHook} implementations
 * via {@link ServiceLoader} and coordinates the full lifecycle:
 * <ol>
 *   <li>Resolve version from package metadata</li>
 *   <li>Merge default env with user overrides</li>
 *   <li>Fire before-install hooks</li>
 *   <li>Delegate to the correct {@link PackageHandler}</li>
 *   <li>Fire after-install hooks</li>
 * </ol>
 * <p>
 * This class is thread-safe for concurrent reads after construction.
 */
public class InstallOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(InstallOrchestrator.class);

    private final Map<String, PackageHandler> handlerByType;
    private final List<LifecycleHook> hooks;

    public InstallOrchestrator() {
        this(ServiceLoader.load(PackageHandler.class),
             ServiceLoader.load(LifecycleHook.class));
    }

    /** Constructor that accepts explicit iterables (for testing or custom wiring). */
    public InstallOrchestrator(Iterable<PackageHandler> handlers,
                                Iterable<LifecycleHook> hooks) {
        this.handlerByType = new HashMap<>();
        handlers.forEach(h -> {
            if (handlerByType.put(h.supportedType(), h) != null) {
                log.warn("Duplicate PackageHandler for type '{}' — later registration wins", h.supportedType());
            }
        });
        this.hooks = new ArrayList<>();
        hooks.forEach(this.hooks::add);
    }

    /**
     * Install a package previously resolved from a registry.
     *
     * @param pkg       the resolved package metadata
     * @param version   specific version to install (null for latest)
     * @param userEnv   user-specified env variable overrides (optional)
     * @return the install result
     * @throws IllegalArgumentException if no handler is registered for the package type
     */
    public PackageHandler.InstallResult install(McpmPackage pkg, String version,
                                                 Map<String, String> userEnv) {
        String ver = version != null ? version : pkg.latestVersion();
        McpmPackage.VersionEntry versionEntry = pkg.versions().get(ver);
        if (versionEntry == null) {
            return PackageHandler.InstallResult.failure(
                    "Version '" + ver + "' not found for package '" + pkg.name() + "'");
        }

        PackageHandler handler = handlerByType.get(pkg.type());
        if (handler == null) {
            return PackageHandler.InstallResult.failure(
                    "No handler registered for package type '" + pkg.type() + "'");
        }

        // Merge default env with user overrides
        Map<String, String> mergedEnv = new HashMap<>();
        if (versionEntry.defaultEnv() != null) {
            mergedEnv.putAll(versionEntry.defaultEnv());
        }
        if (userEnv != null) {
            mergedEnv.putAll(userEnv);
        }

        PackageHandler.InstallRequest request = new PackageHandler.InstallRequest(
                pkg.name(), ver,
                mergedEnv, versionEntry.handlerArgs());

        var hookContext = new LifecycleHook.HookContext(pkg.name(), ver);

        // before-install hooks
        for (LifecycleHook hook : hooks) {
            hook.beforeInstall(request, hookContext);
        }

        PackageHandler.InstallResult result = handler.install(request);

        // after-install hooks
        for (LifecycleHook hook : hooks) {
            hook.afterInstall(result, hookContext);
        }

        return result;
    }

    /**
     * Uninstall a named package.
     *
     * @param pkg       the package metadata
     * @param version   the version to uninstall
     * @return the uninstall result
     * @throws IllegalArgumentException if no handler is registered
     */
    public PackageHandler.UninstallResult uninstall(McpmPackage pkg, String version) {
        PackageHandler handler = handlerByType.get(pkg.type());
        if (handler == null) {
            return PackageHandler.UninstallResult.failure(
                    "No handler registered for package type '" + pkg.type() + "'");
        }

        PackageHandler.UninstallRequest request = new PackageHandler.UninstallRequest(
                pkg.name(), version);

        var hookContext = new LifecycleHook.HookContext(pkg.name(), version);

        for (LifecycleHook hook : hooks) {
            hook.beforeUninstall(request, hookContext);
        }

        PackageHandler.UninstallResult result = handler.uninstall(request);

        for (LifecycleHook hook : hooks) {
            hook.afterUninstall(result, hookContext);
        }

        return result;
    }

    /** Get registered handler by type. */
    public Optional<PackageHandler> handlerFor(String type) {
        return Optional.ofNullable(handlerByType.get(type));
    }

    /** All registered handlers. */
    public Map<String, PackageHandler> handlers() {
        return Collections.unmodifiableMap(handlerByType);
    }
}
