# mcpm — MCP 包管理器 🐱

<div align="center">

[English](README.md) | [中文](README-zh.md)

</div>

**在命令行中发现、安装和管理 MCP 服务器。**

```bash
mcpm search filesystem          # 查找 MCP 服务器
mcpm install @org/my-server     # 安装 + 自动配置
mcpm list                       # 查看已安装的服务
mcpm update                     # 一键升级所有包
mcpm init                       # 创建全新的 mcp.json
```

## 快速开始

```bash
# 下载最新版本
curl -sSfL https://github.com/mcpm/mcpm/releases/latest/download/mcpm.jar -o mcpm.jar
alias mcpm='java -jar mcpm.jar'

# 搜索服务器
mcpm search postgres

# 安装一个
mcpm install mcp-server-postgres

# 重启你的 AI 客户端，大功告成！
```

## 功能特性

- **10 种包类型**: `npx` · `pip` · `uvx` · `jar` · `binary` · `docker` · `go` · `deno` · `bun` · `cargo`
- **自动检测**: 自动查找 `mcp.json`、`claude_desktop_config.json`、Cursor 配置
- **安全写入**: 每次修改前自动创建时间戳备份
- **版本追踪**: 记录已安装版本，支持 `mcpm update` 升级
- **服务发现**: 扫描本机进程、端口和配置，发现运行中的 MCP 服务
- **多环境管理**: dev/staging/production 隔离配置
- **健康监控**: `mcpm health --watch` 持续检查服务状态
- **离线缓存**: 已缓存的包和注册表离线也可用
- **多架构**: 自动选择 linux-amd64 / darwin-arm64 / win-x64
- **安全审计**: `mcpm audit` 检测过期包和明文密钥

## 命令列表

| 命令 | 描述 |
|------|------|
| `search <q>` | 搜索注册表中的包 |
| `info/view/show <pkg>` | 查看包详情（版本列表、下载链接） |
| `install [pkg]` | 安装并写入配置（无参数进入交互模式） |
| `uninstall <pkg>` | 从配置中移除并清理（`--purge` 清除缓存） |
| `list` | 列出已安装的服务器 |
| `update [pkg]` | 升级到最新版本 |
| `detect` | 扫描本机的 MCP 配置文件 |
| `init` | 创建 mcp.json 或生成 MCP 服务端脚手架 |
| `publish` | 发布你的包到注册表 |
| `completion` | 生成 shell 补全脚本 |
| `config` | 查看和修改全局设置 |
| `why <pkg>` | 查看某个包为什么被安装 |
| `audit` | 安全审计：检测过期包和配置问题 |
| `docs <pkg>` | 在浏览器中打开包的主页 |
| `diff <pkg>` | 比较已安装版本和最新版本 |
| `outdated` | 列出所有可升级的包 |
| `backup` | 备份和恢复配置与状态 |
| `env` | 多环境管理 |
| `link/unlink` | 本地开发链接测试 |
| `health` | 服务健康检查（`--watch` 持续监控） |
| `discover` | 发现本机运行中的 MCP 服务 |

## 10 种包处理器

```
npx      Node.js — 即用即装，不占空间
pip      Python — 需先 pip install
uvx      类似 npx，但用于 Python 生态
jar      Java JAR — 自动下载到 ~/.mcpm/jars/
binary   原生二进制 — 自动选择系统架构
docker   Docker 镜像 — docker run 一键启动
go       Go 模块 — go run 临时运行
deno     Deno 模块 — deno run --allow-all
bun      Bun 运行时 — bun run
cargo    Rust 包 — cargo run
```

## 项目结构

```
mcpm/
├── mcpm-spi/                  扩展接口（零依赖）
├── mcpm-core/                 核心编排 + 状态管理
├── mcpm-cli/                  命令行入口（Picocli）
├── mcpm-handler-*/            包处理器（6 + 4）
├── mcpm-config-mcpjson/       mcp.json / claude_desktop_config.json
├── mcpm-registry-static/      内置包注册表
├── mcpm-registry-server/      HTTP 注册表服务器（可独立运行）
├── mcpm-bom/                  依赖版本管理
├── .github/workflows/         CI + Release 自动化
├── .homebrew/                  Homebrew 安装公式
├── .scoop/                     Scoop 安装清单
└── .docker/                    Docker 镜像
```

## 安装方式

```bash
# MacOS + Linux (Homebrew)
brew install mcpm/tap/mcpm

# Windows (Scoop)
scoop bucket add mcpm https://github.com/mcpm/scoop-mcpm
scoop install mcpm

# Docker
docker run mcpm/mcpm

# 或直接下载 JAR
curl -sSfL https://github.com/mcpm/mcpm/releases/latest/download/mcpm.jar -o mcpm.jar
alias mcpm='java -jar mcpm.jar'
```

## 自己搭建注册表

```bash
# 启动本地注册表服务器
java -jar mcpm-registry-server.jar --port 8080 --data ./registry.json

# 发布包到本地服务器
mcpm publish --registry http://localhost:8080
```

## 构建

```bash
git clone https://github.com/mcpm/mcpm.git
cd mcpm
./mvnw package -DskipTests
java -jar mcpm-cli/target/mcpm-cli-*.jar
```

## 项目统计

| 指标 | 数据 |
|------|------|
| 模块数 | 18 个 Maven 模块 |
| CLI 命令 | 24 个 |
| 包处理器 | 10 种 |
| 测试用例 | 140+ 单元测试 |
| CI | 3 个系统 × 2 个 JDK 版本 |
| 运行时依赖 | Picocli、Jackson、SLF4J |
| SPI 依赖 | 0（零依赖扩展点） |

## 许可证

Apache 2.0 — 详见 [LICENSE](LICENSE) 和 [LICENSE-zh](LICENSE-zh)。
