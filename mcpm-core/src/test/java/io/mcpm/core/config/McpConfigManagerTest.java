package io.mcpm.core.config;

import io.mcpm.spi.ConfigFormatProvider;
import io.mcpm.spi.McpConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class McpConfigManagerTest {

    @Test
    void selectsProviderByFilename() {
        var mcpJsonProvider = mock(ConfigFormatProvider.class);
        when(mcpJsonProvider.canHandle("mcp.json")).thenReturn(true);
        when(mcpJsonProvider.read("mcp.json")).thenReturn(new McpConfig());

        var otherProvider = mock(ConfigFormatProvider.class);
        when(otherProvider.canHandle("mcp.json")).thenReturn(false);

        var manager = new McpConfigManager(List.of(otherProvider, mcpJsonProvider));

        var config = manager.read(Path.of("mcp.json"));

        verify(mcpJsonProvider).read("mcp.json");
        verify(otherProvider, never()).read(anyString());
    }

    @Test
    void throwsWhenNoProviderCanHandle() {
        var provider = mock(ConfigFormatProvider.class);
        when(provider.canHandle(anyString())).thenReturn(false);

        var manager = new McpConfigManager(List.of(provider));

        assertThatThrownBy(() -> manager.read(Path.of("unknown.format")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown.format");
    }

    @Test
    void delegatesWriteToCorrectProvider() {
        var provider = mock(ConfigFormatProvider.class);
        when(provider.canHandle("mcp.json")).thenReturn(true);

        var manager = new McpConfigManager(List.of(provider));
        var config = new McpConfig();
        config.addServer("srv", new McpConfig.ServerEntry("cmd", List.of(), Map.of()));

        manager.write(Path.of("mcp.json"), config);

        verify(provider).write("mcp.json", config);
    }

    @Test
    void providersList() {
        var p1 = mock(ConfigFormatProvider.class);
        var p2 = mock(ConfigFormatProvider.class);

        var manager = new McpConfigManager(List.of(p1, p2));

        assertThat(manager.providers()).hasSize(2);
    }

    @Test
    void readsConfigSuccessfully(@TempDir Path tempDir) throws Exception {
        var configFile = tempDir.resolve("mcp.json");

        var provider = mock(ConfigFormatProvider.class);
        when(provider.canHandle(configFile.toString())).thenReturn(true);

        var expectedConfig = new McpConfig();
        expectedConfig.addServer("s1", new McpConfig.ServerEntry("cmd", List.of(), Map.of()));

        when(provider.read(configFile.toString())).thenReturn(expectedConfig);

        var manager = new McpConfigManager(List.of(provider));
        var config = manager.read(configFile);

        assertThat(config.hasServer("s1")).isTrue();
    }
}
