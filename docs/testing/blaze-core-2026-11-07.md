# 熔核爆轰验收 — 2026-11-07

## 实现

佩戴烈焰人头，站立持续按住 R：0～40 tick 喷火，40～59 tick 高热松手放粗火流；继续到 60 tick 停喷并压缩核心，再蓄力 30 tick 就绪松手爆轰。压缩未满松手退回满强度火流。发射后只进入 160 tick（8 秒）过热停火，没有独立大招冷却；自然恢复后即可重新长按 R 蓄满并再次发射。统一恢复时间保存重载/换头/重生保留，旧存档的独立冷却字段忽略。

火核以 1.6 格/tick 飞出，最多 28 格，扫掠检测实体和方块，命中或达到射程后爆燃，水熄灭。爆燃半径 10 格，中心 4 格内 30 点火焰伤害、边缘递减至 16 点；排除施放者、墙/水遮挡、可点燃可燃方块，不主动破坏方块。爆燃接触自有火径后继续火线/火柱传播，整次攻击共享目标去重。

蓄力有向内收拢的火星、逐渐饱满并脉动的核心、递进声和就绪音；发射有压力环、粗火尾和沉重喷发声；命中迅速膨胀成像素火团，推出带火舌的双层地面火环，连接火径继续喷发。主视觉独立于粒子设置，第一人称蓄力核心下压缩小，避免遮住准星。白热几何与原版火焰图集分批绘制。

## 验证结果

```sh
./gradlew -I scripts/testing/blaze-isolated.gradle runGametest test build
./gradlew -I scripts/testing/blaze-isolated.gradle runBlazeValidation build
```

- 26/26 GameTest 通过：喷火→高热→压缩→发射，中心 30 点重击、外圈至 16 点衰减、8 格内命中/10 格外不命中、墙/水、摘头取消、连锁归属和去重、压缩未满退回火流；160 tick 停火第 120 tick 仍生效、第 161 tick 已结束；恢复后重新长按能直接蓄满并发射第二次大招，且没有等待旧 400 tick。覆盖 160 tick 恢复时间的 NBT 保存/换头，以及旧独立冷却字段兼容。旧的独立冷却过热分支已删除。十格爆燃可干扰相邻并行夹具，全熔核释放场景使用独立 batch。
- 56/56 单元测试通过，构建成功；正式 JAR 确认包含熔核实现与白色基础纹理，不含 Validation/GameplayTest 类。
- 真实客户端 PASS，通过站立长按 R 的正常 C2S/S2C 验证从喷火转入熔核、三个主体阶段实际绘制、30 点伤害同步、横向 8 格目标受到衰减伤害、11 格外目标不受伤、客户端收到十格爆燃事件、远端自有火径连锁、最低粒子，以及8 秒过热同步、第 6 秒仍在停火、恢复时不自动喷火、重新长按 R 自然蓄满并再次爆轰（中间不重置恢复计时器）、压缩未满退回火流、入水取消及特效结束。测试客户端正常退出。
- 已查看第三人称的蓄力/飞行/爆燃/后续火径帧，以及第一人称最低粒子的蓄力和爆燃帧。第一版透明球壳已替换为翻卷火焰主体，修正切换纹理层后复用已结束顶点缓冲的问题，最终版本完整复验通过。
- 为比较普通/最低粒子画面，客户端在先完成 8 秒自然恢复和实际二次爆轰后，才为后续独立视觉夹具重置停火；二次大招本身不通过夹具重置计时。

## 产物

- [20 秒大范围爆燃、8 秒恢复、再次爆轰实机预览](../../build/blaze-validation/molten-core-wide-2026-11-07.mp4)
- [蓄力就绪](../../build/blaze-validation/screenshots/core-charge-841.png)
- [火核与粗火尾](../../build/blaze-validation/screenshots/core-impact-850.png)
- [命中爆燃](../../build/blaze-validation/screenshots/core-impact-855.png)
- [火径连锁](../../build/blaze-validation/screenshots/core-impact-860.png)
- [第一人称最低粒子蓄力](../../build/blaze-validation/screenshots/core-first-charge-minimal.png)
- [第一人称最低粒子爆燃](../../build/blaze-validation/screenshots/core-first-blast-minimal.png)
- [大招后第 6 秒仍在过热](../../build/blaze-validation/screenshots/core-eight-second-overheat.png)
- [自然恢复后再次蓄满](../../build/blaze-validation/screenshots/core-ready-again.png)
- [再次爆轰](../../build/blaze-validation/screenshots/core-recast-blast.png)
- [客户端结果](../../build/blaze-validation/result.txt)
- [正式 JAR](../../build/blaze-isolated/libs/mc-head-function-1.0.0.jar)

预览由真实客户端 400 张逐 tick 截图按 20 fps 编码，无音频，不用于证明实际帧率。未验证多名真实玩家同时释放时的性能、第三方资源包或光影兼容性。
