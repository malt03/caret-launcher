# CaretLauncher

常にキーボードが表示されている、テキスト入力中心の Android ランチャー。アプリ名は Caret Launcher（applicationId とソースのパッケージは `com.malt03.caretlauncher`）。

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
- Google Play の掲載情報（言語ごとのテキスト、アイコン、フィーチャー グラフィック、スマートフォンと10インチタブレットのスクリーンショット）は `app/src/main/play` に置き、`scripts/publish-listing.sh` で Gradle Play Publisher から登録する。サービス アカウントの鍵（JSON）の場所を聞かれる（鍵はリポジトリに入れない）。折りたたみ端末のスクリーンショット（`store/screenshots/fold`）は API で登録できないので Play Console で手で登録する。画像の元（`store/` の SVG、HTML、壁紙のスクリプト）は `store/` に置く
- 掲載言語ごとのスクリーンショット（スマートフォンとタブレット）は `scripts/store-screenshots.py --serial <エミュレータ> --device phone|tablet [--langs de-DE,...]` で撮る。端末の言語を切り替え、写真アプリの検索文字列を `store/photo-keywords/<言語>.json` にして6画面を撮り、`app/src/main/play/listings/<言語>/graphics/` に置く（en-US 以外は git に入れない）。終わると端末の言語・検索文字列・アイコンを元に戻す
  - 事前に端末で一度だけ: デバッグ版のランチャーを入れて既定のホームにする、`:store-tools:installDebug`（検索欄に ASCII 以外の文字を入れる補助 APK）、ホームの配置と時計ウィジェット、共有ボタンに Gemini と Chrome、ストア用の壁紙、テーマアイコンはオフ、Gboard の「最近コピーしたテキストを候補に表示」をオフ、日本語など初回に Gboard がレイアウトを聞く言語はそれに答えておく
- 動作確認はエミュレータ（AVD `Small_Phone`）で行う
  - この AVD はハードウェアキーボードが有効なので、画面のキーボードを出すには `adb shell settings put secure show_ime_with_hard_keyboard 1`
  - 既定のホームにする: `adb shell cmd package set-home-activity com.malt03.caretlauncher/.ui.LauncherActivity`（`connectedDebugAndroidTest` の後はアンインストールされて外れるので、やり直す）
