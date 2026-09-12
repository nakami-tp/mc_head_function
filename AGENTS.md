# Agent notes

项目里的可调用 skill 在 [`.agents/skills/`](./.agents/skills/)，完整清单见 [`.agents/skills/README.md`](./.agents/skills/README.md)。动手前先读对应目录的 `SKILL.md`。

这是 Cursor / Claude / Codex 会自动加载的项目技能目录。来源是 [Matt Pocock skills](https://github.com/mattpocock/skills) 已发布的全套（engineering / productivity / misc）。`grill-with-docs` 必须同时加载 `grilling` 和 `domain-modeling`。

词表在根目录 [`CONTEXT.md`](./CONTEXT.md)。规格在 [`.scratch/head-function/spec.md`](./.scratch/head-function/spec.md)。实现备注见 [`HANDOFF.md`](./HANDOFF.md)。

## Agent skills

### Issue tracker

Issues and specs live as markdown under `.scratch/<feature>/`. See `docs/agents/issue-tracker.md`.

### Triage labels

Default roles: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: root `CONTEXT.md` and `docs/adr/`. See `docs/agents/domain.md`.
