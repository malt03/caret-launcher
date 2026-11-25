---
id: Tex-h2no
status: open
deps: [Tex-qcnm]
links: []
created: 2026-11-25T05:10:33Z
type: task
priority: 1
assignee: Koji Murata
parent: Tex-tdmk
---
# ライセンスの表示と自前のコードのライセンス

取り込んだ AOSP（Launcher3、frameworks/libs/systemui、IntentResolver）と依存ライブラリ（AndroidX、Dagger、Guava、Kotlin など）はどれも Apache License 2.0（GPL・LGPL はない。2026-11-25 に確認）。APK を配るときは NOTICE を一緒に渡す必要があるが、今は third_party/NOTICE がリポジトリにあるだけで APK にもアプリの画面にも入っていない。
- NOTICE と LICENSE（依存ライブラリの分も）を APK に入れ、アプリから見られるようにする。設定画面は置かない方針（docs/spec.md 8）なので、空き領域の長押しメニューなどの入口を決める。版（versionName）も一緒に出す
- TextLauncher 自身のコードのライセンスを決める（ソースを公開するなら Apache 2.0 にそろえるのが簡単。非公開で配ることもできる）。リポジトリの最上位に LICENSE がない

