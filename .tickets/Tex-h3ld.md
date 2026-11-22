---
id: Tex-h3ld
status: closed
deps: []
links: []
created: 2026-11-20T11:48:45Z
type: task
priority: 2
assignee: Koji Murata
parent: Tex-ogmk
tags: [home, launcher3, spike]
---
# ウィジェットピッカーの方式調査

次の2つを比べて方式を決める。
- OS 標準の選択画面（AppWidgetManager.ACTION_APPWIDGET_PICK）: Android 17 のエミュレータで使えるか、見た目と使い勝手はどうか
- Launcher3 のウィジェットピッカー（widget/picker/、widgetpicker/）の移植: 依存の深さ
結果はこのチケットのノートに残す。

## Acceptance Criteria

- [ ] ACTION_APPWIDGET_PICK が Android 17 で動くかを確かめた
- [ ] Launcher3 版を移植する場合の範囲を見積もった
- [ ] どちらにするか決めた


## Notes

**2026-11-22T15:26:49Z**

調査結果（Pixel 9 Pro Fold エミュレータ、Android 17 / API 37）:
- ACTION_APPWIDGET_PICK: 動く（com.android.settings/.AppWidgetPickActivity）。見た目は「Choose widget」の1列のダイアログで、アプリのアイコンとウィジェット名だけ。プレビュー、大きさ、アプリごとのまとまり、検索はない。選ぶと設定アプリ側がバインドまで済ませて返す（ランチャーにバインドの権限確認は要らない）。置き場所は呼び出し側が決める（ドラッグで置く操作はない）。
- Launcher3（android-17.0.0_r1）のピッカー: View 版（WidgetsFullSheet など）はもうなく、Compose 版だけ。modules/widgetpicker が 65 ファイル・約 9,800 行（androidx.compose の import が 723）、Launcher 側のつなぎ（src/.../widgetpicker、widget/picker）が 23 ファイル・約 2,700 行。Dagger で組み立てる。移植には Compose（Material3）を依存に足し、Dagger を手で組み直し、プレビューの読み込み（WidgetManagerHelper、DatabaseWidgetPreviewLoader など）も持ってくる必要がある。
- Pixel Launcher の見た目: 左にアプリの一覧（検索、Featured）、右にプレビュー。2パネルの全画面シート。

**2026-11-22T15:43:20Z**

決定（2026-11-23）: まず OS 標準の選択画面（ACTION_APPWIDGET_PICK）で進める。Launcher3 の Compose 版ピッカーの移植は別チケットにする。
