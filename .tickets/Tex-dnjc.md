---
id: Tex-dnjc
status: closed
deps: [Tex-tn9i, Tex-pb0n]
links: []
created: 2026-11-21T09:18:13Z
type: task
priority: 1
assignee: Koji Murata
parent: Tex-neft
tags: [refactor]
---
# フォルダを Launcher3 と同じく AbstractFloatingView にする

上流の Folder は AbstractFloatingView（TYPE_FOLDER）だが、互換クラスは FrameLayout を継承している。そのため、上流なら 1 つの仕組みで済むことを別々に書いている。
- 戻る・キーボードが閉じたとき: LauncherActivity の closeTopFloatingView() || closeFolder()
- 外側のタップで閉じる: OpenFolder.kt:51 の全面の透明な View（上流は onControllerInterceptTouchEvent）
- HomeController の closeFolder / isFolderOpen / folderPopup
- FolderIcon.java:363-422 の FolderContent（add/removeFolderContent、isOpen、notifyDrop）。getFolder() は上流どおり Folder を返す
- 開閉の処理（animateOpen / animateClosed / closeComplete のフォーカス戻し / centerAboutIcon、OpenFolder.kt:96-151）を Folder 互換クラスへ移す
- DeepShortcutView.java:207 の TextLauncher: 改変を消す
上流はアイコンごとにフォルダを 1 つ持ち続けるが、こちらは開くたびに作り直している。上流と同じ持ち方に揃える。上流ではフォルダ自身がドラッグ元なので、FolderDragSource を Folder に戻せるかも確かめる。

## Acceptance Criteria

- [ ] Folder 互換クラスが AbstractFloatingView を継承し、閉じる経路（戻る、キーボードを閉じる、外側のタップ、HOME、ドラッグ開始、文字入力）が AbstractFloatingView を通る
- [ ] closeFolder / isFolderOpen / FolderContent / 透明な View がない
- [ ] フォルダの開閉アニメーション、中からのドラッグ、中へのドロップ、残り 1 つでアイコンに戻る、キーフォーカス（docs/spec.md 2.6）が今と同じ


## Notes

**2026-11-21T09:56:42Z**

Folder 互換クラスを上流と同じく AbstractFloatingView（TYPE_FOLDER）にし、フォルダのアイコンごとに1つ持つ形にした（FolderIcon.setFolder / Folder.bindToIcon）。開閉、外側のタッチ、ドラッグ元（startDrag / onDragStart / onDropCompleted / notifyDrop）、中身の出し入れを Folder.java から、bindItems / createNewView / arrangeChildren を FolderPagedView.java から移した。FolderContent、FolderDragSource、OpenFolder、全面の透明な View、HomeController の closeFolder / isFolderOpen / folderPopup をなくし、戻る・キーボードを閉じる・HOME・停止はすべて AbstractFloatingView を通る。アプリ側は HomeFolder（見た目、アイテムの View、タッチでフォーカスを取らない）と LauncherDelegate の上書き（beginDragShared、replaceFolderWithFinalItem）だけ。上流に合わせて変わった点: 中のアプリを起動してもフォルダはすぐ閉じず、ランチャーが隠れたときに閉じる。外側のタッチは指を置いた時点で閉じる。キーで開くと最初のアイテムにフォーカスが移る。エミュレータで、開閉アニメーション、外側のタッチ、戻る（キーボードあり・メニューあり）、HOME、文字入力、中からの起動と復帰、中からのドラッグ（2個のフォルダは解除）、自分のアイコンへの戻し、受け付けない場所へのドロップでの戻り、フォルダの新規作成、再起動後の保持を確認。DeepShortcutView の TextLauncher: 改変は tag の扱いなので Tex-f33r で消す
