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

## Building

```bash
git clone https://github.com/mcpm/mcpm.git
cd mcpm
mvn package -DskipTests
java -jar mcpm-cli/target/mcpm-cli-0.1.0-SNAPSHOT.jar
```

## License

Apache 2.0
