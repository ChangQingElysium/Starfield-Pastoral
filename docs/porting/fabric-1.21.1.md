# Fabric 平台维护

## 源码职责

共享业务位于根目录 `src/main`；`scripts/port/prepare_fabric_sources.py` 在构建时转换为 Fabric 源码。
`fabric/src/main/java/com/stardew/craft/platform/` 保存平台实现，`fabric/src/overrides/java/` 保存
不能机械转换的来源覆盖。覆盖绑定原文件 SHA256；上游改变时逐份审查差异，不手改生成文件或跳过来源检查。
`fabric/native-resources.gradle` 使用主线登记的模型母本编译资源；生成文件、依赖缓存和测试报告均不入 Git。

## 接入契约

- 自动事件索引按每个方法的事件参数类型区分 MOD/GAME 总线；显式注册保持调用者选定的总线。
  必须分别核对生产事件的时机、取消语义、线程与实际消费者，不能仅凭有事件类判断功能完整。
- 客户端世界与区块生命周期由平台 Mixin 发布；加载、替换、断线与区块移除要保持清理回调可达。
- 嵌套传送门渲染为每次 `renderLevel` 保存独立帧，在正常和异常退出时恢复外层矩阵、相机和阶段。
  FAPI 的可变上下文不能当作不可变快照保存。
- Jade/JEI 等可选集成通过 Fabric metadata 注册；无依赖时不得提前加载其类型。
- 持久化、网络、模型变换、粒子、GUI 与渲染桥分别维护平台回归；共享资源不能因移植困难被静默删除。

## 已知契约差异

`AFTER_LEVEL` 在 NeoForge 的外层 GameRenderer 返回点发布，而 Fabric 桥使用每次
LevelRenderer 的 FAPI END，包括嵌套门视图；其 pose 语义也不同。此差异不能由帧恢复修复冒充已消除。
新增该阶段消费者前需核对并对齐发布边界。它不是全功能兼容结论。
原生流体放置钩子的取消标志与替换状态消费不同，不应把原实现未消费的取消标志报告为移植成功。

## 验证入口

从仓库根目录执行 `./gradlew -p fabric check auditClientMixins`；
CLIENT 字节码验证的隔离方式与成功判据见 [Mixin 验证](fabric-mixin-audit.md)。
生产包通过 `remapJar packageFabricCandidate` 生成，生产服务端保存/重开检查由
`compatibility/run_fabric_release_smoke.py` 执行。发布的完整命令由 CI 工作流维护。

回归报告放 `fabric/build/reports/` 并由 Actions 上传附件；不在本文累加旧测试数字或本机日志。
实机验收记录准确安装包、加载器、光影、场景和现象，不能用无窗口检查替代。
