# Joja 快递盒编辑母本

维护入口为 `joja_parcel.bbmodel`，两张 PNG 与其内嵌纹理一致；编辑时同步保存两者。
母本来源是既有导出器实际使用的扁箱修订，不是按文件日期或版本号选出的新方案。

游戏 ID 保持 `stardewcraft:joja_supermarket_crate`。这是手动导出的编辑源，不是 Gradle 自动编译输入。

在工程根执行：

```sh
python3 scripts/check_authoring_sources.py --verify-exports
python3 tools/art/export_joja_parcel.py --output-root /absolute/path/to/review-resources
```

第二条命令用于隔离导出；省略 `--output-root` 才会写入正式资源，应在确实要更新资源时使用。
维护契约见 [工作区与收录规则](../../../docs/workspace-layout.md)。

2026-10-08 归位时模型及纹理字节未变，隔离导出的 8 项产物与归位前及正式资源完全相同。
本次未修改模型、重做材质、改变游戏逻辑或新增实机验收结论。
原始阶段源、制作脚本、说明和预览保存在本地历史归档，位置及恢复方式见工程根的 `本地资料/整理说明.txt`。
