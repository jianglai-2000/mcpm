package io.mcpm.core.manifest;

import java.util.*;

/**
 * Validates a {@link PackageManifest} for completeness and correctness
 * before publishing.
 */
public class ManifestValidator {

    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "npx", "pip", "uvx", "jar", "binary", "docker");

    public record ValidationResult(boolean valid, List<String> errors, List<String> warnings) {
        public static ValidationResult ok() {
            return new ValidationResult(true, List.of(), List.of());
        }
        public static ValidationResult withErrors(List<String> errors) {
            return new ValidationResult(false, errors, List.of());
        }
    }

    public ValidationResult validate(PackageManifest m) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // name
        if (isBlank(m.name())) {
            errors.add("name is required");
        } else if (m.name().length() < 2) {
            errors.add("name must be at least 2 characters");
        } else if (!m.name().matches("^[a-zA-Z0-9@./_-]+$")) {
            errors.add("name contains invalid characters (use: letters, numbers, @, /, _, -, .)");
        }

        // type
        if (isBlank(m.type())) {
            errors.add("type is required");
        } else if (!SUPPORTED_TYPES.contains(m.type())) {
            errors.add("unsupported type '" + m.type() + "'. Supported: " + SUPPORTED_TYPES);
        }

        // version
        if (isBlank(m.version())) {
            errors.add("version is required");
        } else if (!m.version().matches("^\\d+\\.\\d+\\.\\d+(-[a-zA-Z0-9.]+)?$")) {
            errors.add("version must follow semver (e.g. 1.0.0 or 1.0.0-rc1)");
        }

        // description
        if (isBlank(m.description())) {
            warnings.add("description is recommended");
        }

        // authors
        if (m.authors().isEmpty()) {
            warnings.add("authors is recommended");
        }

        // type-specific checks
        switch (m.type()) {
            case "jar", "binary" -> {
                if (m.handlerArgs().isEmpty()
                        || (!m.handlerArgs().containsKey("downloadUrl")
                        && !m.handlerArgs().containsKey("image"))) {
                    errors.add("type '" + m.type() + "' requires handlerArgs.downloadUrl");
                }
            }
            case "docker" -> {
                if (!m.handlerArgs().containsKey("image")) {
                    warnings.add("type 'docker' should specify handlerArgs.image");
                }
            }
            case "pip" -> {
                if (!m.handlerArgs().containsKey("module")) {
                    warnings.add("type 'pip' should specify handlerArgs.module"
                            + " (defaults to package name with underscores)");
                }
            }
        }

        // repository
        if (isBlank(m.repository()) && isBlank(m.homepage())) {
            warnings.add("repository or homepage is recommended");
        }

        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
