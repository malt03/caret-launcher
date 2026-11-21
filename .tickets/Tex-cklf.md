---
id: Tex-cklf
status: closed
deps: [Tex-oixt]
links: []
created: 2026-11-21T09:18:12Z
type: task
priority: 2
assignee: Koji Murata
parent: Tex-neft
tags: [refactor]
---
# 互換クラスの完全修飾名を上流どおりの import に戻す

BaseDragLayer、CellLayout（119〜125、239〜243 行目など）、Utilities、LauncherAnimUtils（69〜74 行目）、FolderIcon（415 行目）が、上流では import しているクラスを完全修飾名で書いている。上流と並べて比べるときの雑音になり、一致率も下がる。後続の互換クラスの整理（移動・統合）の前にやる。

## Acceptance Criteria

- [ ] 上流と同じ import に戻し、上流との差分が実質的な違いだけになっている


## Notes

**2026-11-21T09:30:40Z**

互換クラス 17 個の完全修飾名を import に置き換え、import を AOSP の並びに揃えた。上流でも完全修飾で書いているもの（LauncherAnimUtils の Looper、CellLayout の IconNormalizer など）と、名前がぶつかるもの（PopupContainerWithArrow の wm.shell.Flags）はそのまま
