# 附属与数据包平台发布证据（2026-07-27）

本记录对应 `addon-datapack-extensibility-roadmap.md` v6。它记录可重复证据，不用单一百分比
代替验收，也不把外部兼容样本当作平台规格来源。

## 1. 构建与自动化

以下命令在当前工作树通过：

```bash
./gradlew build runGameTestServer prepareAddonCanaryJar --no-daemon --console=plain
python3 -m unittest discover -s compatibility -p 'test_*.py'
python3 examples/stardewcraft-data-pack/validate.py
./gradlew -p examples/stardewcraft-addon build --no-daemon --console=plain
python3 compatibility/verify_runtime_smoke.py \
  run-game-test/logs/latest.log --scenario game-test
python3 compatibility/verify_addon_canary.py \
  pinned_mixin_addon .canary/pinned-mixin-addon \
  --addon-classes .canary/pinned-mixin-addon/build/classes/java/main
git diff --check
```

结果：

- 主工程 `BUILD SUCCESSFUL`；
- 31/31 必需 GameTest 通过；
- 18 个兼容工具测试通过；
- 64 份通用数据样例及跨文件关系通过；
- 通用 Java 附属独立构建通过；
- 固定提交的外部 canary 使用当前主工程 JAR 重新编译通过；
- canary 结构验证覆盖 45 个 Mixin、44 个目标类、74 个方法选择器、3 个 accessor 字段、
  18 个 shadow 字段、64 个处理器描述符和 15 个注入调用点；
- 生产代码、测试、公共文档和通用示例中没有具体附属身份；
- `git diff --check` 通过。

## 2. API 成熟度

`compatibility/api-maturity-review-v1.tsv` 对 263 个未进入稳定二进制基线的入口逐项记录：

- 本体运行时直接引用；
- 通用独立样例直接引用；
- 自动测试直接引用；
- 文档直接引用；
- 当前保持实验的首个原因。

`checkApiMaturityReview` 会重新计算这些证据并拒绝缺行、陈旧或被静默改写的决定。本轮没有
为了发布数字批量提升 API；四类静态证据齐全的入口仍需真实网络或第二独立消费者证据。

## 3. 用户旧档副本

测试源为用户旧档的临时副本，原档没有作为服务端运行目录。当前源码完成：

- 数据与配方 reload；
- 旧 Stardew 时间、农场和玩家状态加载；
- 专服稳定运行；
- 正常停止并保存五个维度。

原档在测试前后保持时间戳 `2026-07-26 17:46:52`。最终复核哈希：

```text
d394d595ad246e95e443631ed20b0f688bba10ab66df9de5d5b43a84766e5592  level.dat
a610101bf483864cb2766e36f44dfc2ad6aa2c5799b86c098e799a8731e112da  playerdata/380df991-f603-344c-a090-369bad2a924a.dat
```

缺失附属时，Minecraft 原生注册表对象会出现未知方块、物品、实体或统计警告。这不是平台状态
容器能够无损接管的范围，因此所有卸载兼容测试继续只在副本上运行。

## 4. 真实图形客户端联网与重连

在 macOS 主显示器可用后，当前源码的真实图形客户端通过 OpenGL 4.1 启动并连接同一旧档副本
专服：

1. 首次协商 2 项网络能力；
2. 玩家真实登录、内容快照同步并加载 Stardew 玩家数据；
3. 稳定连接约 58 秒后强制断线；
4. 服务端保存该玩家的 Stardew 数据；
5. 客户端重新启动并重新协商能力；
6. 第二次真实登录、重新同步并稳定连接约 12 秒；
7. 再次登出后，专服正常停止并保存全部五个维度。

最终验证：

```bash
python3 compatibility/verify_runtime_smoke.py \
  build/reports/runtime/current-network-world-reconnect.log \
  --scenario network-world
```

结果为 `verified network-world runtime smoke: 155.925s, 13 ordered markers`。验证器同时要求首次
连接和重连各自至少稳定 10 秒，因此单次连接不能冒充重连通过。

## 5. 客户端降级

真实客户端资源 reload 期间存在既有无效模型，仍继续进入专服；服务端能力协商、权威内容同步、
玩家数据加载和保存均未被展示错误改变。针对新平台展示契约的自动化门禁另外覆盖：

- 缺失翻译显示可读稳定 ID；
- 缺失 NPC 肖像使用占位而不隐藏条目；
- 展示快照构建失败保留上一份完整快照；
- 同一 server epoch 的旧或重复 revision 被拒绝；
- 断线清除跨服务器会话缓存。

## 6. 保留风险与发布边界

- 263 个入口仍是非稳定 API；清单证明它们经过评审，不代表稳定承诺。
- 客户端日志仍包含既有模型 JSON 与声音源告警；它们未阻止本次真实联网，但应作为独立资产/
  音频问题处理，不能夹在平台 API 变更中顺手重构。
- 缺失附属的 Minecraft 原生注册表对象不在平台未知 owner 状态承诺内；管理员必须先备份，
  并避免在附属缺失时加载和保存相关区块。
- 本次未获得提交或推送授权；证据对应当前工作树，而不是尚未创建的 Git 提交。
