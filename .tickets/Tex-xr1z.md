---
id: Tex-xr1z
status: closed
deps: []
links: []
created: 2026-11-22T00:58:38Z
type: task
priority: 2
assignee: Koji Murata
parent: Tex-vs8t
---
# アプリを表すクラスを AppInfo に一本化し、検索結果も BaseAllAppsAdapter を使う

アプリ一つを AppEntry、AllAppsAppInfo、AppItemInfo で表し、アイコンも AppIconLoader の icons マップ（BaseIconCache のメモリ、app_icons.db に続く3つ目）に持つ。AppItemInfo.bind は停止中の扱いを互換の AppInfo とは別に書き直している。検索結果は onBindViewHolder のたびに AppItemInfo（WorkspaceItemInfo）を作る。AppRepository.find はホームのアイテムごとの線形探索。検索結果の一覧は ResultsAdapter で BaseAllAppsAdapter のアイコンのバインドを書き直していて、検索のセルは applyFromWorkspaceItem と1件ずつのアイコン読み込み、A〜Z のセルは applyFromApplicationInfo と一括読み込み。互換の AlphabeticalAppsList に残した setSearchResults は使っていない。

- Launcher3: AllAppsList に起動画面ごとの AppInfo を1つ（アイコン、フラグ、Intent を持つ）。検索と全アプリ一覧は同じ AppInfo をバインドし、ホームのアイテムは AppInfo.makeWorkspaceItem で作る。実行時のフラグは AppInfo.updateRuntimeFlagsForActivityTarget の1か所。検索の一覧は同じ入れ物の2つ目の AdapterHolder（AlphabeticalAppsList.setSearchResults と AllAppsGridAdapter）

やること:
- ComponentKey で引ける AllAppsList 型の AppInfo の置き場を作り、検索・全アプリ一覧・ホームの引き当ての唯一の元にする（アイコンも AppInfo に持たせ、icons マップをなくす）
- 互換の AppInfo に updateRuntimeFlagsForActivityTarget を置いて AppItemInfo から使う。AppItemInfo に本物の起動 Intent（AppInfo.makeLaunchIntent）を持たせ、CaretLauncher だけの ItemInfo.getComponent() のフックをなくす
- 一覧の中の検索から BaseAllAppsAdapter / AllAppsGridAdapter に寄せる。ホームの検索結果も、AppsGridLayoutManager（GridLayoutManager）を逆向きにすれば使える。学習の記録のためのクリックの受け口は残す
- AppEntry はキーだけにするかなくす
- GridSpec.from() を4回作るのをやめ、互換の DeviceProfile（HomeActivityContext）を唯一の元にする。下端の余白も AllAppsView と検索結果で渡し方が違うので揃える


## Notes

**2026-11-23T17:06:42Z**

一部だけ進めた（残りは下）。やったこと: 検索結果（ホームの上・一覧の中・2パネルの左）を上流の AdapterHolder.SEARCH と同じく、アプリを持たない AlphabeticalAppsList の setSearchResults と AllAppsGridAdapter で出すようにした。ResultsAdapter はなくし（セルの ResultIconView は ResultIconView.kt に残す）、onBindViewHolder ごとに AppItemInfo を作るのと1件ずつのアイコン読み込みをやめ、全アプリ一覧の AllAppsStore の AllAppsAppInfo（一括読み込みのアイコン）を applyFromApplicationInfo でバインドする。クリックは ItemClickHandler、長押しは getAllAppsItemLongClickListener（INSTANCE_ALL_APPS と同じ）。学習の記録の受け口は LauncherActivity.launch で、押されたセルが検索結果の一覧のものかで判定する（SearchResults.isResult）。ホームの検索結果は AppsGridLayoutManager を逆向きにした。アイコンやアプリが変わったときは AllAppsStore の OnUpdateListener で検索をやり直す（onIconsChanged はなくした）。小さな違い: 起動直後、全アプリ一覧のアイコンの一括読み込みが終わるまで検索結果が出ない（上流も AllAppsList が読めるまで同じ）。アイコンやアプリが変わると検索結果は先頭に戻る。残り: (1) ComponentKey で引ける AllAppsList 型の AppInfo の置き場を一つにし、AppIconLoader の icons マップと AppRepository.find（HomeLoader のホームの引き当て）をそこに寄せる、(2) 互換の AppInfo に updateRuntimeFlagsForActivityTarget を置いて AppItemInfo.bind（data/HomeItemInfo.kt）から使い、AppItemInfo に makeLaunchIntent の Intent を持たせて ItemInfo.getComponent() のフックをなくす、(3) AppEntry をキーだけにするかなくす（HomeActivityContext.startActivitySafely、ItemLongClickListener.onSearchResultLongPress、SearchTermsDialog も）、(4) GridSpec.from() の重複（LauncherActivity、HomeController、onConfigurationChanged、AllAppsView）を互換の DeviceProfile にまとめ、下端の余白の渡し方を AllAppsView と検索結果で揃える。(1)〜(3) はホームのモデル（HomeLoader、HomeItemInfo、HomeController）と同じコードなので、その作り直しと一緒にやるのがよい。ビルドと単体テストのみ確認（端末では未確認）

