# StardewCraft Fabric 1.21.1

本工程将当前 NeoForge 1.21.1 主线的全部 4411 个 Java 文件及正式资源移植到 Fabric，保持内容 ID。当前为候选测试版；客户端画面、GUI 操作及可选渲染模组组合仍需实机验收。

## 安装

使用 Minecraft 1.21.1、Java 21、Fabric Loader 0.19.5。将候选包 `mods/` 中三个 JAR 装入现有 Fabric 1.21.1 测试实例：

- StardewCraft Fabric 候选 JAR。
- Fabric API 0.116.17+1.21.1。
- Forge Config API Port 21.1.6 Fabric 版。

Porting Lib 所需五个模块及 Maven 版本比较库已内嵌。Jade、JEI、AppleSkin 是可选集成；按对应的 Fabric 版本安装。Sodium、Iris、Continuity 与旧存档完整迁移尚未获得实机验收，不将它们列为已通过的组合。

## 0.6.3fix2 同步与发布验证

本次直接生成全部 4411 个主线 Java 文件和当前资源，59 个严格 SHA256 来源覆盖均与上游匹配。传送门、机器渲染、木乃伊炸弹、Joja、巴士、姜岛船只和棕榈退役迁移随共享源更新，不另建易漂移的 Fabric 分支。

GitHub `Build` 增加 Fabric 作业：完整平台测试、真实 CLIENT Mixin 字节码审计、正式重映射 JAR、安装 ZIP，以及隔离生产专服创建/保存/重开读取检查。最新结果以该版本提交的 Actions 为准；下方 fix1 历史结果不冒充 fix2 验收。

## 历史验证证据（2026-10-15，fix1 开发候选）

- 完整源编译、资源合并及 `check auditClientMixins remapJar` 已通过；755 项平台测试均通过，0 失败、0 错误、0 跳过；12 语言检查与 39 项 Python 来源/转换/索引 fixture 通过。
- 无窗口 CLIENT Mixin 实际字节码转换为 199 个目标成功、23 个未安装的可选目标跳过、0 失败；禁止游戏和图形初始化，不代替客户端启动与资源烘焙。
- 20 个定向 GameTest 的最后一次结果均 PASS，未执行全部 908 项。Solar 曾因未加载区块而在 Y=-64 测试失败两次；预加载后同一测试在 Y=75 通过，保留历史失败，业务与断言未改。
- 开发映射隔离专服的正常地形创建、保存、停止、重开读取持久 marker 已通过。正式生产映射候选 JAR 也已在独立普通世界启动，并完成原生 RCON 保存、五维落盘及正常停止；最终候选也已重开同一存档并读取旧持久 marker，随后再次完成五维保存和正常停止。
- 正式候选的两轮真实 socket 协议 peer 均完成 configuration 任务 ACK、观测 PLAY 登录包、主动断开和重连。manifest ACK 回送服务端声明，未知业务及世界包只作不透明观察；未构造客户端，不据此宣称全部业务 handler、菜单、GUI 或客户端 registry 应用已通过。

构建日志为 `fabric/build/reports/completion-final-validation.log`，生产专服日志为 `completion-candidate-save.log` / `completion-candidate-reopen.log`，两轮协议报告为 `protocol-peer-completion-candidate-sessions.json`，定向测试摘要为 `directed-gametests-summary.json`（上述报告同在 `fabric/build/reports/`）。[完整进度、边界与参考报价](../docs/porting/fabric-1.21.1.md)及 [Mixin 审计与测试环境说明](../docs/porting/fabric-mixin-audit.md)分开记录开发映射、生产映射与客户端验收。[候选 ZIP](build/distributions/stardewcraft-fabric-0.6.3fix2-candidate.zip) 内含三份必需 JAR、中文安装说明与 SHA-256 清单；这些本地验证不代替发布用干净检出/CI gate。

本轮完成审计修复了客户端世界/区块事件缺失、自动订阅总线分类和 Jade/JEI 插件发现入口；[事件闭包审计](../docs/porting/fabric-event-closure-audit.md)与[完成度审计](../docs/porting/fabric-completion-audit.md)记录具体调用链与边界。403 个物理客户端订阅类已在真实 Knot 中完成类定义和方法签名解析，未执行订阅处理函数或游戏初始化。

## 开发与验证

本机已提供配齐 JEI、Jade、Xaero 两件套、JECh、Sodium 和 Iris 的独立测试入口：从仓库根目录运行 `./启动Fabric测试.command`。规格、安装锁文件、无窗口准备和 Java 断点调试见 [本机测试环境](CLIENT.md)。

从仓库根目录运行，环境使用 JDK 21：

```sh
./gradlew -p fabric classes
./gradlew -p fabric check auditClientMixins
python3 -m unittest discover -s compatibility -p 'test_*fabric*.py'
```

安装 JAR 通过 `./gradlew -p fabric remapJar` 生成；`./gradlew -p fabric packageFabricCandidate` 将它与两个原始必需依赖、安装说明和 SHA-256 清单封装到 `fabric/build/distributions/`。独立生产映射专服使用 `./gradlew -p fabric runFabricCandidateServer`，目录为 `fabric/run-candidate-server`；使用正式重映射 JAR 与原始运行依赖。开发专服目录为 `fabric/run-server`。两者均无界面，不读取主线或共同工坊存档。

定向游戏测试可通过 `./gradlew -p fabric runServer -PfabricSmokeGameTests=true` 开启单项测试命令与逐项报告。该选项不自动选择或执行全部测试。依赖天空的测试先加载目标区块，确认 `execute if loaded`，再串行运行并等待完成记录；每次 `test run` 会清除之前尚未完成的测试。

## 维护入口

- 平台实现：`fabric/src/main/java/com/stardew/craft/platform/fabric/`。
- 独立 Mixin 包：`fabric/src/main/java/com/stardew/craft/platform/mixin/`。
- 严格来源覆盖：`fabric/src/overrides/java/`；每个文件固定原文件 SHA256。
- 全源生成器：`scripts/port/prepare_fabric_sources.py`；上游变化会明确失败，重新审查后更新覆盖。
- 审计、证据与参考报价：`docs/porting/fabric-1.21.1.md`。
- 无窗口 Mixin 验证与定向测试环境说明：`docs/porting/fabric-mixin-audit.md`。

候选包的编译、字节码转换、专服与协议检查分别记录；这些证据不替代真实客户端的资源和操作验收。源码与 GitHub 验证工作流随 `main` 一起维护。
