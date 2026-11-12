# 配置加载时机排查（2026-11-12）

本次修复针对客户端在配置尚未加载时触发的二次异常。ZinZincFix 1.0.5 新增 TaCZ、AppleSkin、CoroUtil、Curios 和 ForgeEndertech 的局部保护，保留已有 Wakes 修复。

**Create 的 `create:chocolate_bucket` 未绑定错误仍是原始启动失败，未声称由本补丁修复。** 配置保护让客户端保留原始加载错误，而不在随后运行的回调中再次崩溃。完整整合包、世界内交互仍需复测。

## 原始证据与因果关系

环境：Minecraft 1.21.1、NeoForge 21.1.265 / FML 4.0.45；整合包原始运行使用 Java 25.0.4.1。分析文件来自 `E:/App/PCL/.minecraft/versions/Aeronautics/`：

- `crash-reports/crash-2026-11-12_15.02.25-client.txt`：第一个客户端 tick，TaCZ 的 `CrawlKey.onCrawlHoldingInput(CrawlKey.java:51)` 抛出配置异常。
- `logs/latest.log:1863`：更早的 `Trying to access unbound value: ResourceKey[minecraft:item / create:chocolate_bucket]`。
- `logs/latest.log:1901`、`:1924`：注册失败，回滚到 VANILLA 状态。
- `logs/debug.log:40089`：仅跟踪 `tacz-client.toml`，没有随后读取文件的记录。
- `logs/latest.log:2313` 起：CoroUtil 的首次 tick 同步也反复访问未加载配置。
- `logs/debug-2.log.gz`：较早一次相同 Create / NeoForge 版本的启动成功读取了 TaCZ 的 CLIENT、COMMON 配置，并成功进入世界。

NeoForge 的 `CommonModLoader.begin()` 先执行 Registry initialization，再执行 Config loading。此次前者失败，后者未正常执行。TaCZ 的配置定义已存在，但 `ModConfigSpec.loadedConfig` 仍为 `null`；`ConfigValue.getRaw()` 因此抛出 `IllegalStateException`。

现有 `config/tacz-client.toml` 已通过 TOML 解析，`key.HoldToCrawl = true` 是合法值。把它改成 false 或删除文件不能解决加载流程被中断的问题。

**额外确认的触发条件：Placebo 9.9.4 在 `dev.shadowsoffire.placebo.Placebo` 构造函数中直接调用 `NeoForge.EVENT_BUS.start()`。** NeoForge 默认以 `startShutdown()` 创建游戏事件总线；提前启动后，即使注册失败，客户端 tick 仍有机会调用已经订阅的模组。这解释了整合包中的配置二次异常，而最小 NeoForge 客户端在加载失败时通常不派发这些回调。此处不能推导为 Placebo 导致巧克力桶注册失败；注册事件属于另一条加载链。本补丁保留 Placebo 的行为，在实际越界读取的入口保护配置生命周期。

Create 6.0.10 的触发路径为 `Create.onRegister()` → `AllAdvancements.<clinit>()` → 巧克力桶进度的图标 → `AllFluids.CHOCOLATE.get().getBucket()`。原始 JAR 确实包含巧克力桶注册代码，不能只据本次堆栈认定是移除了该物品或简单的版本不兼容。检查到的 DnDesires `AllFluidsMixin` 方法体为空；Azimuth 的进度 Mixin 只暴露 ID；当前 KubeJS startup 脚本未发现巧克力桶移除代码。未进行足以锁定竞争条件或责任模组的隔离复现，因此没有用默认物品、重复注册或吞异常掩盖该错误。

## 已修复入口

以下修复全部仅在客户端应用；目标模组缺失时通过 `@Pseudo` 跳过。没有修改 NeoForge 的配置读取规则，没有强制提前加载配置，也没有全局捕获配置异常。

