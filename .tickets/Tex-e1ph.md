---
id: Tex-e1ph
status: in_progress
deps: [Tex-m5mh, Tex-ev5z, Tex-hibk, Tex-wgyx]
links: []
created: 2026-11-20T11:16:26Z
type: task
priority: 2
assignee: Koji Murata
parent: Tex-ogmk
tags: [home]
---
# 長押しメニューからドラッグへの移行

アプリの長押しメニューを出したまま一定距離動かすと、メニューを閉じてドラッグに移る（Pixel Launcher と同じ動き）。
- ホーム上のアプリ: そのまま移動
- 検索結果のアプリ: ドラッグ開始時に入力をクリアし、ホームの配置の表示に切り替えて置けるようにする
- 全アプリ一覧のアプリ: ドラッグ開始時に一覧を閉じ、ホームの配置の表示に切り替える
仕様: docs/spec.md 5.3、6.4

## Launcher3 からの移植

- 長押しメニューの見た目と、メニューからドラッグへ移る動きは popup/（ArrowPopup、PopupContainerWithArrow など）から移植する（範囲は Tex-wgyx で決める）

## Acceptance Criteria

- [ ] ホームのアプリを長押しして動かす → メニューが消えてドラッグになる
- [ ] 検索結果から長押しして動かす → ホームの配置に切り替わり、好きな位置に置ける
- [ ] 全アプリ一覧から長押しして動かす → 一覧が閉じ、ホームに置ける


## Notes

**2026-11-20T12:14:50Z**

移植の範囲は docs/launcher3-porting.md の表で確定（Tex-wgyx）

**2026-11-20T12:32:36Z**

長押しメニューから 16dp（deep_shortcuts_start_drag_threshold）動かすとドラッグに移る動きを実装し、エミュレータで確認。メニューの見た目はまだ標準の PopupMenu

**2026-11-20T14:44:16Z**

長押しメニューからの移行を Launcher3 の pre-drag（BubbleTextView.startLongPressAction と PopupContainer.createPreDragCondition）に置き換えた。長押しで DragView が少し持ち上がった状態で出て、16dp 動かすとメニューが閉じてドラッグが始まる。動かさずに離すとメニューだけ残る。メニュー本体は PopupMenu のまま

**2026-11-20T15:40:21Z**

長押しメニューを Launcher3 の View 版（ArrowPopup / PopupContainerWithArrow、Flags.expandableLongPressMenu はリリースで無効）に置き換えた。ArrowPopup・RoundedArrowDrawable・AbstractFloatingView などはコピー、PopupContainer 系は使う経路のメソッドを互換クラスへ。アプリ情報は SystemShortcut.AppInfo、検索文字列の編集はその派生。メニュー外のタップと戻る操作で閉じ、16dp 動かすとドラッグに移ることをホーム・検索結果・フォルダでエミュレータ確認。残りは全アプリ一覧からの移行（一覧の実装待ち）
