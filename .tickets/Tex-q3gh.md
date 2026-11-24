---
id: Tex-q3gh
status: open
deps: [Tex-ydua]
links: []
created: 2026-11-24T13:35:59Z
type: task
priority: 2
assignee: Koji Murata
parent: Tex-op7u
tags: [launcher3, upstream, home]
---
# ⑤ 長押し（ItemLongClickListener・PopupController）とウィジェット追加の流れを上流のコピーにする

- ui/home/ItemLongClickListener.kt（261 行）を touch/ItemLongClickListener.java と popup/PopupControllerAppIcons.kt・PopupControllerForExtraHomeScreenItems.kt・LauncherDeepShortcutDragHandler.kt の derived にする。beginDragShared がラムダでなく PopupController を呼ぶ形に戻る（Tex-ydua のノートの (3)）。② の beginDragShared が必要とするので、この部分は ② と一緒に先に進めてもよい
- ウィジェットの追加: PendingRequestArgs.java・WidgetAddFlowHandler.java・ActivityResultInfo.java は path= にできる（要るのは Launcher.setWaitingForResult と getAppWidgetHolder だけ）。Launcher.java の addPendingItem・processShortcutFromDrop・addAppWidgetFromDrop・handleActivityResult・completeAdd・completeAddShortcut（上流 746-884、1689-1766）を、互換の Launcher に上流の順で derived の区画として移す。ui/home/HomeWidgets.kt（476 行）は HomeWidgetHost（70 行ほど）まで縮む
- 印: BIND_APPWIDGET を持たない（OS の確認画面）、ホームが前面に出るまで待つ、設定画面から戻ってから置く
- その上に Tex-iuyb（ピン留めの要求、AddItemActivity）を移す

リスク: 互換の Launcher は Activity でないので、2,903 行の Launcher.java の一部を derived にすると更新時の食い違いが多い。規模: 中。


## Notes

**2026-11-24T16:10:51Z**

2026-11-25: 前半（長押しとメニュー）を移した。touch/ItemLongClickListener.java（全体）、popup/PopupItemDragHandler.kt（LauncherPopupItemDragHandler を含む）、PopupControllerAppIcons.kt、PopupControllerForExtraHomeScreenItems.kt、PopupDataRepository.kt（Dagger なし）、PopupDataSource.kt（削除・フォルダ・ウィジェットだけ）、PopupDataMapper.kt を derived、PopupResizeStrategy.kt を path=。PopupController の factory、PopupContainerWithArrow.configureForLauncher（ライブ更新なし）、SystemShortcut.Factory と APP_INFO、ActivityContext.getSupportedShortcuts、BubbleTextView.configureMinimalPopup、LauncherAppWidgetInfo.widgetFeatures（ビューを作るときに設定）を戻した。アプリ側は HomeSystemShortcuts（getSupportedShortcuts: 検索文字列を編集・アプリ情報）だけ。ウィジェットの「設定」は Launcher.startWidgetReconfigure（印）でアプリの追加の流れへ。残り: ウィジェット追加の流れ（PendingRequestArgs・WidgetAddFlowHandler・Launcher の add flow）

**2026-11-24T16:11:13Z**

2026-11-25: 後半（ウィジェット追加の流れ）は今回は見送った。HomeWidgets.kt はすでに Launcher.addAppWidgetFromDrop / handleActivityResult / completeAdd* をほぼ上流どおり書き写していて、derived の Launcher に移しても挙動は変わらない一方、確かめるにはシステムのウィジェット作成の許可ダイアログ（ACTION_APPWIDGET_BIND）を通す必要があり、エージェントは押せない。実機で許可を出せるときに、PendingRequestArgs・WidgetAddFlowHandler・ActivityResultInfo（path=）と互換の Launcher の区画に移す。Tex-iuyb も同じ理由で後に。
