package io.mcpm.cli;

import picocli.CommandLine;

import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "unlink",
        description = "Remove a linked local MCP server.",
        mixinStandardHelpOptions = true
)
public class UnlinkCommand implements Callable<Integer> {

    @CommandLine.Parameters(paramLabel = "NAME", description = "Linked server name to remove")
    String name;

    @Override
    public Integer call() throws Exception {
        Map<String, String> links = LinkCommand.loadLinks();
        if (!links.containsKey(name)) {
            System.err.println("No linked server found: " + name);
            System.err.println("  Run 'mcpm list' to see linked servers.");
            return 1;
        }

        String path = links.remove(name);

        // Save back
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var root = mapper.createObjectNode();
        var linksNode = root.putObject("links");
        links.forEach(linksNode::put);
        var linksFile = java.nio.file.Path.of(
                System.getProperty("user.home"), ".mcpm", "links.json");
        mapper.writerWithDefaultPrettyPrinter().writeValue(linksFile.toFile(), root);

        System.out.println("✔ Unlinked '" + name + "' (" + path + ")");
        return 0;
    }
}
