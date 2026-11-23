---
id: Tex-iuyb
status: open
deps: []
links: [Tex-4cve]
created: 2026-11-23T06:16:03Z
type: feature
priority: 3
assignee: Koji Murata
parent: Tex-ogmk
tags: [home, launcher3]
---
# ウィジェットピッカーのショートカット作成とピン留めの要求

ピッカーの一覧に出るショートカットの作成画面（PendingAddShortcutInfo、ACTION_CREATE_SHORTCUT、REQUEST_CREATE_SHORTCUT）と、ほかのアプリからのピン留めの要求（LauncherApps.PinItemRequest、showWidgetsForPinRequest、PinRequestHelper）。Tex-r705 では、ショートカットの「追加」はホームの addToWorkspace が受けず何もしない。PinShortcutRequestActivityInfo は一覧の表示に要る分だけの互換クラス。


## Notes

**2026-11-23T16:44:45Z**

前半（ピッカーの一覧のショートカット）を移した: 「追加」とドラッグの PendingAddShortcutInfo を Launcher.addPendingItem → processShortcutFromDrop（REQUEST_CREATE_SHORTCUT）でアプリの作成画面に渡し、結果のピン留めの要求を PinRequestHelper（そのままコピー）で受けて置く（completeAddShortcut）。android-17 の completeAddShortcut は EXTRA_SHORTCUT_INTENT の古い形式を受けないので、それも同じ。作成画面から戻ったときにセルが埋まっていたら、フォルダは作らず最初の空きセルに置く（上流は createUserFolderIfNecessary / addToExistingFolderIfNecessary。フォルダ側は別のチケットで触っているので避けた）。仕事用プロファイルの作成画面から作ったショートカットは、ホームのショートカットが個人用だけ（ShortcutRepository）なので保存できないはず（未確認）。後半（ほかのアプリからのピン留めの要求: AddItemActivity・showWidgetsForPinRequest・PinItemDragListener・PinWidgetFlowHandler）は残した。システムのアプリでなくても既定のホームアプリなら可能（ShortcutService は既定のホームの CONFIRM_PIN_SHORTCUT / CONFIRM_PIN_APPWIDGET の Activity に要求を送り、accept() はその呼び出し元を確かめるだけ。ウィジェットの bind は今の追加と同じく ACTION_APPWIDGET_BIND の確認画面を経る）。ただし上流の「自動で追加」は ItemInstallQueue（ファイルに保存したキューを、ホームが resume したときに AddWorkspaceItemsTask で空きに置く。キューの中のショートカットは固定を外さない）に依存し、ドラッグのウィジェットは WidgetAddFlowHandler の派生（PinWidgetFlowHandler。bind のあとに要求を accept し、保存状態に入る）に依存する。どちらも TextLauncher の HomeModel / HomeWidgetAddFlow に相当する層がないので、キューをどこに持ちどう流すかを決めて移す別の作業になる
