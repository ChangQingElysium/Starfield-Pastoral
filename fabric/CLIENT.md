# Fabric 1.21.1 本机测试环境

从仓库根目录执行以下一条命令，或双击根目录的 `启动Fabric测试.command`：

```sh
./启动Fabric测试.command
```

启动前自动检查 Java 21、辅助模组哈希及重复客户端，编译当前 Fabric 源码，然后冻结本轮运行所需的类和资源。保留终端，退出游戏后任务结束。测试使用开发玩家 `FabricDev`，不读取启动器账号或密码。

## 规格与目录

采用日常主线测试环境的中文、1280×720 窗口、GUI 4、视距 21、模拟距离 18、140 FPS 上限及垂直同步。游戏堆独立设置为最小 2 GiB、最大 8 GiB；Gradle 的 3 GiB 是构建进程堆。首次生成配置后保留用户对画质、按键和声音的修改。

- 游戏及新测试存档：`fabric/run-client/`。
- 辅助模组原始文件：`fabric/.local/client-mods/`。Loom 将它们重映射后加入运行类路径，故 `run-client/mods/` 留空；不要再放第二份辅助模组或本模组 JAR。
- 模组固定版本、来源和 SHA512/SHA256：`fabric/client-mods.lock.json`。
- 安装检查报告：`fabric/build/reports/client-environment-install.json`。
- 启动阶段日志：`fabric/run-client/logs/launcher.log`。Gradle、Java 命令或快照准备失败时，Minecraft 自己的日志可能尚未生成；优先查看这个文件。
- 游戏日志：`fabric/run-client/logs/latest.log`；崩溃报告：`fabric/run-client/crash-reports/`。

此实例使用独立存档；主线 `run/`、Forge 1.20.1 实例及共同姜岛工坊不参与配置或复制。

## 已安装模组

| 模组 | 固定 Fabric 版本 |
| --- | --- |
| JEI | 19.32.0.358 |
| Jade | 15.10.4 |
| Xaero 世界地图 | 1.40.11 |
| Xaero 小地图 | 25.3.10 |
| JustEnoughCharacters（JECh 拼音搜索） | 4.5.27 |
| Sodium | 0.6.13 |
| Iris | 1.8.8 |

Fabric Loader 0.19.5、Fabric API 0.116.17+1.21.1、Forge Config API Port 21.1.6 与所需 Porting Lib 模块由工程提供。JECh 已内嵌 PinIn，Xaero 两件套已内嵌 XaeroLib；Sodium 0.6 自带 FRAPI，无需另装 Indium。Iris 默认关闭光影；可在游戏的视频设置中选择自己的光影包。

## 无窗口准备与断点调试

重新核对或补装固定模组，不启动游戏：

```sh
python3 fabric/client.py install
python3 fabric/client.py prepare
```

`prepare` 准备 Minecraft 资源、原生库和实际运行参数，并验证客户端快照隔离，全程不打开游戏窗口。后续只核对安装状态可运行 `python3 fabric/client.py check`。

检查真实 `runClient` 任务和 Java 子进程命令，仍不运行 Minecraft：

```sh
python3 fabric/client.py verify-launch
```

该检查使用开发专用 Javaagent，在 `premain` 核对实际类路径、冻结资源、启动配置与 JVM 参数后立即退出；Minecraft、Blaze3D 和 LWJGL 类加载数必须均为零。普通启动不加入此探针，`verify-launch` 不与 `--debug` 同时使用。

Java 断点调试使用：

```sh
./启动Fabric测试.command --debug
```

此模式等待 IDE 连接本机 `localhost:5005` 后继续启动。可在 IDE 中附加远程 JVM；移植后的完整源位于 `fabric/build/generated/sources/stardewcraft/`，平台代码位于 `fabric/src/main/java/`。正常游玩测试不需要加 `--debug`。

工程规则禁止代理另开测试客户端，因此安装、依赖解析及无窗口验证不等同于实际游戏启动或画面验收。首次进入游戏后检查 JEI 拼音检索、Jade 提示、两张地图、视频设置与存读档；需要排查时提供该实例的日志即可。

