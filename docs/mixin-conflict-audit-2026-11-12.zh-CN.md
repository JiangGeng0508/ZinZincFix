# Aeronautics Mixin 冲突修复与排查报告

日期：2026-11-12。目录：`E:\App\PCL\.minecraft\versions\Aeronautics`。

后续更新：**1.0.7 已修复下文的 Deployer / Fluid Logistics 与 Moonlight / Supplementaries 两组功能覆盖**。之前的配置保护、思索修复、测试和原始排查报告已先提交为 `a6cec30`。具体实现和回归范围见 [功能覆盖兼容测试](../tests/overrides/README.md)。扫描统计仍是本报告原始快照，候选复核标签已更新。

2026-11-12 18:09 已安装 `mods/zinzincfix-1.0.7.jar`，SHA-256 为 `552BBE8D386F7D62CFDFCB121FA00802238AD2D4AA76A953112A92773597DC6D`。旧 1.0.6 已备份到 `zinzincfix-backups/function-overrides-20261112-180940/zinzincfix-1.0.6.jar`，备份和安装文件均已校验。需要完全重启客户端；使用仪表取物兼容的专用服务端也应更新并重启。

1.0.7 验证：未打补丁时复现两处功能丢失；修复后的客户端 194 项行为断言、服务端 168 项取物断言、思索回归 124 项数值断言通过；无目标模组的客户端/服务端加载和 Gradle `build` 通过。测试没有创建存档，完整整合包中的显示、玩家输入和网络流程仍需游戏内复测。

## 结果

ZinZincFix **1.0.6** 修复了 Ponderer / Create Simulated 的思索缩放注入冲突，保留 1.0.5 的配置加载保护及此前修复。已完成整套安装目录的静态扫描和现有日志复核；这不等于实际游玩过每个模组的所有功能。

日志中的问题归为 **9 组**：2 组致命注入问题（思索问题和已有补丁处理的 Northstar / Spelunkery），2 组功能覆盖问题（后续由 1.0.7 修复），5 组重复优化、相同访问器或有意覆盖。没有在本次日志集合中发现第三组不同的致命 Mixin 注入错误。

## 本次修复

涉及版本：Create Simulated 1.3.2（内嵌于 Aeronautics 1.3.2）、Ponderer 1.11.0、Ponder 1.0.82、MixinExtras 0.5.5、NeoForge 21.1.265。

原始证据：

