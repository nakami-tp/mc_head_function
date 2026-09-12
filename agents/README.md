# Agent skills

本仓库给其他 agent 用的 [Matt Pocock skills](https://github.com/mattpocock/skills) 全套副本。来源 `main`，提交 `3cca18b`（2026-09-04）。许可证见 [LICENSE](./LICENSE)。

收入官方已发布的 **engineering / productivity / misc** 全部技能。未收入 `deprecated`（空）和 `in-progress`（未完成）。

入口都是各目录下的 `SKILL.md`。`grill-with-docs` 必须同时读 `grilling` 和 `domain-modeling`。新仓库建议先跑一次 `setup-matt-pocock-skills`。

## Engineering

| 目录 | 做什么 |
| --- | --- |
| `ask-matt` | 问该用哪条 skill / 流程 |
| `grill-with-docs` | 对着文档追问设计，并同时落词表 / ADR |
| `setup-matt-pocock-skills` | 给本仓库配 issue 跟踪、标签、文档布局 |
| `triage` | 按角色把 issue 往下推 |
| `to-spec` | 把已对齐的讨论收成 spec |
| `to-tickets` | 把计划拆成带依赖的票据 |
| `implement` | 按 spec / 票据实现，中间走 tdd，收尾走 code-review |
| `wayfinder` | 超出会话容量的大活，画决策图再一块块解 |
| `improve-codebase-architecture` | 扫加深机会，再追问你选中的那条 |
| `tdd` | 先写失败测试再实现 |
| `domain-modeling` | 词一定就改 `CONTEXT.md`，该写 ADR 才写 |
| `codebase-design` | 深模块的共用说法 |
| `code-review` | 对照规范和原始需求两边审 diff |
| `diagnosing-bugs` | 难复现的 bug / 变慢，按阶段查 |
| `research` | 对着一手资料查，结果写成仓库里的 Markdown |
| `prototype` | 用扔掉的原型回答设计问题 |
| `resolving-merge-conflicts` | 按双方意图解正在进行的冲突 |
| `wizard` | 生成只能人来点的交互脚本 |

## Productivity

| 目录 | 做什么 |
| --- | --- |
| `grill-me` | 不写仓库文档的追问 |
| `grilling` | 按设计树一轮一轮问 |
| `handoff` | 把当前会话压成交接文档 |
| `teach` | 用当前目录当课堂，分多场教 |
| `to-questionnaire` | 把答不了的题做成问卷给能答的人 |
| `wait-what` | 一句话没听懂，立刻用词表重讲 |
| `writing-for-agents` | 写给 agent 看的文档 / skill |

## Misc

| 目录 | 做什么 |
| --- | --- |
| `git-guardrails-claude-code` | 拦危险 git 命令 |
| `migrate-to-shoehorn` | 测试里的 `as` 改成 shoehorn |
| `scaffold-exercises` | 搭练习目录 |
| `setup-pre-commit` | 配 Husky / lint-staged / 提交时检查 |
