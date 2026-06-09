package io.mcpm.spi;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for the nested data classes inside {@link PackageHandler}:
 * {@link PackageHandler.InstallRequest}, {@link PackageHandler.InstallResult},
 * {@link PackageHandler.UninstallRequest}, {@link PackageHandler.UninstallResult}.
 */
class PackageHandlerDataTest {

    // ---- InstallRequest ----

    @Test
    void installRequestConstruction() {
        var request = new PackageHandler.InstallRequest(
                "my-pkg", "1.0.0",
                Map.of("KEY", "val"),
                Map.of("option", "value"));

        assertThat(request.packageName()).isEqualTo("my-pkg");
        assertThat(request.version()).isEqualTo("1.0.0");
        assertThat(request.env()).containsEntry("KEY", "val");
        assertThat(request.args()).containsEntry("option", "value");
    }

    @Test
    void installRequestNullEnvAndArgsDefaultsToEmpty() {
        var request = new PackageHandler.InstallRequest("p", "1.0", null, null);

        assertThat(request.env()).isEmpty();
        assertThat(request.args()).isEmpty();
    }

    @Test
    void installRequestEnvAndArgsAreDefensiveCopies() {
        var env = new java.util.HashMap<>(Map.of("K", "v"));
        Map<String, Object> args = new java.util.HashMap<>(Map.of("port", 8080));
        var request = new PackageHandler.InstallRequest("p", "1.0", env, args);

        env.put("K2", "v2");
        args.put("host", "localhost");

        assertThat(request.env()).containsOnlyKeys("K");
        assertThat(request.args()).containsOnlyKeys("port");
    }

    // ---- InstallResult ----

    @Test
    void installResultSuccess() {
        var result = PackageHandler.InstallResult.success(
                "Installed", "npx", List.of("-y", "pkg"), Map.of("K", "v"));

        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("Installed");
        assertThat(result.entryCommand()).isEqualTo("npx");
        assertThat(result.entryArgs()).containsExactly("-y", "pkg");
        assertThat(result.entryEnv()).containsEntry("K", "v");
    }

    @Test
    void installResultFailure() {
        var result = PackageHandler.InstallResult.failure("Something went wrong");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("Something went wrong");
        assertThat(result.entryCommand()).isNull();
        assertThat(result.entryArgs()).isEmpty();
        assertThat(result.entryEnv()).isEmpty();
    }

    @Test
    void installResultDefensiveCopies() {
        var args = new java.util.ArrayList<>(List.of("a"));
        var env = new java.util.HashMap<>(Map.of("K", "v"));
        var result = PackageHandler.InstallResult.success("ok", "cmd", args, env);

        args.add("b");
        env.put("K2", "v2");

        assertThat(result.entryArgs()).containsExactly("a");
        assertThat(result.entryEnv()).containsOnlyKeys("K");
    }

    // ---- UninstallRequest ----

    @Test
    void uninstallRequestConstruction() {
        var request = new PackageHandler.UninstallRequest("my-pkg", "1.0.0");

        assertThat(request.packageName()).isEqualTo("my-pkg");
        assertThat(request.version()).isEqualTo("1.0.0");
    }

    // ---- UninstallResult ----

    @Test
    void uninstallResultSuccess() {
        var result = PackageHandler.UninstallResult.success("Removed");

        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("Removed");
    }

    @Test
    void uninstallResultFailure() {
        var result = PackageHandler.UninstallResult.failure("Not found");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("Not found");
    }
}
