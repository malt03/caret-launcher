---
id: Tex-sfxe
status: closed
deps: []
links: []
created: 2026-11-21T09:18:12Z
type: task
priority: 2
assignee: Koji Murata
parent: Tex-neft
tags: [refactor]
---
# UPSTREAM.md と取り込み方針の記録を今のコードに合わせる

third_party/launcher3/UPSTREAM.md と docs/launcher3-porting.md の記載が古い。
- res-textlauncher の drawable・color・layout にある 25 ファイル（すべて上流と一致）のうち、記録は 7 つだけ。足りないもの: popup のレイアウト 10 個、popup_shade_*、popup_background、*_item_primary、info_24px、gm_edit_24、ic_add_circle_filled、ディープショートカットのプレースホルダー
- 「互換層で受け止めている依存」で置き場所が compat/launcher3/ になっているが、ほとんどは compat/launcher3-internal/
- DeviceProfile の説明（フォルダの列数・行数・アイコンの大きさだけ）が古い。WorkspaceProfile と DeviceProperties も持っている

## Acceptance Criteria

- [ ] res-textlauncher の全ファイルが UPSTREAM.md に載っている
- [ ] 互換クラスの置き場所と DeviceProfile の説明が今のコードと合っている


## Notes

**2026-11-21T09:28:38Z**

res-textlauncher の color・drawable・layout の 25 ファイルが上流と一致することを確かめ、UPSTREAM.md の upstream ブロックに載せた。update-upstream.sh に res_dir（上流の res/ をここでは res-textlauncher/ に置く）を足したので、更新スクリプトの対象になった。互換クラスの置き場所と DeviceProfile の説明を直した
