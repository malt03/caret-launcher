---
id: Tex-wgyx
status: closed
deps: [Tex-68f3]
links: []
created: 2026-11-20T11:48:45Z
type: task
priority: 1
assignee: Koji Murata
parent: Tex-ogmk
tags: [home, launcher3, spike]
---
# 移植範囲の調査（並び替え・ドラッグ・リサイズ枠・フォルダ・長押しメニュー）

Launcher3（android-17.0.0_r1）の次の部品について、依存関係を辿り、何をコピーして何を互換層で受け止めるかを決める。
- 並び替え: CellLayout.java、celllayout/（ReorderAlgorithm、ItemConfiguration など）
- ドラッグ: dragndrop/（DragController、DragView、DragLayer など）
- リサイズ枠: AppWidgetResizeFrame.kt、widget/resize/
- フォルダ: folder/（FolderIcon、Folder、PreviewBackground など）
- 長押しメニュー: popup/（ArrowPopup、PopupContainerWithArrow など）と、メニューからドラッグへの移行
縦スクロールで行数に上限のないグリッドに、CellLayout の並び替えをどう載せるかもここで決める。
結果はこのチケットのノートと docs/launcher3-porting.md に反映する。

## Acceptance Criteria

- [ ] 部品ごとに、コピーするファイルと互換層で用意するクラスの一覧がある
- [ ] 縦スクロールのグリッドとの組み合わせ方が決まっている
- [ ] 移植が割に合わない部品があれば、その理由と代わりの方針が書かれている


## Notes

**2026-11-20T12:14:50Z**

調査結果を docs/launcher3-porting.md の「Launcher3 から持ってくるもの」と「ハプティックを鳴らす場所」に反映。並び替えはアルゴリズムだけコピーし、CellLayout は View ではない互換クラスにする。縦スクロールのグリッドでは、行数を「使っている最終行＋余白」にしてドラッグごとに占有表を作り直す
