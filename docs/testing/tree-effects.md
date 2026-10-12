# 头树原版风格特效验收

2026-10-12，候选分支 `codex/haunted-tree-vanilla-fx`。用户反馈上一版柔光、圆环与连续拖尾不像 Minecraft。本版保留原版蓝色 SOUL 粒子的分布和浓度，移除这些平滑效果，改用原版 `soul_fire_flame` 粒子贴图、小方形余烬和逐层向上冒起的烟 / 灵魂粒子。头顶魂火清晰、无泛光，头的表情保持可见。随后针对“头本身发光不明显”，增加去除方向阴影的自发光贴图通道，以及复用实际头模型、略微扩大的反向外壳亮边；耳朵和角同样沿轮廓发光，亮边轻微明暗变化。没有修改树模型、挂头规则或附身玩法。

## 复现

```sh
./gradlew runTreeValidation
python3 scripts/testing/check-tree-effects.py build/tree-validation/screenshots
./gradlew -I scripts/testing/tree-isolated.gradle test runGameTest build
python3 /Users/nakami/.agents/skills/mc-client-validation/scripts/encode_capture.py \
  build/tree-validation/screenshots build/tree-validation/tree-vanilla-transition.mp4 \
  --fps 20 --prefix transition- --expected-count 71 --overwrite
```

图像检查需要 Pillow。客户端在 `build/tree-validation` 创建独立世界，固定镜头、平地、时间与天气，不访问玩家存档。测试入口不进入发布 JAR。

## 场景与结果

- 服务端真实挂头到 3 / 4 / 6 / 9 / 10 颗，检查客户端数量、闹鬼状态与发光门槛。
- 9→10 的转变逐 tick 采集 71 帧；白天、夜晚、最少粒子、闹鬼开始 / 停止和取回到 3 颗均有截图。
- 追加日夜近景，确认原版魂火贴图的像素边缘、头的可辨认程度，以及最少粒子下核心余烬仍可见。
- 12 项图像检查通过：原有 9 项，加上日夜头本体区域的亮边检查和石墙遮挡检查。旧近景头本体区域的青白亮边覆盖率为 0–0.5%，新近景为约 7.9–9.9%，统计区域排除了头顶魂火。石墙遮住后青色覆盖率为 0。上下限改为适用于离散小光点：下限检查可见性，上限限制大面积青色光晕回归。它不代替人工查看美术风格。
- 真实客户端场景、47 项单测、打包通过；发布 JAR 已检查不含客户端验收入口。
- 本次 21 项闹鬼 GameTest 首轮全部通过，完整 `test runGameTest build` 通过。

原版灵魂粒子仍受粒子设置影响；头顶魂火、方形余烬和升级时短促魂火由渲染器绘制，最少粒子下仍可见。自发光不会照亮周围方块。覆盖原版 Fabric 客户端，未测试外部光影包或多树性能。

## 产物

本体发光增强的前后近景与 4 秒无声视频保存在 [video/intro/tree-head-glow](../../video/intro/tree-head-glow/)。新增近景序列为 `head-glow-001.png` 至 `080.png`，每 tick 采样，以 20 FPS 编码；不是实际墙钟帧率录像。石墙截图为 `13-glow-occluded.png`。

上一版预览保存在 [video/intro/tree-vanilla-fx](../../video/intro/tree-vanilla-fx/)，避免清理构建目录后丢失。转变视频由真实客户端每 tick 截图按 20 FPS 编码，3.55 秒、无声，不代表实际墙钟帧率。

完整截图和状态结果在 `build/tree-validation/`；发布 JAR 为 `build/tree-isolated/libs/mc-head-function-1.0.0.jar`。
