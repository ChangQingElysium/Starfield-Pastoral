# Faster production / 加速生产

Requires a StardewCraft build containing the production configuration update. Older released builds do not understand the new `production` folder.
需要包含本次生产配置更新的模组版本；旧版模组不识别新的 `production` 目录。

Copy this whole folder into `<world>/datapacks/`, then run `/reload`. On a dedicated server, install it on the server. Clients receive the settings automatically. The folder containing `pack.mcmeta` is the pack root.
把整个文件夹放进世界的 `datapacks`，执行 `/reload`。服务器只需在服务端安装，配置自动同步给客户端。

- Tappers: every tree produces in **60 in-game minutes**, including addon trees using the tapper integration.
- Solar panels: **120 effective in-game minutes** of charging, still requiring clear weather and open sky.
- Other included machines: **half their normal production time**; casks age twice as fast.
- Existing jobs keep their chosen products and saved progress. The next cycle uses the new settings.
- Removing/disabling the pack and running `/reload` restores the bundled defaults for subsequent cycles.

树液采集器统一为游戏内 60 分钟；太阳能板为有效游戏时间 120 分钟，仍检查天气和天空；其余示例机器耗时减半。正在运行的任务不重算，下一轮生效。移除数据包并重载即可恢复默认。

These are StardewCraft's calendar minutes, not Minecraft ticks or real-world minutes. A StardewCraft machine day has 1600 minutes, as in Stardew Valley (1200 daytime minutes plus the overnight catch-up to 6:00).
本模组一个机器日为 1600 游戏分钟（与星露谷一致：白天 1200 分钟加入睡后补到早上），这些数值不是 tick，也不是真实世界分钟。

This example covers the machines with production timers. Crab pots, daily statues, farm cave harvests, animal production and fish ponds have separate daily/gameplay systems; it does not change them.
示例覆盖生产计时机器；蟹笼、每日雕像、农场洞穴、动物和鱼塘仍属于各自的每日逻辑，未被此数据包修改。
