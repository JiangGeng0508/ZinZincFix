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

## 客户端配置未加载时的回调保护

针对 `crash-2026-11-12_15.02.25-client.txt`，1.0.5 修复 TaCZ 1.2.0 在客户端 tick 中无条件读取趴下 / 瞄准配置的问题，并修复排查中确认的四个模组入口：

- TaCZ：客户端配置为空或尚未加载时，跳过趴下、瞄准 tick，并清除对应手柄按下状态。
- AppleSkin 3.0.9：配置加载前跳过 HUD 闪烁动画 tick。
- CoroUtil 1.21.0-1.3.9：在正常模组加载结束前保留首次配置同步标记；加载失败时不提前消费该标记。
- Curios 9.5.1：先判断是否为生存 / 创造背包界面，再读取按钮配置，避免加载错误界面触发读取。
- ForgeEndertech 12.1.4.0：先判断世界是否存在，再读取调试快捷键配置。

所有修复仅在客户端应用，未安装对应模组时自动跳过；配置就绪后执行原有逻辑。没有全局吞异常或把未加载配置替换为默认值，也不需要新增运行时前置。

此次原始启动失败是 Create 6.0.10 注册进度时访问未绑定的 `create:chocolate_bucket`，导致后续 CLIENT / COMMON 配置没有加载。上述补丁修复之后的配置异常，**不代表 Create 的注册错误已修复**。

已扫描整合包的 352 个顶层 JAR 及内嵌依赖（总计 590 个 JAR），并复核 tick、界面、输入和静态初始化候选。结果和仍需关注的路径见 [配置加载排查报告](docs/config-loading-audit-2026-11-12.zh-CN.md)，实际字节码对照测试及验证边界见 [配置生命周期测试](tests/configload/README.md)。

## Northstar / Spelunkery 冰块兼容修复

针对 `crash-2026-11-10_09.28.47-client.txt` 中的启动崩溃，涉及 Northstar 0.6.6+1.21.1 和 Spelunkery 1.21.1-0.4.4。

Spelunkery 的 `IceMixin` 以优先级 1000 替换了 `IceBlock.melt()`；Northstar 的 `IceBlockMixin` 同样使用优先级 1000，向其中的 `DimensionType.ultraWarm()` 调用注入温度判断。Mixin 不允许此类注入修改相同或更高优先级的其他 Mixin 合并方法，因此在游戏 Bootstrap 阶段抛出 `InvalidInjectionException`。Northstar 配置中的 `priority: 1200` 只控制配置顺序，不是 Mixin 类的优先级。

补丁通过 NeoForge 自带的 Coremod，在 Mixin 读取 Northstar 的 `IceBlockMixin` 前，将该类默认的 1000 优先级调整为 1100。保留 Northstar 的温度、密封和破冰逻辑，以及 Spelunkery 的盐块融冰逻辑；无需新增前置，也不修改原模组 JAR。若该类已有非 1000 的显式优先级，则保持不变。

此修复适用于客户端和服务端。未安装 Northstar 时没有转换目标；单独安装 Northstar 时也只调整此类的优先级。

## Ponderer / Create Simulated 思索缩放兼容

1.0.6 修复 Ponderer 1.11.0 与 Create Simulated 1.3.2 同时安装时，首次打开铜桶等物品思索触发的 `interpolateScaleFactor (0/1) succeeded` 崩溃。双方原本都独占重定向 `PonderScene.scaleFactor` 的读取。

补丁通过 NeoForge Coremod 将 Ponderer 的缩放 `@Redirect` 转换为 `@ModifyExpressionValue`：接收 Simulated 插值后的结果，再执行原有的缩放覆盖与用户缩放；原来的自由预览、旋转、平移和视图偏移代码保留。同时让 Simulated 在 `screenToScene` 中也使用动画缩放，使绘制与鼠标反算使用相同的比例。没有降低注入成功次数要求，也不依赖修改原模组 JAR。

仅存在对应目标类时转换；单独安装任一模组也可使用，无新增前置。当前验证版本为 Ponder 1.0.82、Ponderer 1.11.0、Simulated 1.3.2；升级后需重新验证字节码结构。

已使用原始场景与双方 Mixin 字节码复现冲突，并验证组合缩放、缩放覆盖、冻结预览、鼠标反算，以及单模组和无模组环境。测试范围见 [思索兼容测试](tests/ponder/README.md)。整合包 Mixin 扫描结果见 [全量排查报告](docs/mixin-conflict-audit-2026-11-12.zh-CN.md)，其中另有两组尚未修复的功能覆盖问题。

## Xaero 玩家位置模组依赖版本修复

针对 `crash-2026-11-10_10.31.14-fml.txt`：`xaeros_playerposition` 1.0.0 将小地图的可选依赖版本锁定为 `[26.4.2]`，而整合包使用 26.6.0。可选依赖在已安装时仍需满足版本要求，因此 NeoForge 在加载模组前就会终止启动。XaeroPlus 2.37.2 同时要求小地图 26.6.0，单独降级小地图会产生新的版本冲突。

此问题通过整合包客户端的 `config/fml.toml` 修复。在原配置中，将空的 `dependencyOverrides = {}` 替换为：

```toml
dependencyOverrides = { xaeros_playerposition = ["-xaerominimap", "+xaerominimap"] }
```

