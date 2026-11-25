---
id: Tex-5b6c
status: open
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

