# 烈焰人喷火视觉验收 — 2026-10-17

将三条静止小火苗线替换为向前推进的像素火舌，复用原版火焰粒子图集。火舌按喷射方向朝向，错开发射位置，逐渐膨大、减速、变暗并缩小；高热增强速度和体积。第三人称起点贴近头部，第一人称压低并缩小火舌。火浪沿原来的传播前沿逐段抬升。按用户要求移除三根环绕烈焰棒，保留热量 HUD。

服务器发送一次性喷射／火浪事件，48 格内客户端生成粒子；停止喷射不依赖额外的停止事件。客户端用逐 tick 扫掠射线截断墙和水，分别产生少量火星和烟汽，粒子中心限制在对应射程内。原有伤害、蓄热、过热、取消和连锁判定保留在服务器。

## 验证结果

- 烈焰人 GameTest：12/12 通过，覆盖伤害、遮挡、火径、火浪、连锁、过热和取消；最终完整构建同时执行的全部 GameTest 为 95/95 通过。
- 单元测试：47/47 通过；正式构建通过。
- 真实 Fabric 1.21.1 客户端通过正常 C2S 输入和 S2C 事件触发，断言粒子实际向前移动、六格射程、松手后生命周期结束、最少粒子仍可见、墙／水阻挡，以及原有 HUD、伤害和中断流程。
- 客户端固定种子、天气、地面、视线和第三人称镜头；检查高热、第一人称、火浪、碰墙与碰水截图，确认三根烈焰棒已消失。侧视镜头 Mixin 仅存在于测试源码集中。
- 动态预览由 70 张连续逐 tick 游戏截图按 20 fps 编码，长 3.5 秒，无音频，不用于测量实际帧率。
- 未做多个真实客户端同时喷火的压力测试或第三方资源包／光影兼容验证。

## 复现与产物

```sh
./gradlew runBlazeValidation
./gradlew -I scripts/testing/blaze-isolated.gradle runGametest test build
./gradlew cleanCompileClientJava build
./gradlew cleanRemapJar cleanRemapSourcesJar remapJar remapSourcesJar
ffmpeg -hide_banner -loglevel error -y -framerate 20 -start_number 0 -i build/blaze-validation/screenshots/motion-%03d.png -frames:v 70 -c:v libx264 -crf 18 -pix_fmt yuv420p -movflags +faststart build/blaze-validation/blaze-breath-preview.mp4
```

删除源码后需清理客户端编译输出及旧 remap JAR，防止已删除的 `BlazeHeatRender.class` 残留。发布 JAR 应含 `BlazeBreathEffects` 和 `BlazeFlameS2CPayload`，不含旧环绕棒渲染类、测试入口或测试镜头 Mixin。

- [高热喷火](../../build/blaze-validation/screenshots/09-side-high.png)
- [第一人称、最少粒子](../../build/blaze-validation/screenshots/11-first-minimal.png)
- [火浪](../../build/blaze-validation/screenshots/10-side-wave-480.png)
- [碰墙](../../build/blaze-validation/screenshots/13-wall-impact.png)
- [碰水](../../build/blaze-validation/screenshots/14-water-impact.png)
- [动态预览](../../build/blaze-validation/blaze-breath-preview.mp4)
- [客户端结果](../../build/blaze-validation/result.txt)
- [正式 JAR](../../build/libs/mc-head-function-1.0.0.jar)
