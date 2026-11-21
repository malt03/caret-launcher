---
id: Tex-mfcm
status: open
deps: [Tex-dnjc, Tex-f33r]
links: []
created: 2026-11-21T09:18:13Z
type: task
priority: 3
assignee: Koji Murata
parent: Tex-neft
tags: [refactor]
---
# DeepShortcutView と PopupContainer 系を上流のコピーとして扱う

PopupContainer.kt と PopupContainerWithArrow.kt は上流から削っただけ。DeepShortcutView は 87% が上流と同じで、フォルダとモデルの整理の後は BubbleTextHolder と Launcher の getAccessibilityDelegate / getStatsLogManager / getModel だけが残る。third_party に置いて TextLauncher: の印で変更を示すか、削った版として git merge-file で更新できる形にする。

## Acceptance Criteria

- [ ] 3 ファイルが UPSTREAM.md の更新手順で上流の変更を取り込める形になっている

