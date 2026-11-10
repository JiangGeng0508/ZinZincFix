# AliveCombat / Subtle Effects 回归测试

使用 Java 21，在仓库根目录运行：

```powershell
.\gradlew.bat build
.\gradlew.bat --no-configuration-cache -I tests/performance/smoke.gradle '-PperformanceMods=E:/App/PCL/.minecraft/versions/Aeronautics/mods' runClient
.\gradlew.bat --no-configuration-cache -I tests/performance/smoke.gradle -PperformanceAbsent runClient runServer
```

`performanceMods` 指向包含以下原版文件的目录，每个模式必须恰好匹配一个 JAR：

- AliveCombat 1.6.0：`alivecombat-*.jar`
- Subtle Effects 1.15.1：`SubtleEffects-*.jar`
- Fzzy Config 0.7.7+fix1：`fzzy_config*.jar`
- Kotlin for Forge 5.12.0：`kotlinforforge-*-all.jar`

测试通过 NeoForge 21.1.265 实际加载这四个模组与本次构建的 ZinZincFix 发布 JAR。客户端测试会短暂启动一个隔离客户端，完成检查后自动退出，不进入玩家存档。缺少可选模组时分别测试客户端和专用服务端加载。运行目录位于 `build/performance-smoke/`，不读取整合包的配置目录。

基线使用独立类加载器直接读取原始模组 JAR 中的 PassHandler、FireflyManager、BiomeEffectsManager 及其 Options record 字节码，绕过 ModLauncher 转换。补丁侧则使用正常加载、实际应用 Mixin 后的类；测试会明确检查注入方法存在。Fzzy 配置类、Minecraft 方块状态、群系以及随机数实现均为真实运行时类。

为了可重复地枚举光照与天气条件，测试使用覆盖相关访问方法的 `ClientLevel` 适配器，记录粒子/声音参数并统计群系查询。通过测试专用 `Unsafe.allocateInstance` 避开世界和玩家构造，不需要创建世界。测试适配器、原版模组和原始字节码不会打包进发布 JAR。

覆盖范围：

- AliveCombat：本类与父类声明、继承覆盖、私有方法、重载、不存在的方法、接口默认方法、不同基类；空类/方法/参数输入及异常类型；修改调用方参数数组、重复查询和并发读取。
- AliveCombat：预热后重复查询缺失方法的线程分配字节数，验证负结果缓存消除了热点中的反复异常创建。
- 群系粒子：三个已注册效果对同一候选位置的查询、不同候选和表面群系、地下提前返回、天气、固体/流体、可变坐标、新世界及配置即时变化。
- 萤火虫：昼夜边界、天空光、方块光 5/6 的边界、降雨、能否见天空、冷暖群系、大整数/负世界时间、配置开关及玩家入服等待。
- 对每种场景比较粒子/声音参数和后续随机数状态；除分散种子的随机场景外，显式强制低随机数分支，确保测试实际覆盖粒子生成与声音播放，而不是双方都不触发。

本次有目标模组的运行结果：

```text
ALIVECOMBAT WORK: 10000 warmed missing-method checks allocated 68880360 -> 0 bytes
SUBTLE BIOME WORK: 3 -> 1 candidate biome queries per invocation
SUBTLE FIREFLY WORK: 1 -> 0 biome queries for a daylight candidate
PERFORMANCE PRESENT PASS: 16577 checks against original mod bytecode
```

上述 0 字节仅指本次 JVM/JIT 状态下的已预热查询测试，不包括游戏调用方构造参数等工作，也不保证任何环境绝对零分配。查询次数下降是固定测试场景结果，不能直接换算为整合包 FPS 提升。

缺少可选模组的两个运行都应输出：

```text
PERFORMANCE ABSENT PASS: packaged patch loads without optional mods
```

本测试验证模组加载、注入及所覆盖条件下的行为一致性；未覆盖整个 Aeronautics 模组栈、第三方季节集成、真实区块并发更新或完整渲染帧时间。正式安装后仍应在相同路线下对照 JFR、帧时间及游戏交互。
