package io.mcpm.spi;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link McpConfig} and {@link McpConfig.ServerEntry}.
 */
class McpConfigTest {

    @Test
    void emptyConfig() {
        var config = new McpConfig();
        assertThat(config.servers()).isEmpty();
        assertThat(config.size()).isZero();
    }

    @Test
    void addAndRetrieveServer() {
        var config = new McpConfig();
        var entry = new McpConfig.ServerEntry("npx", List.of("-y", "@org/srv"), Map.of("KEY", "val"));

        config.addServer("my-server", entry);

        assertThat(config.hasServer("my-server")).isTrue();
        assertThat(config.getServer("my-server")).isSameAs(entry);
        assertThat(config.size()).isEqualTo(1);
    }

    @Test
    void removeServer() {
        var config = new McpConfig();
        config.addServer("s1", new McpConfig.ServerEntry("npx", List.of(), Map.of()));
        config.addServer("s2", new McpConfig.ServerEntry("java", List.of("-jar"), Map.of()));

        config.removeServer("s1");

        assertThat(config.hasServer("s1")).isFalse();
        assertThat(config.hasServer("s2")).isTrue();
        assertThat(config.size()).isEqualTo(1);
    }

    @Test
    void serversViewIsUnmodifiable() {
        var config = new McpConfig();
        config.addServer("x", new McpConfig.ServerEntry("cmd", List.of(), Map.of()));

        assertThatThrownBy(() -> config.servers().put("y", null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void stdioServerEntry() {
        var entry = new McpConfig.ServerEntry(
                "npx", List.of("-y", "@org/srv"), Map.of("FOO", "bar"));

        assertThat(entry.command()).isEqualTo("npx");
        assertThat(entry.args()).containsExactly("-y", "@org/srv");
        assertThat(entry.env()).containsEntry("FOO", "bar");
        assertThat(entry.isStdio()).isTrue();
        assertThat(entry.transportType()).isNull();
        assertThat(entry.transportUrl()).isNull();
    }

    @Test
    void remoteTransportEntry() {
        var entry = new McpConfig.ServerEntry(
                null, null, null, "sse", "http://localhost:8080/events");

        assertThat(entry.isStdio()).isFalse();
        assertThat(entry.transportType()).isEqualTo("sse");
        assertThat(entry.transportUrl()).isEqualTo("http://localhost:8080/events");
        assertThat(entry.command()).isNull();
        assertThat(entry.args()).isEmpty();
    }

    @Test
    void toMapStdio() {
        var entry = new McpConfig.ServerEntry(
                "npx", List.of("-y", "pkg"), Map.of("K", "v"));

        var map = entry.toMap();

        assertThat(map)
                .containsEntry("command", "npx")
                .containsKey("args")
                .containsKey("env");
        assertThat(map.get("args")).asList().containsExactly("-y", "pkg");
        @SuppressWarnings("unchecked")
        var envMap = (Map<String, String>) map.get("env");
        assertThat(envMap).containsEntry("K", "v");
    }

    @Test
    void toMapRemote() {
        var entry = new McpConfig.ServerEntry(
                null, null, null, "streamable-http", "http://localhost:3000/mcp");

        var map = entry.toMap();

        assertThat(map)
                .containsEntry("type", "streamable-http")
                .containsEntry("url", "http://localhost:3000/mcp")
                .doesNotContainKey("command");
    }

    @Test
    void toMapDoesNotContainEmptyCollections() {
        var entry = new McpConfig.ServerEntry("npx", List.of(), Map.of());

        var map = entry.toMap();

        assertThat(map).containsEntry("command", "npx");
        assertThat(map).doesNotContainKey("args");
        assertThat(map).doesNotContainKey("env");
    }

    @Test
    void argsAndEnvAreDefensiveCopies() {
        var args = new java.util.ArrayList<>(List.of("a", "b"));
        var env = new java.util.HashMap<>(Map.of("K", "v"));
        var entry = new McpConfig.ServerEntry("cmd", args, env);

        args.add("c");
        env.put("K2", "v2");

        assertThat(entry.args()).containsExactly("a", "b");
        assertThat(entry.env()).containsOnlyKeys("K");
    }

    @Test
    void nullArgsAndEnvDefaultsToEmpty() {
        var entry = new McpConfig.ServerEntry("cmd", null, null);

        assertThat(entry.args()).isEmpty();
        assertThat(entry.env()).isEmpty();
    }

    @Test
    void configFromMapConstructor() {
        var servers = new LinkedHashMap<String, McpConfig.ServerEntry>();
        servers.put("s1", new McpConfig.ServerEntry("npx", List.of(), Map.of()));
        var config = new McpConfig(servers);

        assertThat(config.hasServer("s1")).isTrue();
        assertThat(config.size()).isEqualTo(1);
    }

    @Test
    void configFromMapConstructorProducesUnmodifiableView() {
        var servers = new LinkedHashMap<String, McpConfig.ServerEntry>();
        servers.put("s1", new McpConfig.ServerEntry("npx", List.of(), Map.of()));
        var config = new McpConfig(servers);

        servers.put("s2", new McpConfig.ServerEntry("java", List.of(), Map.of()));

        assertThat(config.size()).isEqualTo(1); // original snapshot
    }
}
