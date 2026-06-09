# Contributing to mcpm

Thanks for your interest! 🐱

## Quick Start

```bash
git clone https://github.com/mcpm/mcpm.git
cd mcpm
./mvnw compile
```

## Adding a New Package Handler

1. Create a new module: `mcpm-handler-<type>/`
2. Implement `PackageHandler` (from `mcpm-spi`)
3. Register via SPI: `META-INF/services/io.mcpm.spi.PackageHandler`
4. Add module to parent `pom.xml` and `mcpm-bom/pom.xml`
5. Add dependency to `mcpm-cli/pom.xml`

See `mcpm-handler-npx/` as a minimal example (~30 lines).

## Code Style

- 4-space indent, no tabs
- Java 17+ (records, sealed, pattern matching)
- Avoid star imports
- JUnit 5 + AssertJ for tests
- Public API needs JavaDoc

## Testing

```bash
./mvnw test
./mvnw verify  # includes Checkstyle + SpotBugs
```

## Pull Request Process

1. Open an issue first for significant changes
2. Keep PRs focused — one feature per PR
3. Include tests for new functionality
4. Update README.md if needed

## License

Apache 2.0 — by contributing you agree to license your work under this.
