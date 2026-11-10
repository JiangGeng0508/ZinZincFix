# ZinZincFix

Minecraft 1.21.1 / NeoForge 21.1.265+ 整合包兼容补丁。

## Create Grappling Hooks 模型加载修复

针对 `crash-2026-11-09_21.55.29-client.txt` 中的物品渲染崩溃：

- 触发物品：`addon_gancho:plunger_launcher_test`。
- 涉及版本：Create Grappling Hooks 1.2.0、Create Simulated 1.3.2、Flywheel 1.0.6、Ponder 1.0.82。
- 原因：抓钩模组在 `FMLClientSetupEvent` 中初始化 `AddonPartialModels`，可能晚于首次资源加载时 Flywheel 收集模型的时机。烘焙结果缺少对应条目，`PartialModel.get()` 返回 `null`，随后在 `BakedModelBuffererImpl.bufferModel()` 中触发空指针异常。

补丁在 `ModelEvent.RegisterAdditional` 中显式注册以下三个 standalone 模型，让它们参与首次加载和后续资源重载：

```text
addon_gancho:item/plunger_launcher/plunger_tether
addon_gancho:item/plunger_launcher/tether_spool
addon_gancho:item/plunger_launcher/spool_joint
```

使用原模组提供的模型资源，保留其外观和渲染逻辑。仅客户端安装了 `addon_gancho` 时启用，无需新增前置模组；服务端不加载此修复类。

## Wakes 配置加载修复

针对 `crash-2026-11-09_21.28.59-client.txt`，修复 Wakes 1.4.1 在首次客户端 tick 中抛出的 `ExceptionInInitializerError`。

游戏加载期间还没有世界，`WakeClientTicker.onClientTick()` 会调用 `WakeHandler.kill()`。这个清理调用也会触发 `WakeHandler` 的静态初始化，提前读取尚未加载的 `wakeResolution` 配置，导致 `Cannot get config value before config is loaded`，并使该类初始化永久失败。

补丁仅在 `WakesConfig.CLIENT_SPEC.isLoaded()` 为假时跳过 Wakes 的客户端 tick；配置就绪后继续执行原始逻辑，包括进入世界时初始化水波、退出世界时清理实例。Mixin 仅在客户端应用，未安装 Wakes 时自动跳过。

## AliveCombat 交互方法查询优化

针对 AliveCombat 1.6.0 的 `PassHandler.declaresMethod()`：准星拾取和交互判断会反复沿继承链反射查找方法，未找到时逐层创建 `NoSuchMethodException`。

补丁按运行时类、基类、方法名和参数类型缓存判断结果，包含未声明方法的结果。第一次查询仍调用原实现；原实现抛出的异常不缓存。缓存命中时不构造查询键或复制参数数组；首次写入复制参数数组，避免调用方后续修改污染缓存。缓存使用 `ClassValue`，不保存玩家、方块实例或世界对象，并支持并发读取。

不改变父类查找顺序、方法重载及基类声明判断，仅客户端安装 AliveCombat 时应用，无新增运行时依赖。

## Subtle Effects 环境粒子查询优化

针对 Subtle Effects 1.15.1 的两条客户端路径：

- `FireflyManager.tick()`：在原有配置、玩家入服等待、维度和季节检查之后，提前检查原逻辑必须满足的时间与光照条件。白天有天空光或方块光高于 5 的候选，不再继续查询群系和高度。保留原来的浮点时间计算和夜晚边界；提前返回之前没有消费随机数。
- `BiomeEffectsManager.tick()`：一次调用中，多个已注册粒子会重复查询同一个候选位置的群系。补丁在该次调用的局部变量中复用查询结果，表面位置的另一次群系查询仍独立执行。结果不跨调用、tick 或世界缓存，配置读取与随机数调用顺序保留。

补丁保留原粒子密度、声音、触发概率和 tick 频率，不需要降低特效配置。仅在客户端存在目标模组时启用。

新增补丁使用原版 AliveCombat / Subtle Effects JAR 进行字节码对照测试，检查交互判断、粒子/声音输出、随机数序列、配置变化以及实际 Mixin 注入。具体命令、验证结果与范围见 [客户端性能补丁测试](tests/performance/README.md)。完整整合包中的 FPS、帧时间和分配速率收益需要同场景复测。

## 构建与安装

使用 Java 21 工具链：

```powershell
.\gradlew.bat build
```

将 `build/libs/zinzincfix-1.0.4.jar` 放入整合包客户端的 `mods` 文件夹，替换旧版 ZinZincFix，然后重启游戏。目录中只保留一个 ZinZincFix 版本。此版本包含上述兼容修复与性能补丁，AliveCombat 与 Subtle Effects 的优化仅在客户端应用。

## 游戏内复测

1. 完全退出并重新启动客户端，确认首次资源加载完成后，将 `addon_gancho:plunger_launcher_test` 放入快捷栏不再崩溃。
2. 检查快捷栏、背包、主副手和第三人称下的发射器模型，并检查同样使用这些模型的 `addon_gancho:grapple_swing`。
3. 使用 `F3+T` 重载资源，再次检查以上物品。
4. 检查启动过程不再出现 Wakes 配置未加载异常；进入世界游泳或划船，确认水波效果正常，退出并重新进入世界后再次检查。
5. 检查 AliveCombat 准星拾取、穿过无碰撞方块的攻击、可交互方块及主副手工具的交互判断，与原版行为一致。
6. 在白天、夜晚、洞穴及有方块光源的位置检查萤火虫，在花林等群系检查粒子；切换配置、世界与维度后再次确认，并用相同路线录制帧时间与 JFR 分配采样。
