# 山羊矿车乱跑与上车验收

2026-10-11。闹鬼时被附身的矿车在范围内乱跑，不沿一条线冲；路过能上车的生物只加弱倾向，撞上才装上再带着跑。

## 复现

```sh
./gradlew test --tests com.nakami.mcheadfunction.rule.HauntRulesTest -x runGameTest
./gradlew runGameTest
./gradlew runCartValidation
```

客户端为独立测试模组，使用 `build/cart-validation` 内的新世界，固定高机位、平地、半夜；不访问玩家存档。该测试入口不会进入发布 JAR。
`runCartValidation` 在客户端退出后自动运行图像检查，需要 `python3` 和 Pillow；状态或图像检查失败都会使 Gradle 任务失败。单独复查已保存的截图可运行 `python3 scripts/testing/check-cart-wander.py build/cart-validation/screenshots`。

## 输入与断言

只挂山羊头并强制开场。矿车停在树南侧，两侧各一头不能走动的牛。附身后先采约 3 秒轨迹：航向必须转弯或路径必须有横向展开，且不能长时间对准同一头牛。若这段时间没上车，再把牛贴到矿车上证明接触装载。客户端必须看到牛坐在矿车里，装上后矿车继续移动。

连续采样 `sequence-001` 至 `sequence-028`（每 4 tick 一帧）。乱跑段用高机位；上车后镜头拉近，确认牛坐在矿车里再带着跑。图像检查要求乱跑段和载人段画面有明显差异，不能是静止重复帧。

## 产物

- 状态结果：`build/cart-validation/result.txt`
- 截图：`01-ready.png` 至 `05-carrying.png`；采样 `sequence-001.png` 至 `sequence-028.png`
