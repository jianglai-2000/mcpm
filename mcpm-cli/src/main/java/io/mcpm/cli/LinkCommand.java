package io.mcpm.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "link",
        description = "Link a locally developed MCP server for testing.",
        mixinStandardHelpOptions = true
)
public class LinkCommand implements Callable<Integer> {

    private static final Path LINKS_FILE = Path.of(
            System.getProperty("user.home"), ".mcpm", "links.json");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @CommandLine.Parameters(paramLabel = "PATH", description = "Local path to MCP server project")
    String projectPath;

    @CommandLine.Option(names = {"-n", "--name"}, description = "Alias name in mcp.json (default: directory name)")
    String alias;

    @CommandLine.Option(names = {"-c", "--command"}, description = "Run command (default: auto-detect)")
    String command;

    @Override
    public Integer call() throws Exception {
        Path dir = Path.of(projectPath).toAbsolutePath().normalize();
        if (!Files.isDirectory(dir)) {
            System.err.println("Directory not found: " + dir);
            return 1;
        }

        // Auto-detect name from directory
        String name = alias != null ? alias : dir.getFileName().toString();

        // Auto-detect command if not specified
        String cmd = command;
        if (cmd == null) {
            if (Files.exists(dir.resolve("package.json"))) cmd = "npx";
            else if (Files.exists(dir.resolve("pyproject.toml"))) cmd = "uvx";
            else if (Files.exists(dir.resolve("Cargo.toml"))) cmd = "cargo run --package";
            else if (Files.exists(dir.resolve("go.mod"))) cmd = "go run";
            else cmd = "java -jar";
        }

        // Read existing links
        Map<String, String> links = loadLinks();
        links.put(name, dir.toString());

        // Save
        Files.createDirectories(LINKS_FILE.getParent());
        ObjectNode root = MAPPER.createObjectNode();
        var linksNode = root.putObject("links");
        links.forEach(linksNode::put);
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(LINKS_FILE.toFile(), root);

        System.out.println("✔ Linked '" + name + "' → " + dir);
        System.out.println("  Command: " + cmd);
        System.out.println();
        System.out.println("  The server is now available as '" + name + "'.");
        System.out.println("  Run 'mcpm list' to see it in your config.");

        return 0;
    }

    static Map<String, String> loadLinks() {
        if (!Files.exists(LINKS_FILE)) return new LinkedHashMap<>();
        try {
            var root = MAPPER.readTree(LINKS_FILE.toFile());
            Map<String, String> result = new LinkedHashMap<>();
            if (root.has("links")) {
                root.get("links").fields().forEachRemaining(
                        f -> result.put(f.getKey(), f.getValue().asText()));
            }
            return result;
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }
}
