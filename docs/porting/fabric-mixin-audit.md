# Fabric Mixin 验证

在仓库根目录执行：

```sh
./gradlew -p fabric auditClientMixins --console=plain
```

任务编译主源码和平台测试，导出实际运行类路径，在独立 Java 子进程的真实 Knot/FAPI 转换链中
验证 CLIENT Mixin。每次重新执行，旧报告先清除；不会以缓存或上次成功结果代替当前检查。

## 输出与判据

输出目录为 `fabric/build/reports/mixin-audit/`。`client-transform.json` 记录配置 SHA256、
逐目标转换结果、异常及退出码，`client-transform.log` 保存运行日志。CI 将报告作为附件上传。
要求 Gradle 任务成功、`audit_process_exit_code = 0`、`failed_targets = 0`，且无 `setup_failure`。
`optional-target-absent` 单独列出，不算兼容通过。客户端订阅类的定义/签名链接不等于执行其回调。

## 隔离与限制

使用 Loom 的 named 开发命名空间与 intermediary 分发命名空间。
工具只在内存中移除固定 Loader 的一次 `preLaunch` 调用，再获取转换后的目标字节码；
固定位置或调用次数漂移会失败，不修改依赖 JAR。
Java agent 阻止 Minecraft、Blaze3D、GLFW 和 GL 初始化；审计不启动游戏窗口。

此检查证明当前依赖与配置下的字节码接入，不能证明游戏启动、画面、GUI、联机或光影兼容。
需要额外依赖的目标必须安装对应版本后另验。源码覆盖的上游 SHA 变化仍须人工核对语义。

定向 GameTest 依赖实际加载的区块；先检查 `execute if loaded`，逐项执行并等本项完成。
原生批次结束可能释放强制加载票据，下一项须重新确认，不能把地下错误场地误诊为业务缺陷。
