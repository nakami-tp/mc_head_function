# 烈焰人火径与热量验收 — 2026-10-01

规格：[blaze-depth](../../.scratch/blaze-depth/spec.md)。操作说明：[heads](../heads.md#烈焰人火径与热量)。

## 结果

- 烈焰人专属 GameTest：12/12 通过。覆盖喷火直接伤害及墙遮挡、水平投掷留火径与到期、宽火浪一次命中、过热与重新按键、远端连锁、自有归属、低热/卸下取消、薄墙贴身目标、眩晕/死亡取消、换头不重置冷却。
- 真实 Fabric 1.21.1 客户端：通过。按正常 C2S 投掷与头技包触发，验证高热 HUD、过热及按住不自动重启、13 格外连锁目标伤害同步，以及真实旁观/入水中断高热且不释放火浪。
- 客户端结果：`PASS highFrames=62 coolingFrames=40 readySounds=4 waveSounds=6 spectator/water cancelled`。
- 仅含本轮烈焰人改动的提交快照 `/tmp/blaze-commit-check`：完整 46 条 GameTest 通过。
- 13 条单测与正式 JAR 构建通过；已检查正式 JAR 不含测试入口或测试结构。
- 全量 GameTest 曾运行，工作区并行新增的 `GolemGameplayTest.giantRollerCrushesRepeatedlyAndRecovers` 在 `Bedrock remains intact` 断言失败。本轮未修改铁傀儡测试或玩法，不将此结果写成全量通过。

## 复现

正常客户端场景：`./gradlew runBlazeValidation`。

只选烈焰人 GameTest，使用独立构建输出：`./gradlew -I scripts/testing/blaze-isolated.gradle runGameTest test build`。该 init script 仅过滤构建输出的测试注册表，源码默认全量注册表保持完整。

本轮客户端验收使用工作区快照 `/tmp/blaze-checkout`，避免其他并行 Gradle 任务覆盖 Loom 启动配置；执行 `runGameTest runBlazeValidation test build`。场景固定种子 42、白天、无天气及随机刷怪，创建新存档；客户端已自动退出。

## 实际画面与产物

已检查火径全景、高热、火浪传播及第一人称截图；从第一轮大粒子调整为较小火焰并后移喷口，环绕棒改为稳定物品渲染。

- [火径](../../build/blaze-validation/screenshots/01-trail.png)
- [高热与环绕棒](../../build/blaze-validation/screenshots/02-high-heat.png)
- [传播火墙](../../build/blaze-validation/screenshots/03-wave-100.png)
- [第一人称](../../build/blaze-validation/screenshots/04-first-person.png)
- [日志](../../build/blaze-validation/validation.log)
- [本轮提交构建 JAR](../../build/blaze-validation/artifacts/mc-head-function-1.0.0.jar)

截图使用全部粒子设置。尚未做多人压力测试或最少粒子设置验收。环绕棒当前只绘制本地第三人称角色，其他玩家仍能看到同步的喷火、火径及火墙。

## Standards

复核无硬性规范违反。建议的重复热量常量已统一引用；火径边缘粒子合并发包。高密度多人场景性能仍需后续实测。

## Spec

复核发现的薄墙扩张包围盒穿透问题已加入视线检查，并通过回归测试。取消验证已补充死亡、眩晕、换头冷却，以及真实客户端旁观和入水；后两者在高热中断前放置喷火锥外、火浪内的目标，避免漏检中断瞬间误放火浪。

两个轴均无未解决的阻塞项；全量铁傀儡测试失败单独保留记录。
