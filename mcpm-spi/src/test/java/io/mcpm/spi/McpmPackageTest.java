package io.mcpm.spi;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link McpmPackage} and its nested {@link McpmPackage.VersionEntry}.
 */
class McpmPackageTest {

    @Test
    void createMinimalPackage() {
        var pkg = new McpmPackage(
                "my-server", null, "npx", "1.0.0",
                Map.of(), null, null, null, null);

        assertThat(pkg.name()).isEqualTo("my-server");
        assertThat(pkg.description()).isNull();
        assertThat(pkg.type()).isEqualTo("npx");
        assertThat(pkg.latestVersion()).isEqualTo("1.0.0");
        assertThat(pkg.versions()).isEmpty();
        assertThat(pkg.authors()).isNull();
        assertThat(pkg.license()).isNull();
    }

    @Test
    void createFullPackage() {
        var versionEntry = new McpmPackage.VersionEntry(
                "1.0.0", "https://example.com/pkg.jar", "sha256:abc",
                Map.of("port", 8080), Map.of("JAVA_HOME", "/usr/local"));

        var pkg = new McpmPackage(
                "@org/my-server",
                "A great MCP server",
                "jar",
                "1.0.0",
                Map.of("1.0.0", versionEntry),
                List.of("Alice", "Bob"),
                "MIT",
                "https://example.com",
                "https://github.com/org/my-server");

        assertThat(pkg.name()).isEqualTo("@org/my-server");
        assertThat(pkg.description()).isEqualTo("A great MCP server");
        assertThat(pkg.type()).isEqualTo("jar");
        assertThat(pkg.latestVersion()).isEqualTo("1.0.0");
        assertThat(pkg.authors()).containsExactly("Alice", "Bob");
        assertThat(pkg.license()).isEqualTo("MIT");
        assertThat(pkg.homepage()).isEqualTo("https://example.com");
        assertThat(pkg.repository()).isEqualTo("https://github.com/org/my-server");
    }

    @Test
    void versionsMapIsUnmodifiable() {
        var entry = new McpmPackage.VersionEntry("1.0", null, null, null, null);
        var pkg = new McpmPackage("test", null, "npx", "1.0",
                Map.of("1.0", entry), null, null, null, null);

        assertThatThrownBy(() -> pkg.versions().put("2.0", entry))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void versionEntryGetters() {
        Map<String, Object> args = Map.of("port", 8080, "host", "localhost");
        var env = Map.of("API_KEY", "secret");
        var entry = new McpmPackage.VersionEntry(
                "2.0.1", "https://dl.example.com/v2.jar", "sha512:xyz",
                args, env);

        assertThat(entry.version()).isEqualTo("2.0.1");
        assertThat(entry.downloadUrl()).isEqualTo("https://dl.example.com/v2.jar");
        assertThat(entry.checksum()).isEqualTo("sha512:xyz");
        assertThat(entry.handlerArgs()).containsEntry("port", 8080);
        assertThat(entry.defaultEnv()).containsEntry("API_KEY", "secret");
    }

    @Test
    void versionEntryNullsAreSafe() {
        var entry = new McpmPackage.VersionEntry("1.0", null, null, null, null);

        assertThat(entry.downloadUrl()).isNull();
        assertThat(entry.checksum()).isNull();
        assertThat(entry.handlerArgs()).isNull();
        assertThat(entry.defaultEnv()).isNull();
    }
}
