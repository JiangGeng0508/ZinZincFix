# 整合包 Mixin 扫描

```powershell
python tests/mixinaudit/logs.py 'E:\App\PCL\.minecraft\versions\Aeronautics' build/mixin-audit
.\gradlew.bat --no-configuration-cache -I tests/mixinaudit/audit.gradle '-PmixinMods=E:\App\PCL\.minecraft\versions\Aeronautics\mods' mixinAudit
python tests/mixinaudit/summarize.py
python tests/mixinaudit/review.py
```

`logs.py` 检查当前/归档日志与崩溃报告，并加入工作区 RawOutput.txt；保留来源文件、行号及文件 SHA-256。`MixinAudit` 递归读取每个顶层及内嵌 JAR，统计所有 class，识别所有带 `@Mixin` 的类，记录配置中的 Mixin 和已在日志应用的动态 Mixin。未列出的 Mixin 类单独保存。记录类优先级、方法注解、注入位置、最低成功次数、配置插件、来源 JAR、类哈希和 refmap 数据。

`summarize.py` 比较 Overwrite/隐式方法覆盖、Redirect、ModifyConstant 以及与其他注入的重叠。区分不同方法描述符、ordinal、FIELD opcode 和数组 get/set；不执行插件或注入器，也不对任意 refmap/动态选择器做完整运行时解析。可组合的 ModifyExpressionValue/WrapOperation 不被直接当作 Redirect 竞争。表内保留 slice 和其他注解数据，供人工复核。

`review.py` 将本次代码复核结论、历史日志的应用记录写入明细。那些结论适用于 2026-11-12 的已安装版本，升级后应重新复核，不能将标签直接当作新版本验证。

输出：`build/mixin-audit/{inventory,candidates,reviewed-candidates,log-review}.json`；可阅读的候选明细在 `docs/mixin-audit-candidates-2026-11-12.zh-CN.md`。

限制：未完整启动并强制加载整合包所有目标类；不同平台的内嵌副本、可选前置、配置插件、运行时 ASM/MixinSquared、后续资源加载或功能入口会影响实际结果。同一方法被多个模组修改，不等于有冲突；未匹配到静态重叠，也不等于所有行为兼容。
