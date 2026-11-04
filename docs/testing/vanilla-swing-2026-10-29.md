# 恢复原版挥舞

按用户反馈撤回自定义双臂戒备、抬臂抽动、前倾、蓄力前冲与停顿。僵尸头保留左右交替攻击，第一人称使用原版 renderArmHoldingItem 的 swing/equip 参数，空闲时只显示自然主手；第三人称仅选择攻击臂，全部旋转由原版模型计算。拳套保留立体皮肤附着外观，第一/第三人称恢复原版挥舞，不再放大第三拳动作。

呼吸、心跳、命中粒子、吸血回流与三拳斩首音效保留。

验证命令：

```sh
./gradlew -I scripts/testing/zombie-isolated.gradle runZombieValidation runBeheadingValidation build
```

结果：僵尸真实客户端 PASS（自然单臂待机、第三人称无前倾、左右交替、左手盾牌、持物/摘头、拾取不中断连打）；拳套真实客户端 PASS（68 个原版挥击帧、左右手均渲染、第三人称挥击、无平面物品、三拳音效顺序正确与斩首规则）。42 GameTest 和构建通过。

已查看僵尸待机、交替挥击、第三人称放松及拳套挥舞实机截图。新版无声预览：`build/zombie-client-validation/vanilla-swing-preview.mp4`，74 个游戏 tick 截图以 20 fps 合成。发布包：`build/zombie-isolated/libs/mc-head-function-1.0.0.jar`。
