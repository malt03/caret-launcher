# frameworks/libs/systemui から取り込んだコード

方針: [docs/launcher3-porting.md](../../docs/launcher3-porting.md)

`scripts/update-upstream.sh` は、次のブロックを読んで更新する。パスは、このディレクトリと上流のリポジトリで同じ。

```upstream
repo=https://android.googlesource.com/platform/frameworks/libs/systemui
tag=android-17.0.0_r1
path=iconloaderlib/src
path=iconloaderlib/res
```

## 取り込んでいないもの

| 上流のパス | 理由 |
|---|---|
| `iconloaderlib/src_full_lib/` | システム API（`system_current`）が必要なため。Launcher3 本体も `iconloader_base` 相当だけで足りる |
| `iconloaderlib/build.gradle.kts`、`iconloaderlib/AndroidManifest.xml` | 上流のものは AOSP のツリー前提のため、自前のもの（`build.gradle.kts`、`TextLauncherManifest.xml`）を置く |
| `usertypelib/` | `@Parcelize` が AGP 9 の組み込み Kotlin で効かないため。`compat/launcher3` の `com.android.users.UserType` で代用する（Parcelable を外しただけ） |

## 手を入れたファイル

なし。
