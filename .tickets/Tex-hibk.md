---
id: Tex-hibk
status: closed
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

- [x] すべてのアプリが名前順に並び、見出しが付いている（Launcher3 と同じく、見出しはスクロールバーの吹き出しに出す）
- [x] スクロールバーで見出しに飛べる
- [x] タップで起動し、長押しでメニューが出る
- [x] 長押しして動かす → 一覧が閉じ、ホームに置ける（Tex-e1ph から移動）
- [x] ~~テーマアイコンと形状の設定が一覧のアイコンにも反映される（Tex-1eos から移動）~~ → Launcher3 に合わせ、一覧と検索結果にはテーマも選んだ形状も付けない（2026-11-21 決定。Pixel Launcher でも同じことを確認）

## Notes

**2026-11-20T12:14:50Z**

移植の範囲は docs/launcher3-porting.md の表で確定（Tex-wgyx）

## 結果（2026-11-21）

- 決めたこと（ユーザーと確認）: Launcher3 に合わせる
  - 見出しは一覧の中に行として出さず、スクロールバーのつまみを動かしたときの吹き出しにだけ出す
  - テーマアイコンと選んだ形状はホームとフォルダだけ（`BubbleTextView.shouldUseTheme` と `ItemInfoWithIcon.supportsCustomShapes`）。一覧と検索結果は通常のアイコンを OS の形で出す。エミュレータの Pixel Launcher でも、形を角丸の四角にしてもアプリ一覧は丸のままだった
- 取り込み: RecyclerViewFastScroller・FastScrollRecyclerView・AllAppsGridAdapter などはそのままコピー、AlphabeticalAppsList・BaseAllAppsAdapter・AllAppsRecyclerView は derived=。ActivityAllAppsContainerView の役はアプリ側の AllAppsView
- 開閉はまだ状態の切り替えだけ（アニメーションと開くジェスチャーは Tex-17s1）。それまでの確認用に、デバッグビルドでは `adb shell am start -n com.malt03.textlauncher/.ui.LauncherActivity --ez com.malt03.textlauncher.extra.SHOW_ALL_APPS true` で開く
- 一覧を開いている間に検索フィールドをタップすると、一覧内の検索（Tex-9cyk）ができるまでは一覧を閉じる
- エミュレータで確認: 開く、名前順、吹き出しの見出し、タップで起動して戻ると閉じている、長押しでメニュー、動かすと閉じてホームに置ける、戻る操作で閉じる
