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

## 构建与安装

使用 Java 21 工具链：

```powershell
.\gradlew.bat build
```

将 `build/libs/zinzincfix-1.0.0.jar` 放入整合包客户端的 `mods` 文件夹，然后重启游戏。

## 游戏内复测

1. 完全退出并重新启动客户端，确认首次资源加载完成后，将 `addon_gancho:plunger_launcher_test` 放入快捷栏不再崩溃。
2. 检查快捷栏、背包、主副手和第三人称下的发射器模型，并检查同样使用这些模型的 `addon_gancho:grapple_swing`。
3. 使用 `F3+T` 重载资源，再次检查以上物品。
