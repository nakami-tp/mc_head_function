# 僵尸头徒手血食验收

2026-10-26 首版验收记录；随后吸血率与空手摘头规则调整见 `zombie-adjustment-2026-10-26.md`。当前规则见 `.scratch/zombie-wear/spec.md`。

## 命令

```sh
./gradlew -I scripts/testing/zombie-isolated.gradle test runGameTest build
./gradlew -I scripts/testing/zombie-isolated.gradle runZombieValidation
```

使用 `build/zombie-isolated` 构建输出，GameTest 仅注册 `ZombieGameplayTest` 和现有 `HeadGameplayTest`。客户端入口为独立源码集，新存档在 `build/zombie-client-validation`，不影响玩家存档，验收自动退出。发布 JAR 不含验证入口。

## 服务端与构建

47 项 JUnit 测试、33 项 GameTest 通过，构建成功。

- 饱食和饱和度逐渐消耗：前 9 tick 不扣，累计 200 个佩戴 tick 耗完 20 点；摘戴不重置余数，NBT 保存余数。
- 半血徒手伤害 5.5、攻速 12、基础移速 0.2；满血移除加成；额外生命上限按生命比例计算，低血接近约定上限。
- 实际徒手攻击触发增伤和按实际生命损失吸血；持武器没有僵尸伤害、攻速和吸血。持普通物品也不触发击杀回复。
- 精通吸血、击杀额外回复、满血上限、玩家目标吸血与吸收生命不计入吸血。
- 腐肉不直接回血、不补饱食与饱和度，仍给力量和急迫；普通食物中毒，原有其他效果保留；摘头恢复营养。放置蛋糕也不能绕过饮食限制。
- 创造/旁观不额外消耗、不强化。原版饥饿路径验证简单、普通、困难与和平：保留难度保底，困难可饿死。

## 真实客户端

粒子设置固定为最少，固定种子 42，白天、无天气、无随机刷怪。通过正常玩家攻击包触发服务端伤害，未手动注入视觉反馈。

- 收到空饱食状态与残血攻速属性。
- 实际徒手攻击后收到命中与实际回血反馈，爪痕、回流可见；满血命中仅出现爪痕，不出现回血回流。
- 空饱食可启动、维持疾跑；换武器失去徒手攻速；摘头后失去移速和饥饿疾跑豁免。
- 已查看 `screenshots/02-low-health.png`、`impact-02.png`、`impact-07.png`：原版心形区域轻量脉动，命中目标前有三道短红爪痕，小血珠回流；没有倍率文字、数字回血、额外血食条或全屏红边。
- 结果：`build/zombie-client-validation/result.txt`。截图目录：`build/zombie-client-validation/screenshots`。

## 范围说明

额外佩戴消耗按游戏 tick 而非墙钟计算。和平难度自动恢复仍走原版。多客户端旁观者的反馈广播、换维度/死亡复制进度通过代码路径检查，未另开多人客户端场景；现有投掷追咬回归由 `HeadGameplayTest` 覆盖，本次未重录投掷实机镜头。
