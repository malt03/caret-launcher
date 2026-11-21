---
id: Tex-nguc
status: open
deps: []
links: []
created: 2026-11-21T11:27:47Z
type: task
priority: 2
assignee: Koji Murata
parent: Tex-izod
tags: [refactor]
---
# Launcher の互換クラスを ActivityContext にする

上流では Launcher 自身が ActivityContext で、コピーするコードの多くが Launcher.getLauncher(context) や instanceof Launcher で Launcher の機能を直接使う。こちらでは ActivityContext（HomeActivityContext）と Launcher の互換クラスが別のため、そうした箇所を互換クラスで書き換えている（FolderIcon のドロップ、DeepShortcutView など）。上流の Launcher にあたるものも、HomeController・HomeActivityContext・Launcher の互換クラス・LauncherDelegate の4つに分かれている。
これから移植する AllAppsTransitionController、ItemClickHandler、ItemLongClickListener、ウィジェットまわり（PendingItemDragHelper など）も Launcher に直接頼るので、全アプリ一覧（Tex-hibk）の前に揃える。
- HomeActivityContext を Launcher の互換クラスのサブクラスにし、ActivityContext.lookupContext と Launcher.getLauncher が同じものを返すようにする
- DeepShortcutView が使う getAccessibilityDelegate().addToWorkspace、getStatsLogManager()、getModel().updateAndBindWorkspaceItem の代わりを置き、DeepShortcutView を third_party のコピーにする（メニューから来る WorkspaceItemInfo は、ホームに置くときに ShortcutItemInfo へ変換する）
- 何もしない代わりのクラスは、何をしないかを説明に書く
2026-11-21 の検討: DeepShortcutView 単体ではメリットが小さい（違いは約70行で、derived= の3方向マージで追従できている）。効果が出るのは、これから移植するコードの書き換えが減る点

## Acceptance Criteria

- [ ] Launcher の互換クラスが ActivityContext で、上流の Launcher.getLauncher / instanceof Launcher がそのまま動く
- [ ] DeepShortcutView が third_party のコピーになり、UPSTREAM.md の derived= から path= に移っている
- [ ] Launcher にあたるものが Launcher の互換クラスとそのサブクラスにまとまり、Launcher の互換クラスを理由にした TextLauncher: の印が減っている
- [ ] 長押しメニュー（追加ボタン、ショートカットのドラッグ）、フォルダ、ドラッグが今と同じ

