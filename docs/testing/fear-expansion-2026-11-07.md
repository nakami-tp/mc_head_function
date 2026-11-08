# 温雷产物与末影人惊叫验收（2026-11-07）

规格见 [恐惧扩展](../../.scratch/fear-expansion/spec.md)，产物表见 [头效果清单](../heads.md)。按当前工作区的 **100 tick / 5 秒**温雷引信验收；本次未改引信时长。

## 自动验证

```sh
./gradlew -I scripts/testing/fear-expansion-isolated.gradle runGametest compileFearValidationJava -x downloadAssets
./gradlew test runGametest build -x downloadAssets
./gradlew runFearValidation -x downloadAssets
```

- 恐惧隔离场景 11 项通过；全量 GameTest 155 项、JUnit 56 项通过；构建通过。发布 JAR 不含客户端验证入口和 GameTest 类。
- 新增动物奖励覆盖牛、红/棕哞菇、猪、马、驴、骡、普通/行商羊驼、兔子、猫、狼；检查三级前无奖励、同轮不重复、无惊吓伤害，以及所有选定幼体不产物。原鸡/羊/村民场景继续通过。
- 新一轮恐惧在冷却内不奖励，保存并重载同一实体亦不绕过冷却；超过 1200 tick 后的新一轮恢复奖励。
- 末影人三级把草方块转成恰好一份物品，手中清空、无攻击目标、实际远离玩家。恐惧消退后保存重载，再次抱持苔藓块，在原额外产物冷却内仍可正常丢下；未生成珍珠或复制方块。

## 实际客户端

隔离新世界通过真实攻击键与普通网络路径温雷，采用最低粒子设置。原有自爆验收角色穿测试护甲并逐 tick 记录受伤，保证能继续进入末影人场景；仅测试夹具有此装备。

最终结果：`build/fear-validation/result.txt` 为 PASS，Gradle 的像素检查亦通过。客户端收到 1 次原版 `entity.enderman.stare` 起始大叫、3 次 `entity.enderman.scream` 后续惊叫；末影人张嘴实际渲染 360 帧、恢复闭嘴 112 帧。断言实际逃跑、手中方块消失、恰好一份草方块，以及恢复后不再追加惊叫。

已查看真实截图：

- `build/fear-validation/screenshots/enderman-before.png`：抱块、正常嘴部。
- `build/fear-validation/screenshots/enderman-screaming.png`：张嘴、手中无块、地上有草方块。
- `build/fear-validation/screenshots/enderman-flee-600.png`：迈步逃跑并保持张嘴。
- `build/fear-validation/screenshots/enderman-recovered.png`：恢复正常嘴部。

场景使用遮阳顶和海晶灯，排除日光瞬移并保证画面可见。截图不包含录音，声音结论来自客户端实际收到的声音事件。

旧像素检查固定假设第 66 tick 是绿色，在 5 秒引信下该帧实际闪白。现改为从完整采样中定位真实绿色头模型，继续检查模型尺寸、绿色/白色交替、两次晃动和完整反扑帧，不放宽原阈值。最终绿色峰值 0.703、白色峰值 1.000、晃动水平幅度 0.044、方向变化 4 次。

## 审查

按项目约定和本次规格分别完成审查，确认问题均为 0。采用审查建议，将首次惊叫改为原版起始激怒声，后续用愤怒环境叫声。工作区并发的 R 弹药、山羊等改动不属于本次功能范围。
