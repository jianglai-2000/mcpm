package io.mcpm.core.manifest;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link ManifestValidator}.
 */
class ManifestValidatorTest {

    private final ManifestValidator validator = new ManifestValidator();

    private PackageManifest validManifest() {
        return new PackageManifest(
                "my-server", "A great MCP server", "npx", "1.0.0",
                "MIT", "https://example.com", "https://github.com/me/srv",
                List.of("Me"), Map.of(), Map.of());
    }

    // ---- valid ----

    @Test
    void validManifestPasses() {
        var result = validator.validate(validManifest());
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    // ---- name ----

    @Test
    void missingNameFails() {
        var m = new PackageManifest(null, "desc", "npx", "1.0.0",
                null, null, null, List.of("me"), Map.of(), Map.of());
        assertThat(validator.validate(m).errors()).anyMatch(e -> e.contains("name"));
    }

    @Test
    void blankNameFails() {
        var m = new PackageManifest("  ", "desc", "npx", "1.0.0",
                null, null, null, List.of("me"), Map.of(), Map.of());
        assertThat(validator.validate(m).errors()).anyMatch(e -> e.contains("name"));
    }

    @Test
    void shortNameFails() {
        var m = new PackageManifest("x", "desc", "npx", "1.0.0",
                null, null, null, List.of("me"), Map.of(), Map.of());
        assertThat(validator.validate(m).errors()).anyMatch(e -> e.contains("name"));
    }

    @Test
    void nameWithSpecialCharsFails() {
        var m = new PackageManifest("bad name!", "desc", "npx", "1.0.0",
                null, null, null, List.of("me"), Map.of(), Map.of());
        assertThat(validator.validate(m).errors()).anyMatch(e -> e.contains("characters"));
    }

    @Test
    void nameWithValidCharsPasses() {
        var m = new PackageManifest("@org/my-server_0.1", "desc", "npx", "1.0.0",
                null, null, null, List.of("me"), Map.of(), Map.of());
        assertThat(validator.validate(m).valid()).isTrue();
    }

    // ---- type ----

    @Test
    void missingTypeFails() {
        var m = new PackageManifest("srv", "desc", null, "1.0.0",
                null, null, null, List.of("me"), Map.of(), Map.of());
        assertThat(validator.validate(m).errors()).anyMatch(e -> e.contains("type"));
    }

    @Test
    void unsupportedTypeFails() {
        var m = new PackageManifest("srv", "desc", "wasm", "1.0.0",
                null, null, null, List.of("me"), Map.of(), Map.of());
        assertThat(validator.validate(m).errors()).anyMatch(e -> e.contains("wasm"));
    }

    @Test
    void allSupportedTypesPass() {
        for (String type : List.of("npx", "pip", "uvx", "docker")) {
            var m = new PackageManifest("srv", "desc", type, "1.0.0",
                    null, null, null, List.of("me"), Map.of(), Map.of());
            assertThat(validator.validate(m).valid())
                    .as("type '%s' should be valid", type)
                    .isTrue();
        }
        // jar/binary require downloadUrl
        for (String type : List.of("jar", "binary")) {
            var m = new PackageManifest("srv", "desc", type, "1.0.0",
                    null, null, null, List.of("me"),
                    Map.of("downloadUrl", "https://example.com/srv.jar"), Map.of());
            assertThat(validator.validate(m).valid())
                    .as("type '%s' with downloadUrl should be valid", type)
                    .isTrue();
        }
    }

    // ---- version ----

    @Test
    void missingVersionFails() {
        var m = new PackageManifest("srv", "desc", "npx", null,
                null, null, null, List.of("me"), Map.of(), Map.of());
        assertThat(validator.validate(m).errors()).anyMatch(e -> e.contains("version"));
    }

    @Test
    void invalidSemverFails() {
        var m = new PackageManifest("srv", "desc", "npx", "not-a-version",
                null, null, null, List.of("me"), Map.of(), Map.of());
        assertThat(validator.validate(m).errors()).anyMatch(e -> e.contains("semver"));
    }

    @Test
    void semverWithPreReleasePasses() {
        var m = new PackageManifest("srv", "desc", "npx", "1.0.0-rc1",
                null, null, null, List.of("me"), Map.of(), Map.of());
        assertThat(validator.validate(m).valid()).isTrue();
    }

    // ---- type-specific ----

    @Test
    void jarTypeNeedsDownloadUrl() {
        var m = new PackageManifest("srv", "desc", "jar", "1.0.0",
                null, null, null, List.of("me"), Map.of(), Map.of());
        assertThat(validator.validate(m).errors()).anyMatch(e -> e.contains("downloadUrl"));
    }

    @Test
    void binaryTypeNeedsDownloadUrl() {
        var m = new PackageManifest("srv", "desc", "binary", "1.0.0",
                null, null, null, List.of("me"), Map.of(), Map.of());
        assertThat(validator.validate(m).errors()).anyMatch(e -> e.contains("downloadUrl"));
    }

    @Test
    void jarTypeWithDownloadUrlPasses() {
        var m = new PackageManifest("srv", "desc", "jar", "1.0.0",
                null, null, null, List.of("me"),
                Map.of("downloadUrl", "https://example.com/srv.jar"),
                Map.of());
        var result = validator.validate(m);
        assertThat(result.errors()).noneMatch(e -> e.contains("downloadUrl"));
    }

    @Test
    void dockerTypeWithImageHasNoError() {
        var m = new PackageManifest("srv", "desc", "docker", "1.0.0",
                null, null, null, List.of("me"),
                Map.of("image", "mcp/srv:latest"),
                Map.of());
        assertThat(validator.validate(m).errors()).isEmpty();
    }

    // ---- warnings ----

    @Test
    void missingDescriptionWarns() {
        var m = new PackageManifest("srv", null, "npx", "1.0.0",
                null, null, null, List.of("me"), Map.of(), Map.of());
        assertThat(validator.validate(m).warnings()).anyMatch(e -> e.contains("description"));
    }

    @Test
    void missingAuthorsWarns() {
        var m = new PackageManifest("srv", "desc", "npx", "1.0.0",
                null, null, null, List.of(), Map.of(), Map.of());
        assertThat(validator.validate(m).warnings()).anyMatch(e -> e.contains("authors"));
    }

    @Test
    void missingHomepageAndRepoWarns() {
        var m = new PackageManifest("srv", "desc", "npx", "1.0.0",
                null, null, null, List.of("me"), Map.of(), Map.of());
        assertThat(validator.validate(m).warnings()).anyMatch(e -> e.contains("repository"));
    }

    @Test
    void multipleErrorsReported() {
        var m = new PackageManifest(null, null, null, null,
                null, null, null, List.of(), Map.of(), Map.of());
        var result = validator.validate(m);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void noWarningsForCompleteManifest() {
        var result = validator.validate(validManifest());
        assertThat(result.warnings()).isEmpty();
    }
}
