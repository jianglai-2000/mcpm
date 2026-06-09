package io.mcpm.handler.binary;

import io.mcpm.spi.PackageHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link BinaryPackageHandler}.
 */
class BinaryPackageHandlerTest {

    @TempDir
    Path tempDir;

    @Test
    void supportedType() {
        var handler = new BinaryPackageHandler(tempDir);
        assertThat(handler.supportedType()).isEqualTo("binary");
    }

    @Test
    void installFailsWithoutDownloadUrl() {
        var handler = new BinaryPackageHandler(tempDir);
        var req = new PackageHandler.InstallRequest("pkg", "1.0", Map.of(), Map.of());
        var result = handler.install(req);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("downloadUrl");
    }

    @Test
    void uninstallSucceedsForMissingFile() {
        var handler = new BinaryPackageHandler(tempDir);
        var req = new PackageHandler.UninstallRequest("missing-pkg", "1.0");
        var result = handler.uninstall(req);

        assertThat(result.success()).isTrue();
    }

    @Test
    void uninstallRemovesExistingBinary() throws Exception {
        var binaryPath = tempDir.resolve("pkg-1.0.exe");
        Files.writeString(binaryPath, "binary content");

        var handler = new BinaryPackageHandler(tempDir);
        var result = handler.uninstall(
                new PackageHandler.UninstallRequest("pkg", "1.0"));

        assertThat(result.success()).isTrue();
        assertThat(Files.exists(binaryPath)).isFalse();
    }

    @Test
    void uninstallReturnsMessage() {
        var handler = new BinaryPackageHandler(tempDir);
        var result = handler.uninstall(new PackageHandler.UninstallRequest("pkg", "2.0"));

        assertThat(result.success()).isTrue();
        assertThat(result.message()).contains("pkg");
    }
}
