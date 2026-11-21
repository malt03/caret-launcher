---
id: Tex-hibk
status: open
deps: [Tex-l0eq, Tex-68f3, Tex-6ct1, Tex-9vav, Tex-nguc, Tex-ditl, Tex-000h]
links: []
created: 2026-11-20T11:16:26Z
type: task
priority: 2
assignee: Koji Murata
parent: Tex-pa8s
tags: [drawer]
---
# 全アプリ一覧の表示

検索フィールドの下全体に出す全アプリ一覧を作る。
- アプリ名順。英字は A〜Z、かなは あ・か・さ… の見出し
- 見出しに飛べるスクロールバー
- 各アプリのアイコンとアプリ名。タップで起動
- 長押しはアプリの長押しメニューと共通
仕様: docs/spec.md 6.4

## Launcher3 からの移植

- 見出しに飛ぶスクロールバーと見出しでの区切りは views/RecyclerViewFastScroller.java、allapps/AllAppsFastScrollHelper.java、allapps/AlphabeticalAppsList.java をコピーして使う（依存の量は要確認）

## Acceptance Criteria

- [ ] すべてのアプリが名前順に並び、見出しが付いている
- [ ] スクロールバーで見出しに飛べる
- [ ] タップで起動し、長押しでメニューが出る
- [ ] 長押しして動かす → 一覧が閉じ、ホームに置ける（Tex-e1ph から移動）
- [ ] テーマアイコンと形状の設定が一覧のアイコンにも反映される（Tex-1eos から移動）

## Notes

**2026-11-20T12:14:50Z**

移植の範囲は docs/launcher3-porting.md の表で確定（Tex-wgyx）
