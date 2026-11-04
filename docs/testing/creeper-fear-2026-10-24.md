# 苦力怕手持温雷与三级恐惧验收（2026-10-24）

## 实现

- Fabric 1.21.1、Yarn 1.21.1+build.3、Java 21。普通苦力怕头复用原版物品，佩戴 R 技能保持原样。
- 实际攻击键按下点燃，松开投出；总引信 60 tick，碰墙或落地不会提前引爆。预留一颗头后再播放点燃反馈，防止移动物品复制或熄火。切槽自动投出，断线或跨维度后原位置的引信继续；剩余引信可保存重载。爆炸破坏方块、正常伤害投掷者，之后回收头。
- 仅手持温雷刺激 12 格内普通生物，恐惧可跨次积累。同一 tick 多玩家刺激只累计一次。玩家、末影龙、凋灵、监守者和无头身体排除。
- 一级逃跑；累计 30 tick 后二级发抖、移动速度属性降低 45%；75 tick 后三级成鸡掉蛋、可剪毛的羊掉羊毛、成年村民掉绿宝石。普通恐惧不造成惊吓伤害，骷髅保留弓。每轮一次，同一生物奖励冷却 1200 tick，奖励标记与冷却保存到生物 NBT。
- 退出刺激范围 30 tick 后逐 tick 消退，结束时恢复原 AI 和速度。恐惧覆盖期间保留原目标选择器与脑行为的配置，不永久删除原版 AI。
- 苦力怕三级停止逃跑，停顿约 0.75 秒，再点燃并反扑；温雷不能再次打断，自身爆炸正常伤害玩家。
- 客户端通过实体 tracked data 获取引信、温雷状态和恐惧等级。第一人称、第三人称和投出后的头共用绿色/白色闪烁模型，二级开始抖动生物渲染矩阵。音效从服务端正常发出。

## 可重复命令

```sh
./gradlew -I scripts/testing/creeper-isolated.gradle runGametest
./gradlew test runGametest build
./gradlew runFearValidation runCreeperValidation -x downloadAssets
python3 scripts/testing/check-fear-visuals.py build/fear-validation/screenshots
```

客户端使用独立 `build/fear-validation`、`build/creeper-validation` 目录和新世界，不接触用户存档。`-x downloadAssets` 复用已有资源缓存；全新环境应先正常下载资源。本次缺少的缓存均为其他语言包，不影响已使用的模型、纹理与声音。

`runFearValidation` 在启动前删除旧结果与截图，退出后必须存在最终 PASS，且实际截图像素检查通过。异常、缺帧、超时均失败。测试源码集和探针不进入发布 JAR。

## 结果与证据

- 47 个 JUnit 单元测试通过，106 个完整 GameTest 通过；其中 11 个苦力怕相关场景覆盖原有 R 技能与新增温雷/恐惧路径。
- 服务端验证出手前后引信连续、碰撞不提前爆炸、回收恰好一颗头、重复按下不复制、切槽不会熄火、NBT 保留剩余引信和奖励标记、实际逃跑距离增加、二级减速、三级掉落去重、恐惧恢复、苦力怕停顿后点燃、再次恐吓不能中断和反扑伤害不被普通恐惧免伤拦截。
- 全量检查曾出现村民闹鬼唤醒用例的偶发失败；原基线的闹鬼测试、当前独立闹鬼测试和最终全量重跑均通过。未修改闹鬼生产逻辑或放宽该用例断言。
- 真实客户端用攻击键事件进入正式左键拦截，再经正常 C2S 包启动温雷与松手投掷；在最低粒子设置下检查三级 tracked data、抖动模型矩阵、真实头模型绘制和引信音效事件。
- 客户端场景先验证鸡蛋、羊毛、绿宝石，等待恐惧消退，再追踪逃跑的苦力怕，检查三级点燃、实际自爆和生存玩家受伤。
- 第一人称截图曾暴露头模型不可见的问题：取消原版物品绘制后，手臂变换未恢复，导致温雷模型移出画面；已通过独立矩阵作用域和显式刷新顶点缓冲修复。另用白色纹理完成闪白，避免只有状态变更、像素却不变。
- `check-fear-visuals.py` 检查 14 张温雷帧中的实际绿色/白色像素变化，并要求 36 张反扑采样帧完整。已人工查看绿色、白色、三级掉落及反扑关键帧。

最新机器结果见 `build/fear-validation/result.txt` 和 `build/creeper-validation/result.txt`。关键图：

- `build/fear-validation/screenshots/warm-000.png` 等温雷采样帧：真实绿色/白色交替。
- `build/fear-validation/screenshots/level-three-drops.png`：羊已剪毛，场景有可拾取产物。
- `build/fear-validation/screenshots/creeper-pauses.png`、`retaliation-*.png`、`after-explosion.png`：停止逃跑、反扑、自爆和爆炸后的结果。

无声采样预览：`build/fear-validation/warm-preview.mp4`、`build/fear-validation/retaliation-preview.mp4`。采样间隔为两客户端 tick，按 10 fps 编码；不是连续录屏，不能证明真实墙钟帧率或音轨效果。

发布产物：`build/libs/mc-head-function-1.0.0.jar`，已检查包含新功能与闪白纹理、不包含客户端验收入口或 GameTest 类。

## 验收边界

真实客户端覆盖单人集成服务器及正常网络同步路径；未另启专用服务器加多个客户端。鸡、羊、村民与苦力怕完成重点验收，未逐一拍摄所有其他生物。音效确认到达客户端声音系统，未额外录制游戏音轨或完成听觉混音验收。