## 本次准备验证（2026-10-15）

辅助模组八份原件（含工程已提供的 Fabric API）及五个唯一内嵌库全部通过哈希检查。完整源码编译与 39 项移植来源检查通过；实际 Loom 启动参数确认了 macOS 首线程、2–8 GiB 游戏堆及可选的本机调试参数。冻结快照检查覆盖 155 项运行类路径和 77 个输入，检查后已移除自己的测试快照。

配齐辅助模组时，真实 Knot CLIENT 转换为 219 个目标成功、3 个未安装的其他可选目标跳过、0 失败；基础配置为 199 个成功、23 个可选目标跳过、0 失败。两种配置下 403 个客户端订阅类的定义和方法签名解析均无错误。检查禁止游戏、GLFW 和图形初始化，不据此宣称实际游戏或着色器渲染已验收。

本轮同时修正了 Fabric Iris 的版本门，并修复 Sodium 覆盖区块渲染方法后与移植层事件注入的冲突：原版保留原有事件位置，Sodium 使用其同版官方平台事件出口；新选择器已核对实际生产映射。

证据保存在 `build/reports/client-environment-install.json`、`client-runtime-snapshot-check.json`、`client-runtime-snapshot-debug-check.json`，以及 `mixin-audit/client-transform-client-mods.json` 和 `client-transform-base-after-sodium-fix.json`。这些是本机开发环境证据，没有打包、提交或发布新候选 JAR。

## 2026-10-16 启动前失败修复

用户实际启动在 Minecraft 日志生成前失败：`runClient` 的 `internalClasspath` 已被 Gradle 固定，原脚本仍在任务 `doFirst` 中尝试改写。此前的 `checkFabricClientSnapshot` 只检查未执行任务的配置，漏掉了这个执行阶段。

快照改由依赖准备任务生成，在 `runClient` 输入固定前完成；真实启动阶段只读验证，结束清理不再恢复已固定的输入。失败清理保留原始异常。新增的 `verify-launch` 已通过真实任务执行与 Java 子进程检查，报告位于 `build/reports/client-launch-command-probe.json`；原始失败摘要保存在 `client-launch-2026-10-16-failure.log`。

macOS 的 `-XstartOnFirstThread` 会被 Java 启动器消费，不一定出现在 JVM 的运行参数列表；子进程检查同时读取本进程对应的首线程启动标记，只输出布尔结果。该检查证明启动命令和快照时序，仍不替代实际游戏初始化及画面验收。

## 2026-10-16 沉浸式传送门崩溃修复

用户的实际客户端报告 `crash-2026-10-16_03.34.12-client.txt`：进入世界并打开沉浸式传送门后，外层透明区块绘制抛出 `Native level draw stage has no matching installed Fabric frame`。传送门会重入同一个 `LevelRenderer`；旧桥接层在内层 Fabric END 回调清除了外层仍需使用的帧。Fabric 同时复用一份可变渲染上下文，简单保存上下文引用也无法恢复外层相机和矩阵。

修复为每次原生 `renderLevel` 调用独立保存参数与实际局部 PoseStack，并在 `finally` 中恢复上层帧，覆盖内层正常返回及异常中断。6 项渲染桥回归包含同一 Fabric 上下文被内层覆盖、内层缺少 END、外层异常后下一帧恢复；不以吞掉异常或跳过绘制阶段来规避问题。

同份实机日志还发现 Fabric 未注册 NeoForge 模型变换解析器。现已在原生 BlockModel Gson 中补齐矩阵与 TRSR 解析，未改共享模型 JSON。7 项模型回归使用实际导入几何加载器，覆盖日志中失败的 54 个原模型、1700 多个嵌套变换，以及两份此前静默产生空矩阵的旋转模型。主线既有的五个空面碰撞模型及 Joja 超市货箱的错误父模型不属于此移植缺口，仍待其各自任务修复。

