package io.mcpm.core.manifest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.*;

/**
 * A package manifest (mcpm.json) describing an MCP server for publishing.
 * <p>
 * This file lives in the root of an MCP server project and contains
 * everything needed to register it in the mcpm registry.
 *
 * <pre>
 * {
 *   "name": "my-mcp-server",
 *   "description": "Does amazing things with AI",
 *   "type": "npx",
 *   "version": "1.0.0",
 *   "license": "MIT",
 *   "homepage": "https://github.com/me/my-mcp-server",
 *   "repository": "https://github.com/me/my-mcp-server",
 *   "authors": ["Me"],
 *   "handlerArgs": { "module": "my_mcp_server" },
 *   "defaultEnv": { "API_KEY": "" }
 * }
 * </pre>
 */
public class PackageManifest {

    private final String name;
    private final String description;
    private final String type;
    private final String version;
    private final String license;
    private final String homepage;
    private final String repository;
    private final List<String> authors;
    private final Map<String, Object> handlerArgs;
    private final Map<String, String> defaultEnv;

    @JsonCreator
    public PackageManifest(
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("type") String type,
            @JsonProperty("version") String version,
            @JsonProperty("license") String license,
            @JsonProperty("homepage") String homepage,
            @JsonProperty("repository") String repository,
            @JsonProperty("authors") List<String> authors,
            @JsonProperty("handlerArgs") Map<String, Object> handlerArgs,
            @JsonProperty("defaultEnv") Map<String, String> defaultEnv) {
        this.name = name;
        this.description = description;
        this.type = type;
        this.version = version;
        this.license = license;
        this.homepage = homepage;
        this.repository = repository;
        this.authors = authors == null ? List.of() : List.copyOf(authors);
        this.handlerArgs = handlerArgs == null ? Map.of() : Map.copyOf(handlerArgs);
        this.defaultEnv = defaultEnv == null ? Map.of() : Map.copyOf(defaultEnv);
    }

    public String name() { return name; }
    public String description() { return description; }
    public String type() { return type; }
    public String version() { return version; }
    public String license() { return license; }
    public String homepage() { return homepage; }
    public String repository() { return repository; }
    public List<String> authors() { return authors; }
    public Map<String, Object> handlerArgs() { return handlerArgs; }
    public Map<String, String> defaultEnv() { return defaultEnv; }
}
