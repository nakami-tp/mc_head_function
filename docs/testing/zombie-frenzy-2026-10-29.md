# 僵尸头发狂反馈验收

## 实现

- 空手使用自身皮肤双臂保持紧绷戒备，戴上时短暂抬臂；残血提高起伏、间歇抽动、呼吸频率，低血量增加心跳。饱食归零一次性干喘，不循环刷提示。
- 左右交替短蓄力扑击，原版挥击进度驱动，不改伤害、攻击冷却、范围或视角。第三人称手臂前伸与轻微前倾，皮肤外层跟随。副手持物时只改空主手；使用物品、游泳、鞘翅飞行、睡眠交回原版；持物、死亡、摘头停止状态表现。
- 有效命中短闪、像素爪痕与碎粒爆发；服务端增加击杀标记同步，击杀增强爆发。实际吸血碎粒加速回胸口，抵达时心形闪动与吞咽声。
- 原版音源分层：呼吸、心跳、破风、沉击、湿润接触、击杀低吼。去除旧 HUD 固定挥击线条；无额外镜头抖动、FOV 拉扯或持续全屏红边。

## 验证结果

```sh
./gradlew -I scripts/testing/zombie-isolated.gradle test runGametest build runZombieValidation
# 调整动作与测试夹具后，最终完整复验：
./gradlew -I scripts/testing/zombie-isolated.gradle runZombieValidation build
```

- 47 单元测试、42 GameTest 通过，最终 BUILD SUCCESSFUL。
- 隔离真实客户端 PASS：戴头事件一次、饥饿事件一次、持续呼吸；双臂实际渲染；连续挥击左右交替；第一/第三人称；左手主手与右手盾牌；举盾恢复原版；持武器、摘头停用；实际击杀标记、回血到达、效果过期。
- 原有拾取不中断连打、界面/松键停止、持武器单击、空手挖掘仍通过；普通/残血挖掘 61/20 tick。
- 注册原版 SoundInstanceListener 检查声音引擎实际事件：10 次呼吸、6 次心跳、10 次沉击、8 次吞咽等。此为音频事件验证，未录制混音或人工试听，不代表主观听感验收。
- 粒子设置 MINIMAL；查看第一人称戒备/攻击、第三人称、左手盾牌、举盾与击杀截图，检查准星可用性、双臂交叠、目标遮挡；收窄出拳横摆后重新通过。
- 扩展测试夹具：举盾时维持真实使用键状态，第三人称换无遮挡角度；击杀后允许目标移除，继续检查摘头与音频事件。修正均仅在测试源码集中。
- 发布 JAR 检查无 validation、GameplayTest 测试类，测试客户端已退出。

## 产物

- JAR：`build/zombie-isolated/libs/mc-head-function-1.0.0.jar`
- 实机结果：`build/zombie-client-validation/result.txt`
- 截图：`build/zombie-client-validation/screenshots/`
- 动作预览：`build/zombie-client-validation/zombie-frenzy-preview.mp4`，74 张逐 tick 实机截图以 20 fps 合成，约 3.7 秒，无声；不是墙钟帧率测量。
