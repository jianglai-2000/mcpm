package io.mcpm.core.state;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks installed MCP packages and their versions in {@code ~/.mcpm/state.json}.
 * <p>
 * This is the source of truth for "what version is currently installed",
 * enabling the {@code update} command to detect and apply upgrades.
 *
 * <h2>State file structure</h2>
 * <pre>
 * {
 *   "version": 1,
 *   "installed": {
 *     "@anthropic/server-filesystem": {
 *       "name": "@anthropic/server-filesystem",
 *       "version": "0.6.2",
 *       "type": "npx",
 *       "installedAt": "2026-06-09T21:30:00"
 *     }
 *   }
 * }
 * </pre>
 */
public class StateManager {

    private static final Logger log = LoggerFactory.getLogger(StateManager.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int STATE_VERSION = 1;

    private final Path stateFile;
    private final Map<String, InstalledEntry> installed;
    private boolean dirty;

    public StateManager() {
        this(Path.of(System.getProperty("user.home"), ".mcpm", "state.json"));
    }

    StateManager(Path stateFile) {
        this.stateFile = stateFile;
        this.installed = new ConcurrentHashMap<>();
        this.dirty = false;
        load();
    }

    // ---- Query ----

    public boolean isInstalled(String packageName) {
        return installed.containsKey(packageName);
    }

    public Optional<String> installedVersion(String packageName) {
        return Optional.ofNullable(installed.get(packageName))
                .map(InstalledEntry::version);
    }

    public Map<String, InstalledEntry> allInstalled() {
        return Collections.unmodifiableMap(installed);
    }

    public int count() {
        return installed.size();
    }

    // ---- Mutations ----

    /**
     * Record a package as installed. Persists to disk immediately.
     */
    public void recordInstall(String packageName, String version, String type) {
        installed.put(packageName, new InstalledEntry(
                packageName, version, type, LocalDateTime.now().toString()));
        dirty = true;
        save();
    }

    /**
     * Remove a package from state. Persists to disk immediately.
     */
    public void recordUninstall(String packageName) {
        installed.remove(packageName);
        dirty = true;
        save();
    }

    /**
     * Update the version of an installed package. Persists to disk immediately.
     */
    public void recordUpdate(String packageName, String newVersion) {
        InstalledEntry existing = installed.get(packageName);
        if (existing != null) {
            installed.put(packageName, new InstalledEntry(
                    packageName, newVersion, existing.type(), LocalDateTime.now().toString()));
            dirty = true;
            save();
        }
    }

    // ---- Persistence ----

    private void load() {
        File file = stateFile.toFile();
        if (!file.exists()) {
            installed.clear();
            return;
        }
        try {
            JsonNode root = MAPPER.readTree(file);
            JsonNode installedNode = root.get("installed");
            if (installedNode != null && installedNode.isObject()) {
                installed.clear();
                Iterator<Map.Entry<String, JsonNode>> fields = installedNode.fields();
                while (fields.hasNext()) {
                    var field = fields.next();
                    JsonNode entry = field.getValue();
                    InstalledEntry ie = new InstalledEntry(
                            safeText(entry, "name", field.getKey()),
                            safeText(entry, "version", ""),
                            safeText(entry, "type", ""),
                            safeText(entry, "installedAt", ""));
                    installed.put(ie.name(), ie);
                }
            }
            log.debug("Loaded state with {} installed packages", installed.size());
        } catch (IOException e) {
            log.warn("Failed to load state file, starting fresh: {}", e.getMessage());
            installed.clear();
        }
    }

    private synchronized void save() {
        if (!dirty) return;
        try {
            Files.createDirectories(stateFile.getParent());

            ObjectNode root = MAPPER.createObjectNode();
            root.put("version", STATE_VERSION);

            ObjectNode installedNode = MAPPER.createObjectNode();
            for (Map.Entry<String, InstalledEntry> entry : installed.entrySet()) {
                ObjectNode ie = MAPPER.createObjectNode();
                ie.put("name", entry.getValue().name());
                ie.put("version", entry.getValue().version());
                ie.put("type", entry.getValue().type());
                ie.put("installedAt", entry.getValue().installedAt());
                installedNode.set(entry.getKey(), ie);
            }
            root.set("installed", installedNode);

            DefaultPrettyPrinter printer = new DefaultPrettyPrinter()
                    .withObjectIndenter(new DefaultIndenter("  ", "\n"))
                    .withArrayIndenter(new DefaultIndenter("  ", "\n"));

            MAPPER.writer(printer).writeValue(stateFile.toFile(), root);
            dirty = false;
        } catch (IOException e) {
            log.error("Failed to save state file: {}", stateFile, e);
        }
    }

    private static String safeText(JsonNode node, String field, String fallback) {
        return node.has(field) ? node.get(field).asText(fallback) : fallback;
    }

    // ---- Data class ----

    /**
     * A single entry in the installed package state.
     */
    public record InstalledEntry(String name, String version, String type, String installedAt) {}
}
