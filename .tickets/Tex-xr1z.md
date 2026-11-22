---
id: Tex-xr1z
status: open
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
- 互換の AppInfo に updateRuntimeFlagsForActivityTarget を置いて AppItemInfo から使う。AppItemInfo に本物の起動 Intent（AppInfo.makeLaunchIntent）を持たせ、TextLauncher だけの ItemInfo.getComponent() のフックをなくす
- 一覧の中の検索から BaseAllAppsAdapter / AllAppsGridAdapter に寄せる。ホームの検索結果も、AppsGridLayoutManager（GridLayoutManager）を逆向きにすれば使える。学習の記録のためのクリックの受け口は残す
- AppEntry はキーだけにするかなくす
- GridSpec.from() を4回作るのをやめ、互換の DeviceProfile（HomeActivityContext）を唯一の元にする。下端の余白も AllAppsView と検索結果で渡し方が違うので揃える

