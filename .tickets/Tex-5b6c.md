---
id: Tex-5b6c
status: closed
deps: []
links: []
created: 2026-11-25T05:10:33Z
type: task
priority: 1
assignee: Koji Murata
parent: Tex-tdmk
---
# リリースの署名

release のビルドに signingConfig がない。
- アップロード用の鍵を作り、リポジトリの外に置く（パスワードも含め、コミットしない）
- app/build.gradle.kts の release で、鍵の場所とパスワードを local.properties か環境変数から読む
- Play App Signing を使うかを決める（使うなら手元の鍵はアップロード鍵）
- 署名を変えると、GRID_CONTROL の knownSigner など署名に結びつくものはないか確かめる


## Notes

**2026-11-25T06:54:57Z**

2026-11-25: Play App Signing のアップロード鍵で署名する。鍵と別名・パスワードは scripts/build-release.sh が毎回聞き、そのビルドの環境変数（CARET_SIGNING_*）だけで渡す（Gradle のデーモンと設定キャッシュは使わない）。鍵なしで packageRelease・signReleaseBundle・packageReleaseUniversalApk を動かすとエラー。使い捨ての鍵で App Bundle と APK の署名を確認した。本物の鍵（ユーザーの upload-keystore.jks）では試していない