- [RawOutput.txt 第 19 行](../RawOutput.txt#L19)：双方以优先级 1000 重定向 `PonderScene.scaleFactor`，Simulated 被跳过。
- 第 141 行：`interpolateScaleFactor` 要求一次成功注入，实际 `0/1`，导致场景变换类无法加载。
- 第 255–256 行：异常最终表现为 `Rendering screen`。日志没有证明线程死锁；窗口卡住发生在此次渲染异常之后。

`coremods/ponder_scale_compat.js` 在 Mixin 读取目标类之前转换字节码：

1. Ponderer 的 `ponderer$effectiveScale(PonderScene)` 从 `@Redirect` 改成 `@ModifyExpressionValue`，参数改为原表达式的浮点结果；其他地方使用它已经保存的 `ponderer$scene`。
2. 原来重新读取裸 `scaleFactor` 的分支改为消费传入值，所以能接收 Simulated 的动画插值。显式缩放覆盖、预览 partial tick 与用户缩放的原有分支保留。
3. Simulated 的缩放 Redirect 增加 `screenToScene` 目标，让绘制和鼠标反算使用同一动画比例。

正常组合结果为 `Simulated 插值比例 × 用户缩放`；Ponderer 显式覆盖开启时为 `覆盖比例 × 用户缩放`。没有提高优先级抢占、放宽 `require` 或删除整个 Mixin。原模组 JAR 保持原样，无新增运行时前置。

转换有方法签名和字节码形状检查，重复转换不会继续改写。只安装任意一方时也可使用；不存在目标类时无转换。支持范围是上述已验证版本，未来升级需重验。

## 验证

使用构建出的发布 JAR，原始 Ponder 场景/变换类及双方相关 Mixin，在隔离 NeoForge 客户端中运行：

| 场景 | 结果 |
|---|---|
| 不加载补丁，双方同时存在 | 复现 `interpolateScaleFactor` 零次成功注入 |
| 1.0.6，双方同时存在 | 124 项数值断言通过 |
| 1.0.6，仅 Ponderer | 124 项数值断言通过 |
| 1.0.6，仅 Simulated | 20 项数值断言通过 |
| 1.0.6，无双方模组，客户端 / 服务端 | 均通过加载检查 |
| Gradle `build` | 通过 |

检查动画插值、用户缩放、覆盖比例、冻结自由预览、非当前场景的缩放隔离，以及真实绘制矩阵与鼠标坐标反算。测试不创建存档；UI 控制使用小型适配器，未实际播放铜桶脚本或覆盖所有平移/视图偏移组合。原生图形退出也不在本测试范围。详见 [测试说明](../tests/ponder/README.md) 和 `build/ponder-smoke/*.log`。

安装后需完全退出并重新启动游戏，再打开铜桶与其他物品的思索，检查播放、暂停、缩放、自由预览和方块高亮位置。已经运行的 JVM 不会自动取得 Coremod 修复。

安装历史：2026-11-12 17:01 安装了 `mods/zinzincfix-1.0.6.jar`，现已升级为上文的 1.0.7。1.0.6 的 SHA-256：`7D4C1B380D9067D916F8515CCB649F60BF61438672F06E286C2B9CEC3F8DFA53`。旧 1.0.5 备份在 `zinzincfix-backups/ponder-mixin-20261112-170155/zinzincfix-1.0.5.jar`，已核对备份和新包哈希；其他模组 JAR 未改写。

## 日志中确认的其他问题

| 组合 / 入口 | 复核结果 | 处理状态 |
|---|---|---|
| Northstar / Spelunkery：`IceBlock.melt` | 原先同优先级向被覆盖的方法注入，已由已有 Northstar 优先级 Coremod 处理 | 保留既有修复 |
| **Deployer 0.1.3 / Fluid Logistics 1.3.2：`FactoryPanelBlock.getCloneItemStack`** | Deployer 的自定义面板取物逻辑被 Fluid Logistics 覆盖。后者最终调用 `FactoryGaugeDropPolicy.stackFor`，对非资源仪表行为返回普通 `FACTORY_GAUGE`；可能使 Deployer 自定义面板中键取物得到普通仪表 | **1.0.7 已修复：Deployer 改为 HEAD 注入，仅自定义激活面板取消回调，其余继续原资源仪表逻辑** |
| **Moonlight 3.8.2 / Supplementaries 4.0.2：制图台 `containerTick`** | Supplementaries 的 tick 保留自己的图标轮换，但 Moonlight 的 `CartographyTableHelper` 自定义槽位图标 tick 被跳过。双方渲染注入仍存在 | **1.0.7 已修复：Moonlight 改为 TAIL 注入，双方图标都更新，父类仅执行一次** |
| ModernFix / Lithium / Saturn：`Biome.getTemperature` | ModernFix 实现获选，Lithium / Saturn 方法被跳过；属于温度缓存策略重复，不是本次崩溃链 | 未改动；避免把优化收益相加 |
| Lithium / Saturn：堆肥桶 `getSlotsForFace` | Input / Empty 容器有跳过日志；原字节码都是返回固定槽位数组，属重复分配优化 | 未改动 |
| Lithium / Saturn：`PalettedContainer.acquire/release` | 双方都去掉线程检测的相同入口，Lithium 获选，Saturn 被跳过 | 未改动；这不证明整套异步区块实现线程安全 |
| BetterF3 / Sable：`ClientChunkCache.Storage.getChunks` | BetterF3 返回 `chunks`，Sable 是同字段访问器，方法名碰撞但读取对象一致 | 未改动 |
| Sable SA Compat 1.0.4：抓钩链条常量 | `dontGet` / `dontGet2` 保留 ordinal 19 / 9 的 `0.5`，广泛匹配的 `get` 将其他 `0.5` 改成 `0`。两条冲突告警是该写法的有意排除，不是两模组争抢 | 未改动；不应直接删掉两个“冲突”方法 |

这两组功能覆盖在 1.0.6 的思索修复后仍然存在，已按后续要求在 1.0.7 独立修复。两个目标原本只有继承方法，补丁在缺少具体方法时补上等价父类转发入口，使单独安装任何一方也有效。没有批量禁用模组或优化配置。

## 全量扫描范围与结果

| 项目 | 数量 |
|---|---:|
| 顶层 JAR | 352 |
| 递归含内嵌依赖的 JAR | 590 |
| class 文件（包括版本目录和重复副本） | 135,061 |
| Mixin 配置文件出现次数 | 594 |
| 检出的不同 `@Mixin` 类名 | 6,827 |
| 配置列出的不同 Mixin 变体条目 | 6,651 |
| 从实际应用日志补入的动态条目 | 50 |
| 有注解但未被配置或应用日志列出的类名 | 140 |
| 当前/归档日志、崩溃报告和 RawOutput | 44 个文件 |
| 去重后的相关日志消息 | 48（其中 34 条缺失目标，14 条冲突/注入失败消息） |
| 初筛重叠候选 | 387 |

387 个条目是**潜在重叠，不是 387 个故障**：70 个方法覆盖重叠、288 个覆盖与注入重叠、9 个 Redirect 重叠、18 个 Redirect 与其他注入重叠、2 个常量重叠。历史日志分别记录过双方应用的条目有 170 个；这不保证属于同一会话，也不等于对应 handler 都成功执行。

已逐类检索 Mixin 注解，解析配置列出的类和实际日志中的动态类，记录目标、优先级、方法、注入点、成功次数要求、插件、JAR 来源、类哈希及 refmap。140 个未列出类保存在清单中，包含其他平台、开发用途、可选兼容及插件路径；没有据此假定它们都会加载。

主要排除项：

- 67 个条目包含 AsyncParticles / BadOptimizations 的 Fabric 平台副本，不能把打包在同一分发物中等同于在 NeoForge 中同时生效。
- C2ME 同字段读写、数组 get/set 已按 opcode 和参数分开，剔除了初始结果中的 12 个假重叠。
- Iris 区块边框和 XaeroPlus 地图平移使用不同 slice；现有日志未显示这些位置的 Redirect 竞争。
- Voxy 的 Sodium / Fakesight 两份重定向实现相同，`require=0`；当前 Sodium 原始代码已经调用 `ClassLoader.getResourceAsStream`，双方针对旧 `Class.getResourceAsStream` 的注入点均不存在。
- 缺失目标警告包括未安装的可选模组，以及已安装模组中不再存在的旧类路径；例如 Xaero Patreon 旧路径、部分 Iris/Veil 兼容路径。它们应与致命注入错误分开处理。34 条原文全部保留在日志清单中，没有统一宣称它们都无影响。

其余 303 个条目仍属于未证明有运行时冲突的静态候选，集中在 Sable、AsyncParticles、ReplayMod、Sodium、Lithium、ModernFix、C2ME 等共同修改的粒子、渲染、世界和区块入口。它们的运行条件、优先级、slice、最终方法和功能行为不能仅凭注解判断。本次未强制加载所有可选目标，也未对这些组合逐一做玩法回归，因此不作“整包已无任何 Mixin 冲突”的结论。

## 明细和复现

- [387 条候选及复核标签](mixin-audit-candidates-2026-11-12.zh-CN.md)
- [扫描工具及命令](../tests/mixinaudit/README.md)
- `build/mixin-audit/inventory.json`：原始注解和来源清单、140 个未列出类、3 个缺失类记录。
- `build/mixin-audit/reviewed-candidates.json`：每个候选的完整方法注解与复核标签。
- `build/mixin-audit/log-review.json`：原始消息、行号、来源哈希和实际应用记录。

扫描记录的 3 个缺失类来自 Iris 的两个开发环境 Mixin 和 MouseTweaks 的一个 Fabric 配置引用，不是 JAR 读取失败。工具不执行所有配置插件或任意运行时转换，不完整模拟 refmap/动态选择器解析；因此该报告覆盖整个安装集合的静态与日志排查，保留运行时验证边界。
