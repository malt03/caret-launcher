# packages/modules/IntentResolver から取り込んだコード

方針: [docs/launcher3-porting.md](../../docs/launcher3-porting.md)

「テキスト共有」の「追加」の一覧（docs/spec.md 2.7）を、Android の共有画面（IntentResolver の `ChooserActivity`）と同じ見た目と決まりにするために取り込んだ。共有画面そのものは、選んだアプリを起動する作りで、Hilt・Kotlin の flow・システム API に深く依存しているので取り込まない。

`scripts/update-upstream.sh` は、次のブロックを読んで更新する。パスは、このディレクトリと上流のリポジトリで同じ（上流のファイルは `java/src`・`java/res` の下にある）。`derived=` の行は、上流のファイルから使わない部分を削り、変更に `TextLauncher:` の印を付けて作った互換クラスで、同じく3方向マージで更新する。

- `TargetPresentationGetter` は、アイコンのビットマップを作る部分（`SimpleIconFactory`。フレームワークの内部のリソースを使う）を削り、アイコンを Drawable のまま返す（`ImageView` がシステムのアイコンの形で描く）。仕事用プロファイルのバッジは付けない
- `chooser_dialog.xml`・`chooser_dialog_item.xml` は、フレームワークの非公開の id（`androidprv:id/listContainer`・`text`）をモジュールの id にした

`ref=` の行は、上流のファイルを元に書いたがマージはしないもの（アプリ側に書き写したもの）で、`scripts/check-upstream.py` が更新時に上流の差分を出す。

- `ChooserListAdapter` の、同じアプリの複数の受け口を1つのセルにまとめる処理（`sortedComponents` の `groupingBy`）、まとめたセルの印（`bindGroupIndicator`）、並び順（`DisplayResolveInfoAzInfoComparator`）
- `ResolverListAdapter.ViewHolder` の `bindLabel`（2行目がなければ1行目を2行まで、あれば1行ずつ）
- `ChooserStackedAppDialogFragment` と `ChooserTargetActionsDialogFragment` の、まとめたセルを押したときのダイアログ（Fragment の代わりにフレームワークの `Dialog`）

どれもアプリの `ShareTargetPicker` にある。`res-textlauncher/` には、コピーしたレイアウトが使う値（属性・寸法・色）を抜き出して置き、`values_root=` の上流の値と突き合わせる。

```upstream
repo=https://android.googlesource.com/platform/packages/modules/IntentResolver
tag=android-17.0.0_r1
res_dir=res-textlauncher
path=java/src/com/android/intentresolver/widget/BadgeTextView.kt
path=java/src/com/android/intentresolver/widget/ChooserTargetItemView.kt
path=java/res/layout/chooser_grid_item_hover.xml
path=java/res/drawable/chooser_group_background.xml
path=java/res/drawable/ic_chooser_group_arrow.xml
path=java/res/drawable/chooser_dialog_background.xml
derived=java/src/com/android/intentresolver/TargetPresentationGetter.java compat/intentresolver/com/android/intentresolver/TargetPresentationGetter.java
derived=java/res/layout/chooser_dialog.xml compat/intentresolver/res/layout/chooser_dialog.xml
derived=java/res/layout/chooser_dialog_item.xml compat/intentresolver/res/layout/chooser_dialog_item.xml
ref=java/src/com/android/intentresolver/ChooserListAdapter.java app/src/main/java/com/malt03/textlauncher/ui/ShareTargetPicker.kt
ref=java/src/com/android/intentresolver/ResolverListAdapter.java app/src/main/java/com/malt03/textlauncher/ui/ShareTargetPicker.kt
ref=java/src/com/android/intentresolver/chooser/DisplayResolveInfoAzInfoComparator.java app/src/main/java/com/malt03/textlauncher/ui/ShareTargetPicker.kt
ref=java/src/com/android/intentresolver/ChooserStackedAppDialogFragment.java app/src/main/java/com/malt03/textlauncher/ui/ShareTargetPicker.kt
ref=java/src/com/android/intentresolver/ChooserTargetActionsDialogFragment.java app/src/main/java/com/malt03/textlauncher/ui/ShareTargetPicker.kt
values_root=java/res
values_root=https://android.googlesource.com/platform/frameworks/libs/systemui dynamiccolors/res
```

## 手を入れたファイル

`derived=` の3つ（上記）。
