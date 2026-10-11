# 躁动南瓜验收

2026-10-11。闹鬼时范围内仍是方块的雕刻南瓜 / 南瓜灯原地抬起歪头、悬停后骤然回正，眼窝点冷色鬼火；未雕刻南瓜不参加；没挂僵尸头也躁动；宿主南瓜不叠加躁动。

## 复现

```sh
./gradlew test --tests com.nakami.mcheadfunction.rule.HauntRulesTest
./gradlew runPumpkinValidation
python3 scripts/testing/check-pumpkin-effects.py build/pumpkin-validation/screenshots
```

客户端为独立测试模组，使用 `build/pumpkin-validation` 内的新世界，固定镜头、平地、半夜；不访问玩家存档。该测试入口不会进入发布 JAR。

## 输入与断言

先挂 8 种不含僵尸的头并关闭自动开场，拍安静画面；再 `start` 拍齐点眼、躁动中、最少粒子和结束。雕刻南瓜必须仍是方块。随后挂上僵尸头再开场，断言出现带雕刻脸的宿主实体。图像检查比较中间那张雕刻脸：开场及最少粒子下必须出现可见冷色鬼火，结束后消失；24 张连续采样帧中，本体轮廓顶部和底部都移动至少 12 像素；抬起后底部必须露出完整地板，避免静态模型重影或地板裁切。客户端确认真实方块与朝向不变、静态模型在结束后恢复、未雕刻南瓜不替换模型。宿主截图前移除所有剩余原地雕刻脸，避免把别的南瓜眼火误认成宿主眼火，并检查宿主脸部没有冷色火焰。

## 产物

- 状态结果：`build/pumpkin-validation/result.txt`
- 截图：`01-quiet.png` 至 `06-possessed.png`；本体动作采样 `motion-000.png` 至 `motion-023.png`（每 5 tick 一帧）
- 无声采样预览：`build/pumpkin-validation/pumpkin-motion.mp4`（4 fps，对应游戏内 20 tick/s）

## 本轮结果（2026-10-11）

真实客户端、画面检查及构建通过。24 帧中顶部移动 60 像素，底部移动 12 像素；开场和最少粒子下可见冷色眼火，停止后与宿主脸部的冷色火焰比例均为 0。检查过实际歪头帧、最少粒子帧和宿主帧。自动检查只保证动作与可见性，诡异观感仍待用户复看。
