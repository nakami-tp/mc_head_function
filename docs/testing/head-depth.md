# 头部玩法深化验收（2026-10-01）

规格：[玩法深化](../../.scratch/head-depth/spec.md)。能力与强化清单：[heads.md](../heads.md)。

## 自动回归

- `./gradlew build`：成功。26 个 GameTest、13 个 JUnit 测试全部通过。
- 新 GameTest 覆盖：兔子连跳加速、空中请求不重复起跳、R 重置、卸下移速清理；山羊有限转向与再次 R 停止、冷却、先击飞后落地眩晕和到期解除；蜜蜂指定 / 撤回、自有蜂不遮挡选择；熟练度限流、1000 点解锁、存档、死亡继承与精通成就；眩晕禁止兔跳和持续喷火。
- 发布 JAR 检查：不包含客户端验证入口或 GameTest 类。

## 真实客户端

命令：`./gradlew runDepthValidation`。独立测试模组、`build/depth-validation` 目录与新世界，不改用户存档。原有 `runClientValidation` 场景保留。

结果：[result.txt](../../build/depth-validation/result.txt)。正常客户端按跳跃键自动续跳，实测各级上升高度约 1.2473 / 2.5000 / 5.0000 / 10.0000 / 20.0000 / 40.0000 / 70.0000 / 100.00004 格（最后小数为客户端浮点误差，服务端超过起跳点 100 格时校正）。八级落地无伤。

通过网络发送 R 指令后：冲锋限制转向、三次命中、客户端累计观察到 120 个目标眩晕帧，眩晕到期清除；蜜蜂命中指定目标并触发特色成就；熟练度 HUD 与客户端玩家状态同步到 1000。

声音事件监听收到：3 次撞击、3 次蹄声、3 次眩晕钟鸣、2 次蜂群指令声。验证的是游戏客户端声音事件送达，没有录制或主观试听音频。

已查看实际截图，确认眩晕头顶星粒子、花蜜目标标记及百格跳跃画面；没有用自绘图代替游戏截图。

- [兔子第八跳](../../build/depth-validation/screenshots/depth-rabbit-apex.png)
- [山羊冲锋](../../build/depth-validation/screenshots/depth-charge-32.png)
- [落地眩晕星粒子](../../build/depth-validation/screenshots/depth-stunned-70.png)
- [蜜蜂花蜜标记](../../build/depth-validation/screenshots/depth-bee-target-60.png)
- [熟练度解锁](../../build/depth-validation/screenshots/depth-mastery.png)

## 两路审查

- Standards：发现客户端熟练度未进入移动领域状态、眩晕动作入口遗漏，均已修复并复核通过。
- Spec：另发现原版速度包限制导致 100 格跳跃无法达到，已用完整 double 自定义同步修复；三项发现全部复核通过。

剩余手感评估：高延迟独立服务器、多模组交互，以及玩家对冲锋转向速度与熟练度节奏的主观体验，未作为已验证结论。19 种精通效果已接线与构建检查，本次实机重点覆盖兔子、山羊、蜜蜂与熟练度通路，未逐一录制其余头的精通效果。
