---
id: Tex-wuyv
status: open
deps: []
links: []
created: 2026-11-23T04:37:46Z
type: task
priority: 3
assignee: Koji Murata
parent: Tex-ogmk
tags: [home, device]
---
# 未確認の画面での確認（スマホの角の丸み、2パネル、フリップの外側、タブレット）

これまでの変更で、エミュレータでも確かめていない画面がある。
- スマホでの角の丸み（Themes.getDialogCornerRadius を Theme.DeviceDefault から読むようにした変更。シートとホームの編集中の背景）
- 折りたたみの2パネルでのフォルダとウィジェット（開閉、ドラッグ、リサイズ枠）
- 縦に折るフリップ端末の外側の小さい画面（255×400dp 未満は数えない処理）
- タブレット：グリッドの選び方の変更（9cb531f）で 6×5・アイコン 60dp になるはず。Launcher3 の isScalable の伸縮は持ってきていない

