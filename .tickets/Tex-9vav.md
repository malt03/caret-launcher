---
id: Tex-9vav
status: open
deps: []
links: []
created: 2026-11-21T09:18:13Z
type: task
priority: 2
assignee: Koji Murata
parent: Tex-neft
tags: [refactor]
---
# LauncherActivity から検索結果を分ける

LauncherActivity が、検索結果の表示（RecyclerView、並び替え、1 位への枠、Enter での起動）まで持っている。全アプリ一覧内の検索（Tex-9cyk）でも同じ結果表示を使うので、その前に分ける。上流の検索結果の入れ物（ActivityAllAppsContainerView の検索部分、AllAppsSearchBarController）との対応を書く。LauncherActivity には、キーボード・戻る・フォーカスの方針と各部のつなぎだけを残す。

## Acceptance Criteria

- [ ] 検索結果の表示が LauncherActivity の外にある
- [ ] 検索・Enter での起動・1 位の枠・矢印キー・長押しからのドラッグが今と同じ


## Notes

**2026-11-21T09:43:49Z**

検索結果のセルは LinearLayout に AppIconView と別の TextView を並べている。上流の全アプリ一覧と同じく、ラベル付きの BubbleTextView（DISPLAY_ALL_APPS）1 つにできるか確かめる（Tex-htxz で BubbleTextView がアイコンを描くようになった）
