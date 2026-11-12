# 配置生命周期回归测试与排查工具

使用 Java 21，在仓库根目录执行。所有游戏测试使用 `build/config-smoke/` 下的隔离目录，不进入玩家存档，也不使用整合包的配置。

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
.\gradlew.bat build
.\gradlew.bat --no-configuration-cache -I tests/configload/smoke.gradle '-PconfigMods=E:/App/PCL/.minecraft/versions/Aeronautics/mods' runClient
.\gradlew.bat --no-configuration-cache -I tests/configload/smoke.gradle '-PconfigMods=E:/App/PCL/.minecraft/versions/Aeronautics/mods' -PconfigFailure runClient
.\gradlew.bat --no-configuration-cache -I tests/configload/smoke.gradle -PconfigAbsent runClient runServer
```

`configMods` 目录需要恰好各一份匹配的 TaCZ 1.2.0、AppleSkin 3.0.9、CoroUtil 1.21.0-1.3.9、Curios 9.5.1、ForgeEndertech 12.1.4.0 和 CreateCardboardThings 1.21.1-1.0.2 JAR。测试使用本次构建的 ZinZincFix 发布 JAR，而非以开发 source set 代替发布产物；前五个目标模组的内嵌依赖由 NeoForge 加载。CreateCardboardThings 仅提取原始 Config 和 sound 包类到测试模块，注册其真实 COMMON 配置，避免为了无世界声音回调测试加载完整 Create。

## 原始方法与补丁对照

测试直接从原始 JAR 读取回调字节码，通过独立类加载器绕过 Mixin，配置对象仍使用真实 NeoForge / 模组对象；补丁侧由 NeoForge 正常加载，并检查注入方法。

- 加载前：原始 TaCZ 趴下 / 瞄准、AppleSkin tick、Curios 非背包界面、ForgeEndertech 无世界按键均应重现 `IllegalStateException`；补丁侧应正常返回。
- 输入：未加载时清除手柄按下状态；加载后比较按住 / 切换配置和手柄按下 / 松开组合在无世界场景中的原有状态行为。
- AppleSkin：三组配置值下各比较 40 个 tick 的闪烁值、未限制闪烁值和方向，覆盖往返边界。
- 配置卸载：用真实 `ModConfigSpec.acceptConfig(null)` 解除配置，确认补丁跳过且不自行加载配置；重新挂载原对象后继续运行，不修改配置文件。
- CoroUtil：构造期与 common setup 期间不消费首次初始化标记；配置就绪后，模拟加载完成状态并验证同步只初始化一次。
- 非背包界面 / 无世界按键：配置未加载时跳过，配置就绪时与原方法效果一致。
- CreateCardboardThings：原始 `onPlaySound` 在配置未加载时对 MUSIC / RECORDS / MASTER 三种声音均抛出配置异常；补丁保留声音对象与空的唱片机状态。配置开关两种取值下与原始回调对照，卸载时再次复现原始异常，重新挂载后恢复。

正常对照在配置已加载的 common setup 阶段自动退出，未进入玩家世界或验证枪械实际交互。CoroUtil 的完成状态通过测试临时修改 `ClientModLoader.loading` 实现，测试后恢复。

本次正常对照输出：

```text
CONFIG PRESENT PASS: 443 checks against original mod bytecode
```

## 注册失败与缺少模组

`configFailure` 在物品注册事件中故意抛出 `CONFIG SMOKE: intentional registry failure`。预期会生成 FML 加载错误报告，这是测试输入，不是补丁失败。

测试专用 Minecraft 帧 Mixin 等待实际 `LoadingErrorScreen`，随后启用真实 NeoForge 游戏事件总线，在渲染线程派发 5 次 `ClientTickEvent.Post`，每次 tick 另派发 `PlaySoundEvent` 菜单音乐事件给原始纸板唱片机订阅类。NeoForge 默认以停止状态创建该总线，正常注册失败时不会启动；整合包中的 Placebo 9.9.4 构造函数会提前启用它，因此测试显式模拟此条件。要求 CoroUtil 初始化标记保留，TaCZ / AppleSkin 配置仍未加载，音乐对象保持不变，且无二次配置异常。

成功标记：

```text
CONFIG FAILURE PASS: loading error screen reached; five event-bus tick posts after registry rollback without secondary config errors
CONFIG ABSENT PASS: packaged patch loads without optional mods
```

缺少可选模组时分别启动客户端和专用服务端，验证客户端 Mixin 不强制引入目标模组或服务端客户端类依赖。

## Curios 开发环境适配及限制

Curios 9.5.1 的 `MixinEnchantedCountIncreaseFunction` 无法匹配此开发映射环境中的无关战利品方法。测试任务生成隔离副本，只从 `curios.neoforge.mixins.json` 删除该条 Mixin；目标 GUI / 配置类的字节码未改变。原始 JAR 不修改，基线类加载器也继续读取原始 JAR。该适配不进入发布补丁，不代表验证了 Curios 的战利品行为。

正常渲染完整启动的早期尝试及通过断言后的退出曾以 Windows 原生退出码 `0xC0000409` 结束，未归因。正常对照提前在 common setup 执行；完成断言后结束无存档的隔离进程。Windows 使用当前进程句柄调用 `TerminateProcess`，避免 `System.exit` / `Runtime.halt` 仍会执行的原生 DLL 清理；其他系统使用 `Runtime.halt`。这不能被报告为完整整合包渲染、游戏内交互或正常原生关闭流程验证通过。

## 扫描已安装模组

```powershell
.\gradlew.bat --no-configuration-cache -I tests/configload/audit.gradle '-PconfigMods=E:/App/PCL/.minecraft/versions/Aeronautics/mods' configAudit
```

输出：`build/config-audit/candidates.json`。

工具递归读取 JAR / 内嵌 JAR，记录直接 `ConfigValue.get()/getRaw()`、方法调用、静态类初始化和延迟方法引用，从客户端事件和静态初始化入口反向追踪最多 8 层。`deferredLambdaOnPath` 标记路径包含方法引用 / lambda，这些路径不代表类加载时就执行配置读取。

本次扫描：352 个顶层 JAR、590 个含内嵌 JAR、133,896 个 class 文件；45 个客户端事件候选、76 个静态初始化候选，解析错误为 0。候选必须人工检查分支、加载阶段及可选模组条件。工具不覆盖完整的虚调用、反射、所有事件和 Mixin 动态行为；也不模拟内嵌依赖的运行时版本选择。具体复核结论见 [排查报告](../../docs/config-loading-audit-2026-11-12.zh-CN.md)。
