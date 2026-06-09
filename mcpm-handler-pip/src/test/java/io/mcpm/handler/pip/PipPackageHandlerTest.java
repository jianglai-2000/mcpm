package io.mcpm.handler.pip;

import io.mcpm.spi.PackageHandler;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link PipPackageHandler}.
 */
class PipPackageHandlerTest {

    private final PipPackageHandler handler = new PipPackageHandler();

    @Test
    void supportedType() {
        assertThat(handler.supportedType()).isEqualTo("pip");
    }

    @Test
    void installGeneratesPythonModuleFromPackageName() {
        var req = new PackageHandler.InstallRequest("mcp-server-ipinfo", "0.1.2", Map.of(), Map.of());
        var result = handler.install(req);

        assertThat(result.success()).isTrue();
        assertThat(result.entryCommand()).isEqualTo("python");
        assertThat(result.entryArgs()).contains("-m", "mcp_server_ipinfo");
    }

    @Test
    void installUsesExplicitModule() {
        var req = new PackageHandler.InstallRequest("custom-pkg", "1.0", Map.of(),
                Map.of("module", "my_custom_module"));
        var result = handler.install(req);

        assertThat(result.entryArgs()).contains("-m", "my_custom_module");
    }

    @Test
    void installPreservesEnv() {
        var req = new PackageHandler.InstallRequest("pkg", "1.0", Map.of("KEY", "val"), Map.of());
        var result = handler.install(req);

        assertThat(result.entryEnv()).containsEntry("KEY", "val");
    }

    @Test
    void uninstallReturnsSuccess() {
        var req = new PackageHandler.UninstallRequest("pkg", "1.0");
        var result = handler.uninstall(req);

        assertThat(result.success()).isTrue();
    }
}