| 模组 / 版本 | 已确认的问题 | 1.0.5 行为 |
|---|---|---|
| TaCZ 1.2.0 | `CrawlKey.onCrawlHoldingInput()` 即使不在游戏中也读取 `HOLD_TO_CRAWL`；`AimKey.onAimHoldingPreInput()` 同样直接读取 `HOLD_TO_AIM` | `CLIENT_CONFIG_SPEC` 为空或未加载时跳过 tick，清除对应手柄按下状态；就绪后完整执行原逻辑 |
| AppleSkin 3.0.9 | `HUDOverlayHandler.onClientTick()` 每 tick 读取 `MAX_HUD_OVERLAY_FLASH_ALPHA`，不要求世界存在 | `ModConfig.SPEC` 未加载时跳过动画更新；加载后保留闪烁计算 |
| CoroUtil 1.21.0-1.3.9 | `allModsConfigsLoadedAndRegisteredHook()` 在首次 tick 先清除初始化标记，再访问所有 COMMON 配置；失败时会过早消费一次性初始化 | 客户端仍在模组加载中或存在加载错误时，保留初始化标记并等待；正常加载结束后继续原始一次性同步 |
| Curios 9.5.1 | `GuiEventHandler.onInventoryGuiInit()` 对任何 `ScreenEvent.Init.Post` 先读取 `enableButton`，然后才检查是否为背包 | 把原有生存 / 创造背包界面条件放在配置读取前；加载错误界面和标题界面直接跳过 |
| ForgeEndertech 12.1.4.0 | `DevInfo.onKeyPressed()` 先读取调试配置，再检查世界是否存在 | 先检查世界；无世界时不读取配置，进入世界后继续原有快捷键逻辑 |
| Wakes 1.4.1（已有修复） | 无世界时的清理调用会触发 `WakeHandler` 静态配置读取 | 保留原来的 `CLIENT_SPEC.isLoaded()` 保护 |

## 扫描方法和覆盖范围

对扫描时的 **352 个顶层 JAR** 及内嵌依赖递归分析，共读取 **590 个 JAR、133,896 个 class 文件**。去重后为 120,259 个类、1,001,835 个方法；发现 1,615 个直接读取 NeoForge / Forge 配置的方法，解析错误为 0。

从客户端 tick、帧、输入、界面事件签名及静态初始化方法出发，沿静态调用关系最多追踪 8 层，并标记静态字段触发类初始化、lambda / 方法引用。得到 **45 个客户端事件候选（25 个顶层模组 JAR）** 和 **76 个静态初始化候选**。

这些数字不是 bug 数量。34 个事件候选的最短路径不涉及延迟 lambda；静态初始化候选中只有 4 条最短路径不涉及延迟 lambda。创建 Supplier / 方法引用不等于立即执行，其中大量命中属于保守扫描产生的误报。

局限：没有完整解析反射、接口虚调用、跨语言动态分派、所有 Mixin 注入点和所有 GUI / 渲染事件；重复内嵌类按扫描顺序保留首份，不能替代加载器的版本选择；路径未证明分支可达；模组存在也不代表其可选兼容类会加载。CoroUtil 的问题通过日志和人工检查补充识别，不能仅靠此静态调用图发现。**该扫描不构成“其他模组绝无同类问题”的保证。**

工具与复现命令见 [测试说明](../tests/configload/README.md)。原始候选输出位于 `build/config-audit/candidates.json`，不把第三方反编译源码或 JAR 提交到仓库。

## 其他事件候选的复核

“已有条件”仅说明本次启动阶段、无世界场景的路径被阻断，并不保证任意第三方提前调用或运行中卸载配置都安全。

| 模组 | 结论 / 关键条件 |
|---|---|
| LDLib2 2.2.46 | 配置访问器自身检查 `SPEC.isLoaded()`，包括字体缓存淘汰时间、渲染模式；已有默认值处理 |
| Particular 1.5.7 | `CommonClass.onClientTick(world)` 在 `world != null` 分支内读取配置 |
| Lodestone 1.8.2 | `ClientGameEvents.clientTick()` 先检查世界和暂停状态，再调用粒子逻辑 |
| Climbable Ropes 2.2.0 | ClimbRigs、EmfCompat、RopeRideDispatcher 的相关配置读取位于世界 / 玩家存在分支内 |
| NetMusic 1.5.2 | `BigMegaphoneClientManager.refreshSelection()` 先检查世界与玩家 |
| Pipes n Physics 3.2.1 | `previewActive()` 先检查世界、玩家与 Shift |
| ElectroEnergetics 1.1.4 | 客户端 tick 先检查世界与玩家；部分静态候选为延迟 Supplier |
| Ponderer 1.11.0 | 主按键 tick、蓝图持有检测先检查玩家；自动同步检查连接和玩家；另有特定思索界面 / 测试功能路径，未复现启动故障 |
| Create Tweaked Controllers 1.2.7 | tick 受 `isGameActive()` 约束；配置按钮只在 TitleScreen / PauseScreen 分支读取，普通加载错误界面不会走该分支 |
| Simulated Coasters 0.1.5 | 预览配置路径要求世界和已选轨道锚点；未发现本次无世界启动路径 |
| Sophisticated Item Actions 0.5.19 | `ItemActionNudgeManager.tick()` 先检查玩家、世界 |
| Sophisticated Core 1.5.31 | 排序 / 转移成功后才播放配置控制的声音；要求存储界面或对应容器，不适用于加载错误界面 |
| Sophisticated Storage 1.6.16 | 高亮路径先检查玩家；多条升级配置静态命中为延迟工厂引用 |
| Stardew Fishing 3.7 | tick 仅对 `AbstractContainerScreen` 执行相关逻辑 |
| AliveCombat 1.6.0 | 静态命中是延迟配置 Supplier，相关点击路径不是本次无世界自动 tick 的同类故障 |
| Dynamic Waters 13.1.0 | 配置按钮先检查 `TitleScreen`；未发现普通加载错误界面入口 |
| Blueprint 8.2.0 | 活动按钮先检查 `TitleScreen` 和活动时间；未发现普通加载错误界面入口 |
| Exposure 1.9.19 | GUI 回调中的相机支架提示先读取配置再检查世界；正常由世界 HUD 渲染触发。属于条件风险，未复现其在加载错误界面被调用 |
| Dash Panels 2.1 | `guiRenderPost()` 同样在 HUD 入口较早读取配置。正常世界 HUD 场景配置应已加载；第三方强制在加载阶段派发 HUD 事件时有风险 |
| ItemZoom 3.0.0 | 特定缩放 / 开关快捷键可到达配置读取；未完成故障界面快捷键的动态复现，保留为条件风险 |

