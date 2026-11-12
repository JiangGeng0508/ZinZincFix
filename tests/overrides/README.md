# 功能覆盖兼容回归测试

目标版本：Minecraft 1.21.1 / NeoForge 21.1.265，Create 6.0.10、Deployer 0.1.3、Fluid Logistics 1.3.2、Moonlight 3.8.2、Supplementaries 4.0.2。

```powershell
.\gradlew.bat --no-configuration-cache -I tests/overrides/smoke.gradle '-PoverrideMods=E:\App\PCL\.minecraft\versions\Aeronautics\mods' -PoverrideMode=both runClient
```

1.0.8 起，fixture 从原模组 JSON 保留 `overwrites`、`injectors` 等策略，仅裁剪无关 Mixin 和依赖完整模组的 plugin / refmap。之前的 fixture 重新生成了精简配置，遗漏 Fluid Logistics 的 `overwrites.requireAnnotations=true`，因此 1.0.7 的测试无法发现其严格覆盖异常。

用旧发布 JAR 复现本次启动错误（此命令预期失败，不是修复版通过测试）：

```powershell
.\gradlew.bat --no-configuration-cache -I tests/overrides/smoke.gradle '-PoverrideMods=E:\App\PCL\.minecraft\versions\Aeronautics\mods' -PoverrideMode=both '-PoverridePatch=完整路径/zinzincfix-1.0.7.jar' runClient
```

已复现 `FactoryGaugeHostBlockMixin ... @Overwrite is required by the parent configuration`。1.0.8 为该取物方法增加 `@Overwrite(remap=false)`，原始方法指令和严格检查均保留。测试现在在构造期主动加载目标类，若 Mixin 失败则立即退出，避免停留在错误界面。

模式：

| 模式 | 注入来源 | 预期 |
|---|---|---|
| baseline | 四个模组，不加载 ZinZincFix | 检查原始严格配置下的冲突；此前宽松 fixture 的功能覆盖基线仅保留为历史结果 |
| both | 四个模组 + 发布 JAR | 两处功能同时保留 |
| left | Deployer、Moonlight + 发布 JAR | 两个继承方法的转发入口有效 |
| right | Fluid Logistics、Supplementaries + 发布 JAR | 原有行为不变 |
| none | 无四个目标 Mixin + 发布 JAR | 新增转发入口与继承行为一致 |

`both`、`left`、`right`、`none` 也可运行 `runServer` 检查取物修复；服务端不会加载制图台客户端测试。已有 `tests/ponder` 的 `absent` 模式另验证没有 Create 等目标类时的客户端与服务端加载。

准备任务从安装目录读取原始 JAR，只保留此次需要的 Mixin 方法。核心行为使用原始字节码：Deployer / Fluid Logistics 的取物方法，Fluid Logistics 的 `pickStackFor` / `FactoryGaugeDropPolicy`，Moonlight / Supplementaries 的 `containerTick`，Moonlight 的图标注册辅助类。原方法内部指令不由测试重写；真正的补丁通过发布 JAR 的 Coremod 和 NeoForge Mixin 流水线应用。

Create 注册对象、面板数据、面板槽位定位和 Registrate 条目使用小型适配器，目标方块保留实际 `FaceAttachedHorizontalDirectionalBlock` 父类。测试方块在正常注册事件中注册，不创建世界。制图台使用真实 Minecraft 屏幕、菜单和 `CyclingSlotBackground`；计数子类仍调用实际背景 tick。Supplementaries 两个图标列表使用独立测试输入，未加载其全部物品注册和配置。

取物检查包括：同板四槽位、自定义/资源/普通/空面板、16 次即时激活切换、面板替换、未注册资源类型、非仪表状态、缺少方块实体，以及回退调用次数。制图台检查无自定义图标时的条件、注册自定义图标后连续 80 次更新、运行中新增图标、双方图标列表和父类 tick 恰好一次。

2026-11-12 的 1.0.7 历史验证结果（fixture 未保留严格覆盖策略）：baseline 复现两处功能覆盖；修复后的四种客户端组合共 194 项行为断言，四种服务端组合共 168 项取物断言通过。已有思索双模组回归的 124 项数值断言通过。

1.0.8 使用修正后的原始策略，both / left / right 的客户端及服务端组合通过；both 客户端为 42 项取物 + 11 项制图台断言，服务端为 42 项取物断言。客户端与服务端没有任何目标模组时另使用 `tests/configload` 的 `configAbsent` 模式验证发布 JAR 可加载。本次日志为 `build/override-smoke/strict-old.log`、`strict-fixed-{both,left,right}.log`，以及 `build/config-smoke/cardboard-{present,failure,absent}.log`。

测试日志在 `build/override-smoke/*.log`。此验证不覆盖 Create 的实际槽位几何、完整资源包显示、真实玩家输入和服务器网络取物流程；安装后仍需在整合包中复测。Windows 测试只终止自己的临时无世界进程，避免环境中已知的原生图形卸载问题；不验证正常退出流程，也不会终止用户游戏。
