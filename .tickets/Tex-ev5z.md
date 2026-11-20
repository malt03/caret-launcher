---
id: Tex-ev5z
status: in_progress
deps: [Tex-ebnm, Tex-t2ea, Tex-bqud, Tex-wgyx]
links: []
created: 2026-11-20T11:16:26Z
type: task
priority: 2
assignee: Koji Murata
parent: Tex-ogmk
tags: [home]
---
# 編集モードとドラッグ&ドロップ

ホーム上のアイテムの編集を Launcher3 に準拠して実装する。
- フォルダとウィジェットは長押しでドラッグ開始。ウィジェットはリサイズも
- 上部の「削除」へ落とすとホームから外す。「アンインストール」へ落とすとアンインストール
- アプリをアプリに重ねるとフォルダになる。中身が1つになったらフォルダを解除
- 編集モード: 開始時にキーボードを下ろしフォーカスを外す。終了時（ドロップ、枠外タップ、戻る、ホームボタン）にフォーカスとキーボードを戻す
- 編集モード中の戻る操作は終了だけ（入力クリアしない）。キーボード監視も止める
仕様: docs/spec.md 5.3、5.4

## Launcher3 からの移植

- ドラッグの見た目（持ち上げ、追従、ドロップ）は dragndrop/ から、並び替えは CellLayout と celllayout/ から移植する
- 削除・アンインストールの落とし先は DropTargetBar、DeleteDropTarget などを参考にする
- 範囲は Tex-wgyx で決める

## Acceptance Criteria

- [ ] ウィジェットを長押しして移動できる。キーボードは下りている
- [ ] ドロップするとキーボードが戻り、フォーカスが当たる
- [ ] アプリを別のアプリに重ねるとフォルダになる
- [ ] フォルダから1つを残して出すとフォルダが解除される
- [ ] 「削除」へ落とすとホームから消える（アプリ自体は残る）
- [ ] 編集中に戻る操作をすると編集モードが終わる


## Notes

**2026-11-20T12:14:50Z**

移植の範囲は docs/launcher3-porting.md の表で確定（Tex-wgyx）

**2026-11-20T12:32:36Z**

DragController と DropTargetBar を実装。検索結果・ホーム・フォルダからのドラッグ、並び替え（REORDER_TIMEOUT 650ms）、アプリ同士の重ねでフォルダ作成、フォルダから1つ残して出すと解除、削除・アンインストールの落とし先、編集中のキーボード下ろしと終了後の復帰をエミュレータで確認。ウィジェット関連は未実装

**2026-11-20T12:43:18Z**

押しのけのアニメーションを Launcher3 から移植（ReorderPreviewAnimation.kt をコピー。animateChildToPosition と hint/preview の呼び出しは CellLayout に倣って実装）。押下フィードバックは FastBitmapDrawable に押下状態を渡して有効化。フォルダの受け皿（PreviewBackground の ACCEPT_SCALE_FACTOR 1.2 / 100ms）と作成アニメーション（400ms）を追加
