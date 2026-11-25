# TextLauncher

常にキーボードが表示されている、テキスト入力中心の Android ランチャー。アプリ名は Caret Launcher（applicationId は `com.malt03.caretlauncher`）。リポジトリとソースのパッケージ（`com.malt03.textlauncher`）は TextLauncher の名前のまま。

## ドキュメント

- 仕様: [docs/spec.md](docs/spec.md)
- Launcher3 からの取り込み方針: [docs/launcher3-porting.md](docs/launcher3-porting.md)
- タスク: `tk` で管理（`.tickets/`）

## 進め方

- 変更のキリが良いタイミングで、許可を得ずに main へコミットしてプッシュしてよい
- コミットは GPG 署名で行う（`--no-gpg-sign` を付けない）

## ビルドと動作確認

- JDK は Android Studio 同梱のものを使う: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`
- リリース版（Play 用の App Bundle、`--apk` で APK も）は `scripts/build-release.sh` で作る。アップロード鍵の場所・別名・パスワードを聞かれる（パスワードはファイルに書かない）。鍵なしでリリース版の APK や App Bundle を作ろうとするとエラーになる。R8 などの確認だけなら `:app:minifyReleaseWithR8` などのタスクを使う
- 動作確認はエミュレータ（AVD `Small_Phone`）で行う
  - この AVD はハードウェアキーボードが有効なので、画面のキーボードを出すには `adb shell settings put secure show_ime_with_hard_keyboard 1`
  - 既定のホームにする: `adb shell cmd package set-home-activity com.malt03.caretlauncher/com.malt03.textlauncher.ui.LauncherActivity`（`connectedDebugAndroidTest` の後はアンインストールされて外れるので、やり直す）
