# 山羊冲锋、漂移与撞怪验收

本轮在原有破拆、跨步、Shift 急转基础上加入侧滑惯性、人物压弯支撑姿态、第一人称侧倾和短寿命定向扬尘，并处理开发客户端撞怪时缺少 `GoatCharge$Landing` 的崩溃。

## 可重复命令

```sh
python3 scripts/testing/check-development-runtime.py
./gradlew --project-cache-dir .gradle/goat-validation-cache \
  -I scripts/testing/goat-isolated.gradle runGameTest runGoatValidation assemble
```

测试使用独立编译目录、Gradle 执行历史和新建客户端世界，不修改用户存档。验收客户端请求窗口焦点，以经过生产代码的真实 A/D 与 Shift 输入路径。侧面展示镜头和渲染探针只存在于测试模组中。

## 崩溃诊断与修复

- 用户日志的异常是命中分支首次创建落地记录时找不到 `GoatCharge$Landing`；源码和发布 JAR 均含该类。启动后的编译输出暂时消失可以导致这个故障。
- `check-development-runtime.py --unprotected` 在临时项目中先加载外部类，删除编译目录，再触发命中分支，稳定得到相同的 `NoClassDefFoundError: GoatCharge$Landing`。这是开发运行时类被删除的最小复现，不证明用户当时具体由哪一个构建命令删除了输出。
- `scripts/development/runtime-snapshot.gradle` 为开发运行任务复制本地类与资源，并固定 Loom 启动配置中的类路径。快照位于 `.gradle/runtime-snapshots/`，不随 `build/` 清理而消失；JVM 退出后由 finalizer 删除自己的快照。
- 受保护的最小复现通过。本轮还在真实客户端进入世界后，临时移除了隔离编译目录的 `GoatCharge$Landing.class`，让真实冲撞命中苦力怕，成功生成命中截图且不崩溃；随后恢复该文件用于打包。
- 已经启动的旧客户端不会自动获得代码或启动快照，需要退出并重新启动。

## 漂移与崩溃修复验收结果（前一轮）

- `HeadDepthTest` **13/13 通过**。包括原有破拆、跨步、转向、击飞落地眩晕，以及新增的怪物单次命中、落地记录消费、漂移侧滑与松键恢复抓地。
- 新侧滑测试先在旧实现上失败（朝向和行进方向夹角约 0°），新实现通过：持续 10 tick 右漂时夹角在 15°–35°，保持 70% 输出速度，松键后 8 tick 内夹角恢复至 2° 以下。
- 真实客户端 `build/goat-validation/result.txt` 为 **PASS**，最终构建成功。覆盖普通/最少粒子两档撞怪、击飞、落地眩晕和到期恢复；一轮命中一个反馈事件，共 2 个命中包；命中火花实际绘制 408 次。
- 真实客户端检查了行进方向与身体朝向不同、第三人称实际模型矩阵向弯心倾斜（水平分量约 0.309）、左右漂移姿态、第一人称相机实际旋转、松键回正、只按 Shift 不漂移，以及停止后粒子消失。
- 原有脆性方块穿行、1 格跨步、硬墙碎砖、投掷回收、鼠标锁定和停止解锁继续通过。破拆粒子峰值 463，漂移阶段粒子峰值 83。
- 发布 JAR：`build/goat-isolated/libs/mc-head-function-1.0.0.jar`。确认包含 `GoatCharge$Landing`，不含验收入口、GameTest、展示镜头或渲染探针。

## 实机画面

已查看：

- `build/goat-validation/screenshots/goat-drift-peak.png`：人物压弯、支撑步与向外散开的贴地尾烟。
- `goat-drift-first-person.png` / `goat-drift-release.png`：轻微侧倾与松键恢复。
- `goat-monster-impact.png` / `goat-monster-impact-minimal.png`：接触闪光与扩散尘圈。
- `hit-sequence-010.png`：目标真实腾空。
- `goat-monster-stunned-minimal.png`：目标落地后仍存活；眩晕状态及到期由服务端断言。

