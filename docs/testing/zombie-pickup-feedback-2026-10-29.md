# 僵尸头：空手拾取与像素战斗反馈

## 改动

- 自动拾取分配空槽时跳过选中的徒手槽；已有物品照常堆叠，其他槽满则余物留地。损坏工具同样遵守。仅在生存/冒险、戴僵尸头且主手为空时启用，不改手动指定槽放物品。
- 三道爪痕改为小型阶梯像素，回血改为暗红方形粒子；手边尘粒仅在挥拳时产生。核心世界绘制不依赖粒子设置。
- 去掉重复叠加的强攻击声，以低音量原版史莱姆攻击声补湿润质感；实际吸血加进食声，击杀加僵尸低吼。

## 验证

```sh
./gradlew -I scripts/testing/zombie-isolated.gradle test runGametest build
./gradlew -I scripts/testing/zombie-isolated.gradle runZombieValidation
```

- 新增真实 ItemEntity.onPlayerCollision 回归：修复前在“Automatic pickup must preserve selected empty hand”失败，修复后通过。
- 47 单测、42 GameTest 通过；包含满包部分合并、损坏工具不抢手、主动切槽及摘头恢复原版拾取。
- 完整隔离客户端 PASS：持续攻击中真实生成并拾取 3 个腐肉，背包同步且主手仍为空，连续攻击次数达标；界面、松键、持武器行为通过。普通徒手挖掘 61 tick，僵尸残血徒手 20 tick。
- 核心反馈在 MINIMAL 粒子设置下验证，已查看 impact-01/02 以及拾取后的 05-held-fist-combat 实机截图。结果目录：build/zombie-client-validation/。
- 客户端多次初跑在模拟持续按键阶段失败；测试夹具补了 GLFW 请求焦点与背包关闭后的固定瞄准，最后完整通过。正式代码的失焦停止保护未改。
- 音效未进行录音或人工试听，本轮不声称已验证主观听感。
- 发布 JAR 无 validation 或 GameplayTest 类：build/zombie-isolated/libs/mc-head-function-1.0.0.jar。
