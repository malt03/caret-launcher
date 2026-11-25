---
id: Tex-ap72
status: closed
deps: []
links: []
created: 2026-11-21T16:30:30Z
type: bug
priority: 1
assignee: Koji Murata
parent: Tex-epcb
tags: [search]
---
# 検索文字列の編集画面でキーボードが出ない、削除ボタンが枠線付きの ×

実機での指摘（2026-11-22）:
- 検索文字列の編集画面を開いた直後、追加用のフィールドにフォーカスは当たっているがキーボードが出ていない
- 削除ボタンが、枠線で囲まれた × になっている

あわせて、App Switcher の背景が真っ白になる件を調べた（CaretLauncher では直せない。docs/spec.md 8 に記録）

## Acceptance Criteria

- [x] 編集画面を開くと、キーボードが出ている（ホームからも、キーボードが下りている全アプリ一覧からも）
- [x] 削除ボタンが、枠のない × になっている

## 結果（2026-11-22）

- キーボード: ダイアログは別のウィンドウなので、ランチャーのキーボードは引き継がれない。開いたときに soft input を常に表示にし、フォーカスを当てたフィールドに対してキーボードを出すようにした
- 削除ボタン: android.R.drawable.ic_menu_close_clear_cancel（Holo の、丸い枠の中の ×）を、Launcher3 の削除の落とし先と同じ ic_remove_no_shadow に替え、ダイアログの文字色（colorOnSurfaceVariant）にした
- App Switcher: 既定のランチャーに関係なく Pixel Launcher（Quickstep）が描く。サードパーティのランチャーが既定のときは代替の RecentsActivity になり、OverviewBlurFallbackStyle で背景はぼかしなしの不透明な色（overviewScrimColorFallback）になる。CaretLauncher からは変えられない
- エミュレータで確認: ホームからも全アプリ一覧からも、開くとキーボードが出る。× は枠なし
