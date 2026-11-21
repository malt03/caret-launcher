---
id: Tex-6ct1
status: open
deps: [Tex-f33r, Tex-alrp]
links: []
created: 2026-11-21T09:18:13Z
type: task
priority: 2
assignee: Koji Murata
parent: Tex-neft
tags: [refactor]
---
# HomeController を Launcher3 のクラスの境目で分ける

HomeController（776 行）が、Launcher3 でいう次のクラスの役割を 1 つで持っている。
- Launcher（ActivityContext）
- 読み込みと View の作成（LoaderTask / ItemInflater）
- 長押しメニュー（PopupControllerForAppIcon、LauncherPopupItemDragHandler）
- ドラッグ中の状態（spring-loaded）
- 消えたアプリ・ショートカットの整理
上流のクラス名に沿って分け、どこに何があるかを上流と対応させる。フォルダとモデルの整理の後でやる。

## Acceptance Criteria

- [ ] 分けた先が上流のクラスと対応しており、各ファイルの説明に対応する上流のクラスが書いてある
- [ ] ホームの全操作が今と同じ

