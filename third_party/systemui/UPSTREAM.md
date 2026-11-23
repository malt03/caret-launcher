# frameworks/libs/systemui から取り込んだコード

方針: [docs/launcher3-porting.md](../../docs/launcher3-porting.md)

`scripts/update-upstream.sh` は、次のブロックを読んで更新する。パスは、このディレクトリと上流のリポジトリで同じ。

`ref=<上流のパス> <こちらのパス>` は、上流のファイルを元に書いたがマージはしないもの（互換層の代わりのクラスと、`Android.bp` を元にした `build.gradle.kts`）で、`scripts/check-upstream.py` が更新時に上流の差分を出す（docs/launcher3-porting.md の「更新の手順」）。

```upstream
repo=https://android.googlesource.com/platform/frameworks/libs/systemui
tag=android-17.0.0_r1
path=iconloaderlib/src
path=iconloaderlib/res
path=msdllib/src
ref=animationlib/src/com/android/app/animation/Interpolators.java compat/launcher3-internal/com/android/app/animation/Interpolators.java
ref=usertypelib/src/com/android/users/UserType.kt compat/launcher3/src/main/java/com/android/users/UserType.kt
ref=iconloaderlib/Android.bp third_party/systemui/iconloaderlib/build.gradle.kts
ref=iconloaderlib/build.gradle.kts third_party/systemui/iconloaderlib/build.gradle.kts
ref=msdllib/Android.bp third_party/systemui/msdllib/build.gradle.kts
```

## 取り込んでいないもの

| 上流のパス | 理由 |
|---|---|
| `iconloaderlib/src_full_lib/` | システム API（`system_current`）が必要なため。Launcher3 本体も `iconloader_base` 相当だけで足りる |
| `iconloaderlib/build.gradle.kts`、`iconloaderlib/AndroidManifest.xml`、`msdllib/Android.bp`、`msdllib/AndroidManifest.xml` | 上流のものは AOSP のツリー前提のため、自前のもの（`build.gradle.kts`、`TextLauncherManifest.xml`）を置く。`msdllib` の `VIBRATE` 権限は残す |
| `msdllib/tests/` | テストは取り込まない |
| `animationlib/` | `Interpolators.java` が非公開 API（`android.util.MathUtils`）を使うため。使う定数だけを `compat/launcher3-internal` の `com.android.app.animation.Interpolators` に同じ値で置く |
| `usertypelib/` | `@Parcelize` が AGP 9 の組み込み Kotlin で効かないため。`compat/launcher3` の `com.android.users.UserType` で代用する（Parcelable を外しただけ） |

## 手を入れたファイル

なし。