TaCZ 其他扫描入口中，`ShootKey.autoShoot()` 先检查游戏状态、玩家与枪械；`InventoryEvent` 先检查玩家。未把这些入口与已复现的瞄准 / 趴下 tick 混为一谈。

## 需要关注的静态初始化读取

| 模组 / 类 | 风险及当前证据 |
|---|---|
| Wakes `WakeHandler` | 已由原有补丁保护；提前初始化失败会导致后续 `NoClassDefFoundError` |
| TaCZ `HitboxHelper` | `SAVE_TICK` 在静态初始化时读取命中箱历史时长配置；普通玩家 tick / 命中计算发生在配置加载后，尚未复现提前触发 |
| QuarkPonders `ModuleRegistry` | 静态列表构建调用 `toRL(ConfigValue)`，直接读取场景条目配置；需要关注思索注册或第三方引用是否被移到配置加载前 |
| NetMusicList `BiliBiliUtil` | 静态 HTTP client 初始化读取 NetMusic 代理配置；如果音乐源探测提前触发类初始化，可能发生同类错误，尚未复现当前启动路径 |

对这些未复现路径未硬塞默认值，也未让一次性静态字段在配置加载前永久缓存默认值。若将来出现相关堆栈，应在实际触发入口推迟初始化，或把真正需要变化的配置值延迟到使用时读取。

## 验证边界

已完成：发布 JAR 构建、408 项原始字节码对照检查、真实注册失败后的事件总线回调检查，以及不安装目标模组时的客户端 / 专用服务端加载检查。相关测试命令正常退出。可复现结果保存在 `build/config-audit/final-present.log`、`final-failure-exit.log`、`final-absent.log`。

对已修改目标使用发布 JAR 在 NeoForge 中验证实际 Mixin 应用，并以原始方法字节码作对照。测试覆盖加载前、加载后、卸载再挂载、控制器状态、AppleSkin 闪烁序列、非背包界面与无世界按键，以及 CoroUtil 初始化标记。

正常行为测试在配置已加载的 common setup 阶段完成；CoroUtil 的正常加载完成状态通过短暂设置测试中的 `ClientModLoader.loading` 来确定性验证，随后恢复。一次完整渲染启动尝试出现 Windows 原生退出码 `0xC0000409`，未定位其原因，因此没有把该尝试当成完整游戏启动通过。

Curios 9.5.1 的无关战利品 Mixin 在开发映射环境中不兼容；测试副本只排除 `MixinEnchantedCountIncreaseFunction`，GUI / 配置目标 class 保持原始字节码，原模组文件未修改。不能据此宣称 Curios 所有游戏功能已验证。

注册失败测试由测试模组主动在物品注册事件中抛出异常。确认真实回滚和原始加载错误界面后，通过测试专用 Minecraft 帧回调启用事件总线，在渲染线程派发 tick，模拟 Placebo 已启用总线的失败后环境。断言通过后结束无存档的隔离进程；Windows 使用当前进程的 `TerminateProcess`，避免 `System.exit` / `Runtime.halt` 仍会执行的原生 DLL 清理。不验证原生关闭流程。测试辅助代码不进入 ZinZincFix 发布 JAR。