本次 `classes platformTest auditClientMixins -PfabricClientTestMods=true` 通过，765 项平台测试零失败。带上述全部辅助模组时 220 个实际客户端目标完成 Mixin 转换、3 个未安装可选目标跳过；基础配置为 200 个完成、23 个跳过。两种配置均为零转换失败、403 个客户端订阅类链接无误。这些检查不打开游戏窗口，实际传送门画面与连续进出仍须在重启后的现有实例验证。

证据：`build/reports/portal-and-model-full-validation.log`、`portal-and-model-base-audit.log`、`mixin-audit/client-transform-portal-model-client-mods.json`、`mixin-audit/client-transform-portal-model-base.json`；原始崩溃报告与日志保存在 `build/reports/portal-frame-crash-2026-10-16/`。当前修复用于源码启动入口，未打包、提交或发布新候选 JAR。

## 2026-10-16 同类问题排查

继续排查后，额外确认并修复四处 Fabric 功能缺口：

- 屏幕桥原先丢弃 `GameRenderer` 传入的原绘制操作，绕过 FAPI `ScreenEvents.beforeRender/afterRender`。当前 JEI 的界面后绘制实际订阅后者，因此会受到影响。现将原操作穿过原有五参 viewport 入口后执行，保留鼠标坐标重算、Pre/Post 取消规则，以及嵌套绘制和异常退出时的状态恢复。5 项定向回归与真实合并字节码检查通过。
- 普通 `BlockModel` 的根变换此前被解析但未参与烘焙。真实模型探针确认两尊雕像物品、疑虑雕像、放置路灯及空心原木共 5 个模型受影响。现按 NeoForge 21.1.217 将根变换合入面烘焙与剔除方向；非直角变换保留真实顶点，避免原生绕序整理将其压成轴向矩形。6 项定向回归覆盖原文件、父模型继承、子模型 identity 覆盖、UV lock、剔除方向和自定义几何旁路。内建/生成模型保留原有路径，不将本次证据扩大到生成器元数据迁移。
- 普通模型的 `render_type` 提示未被消费，魔法传送圈和暗影脚印声明的透明层落入默认实心路径。现将普通模型的提示接到真实 FRAPI 材质，保留原生几何、父级提示继承和外层模型包装的几何编辑；自定义加载器仍使用自身材质。8 项回归包含两份原 JSON 到实际 Mesh 的透明材质，以及 solid/cutout/mipped、物品模式和包装模型的定向检查，未按含提示文件总数冒算受损模型。
- 模板方块的 `getParticleIcon(ModelData)` 原先没有粒子消费者，填充材质未传到敲击、破坏、奔跑和落地粒子。现由明确来源方块的位置读取模型数据，再由模型选择粒子贴图；未知来源保持 EMPTY 数据查询，普通原生模型保留原调用链。源位置不加入网络编码，保留 Neo 的远程未知位置回退。7 项回归覆盖这些入口及源位置、模型数据和贴图的实际传递。

最终统一 `classes platformTest auditClientMixins -PfabricClientTestMods=true` 通过，791 项平台测试零失败/错误/跳过。全部辅助模组配置为 224 个目标完成转换、3 个未安装可选目标跳过；基础配置为 204 个完成、23 个跳过。两种配置均为零失败、403 个客户端订阅类链接无误。报告为 `build/reports/client-similar-issues-final-validation.log`、`client-similar-issues-final-base-audit.log` 及 `mixin-audit/client-transform-similar-issues-final-{client-mods,base}.json`。

粒子新增的 3 个 common Mixin 还独立通过真实 SERVER 转换：逐项确认源位置接口/字段/读写方法以及原生奔跑、落地方法到源位置处理器的调用。源码配置、处理资源及转换时配置 SHA 一致；报告为 `build/research/terrain-particle-audit/current-server-particle-contract.json`。这是无游戏初始化的挂载和调用链检查，不是本轮专服启动或粒子画面的验收。

