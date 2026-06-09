package io.mcpm.core.registry;

import io.mcpm.spi.McpmPackage;
import io.mcpm.spi.RegistryProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Aggregates results from all discovered {@link RegistryProvider} implementations.
 * <p>
 * Queries each registered provider in order and merges the results.
 * For conflicts (same package name from multiple providers), the first provider wins.
 */
public class RegistryAggregator {

    private static final Logger log = LoggerFactory.getLogger(RegistryAggregator.class);

    private final List<RegistryProvider> providers;

    public RegistryAggregator() {
        this(ServiceLoader.load(RegistryProvider.class));
    }

    public RegistryAggregator(Iterable<RegistryProvider> providers) {
        this.providers = new ArrayList<>();
        providers.forEach(this.providers::add);
    }

    /**
     * Search all registries for packages matching the query.
     * Results are deduplicated by package name (first provider wins).
     */
    public List<McpmPackage> search(String query) {
        Map<String, McpmPackage> merged = new LinkedHashMap<>();
        for (RegistryProvider provider : providers) {
            try {
                List<McpmPackage> results = provider.search(query);
                for (McpmPackage pkg : results) {
                    merged.putIfAbsent(pkg.name(), pkg);
                }
            } catch (Exception e) {
                log.warn("Registry provider '{}' failed search: {}", provider.name(), e.getMessage());
            }
        }
        return List.copyOf(merged.values());
    }

    /**
     * Get a specific package by name. Queries registries in order and returns
     * the first non-null result.
     */
    public Optional<McpmPackage> getPackage(String name) {
        for (RegistryProvider provider : providers) {
            try {
                McpmPackage pkg = provider.getPackage(name);
                if (pkg != null) {
                    return Optional.of(pkg);
                }
            } catch (Exception e) {
                log.warn("Registry provider '{}' failed getPackage({}): {}",
                        provider.name(), name, e.getMessage());
            }
        }
        return Optional.empty();
    }

    /** Refresh all registry caches. */
    public void refreshAll() {
        for (RegistryProvider provider : providers) {
            try {
                provider.refresh();
            } catch (Exception e) {
                log.warn("Registry provider '{}' failed refresh: {}", provider.name(), e.getMessage());
            }
        }
    }

    /** All registered providers. */
    public List<RegistryProvider> providers() {
        return List.copyOf(providers);
    }
}
