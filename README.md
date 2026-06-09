# mcpm — MCP Package Manager 🐱

**Discover, install, and manage MCP servers from the command line.**

```bash
mcpm search filesystem          # Find MCP servers
mcpm install @org/my-server     # Install + auto-configure
mcpm list                       # See what's installed
mcpm update                     # Upgrade everything
mcpm init                       # Create a fresh mcp.json
```

## Quick Start

```bash
# Download the latest release
curl -sSfL https://github.com/mcpm/mcpm/releases/latest/download/mcpm.sh | sh

# Search for servers
mcpm search postgres

# Install one
mcpm install mcp-server-postgres

# Restart your AI client and enjoy!
```

## Features

- **6 package types**: `npx` · `pip` · `uvx` · `jar` · `binary` · `docker`
- **Auto-detect**: finds `mcp.json`, `claude_desktop_config.json`, Cursor configs
- **Safe writes**: timestamped backups before every modification
- **Version tracking**: knows what's installed, powers `mcpm update`
- **Extensible by design**: SPI-based plugin architecture
- **Zero-runtime-dependency SPI**: the extension API has no dependencies

## Commands

| Command | Description |
|---------|-------------|
| `search <q>` | Search the package registry |
| `info <pkg>` | Show package details |
| `install <pkg>` | Install + write to config |
| `uninstall <pkg>` | Remove from config + clean up |
| `list` | Show installed servers |
| `update [pkg]` | Upgrade to latest version |
| `detect` | Find config files on this machine |
| `init` | Create a fresh mcp.json |

## Project Structure

```
mcpm/
├── mcpm-spi/                  Extension interfaces (zero deps)
├── mcpm-core/                 Orchestration + state management
├── mcpm-cli/                  CLI entry point (Picocli)
├── mcpm-handler-*/            Package handlers (npx, pip, jar, …)
├── mcpm-config-mcpjson/       mcp.json / claude_desktop_config.json
├── mcpm-registry-static/      Built-in package registry
├── mcpm-bom/                  Bill of Materials
└── .github/workflows/ci.yml   CI: 3 OS × 2 JDK
```

## Running Your Own Registry

```bash
# Start a local registry server
java -jar mcpm-registry-server/target/mcpm-registry-server-0.1.0-SNAPSHOT.jar \
  --port 8080 --data ./registry.json

# Publish your package to it
mcpm publish --registry http://localhost:8080
```

## Building

```bash
git clone https://github.com/mcpm/mcpm.git
cd mcpm

# Using Maven wrapper (no pre-installed Maven needed)
./mvnw package -DskipTests

# Run the CLI
java -jar mcpm-cli/target/mcpm-cli-0.1.0-SNAPSHOT.jar

# Run your own registry server
java -jar mcpm-registry-server/target/mcpm-registry-server-0.1.0-SNAPSHOT.jar
```

## Project Stats

| Metric | Value |
|--------|-------|
| Modules | 14 Maven modules |
| CLI commands | 9 (search, info, install, uninstall, list, update, detect, init, publish) |
| Package handlers | 6 (npx, pip, uvx, jar, binary, docker) |
| Tests | 80+ unit tests |
| CI | 3 OS × 2 JDK (17, 21) |
| Dependencies | Runtime: Picocli, Jackson, SLF4J. SPI: zero. |

## License

Apache 2.0
