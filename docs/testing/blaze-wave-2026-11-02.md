# 烈焰人连锁火柱验收

- 命令：`./gradlew -I scripts/testing/blaze-isolated.gradle runGametest build`，15 个 GameTest 全部通过。新增两条分离火径同时传播并命中高处目标的回归。
- 命令：`./gradlew -I scripts/testing/blaze-isolated.gradle runBlazeValidation build`，真实客户端 PASS，构建通过。两侧高处目标均受伤，既有喷火移动、过热、最少粒子、墙和水碰撞及余焰消散检查通过。
- 查看截图：`build/blaze-validation/screenshots/03-wave-108.png`，可见火柱高于地面火径；验收场景中的牛为无重力高处目标。
- 发布 JAR 不包含 Validation / GameplayTest 类。
- 高火柱天花板截断尚无单独的回归场景；沿用射线截断。
