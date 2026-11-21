---
id: Tex-qkyu
status: open
deps: []
links: []
created: 2026-11-21T16:03:46Z
type: feature
priority: 1
assignee: Koji Murata
parent: Tex-ogmk
tags: [home, drag]
---
# 編集中にホームを縮小する（Launcher3 の SpringLoadedState）

ドラッグなどの編集中、上部の削除・アンインストールのボタンの分だけホームがずれるので、編集中のどこがホームのどこに当たるか分からない。Launcher3 は編集中（SpringLoadedState）にホームを縮小して、ボタンの下に収めている。実装もなるべく持ってきて、挙動を同じにする。違いはホームが1つ（縦スクロール）しかないことだけ。

## Launcher3 からの移植（調べる点）

- states/SpringLoadedState.java の getWorkspaceScaleAndTranslation（DeviceProfile.getWorkspaceSpringLoadScale、centerSpringLoadedStateVertically のときの位置）
- 縮小の出入りのアニメーション（StateAnimationConfig の ANIM_WORKSPACE_SCALE など、150ms）
- 編集中のセルの背景（CellLayout の spring-loaded の枠）と壁紙のぼかし（奥行き 15%）。今の『壁紙を少し暗くする』（docs/spec.md 5.4）は Launcher3 のぼかしの代わりなので、ぼかしにできるならそちらに合わせる
- 縦スクロールの入れ物を縮小したときのスクロールとドロップ位置の座標変換

仕様: docs/spec.md 5.4（見た目の項を直す）

## Acceptance Criteria

- [ ] ドラッグを始めると、Launcher3 と同じ比率とアニメーションでホームが縮小し、上部のボタンと重ならない
- [ ] 縮小中もスクロールでき、落とした位置が見た目どおりのセルになる
- [ ] ドロップ・キャンセルで元の大きさに戻る

