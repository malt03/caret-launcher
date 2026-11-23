---
id: Tex-vs8t
status: open
deps: []
links: []
created: 2026-11-22T00:57:38Z
type: epic
priority: 1
assignee: Koji Murata
---
# 設計の見直し（2026-11-22 のコード全体レビュー）

コード全体を、ホームとドラッグ、全アプリ一覧と検索、モデルとアイコン、コピーと互換層の4つに分けて見直した結果。データが消えるバグは各機能の親チケットの下に、責務分担と小さい整理はこのチケットの下に置く。

確認済みで問題がなかったもの: third_party のコピー192ファイルはすべて上流と同一。derived= の7ファイルは削除と TextLauncher: 印の変更だけ（細かい例外は別チケット）。


## Notes

**2026-11-23T09:06:57Z**

2026-11-23 の再レビュー（Launcher3 に追従するための、コピーとの責務分担）: third_party の 353 の path= はすべて上流と同一、記録のないファイルは shade_panel_* の4つ（Tex-p310）。問題は追跡の外にある上流コード: 互換のスタンドイン（約 2,700 行。Tex-n0ut）、アプリの書き写し（約 2,500 行。Tex-ydua）、抜き出した値とフラグ（Tex-hpdq）、スクリプト（Tex-yonv）。ずれがすでに出ている例: グリッドの列数の維持（Tex-oppz）、移し替えのウィジェット（Tex-lj5u）、ウィジェットの追加フロー（Tex-qnof）、Folder.closeComplete のドット（Tex-n0ut）
