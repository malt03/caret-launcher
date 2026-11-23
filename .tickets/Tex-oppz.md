---
id: Tex-oppz
status: open
deps: []
links: []
created: 2026-11-23T09:06:34Z
type: bug
priority: 2
assignee: Koji Murata
parent: Tex-ogmk
tags: [home, launcher3]
---
# グリッドの選び方に列数の維持（filterByColumnCount）がなく、表も手で写している

上流の InvariantDeviceProfile.initGrid（android-17.0.0_r1 の 332-341 行）は、DeviceGridState に保存した列数（migration_src_workspace_size）の表示の候補に先に絞ってから invDistWeightedInterpolate する。表示サイズやナビゲーションの変更でも、できれば列数を保つ。GridSpec.kt（85-89 行）は全候補から選ぶので、上流なら起きない列数の変化と移し替えが起きうる。アプリは同じ設定を書いている（GridSizeMigration.kt 23・46 行）ので、絞り込みはできる。docs/launcher3-porting.md の GRID_NAME の除外の説明とは別の話。

また、表示の候補（GridSpec.kt 182-199 行）は res/xml/device_profiles.xml の数字を手で写していて、上流の変更が見えない。

直し方: 列数で絞り込む。device_profiles.xml を path= で取り込んで読む

