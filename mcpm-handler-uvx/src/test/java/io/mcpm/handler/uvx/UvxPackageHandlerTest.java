package io.mcpm.handler.uvx;

import io.mcpm.spi.PackageHandler;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link UvxPackageHandler}.
 */
class UvxPackageHandlerTest {

    private final UvxPackageHandler handler = new UvxPackageHandler();

    @Test
    void supportedType() {
        assertThat(handler.supportedType()).isEqualTo("uvx");
    }

    @Test
    void installGeneratesUvxCommand() {
        var req = new PackageHandler.InstallRequest("mcp-server-duckdb", "0.3.0", Map.of(), Map.of());
        var result = handler.install(req);

        assertThat(result.success()).isTrue();
        assertThat(result.entryCommand()).isEqualTo("uvx");
        assertThat(result.entryArgs()).contains("mcp-server-duckdb");
    }

    @Test
    void installPassesExtraArgs() {
        var req = new PackageHandler.InstallRequest("pkg", "1.0", Map.of(),
                Map.of("option", "value"));
        var result = handler.install(req);

        assertThat(result.entryArgs()).contains("--option", "value");
    }

    @Test
    void installPreservesEnv() {
        var req = new PackageHandler.InstallRequest("pkg", "1.0", Map.of("K", "v"), Map.of());
        var result = handler.install(req);

        assertThat(result.entryEnv()).containsEntry("K", "v");
    }

    @Test
    void uninstallReturnsSuccess() {
        var result = handler.uninstall(new PackageHandler.UninstallRequest("pkg", "1.0"));
        assertThat(result.success()).isTrue();
    }
}