`-xaerominimap` 解除玩家位置模组对小地图的依赖约束，`+xaerominimap` 保持它在小地图之后加载。若已有其他依赖覆盖，应合并此条目并保留原配置；修改前备份文件，修改后重启游戏。

该配置独立于 ZinZincFix JAR，必须随整合包的 `config/fml.toml` 分发；仅更新补丁 JAR 不会修复这次加载错误。配置不会限定小地图版本，当前验证范围是玩家位置 1.0.0、小地图 26.6.0、世界地图 1.47.0、XaeroPlus 2.37.2；升级这些模组后需重新检查是否仍需此覆盖。

已使用上述四个原始 JAR，在 Minecraft 1.21.1 / NeoForge 21.1.265 的隔离客户端中通过加载测试，并检查小地图边框、玩家追踪和世界地图的 Mixin 已应用，玩家追踪反射接口初始化成功。完整整合包及多人游戏中的头像显示仍需游戏内复测。

## Simulated Coasters 船体扫描优化

针对 Simulated Coasters 0.1.5 / Sable 2.0.6 的持续 tick 热点：`CoasterCartPlotScan.scanBearingCells()` 在物理回调中扫描船体的每个非空区段，每个区段逐个检查 4096 个方块，即使整个区段都没有目标 bearing 或 rivet。

补丁在 `scanBearingCells()` 和 `forEachBearingWorldCell()` 的区段过滤处增加 Minecraft 自带的 `LevelChunkSection.maybeHas()` 检查，先查询区段的方块状态 palette；确认不含目标方块时跳过整个区段。可能含有目标时继续执行原扫描，保留数量、代表位置、结果顺序以及回调行为。

每次都检查当前 palette，不跨 tick 缓存结果，因此无需为放置、拆除、组装、区块替换添加缓存失效钩子。palette 可能保留已被移除的状态，此时只会多做一次原扫描，不会漏掉新放置的目标。若目标遍布所有区段或 palette 保留大量旧状态，收益会降低。

此 Mixin 适用于客户端与服务端，未安装 Simulated Coasters 时自动跳过；无需新增前置，不修改原模组 JAR。当前目标为 0.1.5，升级 Simulated Coasters 后需要重新验证注入位置。

已用 0.1.5 JAR 中的原始扫描字节码、真实 Minecraft 区段 / palette，在 NeoForge 21.1.265 中对发布 JAR 做对照测试：覆盖空区段、负坐标、多区块、多种目标状态、即时增删、旧 palette 条目、全局 palette、区段替换、32 组随机布局和回调内修改。测试使用轻量 plot / holder 适配器，不依赖完整 Sable 世界。128 个非空区段中只有 1 个含目标的场景，两种扫描入口的方块读取均从 **524,288 次降至 4,096 次（减少 99.22%）**；这是该测试场景的读取量变化，不是整合包 FPS / MSPT 收益。完整整合包和实际船体仍需游戏内复测。

复现命令与测试范围见 [扫描回归测试](tests/coasters/README.md)。

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

将 `build/libs/zinzincfix-1.0.6.jar` 放入整合包的 `mods` 文件夹，替换旧版 ZinZincFix，然后重启游戏。目录中只保留一个 ZinZincFix 版本。此版本包含上述兼容修复与性能补丁；使用 Northstar / Spelunkery 或 Simulated Coasters 的专用服务端也应安装此补丁，客户端配置保护以及 AliveCombat 与 Subtle Effects 的优化仅在客户端应用。

## 游戏内复测

1. 完全退出并重新启动客户端，确认首次资源加载完成后，将 `addon_gancho:plunger_launcher_test` 放入快捷栏不再崩溃。
2. 检查快捷栏、背包、主副手和第三人称下的发射器模型，并检查同样使用这些模型的 `addon_gancho:grapple_swing`。
3. 使用 `F3+T` 重载资源，再次检查以上物品。
4. 检查启动过程不再出现 Wakes 配置未加载异常；进入世界游泳或划船，确认水波效果正常，退出并重新进入世界后再次检查。
5. 同时安装 Northstar 和 Spelunkery 启动，确认不再出现 `IceBlockMixin` 的优先级冲突；检查盐块相邻冰块的融化，以及 Northstar 高温环境和密封区域中的冰块融化、破坏行为。
6. 使用相同船体、实体数量与位置，对比 Simulated Coasters 的 tick 耗时；检查 coaster cart / rivet 的放置、拆除、重复限制、组装和解体行为，确认不会漏检目标方块。
7. 检查 AliveCombat 准星拾取、穿过无碰撞方块的攻击、可交互方块及主副手工具的交互判断，与原版行为一致。
8. 在白天、夜晚、洞穴及有方块光源的位置检查萤火虫，在花林等群系检查粒子；切换配置、世界与维度后再次确认，并用相同路线录制帧时间与 JFR 分配采样。
9. 启动后检查 TaCZ 按住 / 切换瞄准和趴下、键盘与手柄输入，AppleSkin 食物闪烁，Curios 生存 / 创造背包按钮，以及 ForgeEndertech 调试快捷键。若仍有加载失败，优先检查日志中的原始注册错误。
10. 打开铜桶及其他思索，检查播放、暂停、拖动进度、用户缩放和自由预览；缩放动画中检查方块高亮位置。此版本需要完全重启游戏才会生效。
