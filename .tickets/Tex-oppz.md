---
id: Tex-oppz
status: closed
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


## Notes

**2026-11-23T16:04:19Z**

res/xml/device_profiles.xml を path= でそのまま取り込み、GridSpec が上流の deviceprofile/parser/GridOption.kt・DisplayOption.kt の読み方（parseAllDefined、getPredefinedDisplayOptions、isEnabled、filterByFlag）を移して R.xml.device_profiles を TypedArray で読むようにした。手で写した表は消した。属性 GridDisplayOption・ProfileDisplayOption は attrs.xml から launcher3.xml に写し、読まない参照先（default_workspace_*、paddings_*、spec_*、taskbar_button_margin_*、FolderStyleDefault）は CaretLauncher: の印を付けて空の値で宣言した。InvariantDeviceProfile.initGrid と同じく、DeviceGridState の列数（GridSizeMigration.getColumns が migration_src_workspace_size を読む）の候補に先に絞り（filterByColumnCount）、なければ全候補から選ぶ。getPredefinedDeviceProfiles は GRID_NAME が空なので canBeDefault の候補。Flags.oneGridRotationHandling（リリースでは無効）を互換の Flags に足した。端末のグリッドは今までと同じ候補から選ばれるので、スマホでの列数・アイコンの大きさは変わらないはず
