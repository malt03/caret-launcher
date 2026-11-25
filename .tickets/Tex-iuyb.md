---
id: Tex-iuyb
status: closed
deps: [Tex-q3gh]
links: [Tex-4cve, Tex-op7u]
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

前半（ピッカーの一覧のショートカット）を移した: 「追加」とドラッグの PendingAddShortcutInfo を Launcher.addPendingItem → processShortcutFromDrop（REQUEST_CREATE_SHORTCUT）でアプリの作成画面に渡し、結果のピン留めの要求を PinRequestHelper（そのままコピー）で受けて置く（completeAddShortcut）。android-17 の completeAddShortcut は EXTRA_SHORTCUT_INTENT の古い形式を受けないので、それも同じ。作成画面から戻ったときにセルが埋まっていたら、フォルダは作らず最初の空きセルに置く（上流は createUserFolderIfNecessary / addToExistingFolderIfNecessary。フォルダ側は別のチケットで触っているので避けた）。仕事用プロファイルの作成画面から作ったショートカットは、ホームのショートカットが個人用だけ（ShortcutRepository）なので保存できないはず（未確認）。後半（ほかのアプリからのピン留めの要求: AddItemActivity・showWidgetsForPinRequest・PinItemDragListener・PinWidgetFlowHandler）は残した。システムのアプリでなくても既定のホームアプリなら可能（ShortcutService は既定のホームの CONFIRM_PIN_SHORTCUT / CONFIRM_PIN_APPWIDGET の Activity に要求を送り、accept() はその呼び出し元を確かめるだけ。ウィジェットの bind は今の追加と同じく ACTION_APPWIDGET_BIND の確認画面を経る）。ただし上流の「自動で追加」は ItemInstallQueue（ファイルに保存したキューを、ホームが resume したときに AddWorkspaceItemsTask で空きに置く。キューの中のショートカットは固定を外さない）に依存し、ドラッグのウィジェットは WidgetAddFlowHandler の派生（PinWidgetFlowHandler。bind のあとに要求を accept し、保存状態に入る）に依存する。どちらも CaretLauncher の HomeModel / HomeWidgetAddFlow に相当する層がないので、キューをどこに持ちどう流すかを決めて移す別の作業になる

**2026-11-23T17:30:37Z**

2026-11-24: 後半（ほかのアプリからのピン留めの要求）は今回も残した。下地はできた: ホームのモデルは BgDataModel と ModelWriter（Tex-2es1）になり、BgDataModel.pendingShortcuts（ItemInstallQueue.getPendingShortcuts の受け口。固定の更新でキューの中のショートカットを外さない）を用意した。HomeController には bindItemsUpdated / bindWorkspaceComponentsRemoved（Tex-3dnt）があるが bindItemsAdded はまだない。残る作業: (1) ItemInstallQueue（ファイルに保存するキュー、Launcher.onResume で flush）と AddWorkspaceItemsTask（WorkspaceItemSpaceFinder はページが1つなので空きがなければ追加しない・Toast を出すかは要判断）を移し、ModelCallbacks.bindItemsAdded で bindInflatedItems のバウンスを付けて置く、(2) AddItemActivity（CONFIRM_PIN_SHORTCUT / CONFIRM_PIN_APPWIDGET を受ける Activity。上流は WidgetCell のプレビュー・「自動で追加」・ドラッグ（PinItemDragListener）を持つシート）をマニフェストに足し、ウィジェットは PinWidgetFlowHandler（bind の後に PinItemRequest.accept、HomeWidgetAddFlow の保存状態に入れる）で追加する。シートの見た目（上流の AddItemActivity は Compose 版のウィジェットピッカーとは別の View 版）をどちらで作るかは製品判断が要る

**2026-11-24T12:20:53Z**

2026-11-24 訂正: 上の「上流の AddItemActivity は View 版」は android-16 の話だった。android-17.0.0_r1 の AddItemActivity.kt は add_item_confirmation_activity_compose（SimpleDragLayer だけ）を出し、WidgetPickerComposeWrapper.showWidgetsForPinRequest で widgetpicker モジュールの PinAppWidgetCatalog（移植済みの third_party/launcher3/modules/widgetpicker/.../ui/pin）を表示する。見た目の判断は要らず、上流どおり移植すればよい。互換の WidgetPickerComposeWrapper は showWidgetsForPinRequest を削っている（44 行目）ので、そこを戻すところから

**2026-11-25T00:23:03Z**

2026-11-25: 後半も移した。derived: AddItemActivity.kt（ロギングなし、AppWidgetHost（id 1024）でバインドの許可を開く）、PinItemDragListener、ItemInstallQueue（Dagger とスナックバーなし、アプリが作る）、WorkspaceItemSerializer（インストールのセッションなし）、WorkspaceItemSpaceFinder・AddWorkspaceItemsTask（ページが1つ、いっぱいなら置かない）。path=: WidgetPickerComposeWrapper(Impl)（差がピン留めの部分だけだったので上流のまま）、PinWidgetFlowHandler、PersistedItemArray、ListStore、WorkspaceItemCoordinates、add_item_confirmation_activity_compose.xml。LauncherModel・PackageTaskFactory・BgDataModel のキューの呼び出しを上流に戻し、ホームは読み込みのあとと onResume でキューを流す。マニフェストに CONFIRM_PIN_SHORTCUT・CONFIRM_PIN_APPWIDGET の Activity を足した。エミュレータでは確かめていない（許可ダイアログが開いたままだったため）
