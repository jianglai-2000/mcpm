package io.mcpm.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mcpm.core.manifest.ManifestValidator;
import io.mcpm.core.manifest.PackageManifest;
import picocli.CommandLine;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "publish",
        description = "Publish your MCP server to the mcpm registry.",
        mixinStandardHelpOptions = true
)
public class PublishCommand implements Callable<Integer> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @CommandLine.Option(names = {"-f", "--file"}, description = "Path to mcpm.json (default: ./mcpm.json)")
    String manifestPath;

    @CommandLine.Option(names = {"-r", "--registry"}, description = "Registry URL to submit to (default: print entry)")
    String registryUrl;

    @CommandLine.Option(names = {"-y", "--yes"}, description = "Skip confirmation")
    boolean yes;

    @Override
    public Integer call() throws Exception {
        // Step 1: Read manifest
        Path manifestFile = manifestPath != null
                ? Path.of(manifestPath)
                : Path.of("./mcpm.json").toAbsolutePath().normalize();

        if (!manifestFile.toFile().exists()) {
            System.err.println("✘ No mcpm.json found at " + manifestFile);
            System.err.println("  Create one with the following structure:");
            System.err.println("  {");
            System.err.println("    \"name\": \"my-mcp-server\",");
            System.err.println("    \"description\": \"...\",");
            System.err.println("    \"type\": \"npx\",");
            System.err.println("    \"version\": \"1.0.0\",");
            System.err.println("    \"authors\": [\"You\"],");
            System.err.println("    \"homepage\": \"https://...\",");
            System.err.println("    \"repository\": \"https://...\"");
            System.err.println("  }");
            return 1;
        }

        System.out.println("Reading manifest: " + manifestFile + "\n");

        PackageManifest manifest = MAPPER.readValue(manifestFile.toFile(), PackageManifest.class);

        // Step 2: Validate
        ManifestValidator validator = new ManifestValidator();
        ManifestValidator.ValidationResult result = validator.validate(manifest);

        if (!result.warnings().isEmpty()) {
            System.out.println("Warnings:");
            for (String w : result.warnings()) {
                System.out.println("  ⚠  " + w);
            }
            System.out.println();
        }

        if (!result.valid()) {
            System.out.println("Errors:");
            for (String e : result.errors()) {
                System.out.println("  ✘ " + e);
            }
            System.out.println();
            System.err.println("✘ Validation failed. Fix the errors and try again.");
            return 1;
        }

        System.out.println("✔ Manifest is valid.");

        // Step 3: Build registry entry
        Map<String, Object> versionEntry = new LinkedHashMap<>();
        if (!manifest.defaultEnv().isEmpty()) {
            versionEntry.put("defaultEnv", manifest.defaultEnv());
        }

        Map<String, Object> handlerArgs = new LinkedHashMap<>(manifest.handlerArgs());
        if (!handlerArgs.isEmpty()) {
            versionEntry.put("handlerArgs", handlerArgs);
        }

        Map<String, Object> versions = Map.of(manifest.version(), versionEntry);

        Map<String, Object> registryEntry = new LinkedHashMap<>();
        registryEntry.put("name", manifest.name());
        registryEntry.put("description", manifest.description());
        registryEntry.put("type", manifest.type());
        registryEntry.put("latestVersion", manifest.version());
        registryEntry.put("authors", manifest.authors());
        if (manifest.license() != null) registryEntry.put("license", manifest.license());
        if (manifest.homepage() != null) registryEntry.put("homepage", manifest.homepage());
        if (manifest.repository() != null) registryEntry.put("repository", manifest.repository());
        registryEntry.put("versions", versions);

        String entryJson = MAPPER.writerWithDefaultPrettyPrinter()
                .writeValueAsString(registryEntry);

        // Step 4: Submit or output
        if (registryUrl != null) {
            System.out.println("Submitting to " + registryUrl + "...");
            String targetUrl = registryUrl.endsWith("/")
                    ? registryUrl + "api/v1/packages"
                    : registryUrl + "/api/v1/packages";

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(entryJson))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                System.out.println("✔ Package published successfully!");
                System.out.println("  " + manifest.name() + " v" + manifest.version());
                return 0;
            } else {
                System.err.println("✘ Registry responded with HTTP " + resp.statusCode());
                System.err.println("  " + resp.body());
                return 1;
            }
        }

        // No registry URL — print entry for manual submission
        System.out.println("\nRegistry entry:\n");
        System.out.println(entryJson);
        System.out.println();

        System.out.println("To publish:");
        System.out.println("  1. Copy the JSON above");
        System.out.println("  2. Open a PR to github.com/mcpm/registry");
        System.out.println("  3. Add it to registry.json");
        System.out.println();
        System.out.println("Or submit directly to a registry server:");
        System.out.println("  mcpm publish --registry https://registry.mcpm.io");

        return 0;
    }
}
