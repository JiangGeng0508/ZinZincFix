# 思索缩放兼容回归测试

目标：Minecraft 1.21.1 / NeoForge 21.1.265，Ponder 1.0.82、Ponderer 1.11.0、Create Simulated 1.3.2。

```powershell
python tests/ponder/prepare.py 'E:\App\PCL\.minecraft\versions\Aeronautics\mods' build/ponder-smoke/fixtures both
.\gradlew.bat --no-configuration-cache -I tests/ponder/smoke.gradle -PponderMode=both runClient
```

依次将两个命令的模式参数改为 `baseline`、`both`、`ponderer`、`simulated`、`absent`。`baseline` 不安装 ZinZincFix，必须在 `interpolateScaleFactor` 上复现零次成功注入才判为预期失败；其余模式加载构建出的发布 JAR。`absent` 另执行 `runServer`。

测试素材自动从整合包 JAR 及内嵌依赖提取，写入 `build/ponder-smoke/fixtures`；不修改安装目录。使用原始 Ponder 类、Simulated 场景/变换 Mixin、Ponderer 场景偏移/变换/Accessor。仅注册这次验证需要的 Mixin，保留原始方法字节码。Ponder 原有 Access Transformer 也加入测试环境，否则其 UI 基类会因 `AbstractWidget.render` 的 final 标记而无法链接。

测试通过 Unsafe 分配场景并填写缩放/插值字段，不创建玩家世界。SceneTransform 使用原始构造器与实际 `apply` / `screenToScene`；UI 缩放控制由小型子类提供，实际 PonderFreePreview 逻辑保持原样。检查：

- 0、0.25、0.5、0.75、1 的动画插值；
- 0.5、1、2.5 倍用户缩放；
- Ponderer 显式缩放覆盖；
- 自由预览冻结到 0.25 partial tick；
- 不属于当前 UI 的场景不乘用户缩放；
- 每个场景的真实矩阵缩放和屏幕坐标反算。

双模组和仅 Ponderer 各 124 项数值断言，仅 Simulated 20 项。反算允许 0.01 像素误差，因为 Catnip 快速三角函数与 Matrix4f 浮点路径略有差异。这个测试验证缩放组合，不覆盖所有非零平移/视图偏移、实际铜桶脚本播放或完整 UI 输入。

客户端与服务端的 `absent` 模式均检查无目标模组时补丁可加载。Windows 测试使用 JNA 终止自身这个无世界的临时测试进程，避开此前环境中的原生图形卸载异常；没有终止用户游戏，测试也不验证正常图形退出过程。

运行结果保存在 `build/ponder-smoke/{baseline,both,ponderer,simulated,absent}.log`。
