# Simulated Coasters 扫描回归测试

使用 Java 21，在仓库根目录运行：

```powershell
.\gradlew.bat build
.\gradlew.bat --no-configuration-cache -I tests/coasters/smoke.gradle '-PcoastersJar=E:/App/PCL/.minecraft/versions/Aeronautics/mods/simulatedcoasters-0.1.5.jar' runServer
.\gradlew.bat --no-configuration-cache -I tests/coasters/smoke.gradle -PcoastersAbsent runServer
```

将 `coastersJar` 换成你的原版 0.1.5 JAR 路径。测试使用 `build/libs` 下本次构建的发布 JAR；原模组中只有 `CoasterCartPlotScan` 及其结果 record 的字节码被复制到测试资源。测试模组与 plot / holder 适配器不会打包进 ZinZincFix。

两次测试均在 NeoForge 加载阶段结束，成功时输出 `COASTERS PRESENT PASS` 或 `COASTERS ABSENT PASS` 并退出，不打开或生成玩家世界。隔离运行目录位于 `build/coasters-smoke`。缺少可选模组的测试不需要 `coastersJar`。

有目标类的测试验证实际 Mixin 已加载，并通过独立类加载器加载同一份未转换字节码作为基线，比较：

- 数量、代表位置、完整位置列表及顺序；回调位置与顺序。
- 空 plot、未加载区块、空区段、不含目标的非空区段。
- 负坐标、多区块、同一方块的多个状态、全区段均为目标。
- 立即放置 / 移除、palette 保留旧状态、替换区段、全局 palette。
- 32 组随机布局，以及回调中新增后续扫描位置的方块。
- 原始与补丁代码的实际方块读取量。

区段使用真实 `LevelChunkSection` 和 `PalettedContainer`；扫描用的 chunk 覆盖了所有访问方法，测试用 `Unsafe.allocateInstance` 避开必须依赖真实世界的 chunk 构造器。此做法仅在测试代码中使用。

在 128 个非空区段、只有一个区段包含目标的布局中，两种入口均应输出：

```text
524288 -> 4096 block reads (99.21875% fewer)
```

该结果说明补丁确实省去了无关区段的逐方块读取；不是通用的速度倍数或整合包帧率提升。测试未加载完整 Sable / Create 模组栈，真实船体操作及整合包兼容性仍需游戏内复测。
