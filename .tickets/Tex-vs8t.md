---
id: Tex-vs8t
status: closed
deps: []
links: []
created: 2026-11-22T00:57:38Z
type: epic
priority: 1
assignee: Koji Murata
---
# 設計の見直し（2026-11-22 のコード全体レビュー）

コード全体を、ホームとドラッグ、全アプリ一覧と検索、モデルとアイコン、コピーと互換層の4つに分けて見直した結果。データが消えるバグは各機能の親チケットの下に、責務分担と小さい整理はこのチケットの下に置く。

確認済みで問題がなかったもの: third_party のコピー192ファイルはすべて上流と同一。derived= の7ファイルは削除と CaretLauncher: 印の変更だけ（細かい例外は別チケット）。


## Notes

**2026-11-23T09:06:57Z**

2026-11-23 の再レビュー（Launcher3 に追従するための、コピーとの責務分担）: third_party の 353 の path= はすべて上流と同一、記録のないファイルは shade_panel_* の4つ（Tex-p310）。問題は追跡の外にある上流コード: 互換のスタンドイン（約 2,700 行。Tex-n0ut）、アプリの書き写し（約 2,500 行。Tex-ydua）、抜き出した値とフラグ（Tex-hpdq）、スクリプト（Tex-yonv）。ずれがすでに出ている例: グリッドの列数の維持（Tex-oppz）、移し替えのウィジェット（Tex-lj5u）、ウィジェットの追加フロー（Tex-qnof）、Folder.closeComplete のドット（Tex-n0ut）

**2026-11-23T12:17:52Z**

2026-11-23 整理: Tex-n0ut・Tex-hpdq・Tex-yonv を閉じた（互換クラスの derived= 化、上流の追跡、更新スクリプト）。P0 の Tex-536b・Tex-twu9・Tex-a8os も閉じ、応急の直しの続きを Tex-2es1・Tex-3dnt に記した。残りの子: Tex-2es1、Tex-3dnt、Tex-o5bb（モデル）、Tex-ydua（アプリの書き写し）、Tex-rpfa・Tex-fco2・Tex-xr1z（全アプリ一覧）、Tex-p310・Tex-plll（記録と掃除）

**2026-11-23T15:47:19Z**

2026-11-24 整理: データが消える不具合（P0 の3件、Tex-8w0r、Tex-yh7w、Tex-9tv6）はすべて閉じた。フォルダー内のドラッグを上流の Folder・FolderPagedView に乗り換えた（Tex-ymk9 のページ送りが残る）。残りの子は責務の整理（Tex-2es1、Tex-3dnt、Tex-o5bb、Tex-ydua）、全アプリ一覧（Tex-rpfa、Tex-fco2、Tex-xr1z）、記録と掃除（Tex-p310、Tex-plll）
