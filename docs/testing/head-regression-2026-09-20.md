# 投掷与佩戴回归验收（2026-09-20）

环境：Minecraft 1.21.1、Fabric Loader 0.16.14、Yarn 1.21.1+build.3、Java 21、macOS 图形客户端。

## 修复与复现

修复前运行 `./gradlew runGameTest`，新增的三项回归均失败：

- `stackedHeadsEquipExactlyOne`：`Must equip exactly one ZOMBIE`。
- `allHeadsCanReplaceEachOther`：`Cannot replace ZOMBIE with CREEPER`。
- `zombieHopsStayAboveFloor`：`Zombie penetrated floor`。

生效中的头原先继续调用 `ThrownEntity.tick()`，其位移直接设置坐标，不解决方块碰撞；僵尸还会按固定周期在空中重置跳跃速度。现在改为带碰撞的移动、落地起跳、随距离收短水平跳跃，咬击动作只在成功伤害时触发。客户端预测在撞击面停止，避免等待服务器消息期间穿地；效果和回收物也定位到撞击面。

新增头原先直接使用原版装备交换逻辑，会复制整组物品；原版头没有走新增头的入口。现在表内 19 种头共用单件穿戴逻辑。单个换装把旧装备放回手中；整组换装把旧装备放入背包，满背包则掉落。保留物品组件、创造模式不消耗手持物品、绑定诅咒限制。

僵尸咬击使用更清楚的进食声；末影头每次成功吃块，在目标位置播放进食声和末影人瞬移声，保留原版破坏方块的声音。

## 自动验证

```sh
./gradlew test runGameTest build
./gradlew runClientValidation
```

- 11 项 JUnit 测试通过。
- 20 项 GameTest 通过。覆盖全部头单件穿戴、342 个不同头之间的替换组合、主副手整组替换、组件保留、创造模式、绑定诅咒、满背包旧装备掉落，以及僵尸落地追咬、苦力怕回收位置和现有玩法。
- 正式 `build/libs/mc-head-function-1.0.0.jar` 不含 GameTest 或客户端验收入口。

客户端入口独立位于 `src/clientValidation`，运行目录是 `build/client-validation`，每轮创建新世界、清理上一轮结果与截图，不使用玩家存档。缺少结果、超时、异常或断言失败会使 Gradle 任务失败。

## 真实客户端场景

固定种子 42、白天、晴天、石英平台、镜头、目标位置和生命值。服务端触发正常投掷处理；声音监听器记录客户端音效播放，右键装备由客户端交互管理器发包并检查同步结果。

1. 僵尸头向下投掷，落地后追牛。连续检查头底部不低于平台、落地→腾空→再次落地、同步咬击动作、目标受伤和独立进食声。
2. 末影头命中 16 个相连石头，检查全部吃完，收到 16 次进食声、16 次吃块瞬移声及 1 次投掷声，结束后没有继续触发。
3. 普通苦力怕头向下投掷，检查回收物存在且保持在地面上，查看撞击后实际帧。
4. 生存模式已戴末影头，主手持 64 个僵尸头或苦力怕头，客户端发起右键。检查头盔槽为 1、手持为 63、旧末影头进入背包。

已查看真实截图中的僵尸腾空、咬中目标、末影头吃块和苦力怕回收画面。最终断言与声音事件分别保存在 `build/client-validation/result.txt` 和 `sound-events.txt`。

视频是按客户端 tick 采样帧以 20 fps 编码的无声预览，不是音频录制，也不能证明真实墙钟帧率。音效验收证明客户端实际触发播放，未录音评估主观听感。场景使用固定平地，不声称覆盖所有复杂地形或多人网络延迟。

```sh
python3 /Users/nakami/.agents/skills/mc-client-validation/scripts/encode_capture.py \
  build/client-validation/screenshots build/client-validation/zombie-hops.mp4 \
  --prefix zombie- --fps 20 --expected-count 100 --overwrite
python3 /Users/nakami/.agents/skills/mc-client-validation/scripts/encode_capture.py \
  build/client-validation/screenshots build/client-validation/enderman-eating.mp4 \
  --prefix enderman- --fps 20 --expected-count 100 --overwrite
```
