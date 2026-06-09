package io.mcpm.spi;

import java.util.List;
import java.util.Map;

/**
 * Metadata for a published MCP server package.
 * <p>
 * This is the canonical representation returned by registries and consumed
 * by the install pipeline. It is intentionally kept in the SPI module
 * so that both registry providers and handler implementations can reference it
 * without depending on mcpm-core.
 */
public final class McpmPackage {

    private final String name;
    private final String description;
    private final String type;            // matches PackageHandler.supportedType()
    private final String latestVersion;
    private final Map<String, VersionEntry> versions;
    private final List<String> authors;
    private final String license;
    private final String homepage;
    private final String repository;

    public McpmPackage(String name, String description, String type,
                       String latestVersion, Map<String, VersionEntry> versions,
                       List<String> authors, String license,
                       String homepage, String repository) {
        this.name = name;
        this.description = description;
        this.type = type;
        this.latestVersion = latestVersion;
        this.versions = versions;
        this.authors = authors;
        this.license = license;
        this.homepage = homepage;
        this.repository = repository;
    }

    public String name() { return name; }
    public String description() { return description; }
    public String type() { return type; }
    public String latestVersion() { return latestVersion; }
    public Map<String, VersionEntry> versions() { return versions; }
    public List<String> authors() { return authors; }
    public String license() { return license; }
    public String homepage() { return homepage; }
    public String repository() { return repository; }

    /**
     * Details for a specific version of a package.
     */
    public static final class VersionEntry {
        private final String version;
        private final String downloadUrl;
        private final String checksum;
        private final Map<String, Object> handlerArgs;   // handler-specific args
        private final Map<String, String> defaultEnv;     // default env vars

        public VersionEntry(String version, String downloadUrl,
                            String checksum, Map<String, Object> handlerArgs,
                            Map<String, String> defaultEnv) {
            this.version = version;
            this.downloadUrl = downloadUrl;
            this.checksum = checksum;
            this.handlerArgs = handlerArgs;
            this.defaultEnv = defaultEnv;
        }

        public String version() { return version; }
        public String downloadUrl() { return downloadUrl; }
        public String checksum() { return checksum; }
        public Map<String, Object> handlerArgs() { return handlerArgs; }
        public Map<String, String> defaultEnv() { return defaultEnv; }
    }
}
