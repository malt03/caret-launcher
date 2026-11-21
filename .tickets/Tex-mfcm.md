---
id: Tex-mfcm
status: closed
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


## Notes

**2026-11-21T10:20:06Z**

update-upstream.sh に derived=（上流のファイルから削って作った互換クラスを、ours=互換クラス・base/theirs=上流で3方向マージする）を足し、DeepShortcutView・PopupContainer・PopupContainerWithArrow を UPSTREAM.md の upstream ブロックに載せた。今のタグ同士で何も変わらないこと、残している関数への上流の変更がそのまま入ること、削った部分の変更は衝突として出ることを確かめた。DeepShortcutView は BubbleTextHolder と FloatingIconViewCompanion（どちらも上流のままコピー）を実装する形に戻し、上流との違いは Launcher への依存（追加ボタン、統計、バッジ付きのコピー）だけになった。third_party に完全なコピーとして置くには Launcher を ActivityContext として持つ必要があり、今回はしていない
