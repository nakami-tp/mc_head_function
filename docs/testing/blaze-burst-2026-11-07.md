# 烈焰人喷发手感验收 — 2026-11-07

## 本轮行为

- 按住 R 的持续喷火加快前推；高热松手从嘴部轰出粗火流，在 6 tick 内冲到 10～12 格，蓄热越接近过热，射程、宽度和水平击退越强。火流主体不额外上浮，只有最后几 tick 的余烬减速上扬。
- 自有火径先闪出地面火线，1 tick 后喷出最高 5 格的短促火墙。像素火舌沿高度分布，底部保持贴地；10 tick 内消散，避免整个火团升空。保留多条火径同时连锁。
- 释放叠加短促喷发声，实际命中普通目标散出碎火并击退；基础伤害仍为 6，同波火流与火径共享目标去重。
- 此轮是喷火与火径的手感调整，独立大招尚未实现。

## 验证命令

```sh
./gradlew -I scripts/testing/blaze-isolated.gradle runGametest test build
./gradlew -I scripts/testing/blaze-isolated.gradle runBlazeValidation build
```

服务端覆盖 6 tick 内命中九格外目标、高热后段命中十一格外目标、较早松手射程不足十一格，以及墙/水遮挡。既有火径存续、归属、多路连锁、目标去重、过热和取消场景一并回归。

客户端通过正常 C2S 输入与 S2C 事件触发，检查火流实际前冲、上浮幅度、扩展射程、贴地火墙、最低粒子释放主体、碰墙/碰水及停止后余焰结束。墙前出现火焰在短时间窗口内取样，不要求快速粒子每个采样 tick 都恰好出现在同一窄带；不穿墙则逐 tick 检查。

## 结果

- 19/19 烈焰人 GameTest 通过，56/56 单元测试通过，构建成功。
- 真实客户端 PASS；已查看喷发前段、远端火流、贴地火墙和第一人称最低粒子截图。测试客户端正常退出。
- 正式 JAR 已检查不含 Validation / GameplayTest 类。

## 产物

- [向前喷发](../../build/blaze-validation/screenshots/10-side-wave-477.png)
- [火流抵达远端](../../build/blaze-validation/screenshots/10-side-wave-480.png)
- [贴地连锁火墙](../../build/blaze-validation/screenshots/03-wave-100.png)
- [第一人称、最低粒子释放](../../build/blaze-validation/screenshots/12b-first-release-minimal.png)
- [松手火流碰墙](../../build/blaze-validation/screenshots/13b-wall-release.png)
- [动态预览](../../build/blaze-validation/blaze-burst-2026-11-07.mp4)
- [客户端结果](../../build/blaze-validation/result.txt)
- [本轮构建 JAR](../../build/blaze-isolated/libs/mc-head-function-1.0.0.jar)

预览为真实客户端逐 tick 采样帧按 20 fps 编码的无声回放，不用于证明实际帧率。没有验证多名真实玩家同时释放时的性能或光影/第三方资源包兼容性。
