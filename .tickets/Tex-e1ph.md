---
id: Tex-e1ph
status: open
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

