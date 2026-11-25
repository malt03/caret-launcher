---
id: Tex-9hi3
status: closed
deps: []
links: []
created: 2026-11-25T02:16:22Z
type: feature
priority: 2
assignee: Koji Murata
tags: [search]
---
# 検索フィールドの右に共有ボタンを並べる（テキスト共有）

検索欄に入力した文字を、ユーザーが選んだアプリ（3つまで）に共有（ACTION_SEND の text/plain）するボタンを、検索フィールドの右端に並べる。Web 検索や LLM のアプリ（Gemini、ChatGPT、Claude など）に渡すため。仕様は docs/spec.md 2.7、経緯は docs/text-handoff-research.md の5章。

## Design

- 当初の案（検索結果のグリッドに「◯◯に聞く」のセルを足す、PROCESS_TEXT も使う）から変えた。グリッドはアプリだけにし、渡し方は共有だけにする
- 選ぶ入口は、空いている領域の長押しメニューの「テキスト共有」（Launcher3 の「ホームの設定」の位置。`Launcher.getShareTextOption` でアプリが作る）。設定画面は作らない
- ダイアログ（`ShareTextDialog`、フレームワークの AlertDialog と RecyclerView）で、ハンドルのドラッグ（ItemTouchHelper）で並べ替え、× で外す。TalkBack は「上に移動」「下に移動」の操作
- 「追加」は OS のアプリ選択画面（ACTION_PICK_ACTIVITY、EXTRA_INTENT に SEND text/plain）。3つで押せなくなる
- アイコンとラベルは ResolveInfo のもの（共有シートと同じ）。<queries> に SEND text/plain
- 保存は SharedPreferences（`ShareTargetStore`）

## Acceptance Criteria

- 長押しメニューの「テキスト共有」から、アプリを選び、並べ替え、外せる
- 入力があるとき、検索フィールドの右に選んだ順でボタンが出て、タップで入力した文字がそのアプリに渡る
- エミュレータ（Small_Phone）で確認済み（Gemini に渡ると入力欄に入る）
