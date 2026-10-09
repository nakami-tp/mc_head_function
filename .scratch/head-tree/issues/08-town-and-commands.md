# 08 闹鬼小镇与 `/haunt` 命令

Status: ready-for-human
Blocked by: 05
进度：`/haunt` 与代码生成小镇已实现；GameTest 覆盖建镇和 `tree fill except`。

## 内容

- `/haunt`（OP 2 级）：`start`、`stop`、`count <物件类> <件数>`、`town build`、`town reset`、`tree fill [except <头>]`，行为见 spec。
- 小镇：固定种子代码生成，约 64×64，中央广场放枯树，几栋小屋和院子；9 类物件都多于默认件数；整体暗色调（深色橡木、云杉、石砖、蜘蛛网、枯灌木等）。
- 小镇位置按世界保存；`town reset` 先停闹鬼、清空范围内方块和实体，再重建。

## 验收

- GameTest：`town build` 后范围内每类候选都不少于默认件数；`town reset` 能把被撞碎的方块恢复；`tree fill except` 后挂了 18 种、缺指定那种。
