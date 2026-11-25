---
id: Tex-qcnm
status: closed
deps: []
links: []
created: 2026-11-25T05:10:33Z
type: task
priority: 2
assignee: Koji Murata
parent: Tex-tdmk
---
# 版の付け方を決める

versionCode 1、versionName "1.0" のまま。
- versionName の形（例：1.0.0）と、versionCode の上げ方（手で上げる、git のコミット数やタグから作る など）を決める
- ライセンスの画面などで版を出すなら、そこからも読む


## Notes

**2026-11-25T05:32:13Z**

2026-11-25: versionName は major.minor.patch（最初は 1.0.0）。versionCode は versionName から major * 1,000,000 + minor * 1,000 + patch で計算する（1.0.0 は 1000000）。app/build.gradle.kts の appVersionName だけを書き換える。minor と patch は 1,000 未満
