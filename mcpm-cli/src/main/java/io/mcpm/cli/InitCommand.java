package io.mcpm.cli;

import io.mcpm.core.config.ConfigHelper;
import io.mcpm.spi.McpConfig;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "init",
        description = "@|green Init|@ create an empty MCP configuration file.",
        mixinStandardHelpOptions = true
)
public class InitCommand implements Callable<Integer> {

    @CommandLine.Option(names = {"-f", "--format"}, description = "Config format: mcp.json (default) or claude_desktop_config.json")
    String format;

    @CommandLine.Option(names = {"-y", "--overwrite"}, description = "Overwrite existing file without confirmation")
    boolean overwrite;

    @CommandLine.Option(names = {"-p", "--path"}, description = "Target directory (default: current directory)")
    String targetPath;

    @CommandLine.Option(names = {"-e", "--example"}, description = "Include an example server entry")
    boolean withExample;

    @CommandLine.Option(names = {"-t", "--template"}, description = "Generate a MCP server project template (e.g. server)")
    String template;

    @Override
    public Integer call() throws Exception {
        // ---- Template mode ----
        if ("server".equals(template)) {
            return generateServerTemplate();
        }

        // Determine file name
        String fileName;
        if ("claude_desktop_config.json".equals(format)) {
            fileName = "claude_desktop_config.json";
        } else {
            fileName = "mcp.json";
        }

        // Determine target path
        Path targetDir;
        if (targetPath != null) {
            targetDir = Path.of(targetPath);
            if (!Files.isDirectory(targetDir)) {
                Files.createDirectories(targetDir);
            }
        } else {
            targetDir = Path.of(".").toAbsolutePath().normalize();
        }

        Path configFile = targetDir.resolve(fileName).toAbsolutePath().normalize();

        // Check if exists
        if (Files.exists(configFile) && !overwrite) {
            System.err.println("✘ " + configFile.getFileName() + " already exists at " + configFile.getParent());
            System.err.println("  Use --overwrite to replace it.");
            return 1;
        }

        // Build config content
        McpConfig config = new McpConfig();
        if (withExample) {
            config.addServer("_example", new McpConfig.ServerEntry(
                    "npx",
                    java.util.List.of("-y", "@org/my-mcp-server"),
                    java.util.Map.of("API_KEY", "your-key-here")));
        }

        // Write
        ConfigHelper configHelper = new ConfigHelper();
        configHelper.writeWithBackup(configFile, config);

        // Summary
        System.out.println("✔ Created " + fileName + " at " + configFile);

        if (withExample) {
            System.out.println();
            System.out.println("  Includes an example entry 'hello-mcp'. Edit or remove it,");
            System.out.println("  then run 'mcpm install <package>' to add real servers.");
        } else {
            System.out.println();
            System.out.println("  Next step: run 'mcpm install <package>' to add an MCP server.");
            System.out.println("  Or:        run 'mcpm search <query>' to find available servers.");
        }

        return 0;
    }

    // ---- Template: MCP server project ----

    private Integer generateServerTemplate() throws Exception {
        Path dir = targetPath != null ? Path.of(targetPath) : Path.of("./my-mcp-server");
        Files.createDirectories(dir);

        // pom.xml
        Files.writeString(dir.resolve("pom.xml"), """
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example</groupId>
    <artifactId>my-mcp-server</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>
    <name>My MCP Server</name>
    <properties><maven.compiler.release>21</maven.compiler.release></properties>
    <dependencies>
        <dependency>
            <groupId>io.mcpm</groupId>
            <artifactId>mcpm-spi</artifactId>
            <version>0.1.0</version>
        </dependency>
    </dependencies>
</project>
""");

        // Main.java
        Path src = dir.resolve("src/main/java/com/example");
        Files.createDirectories(src);
        Files.writeString(src.resolve("MyMcpServer.java"), """
package com.example;

import io.mcpm.spi.PackageHandler;
import java.util.List;
import java.util.Map;

public class MyMcpServer implements PackageHandler {
    @Override
    public String supportedType() { return "custom"; }

    @Override
    public InstallResult install(InstallRequest request) {
        return InstallResult.success(
            "Installed " + request.packageName(),
            "java",
            List.of("-jar", "my-server.jar"),
            request.env());
    }

    @Override
    public UninstallResult uninstall(UninstallRequest request) {
        return UninstallResult.success("Removed " + request.packageName());
    }
}
""");

        // mcpm.json manifest
        Files.writeString(dir.resolve("mcpm.json"), """
{
  "name": "my-mcp-server",
  "description": "A custom MCP server",
  "type": "jar",
  "version": "1.0.0",
  "authors": ["You"],
  "license": "MIT",
  "homepage": "https://github.com/you/my-mcp-server",
  "handlerArgs": {
    "downloadUrl": "https://github.com/you/my-mcp-server/releases/download/v1.0.0/my-mcp-server-1.0.0.jar"
  }
}
""");

        // README
        Files.writeString(dir.resolve("README.md"), """
# My MCP Server

A custom MCP server built with mcpm.

## Build

mvn package

## Publish

mcpm publish
""");

        System.out.println("✔ Created MCP server project at: " + dir.toAbsolutePath());
        System.out.println();
        System.out.println("  " + dir.getFileName() + "/");
        System.out.println("  ├── pom.xml");
        System.out.println("  ├── src/main/java/com/example/MyMcpServer.java");
        System.out.println("  ├── mcpm.json              ← package manifest");
        System.out.println("  └── README.md");
        System.out.println();
        System.out.println("  Build:   cd " + dir.getFileName() + " && mvn package");
        System.out.println("  Publish: mcpm publish");
        System.out.println("  Install: mcpm install ./my-mcp-server");

        return 0;
    }
}
