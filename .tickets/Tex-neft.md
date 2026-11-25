---
id: Tex-neft
status: closed
deps: []
links: []
created: 2026-11-21T09:18:12Z
type: epic
priority: 1
assignee: Koji Murata
tags: [refactor]
---
# Launcher3 取り込みの整理

2026-11-21 の設計見直しで見つかった、取り込んだコードと独自実装の境目の問題をまとめて直す。third_party/ のコピー（68ファイル）は上流と一致しており問題なし。問題は、Launcher3 の役割をアプリ側で作り直している箇所と、CaretLauncher の方針が互換クラスに入り込んでいる箇所。全アプリ一覧（Tex-pa8s）とウィジェット（Tex-t2ea）に入る前に片付ける。方針: docs/launcher3-porting.md


## Notes

**2026-11-21T09:18:29Z**

進め方（2026-11-21 決定）。各タスクで UPSTREAM.md と docs/launcher3-porting.md を合わせて直し、エミュレータで今と同じ動きを確かめてからコミットする。
1. 記録とライセンスの整備: Tex-sfxe → Tex-oixt → Tex-cklf → Tex-uji5（動きは変えない。先に import を戻して以降の差分を読みやすくする）
2. 互換クラスの形: Tex-t281、Tex-pb0n、Tex-gop9、Tex-htxz、Tex-tn9i（互いに独立、どれも Tex-cklf の後）
3. フォルダ: Tex-dnjc（Tex-tn9i と Tex-pb0n の後）
4. モデル: Tex-f33r → Tex-alrp（フォルダの中身の持ち方が決まってから）
5. 分割: Tex-9vav、Tex-6ct1（Tex-f33r の後）、Tex-mfcm
全アプリ一覧の表示（Tex-hibk）は Tex-6ct1 と Tex-9vav の後、ウィジェット（Tex-t2ea）は Tex-f33r の後にする。
同じファイルを触るタスクが多いので、並行のセッションには分けず、このセッションで順に進める。

**2026-11-21T10:20:30Z**

15 件をすべて閉じた。third_party のコピーは上流と一致したまま（98 ファイル。res/color は先に curl で確認）。互換クラスは上流にソースがない 5 ファイル以外すべてにライセンスヘッダーがある。整理の途中で見つけた未再現の不具合は Tex-ijxl に切り出した
