# 烈焰人熔炉溢火验收

2026-10-11。闹鬼时被附身的熔炉点亮，炉火从炉口和顶上溢出来，像壁炉失火；不转向、不喷 6 格火柱、不造成伤害。

## 复现

```sh
./gradlew test --tests com.nakami.mcheadfunction.rule.HauntRulesTest
./gradlew runGameTest --tests com.nakami.mcheadfunction.HauntGameplayTest.aPossessedFurnaceOverflowsWithoutHarm
./gradlew runFurnaceValidation
```

客户端为独立测试模组，使用 `build/furnace-validation` 内的新世界，固定镜头、平地、半夜；不访问玩家存档。该测试入口不会进入发布 JAR。
`runFurnaceValidation` 在客户端退出后自动运行图像检查，需要 `python3` 和 Pillow；状态或图像检查失败都会使 Gradle 任务失败。单独复查已保存的截图可运行 `python3 scripts/testing/check-furnace-overflow.py build/furnace-validation/screenshots`。

## 输入与断言

只挂烈焰人头并强制开场。熔炉朝南，东侧放一头牛。拍安静（未点亮）、附身后溢火、最少粒子和结束后熄灭。熔炉必须仍是方块、朝向不变。牛的生命值和着火状态不变。图像检查比较炉口附近暖色火焰：附身及最少粒子下必须明显多于安静画面；画面远端不应出现火柱；结束后火焰消失。连续采样帧的火焰轮廓必须晃动。

客户端以 `HauntFurnaceEffects.overflowing` 确认特效存在；点亮、朝向和牛的状态在服务端线程检查，不用异步诊断字符串替代客户端画面。安静截图写完才开场，24 张采样全部在停止之前拍摄，停止后等待幽灵头返回，所有截图写完才退出。镜头将玩家眼高计入取景；图像按 854×480 逻辑像素分析，避免 Retina 倍率影响阈值，并排除安静画面里挂着的烈焰人头对火焰轮廓的干扰。

## 产物

- 状态结果：`build/furnace-validation/result.txt`
- 截图：`01-quiet.png` 至 `04-stopped.png`；溢火采样 `motion-000.png` 至 `motion-023.png`（每 5 tick 一帧）
