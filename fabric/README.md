# StardewCraft Fabric 1.21.1

Fabric 与 NeoForge 在 `main` 中共享玩法源码和正式资源。此目录是 Fabric 平台适配和独立构建入口，
不维护另一份玩法分支。生成源码位于 `build/generated/`，不提交进 Git。

## 安装

从 [GitHub Releases](https://github.com/ChangQingElysium/Starfield-Pastoral/releases) 下载对应 Fabric 安装包。
Minecraft 1.21.1 使用 Java 21；加载器及必需依赖见 [安装说明](INSTALL.md)。
当前 Fabric 为实验性 alpha；客户端画面、GUI、光影及旧存档迁移须按实际组合验收。
构建、服务端和 Mixin 检查通过不等于全部光影兼容。

## 开发

在仓库根目录运行：

```sh
./gradlew -p fabric classes
./gradlew -p fabric check auditClientMixins
python3 -m unittest discover -s compatibility -p 'test_*fabric*.py'
```

安装包任务为 `remapJar`，含必需依赖的分发 ZIP 任务为 `packageFabricCandidate`。
完整发布检查以根目录 `.github/workflows/build.yml` 为准；结果保存在对应提交的 Actions 中。

- [平台维护边界](../docs/porting/fabric-1.21.1.md)
- [Mixin 验证方式与限制](../docs/porting/fabric-mixin-audit.md)
- [本机客户端启动与调试](CLIENT.md)
- [多版本同步与发布](../docs/forge-1.20.1-migration-plan.md)