无声预览：`build/goat-validation/goat-drift.mp4`（25 帧）、`goat-monster-impact.mp4`（33 帧）。均为真实逐 tick 截图按 20 fps 重放，不含音轨，不作为墙钟帧率证明。保留了 45 帧破拆序列；旧 `goat-crush-through.mp4` 是上一轮产物，本轮未重新编码。

限制：本轮跑的是隔离的 `HeadDepthTest`，没有跑全量 GameTest。70% 输出速度在 GameTest 中断言；客户端后续原版摩擦会改写速度大小，因此客户端检查侧滑方向与可见反馈。最少粒子档覆盖漂移与撞怪，未重复脆性方块破拆。尚未专门验证多人观察者视角；特效和姿态消息已发送给追踪者。

## 时长配置追加验收

默认冲撞时长由 5 秒翻倍为 10 秒。使用原版游戏规则配置，单位为整数秒，范围 1–3600：

```mcfunction
/gamerule goatChargeDurationSeconds
/gamerule goatChargeDurationSeconds 20
/gamerule goatChargeDurationSeconds 10
```

分别为查询、改为 20 秒、恢复默认；服务器控制台去掉开头 `/`。需要管理员权限或启用作弊。规则随存档保存，对该存档内玩家生效；每次冲撞开始时读取，修改不影响已经开始的冲撞。结束后冷却仍为 8 秒。

命令 `./gradlew --project-cache-dir .gradle/goat-validation-cache -I scripts/testing/goat-isolated.gradle runGameTest assemble` 通过，**14/14 GameTest**。新增测试先因未注册命令失败，随后通过；覆盖默认 200 tick、命令查询和修改、下一次冲撞使用新值、正在冲撞时长不变、2 秒期限前后、配置序列化重载、非法值拒绝与管理员权限。构建成功，发布 JAR 已更新且不含验收入口。本次只修改服务端时长配置，没有重跑上一轮视觉验收。

## 第一人称转向顿挫追加验收

症状主要出现在 A/D 转向和 Shift 漂移。用独立真实客户端的无障碍平地场景复现：正常输入发往服务器，由服务器正常返回朝向包；探针在收到包前后，以相同模拟插值时间调用实际 `Camera.update`，另在 `WorldRenderEvents.END` 逐渲染帧记录镜头角度与位置。没有直接在客户端伪造朝向包。

复现/回归命令：

```sh
MHF_GOAT_SMOOTHNESS=1 ./gradlew --project-cache-dir .gradle/goat-validation-cache \
  -I scripts/testing/goat-isolated.gradle runGoatValidation
```

旧实现该命令失败。根因是服务器每 tick 的朝向更新直接修改本地玩家 yaw，第一人称跟随这个离散角度；普通转向每包跳 1.8°、漂移每包跳 6°。同一场景中的位置回拉包为 0。仅修改镜头角度过渡后，服务端速度包数量仍为 152，镜头顿挫明显消失，支持这是镜头同步路径的问题，而非该场景中的位置纠正或特效掉帧。

修复：`GoatChargeControl` 保存连续的可见角度，每次从当前可见角度向新目标用 50 ms 渲染时钟过渡；`GoatCameraMixin` 只对本地第一人称应用这个角度。服务器的移动方向、转向限制和命中判断继续使用权威状态；镜头轻震和漂移侧倾继续叠加。停止时从当前可见角度恢复自由视角，避免结束瞬间跳角。

| 实机指标 | 修复前 | 修复后 |
| --- | --- | --- |
| 普通转向同一时刻的收包跳角 | 1.800003° | 约 0.000298° |
| 漂移同一时刻的收包跳角 | 6.000008° | 约 0.001083° |
| 普通转向角度停住的渲染帧比例 | 64.9% | 1.35% |
| 漂移角度停住的渲染帧比例 | 64.7% | 1.96% |
| 停止冲撞跳角 | 未测 | 0° |
| 位置回拉包 | 0 | 0 |

“角度停住”指正常连续按住转向时，两个渲染帧间角速度小于 1°/秒；只统计间隔 5–100 ms 的帧，区别于游戏掉帧。回归断言收包/停止跳角小于 0.25°、普通与漂移各自停住帧比例低于 25%，并要求足够实际渲染帧。修复后采集了 248 个渲染帧，断言通过。可重复命令约 20 秒（含客户端启动）。

