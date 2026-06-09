package io.mcpm.handler.docker;

import io.mcpm.spi.PackageHandler;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link DockerPackageHandler}.
 */
class DockerPackageHandlerTest {

    private final DockerPackageHandler handler = new DockerPackageHandler();

    @Test
    void supportedType() {
        assertThat(handler.supportedType()).isEqualTo("docker");
    }

    @Test
    void installGeneratesDockerRunCommand() {
        var req = new PackageHandler.InstallRequest("mcp-postgres", "1.0", Map.of(), Map.of());
        var result = handler.install(req);

        assertThat(result.success()).isTrue();
        assertThat(result.entryCommand()).isEqualTo("docker");
        assertThat(result.entryArgs()).startsWith("run", "-i", "--rm");
        assertThat(result.entryArgs()).endsWith("mcp-postgres:latest");
    }

    @Test
    void installUsesCustomImage() {
        var req = new PackageHandler.InstallRequest("mcp-postgres", "1.0", Map.of(),
                Map.of("image", "my-registry/mcp/postgres:2.0"));
        var result = handler.install(req);

        assertThat(result.entryArgs()).endsWith("my-registry/mcp/postgres:2.0");
    }

    @Test
    void installAddsEnvAsFlags() {
        var req = new PackageHandler.InstallRequest("pg", "1.0",
                Map.of("POSTGRES_PASSWORD", "secret"), Map.of());
        var result = handler.install(req);

        assertThat(result.entryArgs()).contains("-e", "POSTGRES_PASSWORD=secret");
    }

    @Test
    void installAddsPortsAndVolumes() {
        var req = new PackageHandler.InstallRequest("pg", "1.0", Map.of(),
                Map.of("ports", List.of("5432:5432"), "volumes", List.of("data:/var/lib/pg")));
        var result = handler.install(req);

        assertThat(result.entryArgs()).contains("-p", "5432:5432");
        assertThat(result.entryArgs()).contains("-v", "data:/var/lib/pg");
    }

    @Test
    void installWithExtraArgs() {
        var req = new PackageHandler.InstallRequest("pg", "1.0", Map.of(),
                Map.of("image", "pg:latest", "read-only", "true"));
        var result = handler.install(req);

        assertThat(result.entryArgs()).contains("--read-only", "true");
    }

    @Test
    void uninstallReturnsSuccess() {
        var result = handler.uninstall(new PackageHandler.UninstallRequest("pg", "1.0"));
        assertThat(result.success()).isTrue();
        assertThat(result.message()).contains("pg");
    }
}