**2026-11-23T17:29:52Z**

2026-11-24（ホームのモデルの作業と合わせて）: (2) を済ませた。AppItemInfo は本物の起動 Intent（AppInfo.makeLaunchIntent、favorites の intent 列として保存）を持ち、ItemInfo.getComponent() のフックをなくした（getTargetComponent は上流どおり intent から）。互換の AppInfo に updateRuntimeFlagsForActivityTarget（停止中とシステムのアプリのフラグ。アーカイブ・自動化・プライベートスペース・App Lock・進捗はなし）を置き、AppInfo のコンストラクタと AppItemInfo.bind から使う。システムのアプリには FLAG_SYSTEM_YES が付くので、アンインストールのドロップ先は上流と同じくフラグで先に判定する。残り: (1) AllAppsList 型の AppInfo の置き場（AppIconLoader の icons マップと AppRepository.find の置き換え。HomeModelTasks の PackageUpdatedTask はいま AppRepository.apps を起動画面の一覧として使う）、(3) AppEntry の縮小、(4) GridSpec.from() の重複

**2026-11-23T18:01:24Z**

2026-11-24: 残りの (1)(3)(4) を済ませた。(1) 上流の AllAppsList を削った互換クラス（compat/.../model/AllAppsList.java、derived=）を置き、AppRepository が上流と同じくモデルのスレッド（MODEL_EXECUTOR）で LoaderTask.loadAllApps（アイコンは1件ずつ）とパッケージの作業（PackageUpdatedTask の updateIconsForPkg と updatePackage、appsRemoved・appsUnavailable の removePackage、停止・再開の updateRuntimeFlags、CacheDataUpdatedTask の updateIconsAndLabels、テーマの切り替えでの読み直し）を行い、copyData をメインスレッドに渡す（bindAllApplications）。全アプリ一覧（AllAppsStore.setApps）・検索結果・ホームの引き当て（AppRepository.getApp(ComponentKey)、二分探索）はどれもこの AppInfo を使う。AppIconLoader の icons マップと AppRepository.find はなくし、ホームのアプリのアイコンは IconCache.getTitleAndIcon(info, DESKTOP_ICON_FLAG) でアイテムに直接引く（ビューを作るときは verifyHighRes と同じく足りないときだけ）。PackageUpdatedTask には上流と同じく updatePackage が返した起動画面を渡し、AppItemInfo は intent・FLAG_DISABLED_NOT_AVAILABLE の解除・updateRuntimeFlagsForActivityTarget を上流の順で受ける。読み込み時の引き当ては WorkspaceItemInfo(AppInfo) と同じく AppInfo の題名とフラグを写す。アプリの一覧の読み込みがホームより先に MODEL_EXECUTOR に積まれるので、ホームのバインドの時点でアプリの一覧がまだないことはなくなった。(3) AppEntry と AllAppsAppInfo はなくし、保存用の文字列のキーは AppKey（ComponentKey とユーザーの通し番号）にした。AppItemInfo は起動画面とユーザーの通し番号から作り、homeKey は intent から求める。Host.launch は ItemInfo、editSearchTerms は AppItemInfo を受ける。HomeController.onSearchResultLongPress（未使用）は消した。(4) LauncherActivity は自分で GridSpec を作らず、2パネルかどうかをホームの DeviceProfile から読む。全アプリ一覧と検索結果のセル（ResultIconView）は上流の BubbleTextView と同じく DeviceProfile.AllAppsProfile（iconTextSizePx を足した）からアイコンと文字の大きさを取り、AllAppsView と SearchResults は GridSpec を受けない。GridSpec.from は、ホームを作るとき（HomeController）、構成の変化での比較（InvariantDeviceProfile.onConfigChanged と同じ）、ホームのない間の HomeGridInitializer の3か所だけ。ホームの中（HomeGridView、HomeLoader、GridSizeMigration）は上流の InvariantDeviceProfile にあたる GridSpec を読むまま。下端の余白は、全アプリ一覧と一覧の中の検索結果のどちらにも LauncherRootLayout.onBottomInsetsChanged から渡す（2パネルでは前と同じく下マージン）。小さな違い: 読み込み時に SD カード上で使えないアプリも、ホームの上（フォルダの外）に灰色で出るようにした（docs/spec.md 5.2 の記述どおり。前はフォルダの中だけ出ていた）。ビルドと単体テストのみ確認（端末では未確認）