用户自行在 04:13 启动的本机日志提供了额外资源证据：失败模型从 59 个降至主线既有的 5 个空面模型，原 54 个解析错误消失；该轮世界正常保存，未出现原帧状态异常或传送门失败后禁用记录。日志及逐项差异保存在 `build/reports/client-similar-issues-audit-2026-10-16/`。这次运行早于本节四项修复，不能作为它们的实机证据；这些修复须下次启动后检查。没有修改共享模型、贴图或用户存档，也没有启动或操作用户客户端。

渲染重入、HUD、资源重载、世界切换及客户端数据桥的定向只读审查未再确认新的同类秒崩路径。未消费的 `AFTER_LEVEL` 边界差异仍见 [事件审计](../docs/porting/fabric-event-closure-audit.md)；本次结论不代表全功能游玩或全部可选组合已验收。

## 2026-10-16 主动自查

本轮围绕退出／重连、菜单同步、存档生命周期和客户端输入继续检查，修复三处：

- **退出／重连后的旧网络回调。** FAPI 将 play 回调排入客户端任务队列，但 `Context.player()` 在执行时读取全局玩家。已复现旧回调在玩家清空后、尚未进入原有连接有效性检查前抛出空指针；原生任务循环会记录异常，没有据此认定整端崩溃。现在先比较接收回调与当前连接的 sender 身份，再读取玩家和分发数据，避免旧回调误用新会话。固定 FAPI 的源码及实际 JAR 字节码证明 sender 是每个监听器的稳定 addon；Minecraft 的 `getConnection()` 在玩家为空时也返回空。5 项新增回归覆盖退出、重连隔离、当前回调顺序、异常传播及正式注册接线。
- **服务器停止时引用释放过早。** 原安装顺序会先清空当前服务器，再触发 GAME `ServerStoppedEvent`。现使用显式后置阶段，在默认阶段的停止清理完成后释放引用；旧服务器的迟到停止也不会清空新服务器。2 项新增回归经过真实 FAPI 回调和 GAME 事件总线。此项是与 NeoForge 的生命周期契约差异，未发现已造成存档损坏的证据。
- **已处理拖拽仍触发 Post。** 对齐 NeoForge：原生界面处理了拖拽后直接返回，只有未处理时才触发 `MouseDragged.Post`；Pre 取消仍跳过原生调用及 Post。调整原回归并新增取消用例，屏幕事件共 5 项通过。当前未找到该 Post 的业务订阅者，因此不把它描述为已观察到的界面故障。

统一执行 `classes platformTest auditClientMixins -PfabricClientTestMods=true` 成功，799 项平台测试零失败／错误／跳过，其中本轮新增 8 项。全部指定辅助模组下，224 个客户端目标完成真实 Mixin 转换，3 个未安装的其他可选目标跳过；零转换失败，403 个客户端订阅类链接无误。菜单原生同步生命周期另经 1000 轮随机有符号整数更新检查。完整日志为 `build/reports/self-audit-2026-10-16-validation.log`，转换报告为 `build/reports/mixin-audit/client-transform-self-audit-2026-10-16.json`。

用户自行于 05:58 启动的本机日志显示世界正常保存，未生成新的崩溃报告。06:01 退出后的资源重载出现 JEI “没有客户端世界”错误；实际合并字节码和 JEI 回调定位到当前 JEI Fabric 的停止／重载边界，06:02 再进世界后正常重建。该错误未被算作本模组已修复问题。本轮未更换依赖 JAR。

审计、原始日志副本、修复前复现和定向验证集中在 `build/research/self-audit-2026-10-16/`。持久化、分片、DataMap、按键和 tooltip 的受查路径未再确认新故障；`ScreenEvents.remove` 与 Opening 取消的时序仍有接口差异，当前业务取消路径与已安装辅助模组没有对应消费者，未扩大改动范围。

本轮仅修改 Fabric 平台 Java、维护回归和本文档；没有修改共享模型、语言资源或用户存档，没有启动或操作游戏，没有打包、提交或推送。下次使用原源码入口启动会编译这些修复；上述证据仍不代替实际传送门、画面和完整游玩的验收。