原始采样和对比：`build/goat-smoothness/before-frames.csv`、`after-frames.csv`、`before-metrics.txt`、`after-metrics.txt`、`comparison.txt`。已查看实际帧 `build/goat-smoothness/screenshots/goat-first-person-turn.png` 与 `goat-first-person-drift-smooth.png`；截图只证明实机场景，连续性结论来自逐渲染帧和实际收包探针。

完整回归再次执行 `runGameTest runGoatValidation assemble`：14/14 GameTest、原有 738 tick 真实客户端场景与构建全部通过，包含左右漂移、实际相机侧倾/回正、正常/最少粒子撞怪、落地眩晕到期及破拆跨步。发布 JAR 已更新，确认包含镜头平滑代码且不含验收探针；本轮验收客户端已自动退出。

限制：本次平滑度对比为本机集成服务器、60 fps 上限；尚未专门测试远程高延迟、低 TPS 或超高帧率。平滑是渲染端一个 tick 的过渡，不改变配置时长与冷却。

## 第三人称顿挫追加验收

上一轮只平滑第一人称，第三人称仍用离散朝向计算镜头环绕位置；漂移模型也用未平滑的本地玩家朝向。新增第三人称后视角复现稳定失败：每包跳角 1.8° / 6°，漂移时镜头环绕位置同一时刻跳约 0.4187 格，实际模型朝向停住的渲染帧占 64.7%。

修复将朝向平滑移到 `Camera.update` 的首次 `setRotation` 参数处，在原版前视角反转、环绕位移和障碍缩距计算之前应用；人物压弯使用同一连续朝向，停止时所有视角均从可见角度恢复自由观察。镜头位置、方向与人物因此一起连续，继续复用原版镜头碰撞计算。

可重复命令：

```sh
MHF_GOAT_SMOOTHNESS=1 MHF_GOAT_PERSPECTIVE=back ./gradlew \
  --project-cache-dir .gradle/goat-validation-cache \
  -I scripts/testing/goat-isolated.gradle runGoatValidation
```

将 `back` 换为 `front` 验证前视角，换为 `first` 回归第一人称。三个真实客户端依次启动以避免窗口焦点争抢，均通过收包跳角、逐渲染帧停顿、停止跳角检查；第三人称额外检查环绕位置、实际模型朝向和靠墙缩距。第一人称回归中普通转向及漂移停住帧均为 0%。

| 指标 | 修复前后视角 | 修复后后视角 | 修复后前视角 |
| --- | --- | --- | --- |
| 普通转向收包跳角 | 1.8000° | 0.0010° | 0.0014° |
| 漂移收包跳角 | 6.0000° | 0.0025° | 0.0027° |
| 漂移环绕位置收包跳变 | 0.4187 格 | 0.00018 格 | 0.00019 格 |
| 漂移镜头停住帧 | 64.7% | 1.96% | 1.96% |
| 漂移人物朝向停住帧 | 64.7% | 0% | 0% |

另从原始 CSV 检查普通转向的人物模型朝向，前后视角均为 0% 停住帧。靠墙时镜头由默认约 4 格收至后视约 1.98 格、前视约 0.41 格；保留原版 ±0.1 格角点射线造成的少量贴面重叠（断言小于 0.05 格，实测约 0.005 / 0.025 格），不把原版贴面行为误判为这次改动导致的穿墙。

原始采样：`build/goat-smoothness/third-back/`（含修改前后数据）、`third-front/`、`first-regression/`。已查看 `third-back/screenshots/goat-back-drift-smooth.png` 和 `third-front/screenshots/goat-front-drift-smooth.png`，确认人物压弯、扬尘及前后视角均实际绘制。

最终再执行 `runGameTest runGoatValidation assemble`，14/14 GameTest、完整 738 tick 真实客户端玩法回归和构建均通过。发布 JAR 已更新，确认包含环绕平滑代码、不含验收探针；所有本轮启动的验收客户端均已退出。仍保留上一轮本机集成服务器、60 fps 上限的测试范围限制。
