package io.mcpm.core.state;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link StateManager}.
 */
class StateManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void emptyState() {
        var state = new StateManager(tempDir.resolve("state.json"));
        assertThat(state.count()).isZero();
        assertThat(state.allInstalled()).isEmpty();
        assertThat(state.isInstalled("anything")).isFalse();
    }

    @Test
    void recordInstall() {
        var state = new StateManager(tempDir.resolve("state.json"));
        state.recordInstall("my-pkg", "1.0.0", "npx");

        assertThat(state.isInstalled("my-pkg")).isTrue();
        assertThat(state.installedVersion("my-pkg")).contains("1.0.0");
        assertThat(state.count()).isEqualTo(1);
    }

    @Test
    void recordInstallMultiple() {
        var state = new StateManager(tempDir.resolve("state.json"));
        state.recordInstall("a", "1.0", "npx");
        state.recordInstall("b", "2.0", "pip");

        assertThat(state.count()).isEqualTo(2);
        assertThat(state.allInstalled()).containsKeys("a", "b");
    }

    @Test
    void recordUninstall() {
        var state = new StateManager(tempDir.resolve("state.json"));
        state.recordInstall("pkg", "1.0", "npx");
        state.recordUninstall("pkg");

        assertThat(state.isInstalled("pkg")).isFalse();
        assertThat(state.count()).isZero();
    }

    @Test
    void recordUpdate() {
        var state = new StateManager(tempDir.resolve("state.json"));
        state.recordInstall("pkg", "1.0", "npx");
        state.recordUpdate("pkg", "2.0");

        assertThat(state.installedVersion("pkg")).contains("2.0");
    }

    @Test
    void recordUpdateNonExistentDoesNothing() {
        var state = new StateManager(tempDir.resolve("state.json"));
        state.recordUpdate("missing", "2.0");
        assertThat(state.count()).isZero();
    }

    @Test
    void persistenceAcrossInstances() {
        var path = tempDir.resolve("state.json");

        var state1 = new StateManager(path);
        state1.recordInstall("persistent", "3.0", "jar");

        var state2 = new StateManager(path);
        assertThat(state2.isInstalled("persistent")).isTrue();
        assertThat(state2.installedVersion("persistent")).contains("3.0");
        assertThat(state2.count()).isEqualTo(1);
    }

    @Test
    void persistenceAfterUninstall() {
        var path = tempDir.resolve("state.json");

        var state1 = new StateManager(path);
        state1.recordInstall("tmp", "1.0", "npx");
        state1.recordUninstall("tmp");

        var state2 = new StateManager(path);
        assertThat(state2.count()).isZero();
    }

    @Test
    void installedVersionReturnsEmptyForMissing() {
        var state = new StateManager(tempDir.resolve("state.json"));
        assertThat(state.installedVersion("nope")).isEmpty();
    }

    @Test
    void allInstalledIsUnmodifiable() {
        var state = new StateManager(tempDir.resolve("state.json"));
        state.recordInstall("pkg", "1.0", "npx");

        assertThatThrownBy(() -> state.allInstalled().put("x", null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void stateFileIsCreatedOnDisk() {
        var path = tempDir.resolve("mcpm-state.json");
        var state = new StateManager(path);
        state.recordInstall("pkg", "1.0", "docker");

        assertThat(Files.exists(path)).isTrue();
        assertThat(path.toFile().length()).isGreaterThan(10);
    }

    @Test
    void handlesCorruptedStateFileGracefully() throws Exception {
        var path = tempDir.resolve("corrupt.json");
        Files.writeString(path, "{{{ not json");
        var state = new StateManager(path);

        // Should start fresh, not crash
        assertThat(state.count()).isZero();
        state.recordInstall("fresh", "1.0", "npx");
        assertThat(state.isInstalled("fresh")).isTrue();
    }

    @Test
    void installedEntryHasCorrectFields() {
        var state = new StateManager(tempDir.resolve("state.json"));
        state.recordInstall("my-pkg", "2.0.1", "pip");

        var entry = state.allInstalled().get("my-pkg");
        assertThat(entry).isNotNull();
        assertThat(entry.name()).isEqualTo("my-pkg");
        assertThat(entry.version()).isEqualTo("2.0.1");
        assertThat(entry.type()).isEqualTo("pip");
        assertThat(entry.installedAt()).isNotEmpty();
    }
}
