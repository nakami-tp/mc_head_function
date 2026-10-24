# 断头拳套与末影人外观验收（2026-10-18）

## 改动

- 左上角移除熟练度文字和进度条，保留装备栏物品提示及兔子/山羊技能状态。
- 主手断头拳套命中表内成体任意部位，从第一拳起两秒内三拳取头，不造成近战伤害。拳套由 6 皮革 + 1 铁锭合成，256 耐久，每次成功断头消耗 1 点；创造模式不消耗。
- 每次有效拳击有约 0.5 秒衰减的头部摇晃；原空手瞄头方式仍可用。
- 拳套以立体模型包住实际玩家手臂。第一人称使用短蓄力、快速横扫、接触停顿和回收的大摆拳动作，移除原版物品挥动及冷却时的下沉；第三人称同步手臂横扫，左右手均支持，第三拳幅度更大。
- 三拳分别播放原创的短促拳击音效，逐拳加重，第三拳加入打飞破风声，避免叠加原版弱击/强击音效。素材由 `scripts/audio/generate-glove-sounds.py` 生成。
- 删除无头生物周期流血粒子及其全世界实体扫描，同时删除红色颈部横截面，露出原模型身体顶部。
- 末影人原版 `setAngles` 会重新设置 `head.visible = true`。隐藏操作移到动画之后，覆盖本体和发光眼睛层，绘制后恢复共享模型。
- 第三拳从受击生物的头部位置发射头，沿玩家出拳方向抛飞约 2.5 格，随后继续受重力影响下落；撞墙或落地才转为掉落物，不在半空按距离直接回收。忽略原受击身体的碰撞，避免末影人瞬移；回收物品不附加随机弹跳速度。
- 修补末影头翻滚时露出的透明底面。

## 可重复命令

```sh
./gradlew -I scripts/testing/beheading-isolated.gradle runBeheadingValidation runGametest test build
```

构建目录为 `build/beheading-isolated`；客户端使用独立 `build/beheading-validation` 目录和新世界，不操作玩家存档。测试入口不进入发布 JAR。发布前另执行 `clean build` 清除已删除类的旧编译产物。

## 结果

末影人修复前的真实渲染断言失败：`Decapitated enderman still renders its head: wrongFrames=98 hiddenFrames=0`。此时服务端已成功打飞一颗头，客户端已收到无头状态。

打飞修复前，新增 GameTest 失败：`Head must launch from victim, not player's fist`，确认旧实现从玩家手部创建飞头。

最终实机结果：

```text
PASS hiddenFrames=555 intactFrames=338 shakeFrames=51 gloveHiddenFrames=310 rightArmFrames=216 leftArmFrames=151 thirdPersonPunchFrames=8 flatGloveFrames=0 orderedPunchSounds=3 flightFrames=21 maxFlightZ=2.68825
```

空手断头和客户端正常攻击包触发的拳套三拳均通过；拳套测试准星位于躯干，两拳未断头，三拳后身体仍满血且位置未因飞头碰撞而瞬移。实际渲染覆盖左右手、第三人称摆拳、头部摇晃与隐藏，未渲染原版平面手持拳套。客户端声音监听确认三段音效按顺序各播放一次。飞头连续观察 21 个客户端刻，最大前向位移约 2.69 格。

29 项 GameTest 全部通过，包括拳套对末影人、猪、青蛙、蝙蝠、蜜蜂、铁傀儡的无伤取头、单次掉落、耐久、超时重新计数，以及幼体/表外生物/墙壁/距离限制、抛飞落地与原有头部玩法回归。

JUnit：47 项，失败 0，错误 0，跳过 0。构建通过。声音素材为 44.1 kHz 单声道 Vorbis，解码峰值分别约 0.781、0.846、0.954。

## 已查看的实际画面与预览

- `../../build/beheading-validation/screenshots/after.png`：无头末影人与完整末影人同帧，左上角无熟练度条。
- `../../build/beheading-validation/screenshots/punch-99.png`：准星在躯干，命中后末影人头部侧摆，立体拳套随实际皮肤手臂向前出拳。
- `../../build/beheading-validation/screenshots/head-in-flight.png`：完整末影头从无头身体前上方翻滚飞出。
- `../../build/beheading-validation/screenshots/golem-clean-neck.png`：铁傀儡顶部保留原身体表面，无红色截面。
- `../../build/beheading-validation/glove-combo-silent.mp4`：真实客户端逐刻截图按 20 fps 合成的 3.05 秒无声预览，不是实时帧率或游戏音频录制。
- `../../build/beheading-audio-preview.wav`：三拳原创音效素材的串联试听，不是游戏内录音。

保留末影人原版紫色粒子。此次客户端完整连击画面针对末影人验证，其他体型的取头结果由 GameTest 覆盖。未执行两台独立客户端的联网观战测试。

## 快速大摆拳追加验收

挥拳周期前 12% 向外蓄力，12%～30% 快速横扫，30%～44% 保持接触姿势，剩余时间收拳。横扫阶段约占整个动作的 18%，第三拳幅度增加 25%。第一人称包含侧向位移、拳面转向和手臂倾斜，第三人称使用对应手臂的肩部旋转；左右手镜像。渲染探针额外断言右手横向移动范围大于 0.65，防止退化成小幅直拳。

追加实机验证通过：`hookSweep=0.9861256 rightArmFrames=223 leftArmFrames=157 thirdPersonPunchFrames=8 flatGloveFrames=0`；29 项 GameTest 通过。最新摆拳无声采样视频：`../../build/beheading-validation/glove-hook-silent.mp4`。
