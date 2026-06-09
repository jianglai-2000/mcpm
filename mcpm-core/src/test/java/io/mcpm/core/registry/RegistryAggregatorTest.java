package io.mcpm.core.registry;

import io.mcpm.spi.McpmPackage;
import io.mcpm.spi.RegistryProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistryAggregatorTest {

    private final McpmPackage pkgA = new McpmPackage("pkg-a", "desc A", "npx", "1.0",
            Map.of("1.0", versionEntry()), null, null, null, null);
    private final McpmPackage pkgB = new McpmPackage("pkg-b", "desc B", "npm", "2.0",
            Map.of("2.0", versionEntry()), null, null, null, null);
    private final McpmPackage pkgC = new McpmPackage("pkg-c", "desc C", "jar", "3.0",
            Map.of("3.0", versionEntry()), null, null, null, null);

    private static McpmPackage.VersionEntry versionEntry() {
        return new McpmPackage.VersionEntry("1.0", null, null, null, null);
    }

    @Test
    void emptyProvidersReturnsEmpty() {
        var aggregator = new RegistryAggregator(List.of());
        assertThat(aggregator.search("anything")).isEmpty();
        assertThat(aggregator.getPackage("x")).isEmpty();
    }

    @Test
    void searchMergesResultsFromMultipleProviders() {
        var provider1 = mock(RegistryProvider.class);
        var provider2 = mock(RegistryProvider.class);

        when(provider1.search("query")).thenReturn(List.of(pkgA, pkgB));
        when(provider2.search("query")).thenReturn(List.of(pkgC));

        var aggregator = new RegistryAggregator(List.of(provider1, provider2));
        var results = aggregator.search("query");

        assertThat(results).hasSize(3)
                .extracting(McpmPackage::name)
                .containsExactly("pkg-a", "pkg-b", "pkg-c");
    }

    @Test
    void searchDeduplicatesByFirstProviderWins() {
        var provider1 = mock(RegistryProvider.class);
        var provider2 = mock(RegistryProvider.class);

        when(provider1.search("q")).thenReturn(List.of(pkgA));
        when(provider2.search("q")).thenReturn(List.of(pkgA, pkgB));

        var aggregator = new RegistryAggregator(List.of(provider1, provider2));
        var results = aggregator.search("q");

        assertThat(results).hasSize(2)
                .extracting(McpmPackage::name)
                .containsExactly("pkg-a", "pkg-b");
    }

    @Test
    void getPackageReturnsFirstNonNullResult() {
        var provider1 = mock(RegistryProvider.class);
        var provider2 = mock(RegistryProvider.class);

        when(provider1.getPackage("pkg-a")).thenReturn(null);
        when(provider2.getPackage("pkg-a")).thenReturn(pkgA);

        var aggregator = new RegistryAggregator(List.of(provider1, provider2));
        var result = aggregator.getPackage("pkg-a");

        assertThat(result).contains(pkgA);
    }

    @Test
    void getPackageReturnsEmptyWhenNoProviderHasIt() {
        var provider1 = mock(RegistryProvider.class);
        when(provider1.getPackage("missing")).thenReturn(null);

        var aggregator = new RegistryAggregator(List.of(provider1));
        var result = aggregator.getPackage("missing");

        assertThat(result).isEmpty();
    }

    @Test
    void providerFailureDoesNotCrashSearch() {
        var provider1 = mock(RegistryProvider.class);
        var provider2 = mock(RegistryProvider.class);

        when(provider1.search("q")).thenThrow(new RuntimeException("Provider 1 down"));
        when(provider2.search("q")).thenReturn(List.of(pkgA));

        var aggregator = new RegistryAggregator(List.of(provider1, provider2));
        var results = aggregator.search("q");

        assertThat(results).hasSize(1).extracting(McpmPackage::name).containsExactly("pkg-a");
    }

    @Test
    void providerFailureDoesNotCrashGetPackage() {
        var provider1 = mock(RegistryProvider.class);
        var provider2 = mock(RegistryProvider.class);

        when(provider1.getPackage("pkg-a")).thenThrow(new RuntimeException("boom"));
        when(provider2.getPackage("pkg-a")).thenReturn(pkgA);

        var aggregator = new RegistryAggregator(List.of(provider1, provider2));
        var result = aggregator.getPackage("pkg-a");

        assertThat(result).contains(pkgA);
    }

    @Test
    void providerFailureDoesNotCrashRefresh() {
        var provider1 = mock(RegistryProvider.class);
        var provider2 = mock(RegistryProvider.class);

        doThrow(new RuntimeException("bad")).when(provider1).refresh();
        doNothing().when(provider2).refresh();

        var aggregator = new RegistryAggregator(List.of(provider1, provider2));
        // should not throw
        aggregator.refreshAll();

        verify(provider1).refresh();
        verify(provider2).refresh();
    }

    @Test
    void providersListIsUnmodifiable() {
        var provider = mock(RegistryProvider.class);
        var aggregator = new RegistryAggregator(List.of(provider));

        assertThatThrownBy(() -> aggregator.providers().add(mock(RegistryProvider.class)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
