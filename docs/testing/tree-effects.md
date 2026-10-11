# 头树分档特效验收

2026-10-11。保留原版蓝色 SOUL 粒子、原有挂头门槛和闹鬼规则；新增灰雾、带拖尾的绕树游魂、头颅呼吸光晕与浮动光点，以及升级 / 闹鬼开场的约 60 tick 上升光潮。自发光几何不会照亮周围方块。飞出去的幽灵头渲染未修改。

## 复现

```sh
./gradlew runTreeValidation
python3 scripts/testing/check-tree-effects.py build/tree-validation/screenshots
./gradlew -I scripts/testing/tree-isolated.gradle test runGametest build
python3 /Users/nakami/.agents/skills/mc-client-validation/scripts/encode_capture.py \
  build/tree-validation/screenshots build/tree-validation/tree-transition.mp4 \
  --fps 20 --prefix transition- --expected-count 71 --overwrite
```

图像检查需要 Pillow。客户端为独立测试模组，使用 `build/tree-validation` 内的新世界，固定镜头、平地、时间与天气；不访问玩家存档。该测试入口不会进入发布 JAR。

## 输入与断言

通过服务端真实 `hang` / `take` 行为依次挂至 3、4、6、9、10 颗，客户端断言同步的头数、闹鬼状态和发光门槛。捕捉 9→10 的 71 帧，检查日夜、最少粒子、闹鬼开启 / 停止，最后取回至 3 颗。整个场景缺结果、失败或超时会使 Gradle 任务失败。

`check-tree-effects.py` 检查固定画面区域的青色像素占比，证明实际绘制了明显光效；对未触发和结束状态也设上限，防止残留。旧渲染器截图的升级、日夜发光、最少粒子和闹鬼画面均失败，新画面通过。它只覆盖可见面积，审美、脸部遮挡、原版蓝色灵魂粒子的保留仍需查看实际截图。

已查看烟灰档、灵魂档、升级光潮、10 颗夜景、日景、最少粒子、闹鬼及降档画面。原版灵魂粒子与新增光效同时存在，头的外观仍可辨认。粒子改为每世界 tick 最多生成一批，按原实现约 60 FPS 的浓度保留；不再随渲染帧率增减。小光点使用更少面数。

## 结果

真实客户端场景通过，9 项图像检查通过；27 项单测、13 项闹鬼 GameTest 和发布构建全部通过。发布 JAR 已检查不包含测试入口。顺带修正既有黎明测试：`waitAndRun` 的参数是绝对测试 tick，原先嵌套的 tick 5 会在同一 tick 检查；现改为 tick 10，并拆开修改世界时间的批次。

## 产物

- 状态结果：`build/tree-validation/result.txt`
- 截图：`build/tree-validation/screenshots/01-three-quiet.png` 至 `10-quiet-restored.png`
- 3.55 秒无声预览：`build/tree-validation/tree-transition.mp4`，由实际客户端每 tick 采样按 20 FPS 编码，不代表真实墙钟帧率。
- 发布 JAR：`build/tree-isolated/libs/mc-head-function-1.0.0.jar`

覆盖原版 Fabric 客户端；没有验收外部光影包，也没有进行多棵满挂树的性能压测。
