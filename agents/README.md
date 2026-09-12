# Agent skills

本仓库给其他 agent 用的 [Matt Pocock skills](https://github.com/mattpocock/skills) 副本。来源 `main`，提交 `3cca18b`（2026-09-04）。许可证见 [LICENSE](./LICENSE)。

这次设计追问实际用到的四份：

| 目录 | 做什么 |
| --- | --- |
| `grill-with-docs` | 对着文档追问设计，并同时落词表 / ADR |
| `grilling` | 按设计树一轮一轮问，先问能问的 |
| `domain-modeling` | 词一定就改 `CONTEXT.md`，该写 ADR 才写 |
| `handoff` | 把当前会话压成交接文档 |

入口都是各目录下的 `SKILL.md`。`grill-with-docs` 必须同时读 `grilling` 和 `domain-modeling`。
