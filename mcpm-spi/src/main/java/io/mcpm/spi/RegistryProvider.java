package io.mcpm.spi;

import java.util.List;

/**
 * Provides package metadata from a registry source.
 * <p>
 * The registry is the source of truth for what packages exist, their
 * types, versions, and download coordinates. Multiple providers can
 * be active — results are merged by {@code io.mcpm.core}.
 * <p>
 * Discovered via {@link java.util.ServiceLoader} from
 * {@code META-INF/services/io.mcpm.spi.RegistryProvider}.
 */
public interface RegistryProvider {

    /**
     * Short name identifying this registry, e.g. {@code "static"}, {@code "mcpm.io"}.
     */
    String name();

    /**
     * Search for packages matching the query.
     *
     * @return a list of matching packages (empty list if none found)
     */
    List<McpmPackage> search(String query);

    /**
     * Get a single package by its fully qualified name.
     *
     * @return the package, or {@code null} if not found
     */
    McpmPackage getPackage(String name);

    /**
     * Refresh cached registry data if applicable. No-op if the provider
     * has no cache.
     */
    default void refresh() {}
}
