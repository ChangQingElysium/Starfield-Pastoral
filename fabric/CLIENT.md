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
