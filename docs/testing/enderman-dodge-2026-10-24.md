# 末影人头文案对齐验收

普通闪避冷却为 40 tick（2 秒），精通为 20 tick（1 秒）。受伤时随机水平瞬移 8 格，允许与方块重叠；只有瞬移成功才取消触发伤害。保留世界边界、高度和已加载区块检查。窒息伤害不触发闪避、不消耗冷却。投掷吃块和多头任务逻辑没有调整。

## 可重复命令

```sh
./gradlew -I scripts/testing/enderman-isolated.gradle test runGameTest build
./gradlew -I scripts/testing/enderman-isolated.gradle runEndermanValidation
```

隔离脚本使用 `build/enderman-isolated` 构建目录，仅注册末影人 GameTest；完整测试注册表仍保留其他场景。真实客户端使用 `build/enderman-client-validation` 下的新存档，测试后自动退出。

## 结果

- 47 项 JUnit 测试通过，4 项末影人 GameTest 通过，构建成功。
- GameTest 通过真实伤害入口验证：触发伤害免疫、水平移动 8 格、允许卡墙、冷却中正常受伤、普通第 40 tick 和精通第 20 tick 恢复闪避、窒息不会触发闪避。冷却边界在服务端测试中通过实际玩家状态的 tick 方法推进。
- 真实客户端验证普通闪避：服务端触发伤害被取消、初始冷却 40 tick、客户端收到新落点并与石墙重叠，随后自然窒息伤害同步到客户端。结果文件为 `build/enderman-client-validation/result.txt`。
- 已查看实际截图 `build/enderman-client-validation/screenshots/trapped-landing.png`：第一人称视野被墙内石头遮住，HUD 可见；截图用于确认卡墙视野，不作为外部镜头或粒子效果验收。
- 发布产物 `build/enderman-isolated/libs/mc-head-function-1.0.0.jar` 不包含测试或客户端验收入口。

本次未重跑投掷吃块实机场景；相关实现没有修改。世界边界和未加载区块失败分支保留代码检查，未新增实机场景。
