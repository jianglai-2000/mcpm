# mcpm — MCP Package Manager 🐱

<div align="center">

[English](README.md) | [中文](README-zh.md)

</div>

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

- **10 package types**: `npx` · `pip` · `uvx` · `jar` · `binary` · `docker` · `go` · `deno` · `bun` · `cargo`
- **Auto-detect**: finds `mcp.json`, `claude_desktop_config.json`, Cursor configs
- **Safe writes**: timestamped backups before every modification
- **Version tracking**: knows what's installed, powers `mcpm update`
- **Service discovery**: scan local processes, ports, and configs for MCP servers
- **Multi-environment**: dev/staging/production isolated configs with `mcpm env`
- **Health monitoring**: `mcpm health --watch` continuous uptime checks
- **Offline cache**: cached registry + packages work without network
- **Shell completion**: bash/zsh/fish with one-command `mcpm completion --install`
- **Security audit**: `mcpm audit` detects outdated packages and plaintext secrets
- **Extensible by design**: SPI-based plugin architecture, zero-dependency API

## Commands

| Command | Description |
|---------|-------------|
| `search <q>` | Search the package registry |
| `info/view/show <pkg>` | Show package details with version list |
| `install [pkg]` | Install (or interactive mode with no args) |
| `uninstall <pkg>` | Remove from config + clean up (`--purge`) |
| `list` | Show installed servers |
| `update [pkg]` | Upgrade to latest version |
| `detect` | Find config files on this machine |
| `init` | Create a fresh mcp.json or project template |
| `publish` | Publish your package to the registry |
| `completion` | Generate shell completion scripts |
| `config` | View/modify global settings |
| `why <pkg>` | Explain why a package is installed |
| `audit` | Security audit of installed packages |
| `docs <pkg>` | Open package homepage in browser |
| `diff <pkg>` | Compare installed vs latest version |
| `outdated` | List packages with newer versions available |
| `backup` | Backup/restore config and state |
| `env` | Multi-environment management |
| `link/unlink` | Link local projects for development |
| `health` | Check if servers are responsive (`--watch`) |
| `discover` | Find MCP services running on this machine |

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
| Modules | 18 Maven modules |
| CLI commands | 24 (search, info, install, uninstall, list, update, detect, init, publish, completion, config, why, audit, docs, diff, outdated, backup, env, link, unlink, health, discover, help) |
| Package handlers | 10 (npx, pip, uvx, jar, binary, docker, go, deno, bun, cargo) |
| Tests | 140+ unit tests |
| CI | 3 OS × 2 JDK (17, 21) |
| Dependencies | Runtime: Picocli, Jackson, SLF4J. SPI: zero. |

## License

Apache 2.0
