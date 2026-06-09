package io.mcpm.core.config;

import io.mcpm.spi.McpConfig;
import io.mcpm.spi.McpmPackage;
import io.mcpm.spi.PackageHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link ConfigHelper}.
 */
class ConfigHelperTest {

    @TempDir
    Path tempDir;

    // ---- resolveConfigPath ----

    @Test
    void resolveConfigPathUsesUserSpecified() {
        var helper = new ConfigHelper();
        Path p = helper.resolveConfigPath("C:/custom/path/mcp.json");
        assertThat(p.toString()).contains("custom").contains("mcp.json");
    }

    @Test
    void resolveConfigPathWithDirectoryFindsMcpJsonInside() throws Exception {
        Files.writeString(tempDir.resolve("mcp.json"), "{}");
        var helper = new ConfigHelper();
        Path result = helper.resolveConfigPath(tempDir.toString());
        assertThat(result.getFileName().toString()).isEqualTo("mcp.json");
    }

    @Test
    void resolveConfigPathWithDirectoryDefaultsToMcpJson() throws Exception {
        var helper = new ConfigHelper();
        Path result = helper.resolveConfigPath(tempDir.toString());
        assertThat(result.getFileName().toString()).isEqualTo("mcp.json");
    }

    // ---- readOrEmpty ----

    @Test
    void readOrEmptyReturnsEmptyForMissingFile() {
        var helper = new ConfigHelper();
        assertThat(helper.readOrEmpty(tempDir.resolve("nope.json")).size()).isZero();
    }

    @Test
    void readOrEmptyReadsExistingFile() throws Exception {
        Files.writeString(tempDir.resolve("mcp.json"),
                "{\"mcpServers\":{\"srv\":{\"command\":\"echo\",\"args\":[\"hi\"]}}}");
        var helper = new ConfigHelper();
        McpConfig config = helper.readOrEmpty(tempDir.resolve("mcp.json"));
        assertThat(config.hasServer("srv")).isTrue();
    }

    // ---- checkConflict ----

    @Test
    void checkConflictReturnsNullWhenNotPresent() {
        var config = new McpConfig();
        var helper = new ConfigHelper();
        assertThat(helper.checkConflict(config, "missing")).isNull();
    }

    @Test
    void checkConflictReturnsMessageWhenPresent() {
        var config = new McpConfig();
        config.addServer("srv", new McpConfig.ServerEntry("cmd", List.of(), Map.of()));
        var helper = new ConfigHelper();

        String msg = helper.checkConflict(config, "srv");
        assertThat(msg).contains("srv").contains("already exists");
    }

    // ---- removeServer ----

    @Test
    void removeServerReturnsNullForMissingFile() throws Exception {
        var helper = new ConfigHelper();
        assertThat(helper.removeServer(tempDir.resolve("nope.json"), "srv")).isNull();
    }

    @Test
    void removeServerReturnsNullForMissingServer() throws Exception {
        Files.writeString(tempDir.resolve("mcp.json"), "{\"mcpServers\":{}}");
        var helper = new ConfigHelper();
        assertThat(helper.removeServer(tempDir.resolve("mcp.json"), "ghost")).isNull();
    }

    @Test
    void removeServerRemovesAndReturnsEntry() throws Exception {
        Files.writeString(tempDir.resolve("mcp.json"),
                "{\"mcpServers\":{\"srv\":{\"command\":\"echo\"}}}");
        var helper = new ConfigHelper();
        var removed = helper.removeServer(tempDir.resolve("mcp.json"), "srv");

        assertThat(removed).isNotNull();
        assertThat(removed.command()).isEqualTo("echo");

        var config = helper.readOrEmpty(tempDir.resolve("mcp.json"));
        assertThat(config.hasServer("srv")).isFalse();
    }

    @Test
    void removeServerCreatesBackup() throws Exception {
        Files.writeString(tempDir.resolve("mcp.json"),
                "{\"mcpServers\":{\"srv\":{\"command\":\"echo\"}}}");
        var helper = new ConfigHelper();
        helper.removeServer(tempDir.resolve("mcp.json"), "srv");

        var backups = Files.list(tempDir)
                .filter(p -> p.getFileName().toString().startsWith("mcp.json.bak."))
                .toList();
        assertThat(backups).hasSize(1);
    }

    // ---- writeWithBackup ----

    @Test
    void writeWithBackupCreatesFile() throws Exception {
        var configFile = tempDir.resolve("mcp.json");
        var config = new McpConfig();
        config.addServer("srv", new McpConfig.ServerEntry("npx", List.of(), Map.of()));

        var helper = new ConfigHelper();
        helper.writeWithBackup(configFile, config);

        assertThat(Files.exists(configFile)).isTrue();
    }

    @Test
    void writeWithBackupCreatesBackupOfExisting() throws Exception {
        var configFile = tempDir.resolve("mcp.json");
        Files.writeString(configFile, "{\"mcpServers\":{}}");

        var config = new McpConfig();
        config.addServer("srv", new McpConfig.ServerEntry("npx", List.of(), Map.of()));

        var helper = new ConfigHelper();
        helper.writeWithBackup(configFile, config);

        var backups = Files.list(tempDir)
                .filter(p -> p.getFileName().toString().startsWith("mcp.json.bak."))
                .toList();
        assertThat(backups).hasSize(1);
    }

    @Test
    void writeWithBackupReturnNullWhenNoExisting() throws Exception {
        var configFile = tempDir.resolve("mcp.json");
        var helper = new ConfigHelper();
        var result = helper.writeWithBackup(configFile, new McpConfig());
        assertThat(result).isNull();
    }

    // ---- discoverConfigs ----

    @Test
    void discoverConfigsReturnsEmptyWhenNoneExist() {
        var helper = new ConfigHelper();
        var configs = helper.discoverConfigs();
        assertThat(configs).isNotNull();
    }

    @Test
    void discoverConfigsDoesNotThrow() {
        var helper = new ConfigHelper();
        assertThatCode(helper::discoverConfigs).doesNotThrowAnyException();
    }

    // ---- describeEntry ----

    @Test
    void describeEntryFromPkgAndResult() {
        var pkg = new McpmPackage("test", null, "npx", "1.0",
                Map.of(), null, null, null, null);
        var result = PackageHandler.InstallResult.success(
                "ok", "npx", List.of("-y", "pkg"), Map.of("K", "v"));

        String desc = ConfigHelper.describeEntry(pkg, result);
        assertThat(desc).contains("npx").contains("-y").contains("K");
    }
}
