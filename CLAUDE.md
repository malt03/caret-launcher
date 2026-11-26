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
- Google Play の掲載情報（言語ごとのテキスト、アイコン、フィーチャー グラフィック、スマートフォンと10インチタブレットのスクリーンショット）は `app/src/main/play` に置き、`scripts/publish-listing.sh` で Gradle Play Publisher から登録する。サービス アカウントの鍵（JSON）の場所を聞かれる（鍵はリポジトリに入れない）。折りたたみ端末のスクリーンショット（`store/screenshots/fold`）は API で登録できない（AppImageType に種類がない）ので Play Console で手で登録する。言語別に載せるのは en-US、ja-JP、ko-KR、pt-BR、es-419 の5言語だけ（ほかの言語は既定の en-US に任せる）。画像の元（`store/` の SVG、HTML、壁紙のスクリプト）は `store/` に置く
- 掲載言語ごとのスクリーンショットは `scripts/store-screenshots.py --serial <エミュレータ> --device phone|tablet|fold [--langs de-DE,...] [--screens home,search,...]` で撮る（`--screens` で一部の画面だけ撮り直せる）。検索欄のある画面は、カーソルの点滅とキーボードが出ていることを確かめて、カーソルが写ったコマを保存する（どちらかがなければ失敗する）。ステータスバーは、撮る間だけ12時間表示にして通知アイコンを隠し、全言語で「9:00」にそろえる（終わると戻す）。端末の言語を切り替え、写真アプリの検索文字列を `store/photo-keywords/<言語>.json` にして撮る。終わると端末の言語・検索文字列・アイコン・ホームの配置を元に戻す
  - スマートフォンとタブレット: 6画面を `app/src/main/play/listings/<言語>/graphics/` に置く（en-US 以外は git に入れない）
  - 折りたたみ（AVD `Pixel_10_Pro_Fold`）: 開いた画面の5画面（1:1 に切る）、横向きで 4:3 に広げたホーム、閉じて 10:16 に広げたホームの7枚を `store/screenshots/fold/<言語>/` に置く（en-US は `store/screenshots/fold/` で、これだけ git に入れる）。閉じた画面は列数が変わって配置が詰め直されるので、スクリプトの `folded_cells` で置き直す（ホームの配置を変えたらここも直す）
  - 折りたたみの Gboard の日本語の入力レイアウトは、タブレットと同じ QWERTY にしてある
  - 事前に端末で一度だけ: デバッグ版のランチャーを入れて既定のホームにする、`:store-tools:installDebug`（検索欄に ASCII 以外の文字を入れる補助 APK）、ホームの配置と時計ウィジェット、共有ボタンに Gemini と Chrome、ストア用の壁紙、テーマアイコンはオフ、Gboard の「最近コピーしたテキストを候補に表示」をオフ（Mac のクリップボードが候補に写る）、日本語など初回に Gboard がレイアウトを聞く言語はそれに答えておく
- 動作確認はエミュレータ（AVD `Small_Phone`）で行う
  - この AVD はハードウェアキーボードが有効なので、画面のキーボードを出すには `adb shell settings put secure show_ime_with_hard_keyboard 1`
  - 既定のホームにする: `adb shell cmd package set-home-activity com.malt03.caretlauncher/.ui.LauncherActivity`（`connectedDebugAndroidTest` の後はアンインストールされて外れるので、やり直す）
