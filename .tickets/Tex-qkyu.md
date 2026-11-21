---
id: Tex-qkyu
status: closed
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

- [x] ドラッグを始めると、Launcher3 と同じ比率とアニメーションでホームが縮小し、上部のボタンと重ならない
- [x] 縮小中もスクロールでき、落とした位置が見た目どおりのセルになる
- [x] ドロップ・キャンセルで元の大きさに戻る

## 結果（2026-11-22）

- SpringLoadedState.getWorkspaceScaleAndTranslation（centerSpringLoadedStateVertically はリリース設定になく無効なので、上寄せの方）と DeviceProfile.getWorkspaceSpringLoadScale を SpringLoadedListener に移した。ページの高さは、スクロールの入れ物の見えている範囲。ホットシートの代わりは検索フィールドで、spring_loaded_hotseat_top_margin（76dp）を上に空ける
- 動きは WorkspaceStateTransitionAnimation と同じく 150ms、拡大縮小と位置は ZOOM_OUT。スケールは WORKSPACE_SCALE_PROPERTY_FACTORY の SCALE_INDEX_WORKSPACE_STATE（フォルダのアニメーションのスケールと掛け合わさる）
- CellLayout の互換クラスに mBackground（bg_celllayout、drawable-v28 版）、setSpringLoadedProgress、setIsDragOverlapping を移し、ホームの入れ物（Workspace）がその背景を描く。ドラッグがホームの上にある間は state_active の枠
- 壁紙の暗さは DragLayer の workspace drag scrim の値（wallpaper_popup_scrim = system_neutral1_900、0.2）にした。奥行き（ぼかし 15%）は Tex-dpqn のぼかしと一緒に入れる
- エミュレータで確認: ホームのアイテムを長押しで動かすと、削除ボタンの下に縮小して背景と枠が出る。落とした位置が見た目どおりのセルになり、元の大きさに戻る
