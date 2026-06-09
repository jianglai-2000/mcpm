# Contributing to mcpm / 参与贡献

Thanks for your interest! 🐱
感谢你对 mcpm 感兴趣！

---

## English

### Quick Start

```bash
git clone https://github.com/mcpm/mcpm.git
cd mcpm
./mvnw compile
```

### Adding a New Package Handler

1. Create a new module: `mcpm-handler-<type>/`
2. Implement `PackageHandler` (from `mcpm-spi`)
3. Register via SPI: `META-INF/services/io.mcpm.spi.PackageHandler`
4. Add module to parent `pom.xml` and `mcpm-bom/pom.xml`
5. Add dependency to `mcpm-cli/pom.xml`

See `mcpm-handler-npx/` as a minimal example (~30 lines).

### Code Style

- 4-space indent, no tabs
- Java 17+ (records, sealed, pattern matching)
- Avoid star imports
- JUnit 5 + AssertJ for tests
- Public API needs JavaDoc

### Testing

```bash
./mvnw test
./mvnw verify  # includes Checkstyle + SpotBugs
```

### Pull Request Process

1. Open an issue first for significant changes
2. Keep PRs focused — one feature per PR
3. Include tests for new functionality
4. Update README.md if needed

---

## 中文

### 快速开始

```bash
git clone https://github.com/mcpm/mcpm.git
cd mcpm
./mvnw compile
```

### 添加新的包处理器

1. 创建新模块：`mcpm-handler-<类型>/`
2. 实现 `PackageHandler` 接口（来自 `mcpm-spi`）
3. 通过 SPI 注册：`META-INF/services/io.mcpm.spi.PackageHandler`
4. 在父 `pom.xml` 和 `mcpm-bom/pom.xml` 中添加模块
5. 在 `mcpm-cli/pom.xml` 中添加依赖

参考 `mcpm-handler-npx/` 作为最小示例（约 30 行）。

### 代码风格

- 4 空格缩进，不使用 Tab
- Java 17+（records、sealed、pattern matching）
- 避免星号导入
- JUnit 5 + AssertJ 测试框架
- 公开 API 需要 JavaDoc

### 测试

```bash
./mvnw test
./mvnw verify  # 包含 Checkstyle + SpotBugs
```

### PR 流程

1. 重大变更先提 issue 讨论
2. 保持 PR 聚焦——一个 PR 一个功能
3. 新功能包含测试
4. 如有需要更新 README.md

---

## License / 许可证

Apache 2.0 — by contributing you agree to license your work under this.
参与贡献即表示你同意以本许可证授权你的工作。
