---
id: Tex-heiy
status: open
deps: []
links: []
created: 2026-11-24T13:35:59Z
type: task
priority: 3
assignee: Koji Murata
parent: Tex-op7u
tags: [launcher3, upstream, home]
---
# ⑥ グリッドの決め方を上流の InvariantDeviceProfile と設定の読み込みのコピーにする

ui/GridSpec.kt（387 行）は InvariantDeviceProfile の initGrid・getPredefinedDeviceProfiles・filterByColumnCount・invDistWeightedInterpolate と、deviceprofile/parser の GridOption・DisplayOption の私的な写しを持っている（約 65% が上流の処理）。

- res/xml/device_profiles.xml はもう res-textlauncher/xml にあり、読み込める
- deviceprofile/parser の GridOption.kt・DisplayOption.kt・DeviceTypedMap.kt は、LauncherDisplayInfo.kt（264 行）を derived にすれば path= にできる（GridSpec.kt:78-95 が isLargeScreen・supportedBounds・deviceType をもう書き直している）
- 互換の InvariantDeviceProfile（81 行、上流 868 行）を derived にする: initGrid・initGridForDisplayOption・getPredefinedDeviceProfiles・filterByColumnCount・invDistWeightedInterpolate は上流のまま、Dagger・LauncherPrefs・GRID_NAME は印で外す
- GridSpec にはこのランチャーだけの部分が残る: セルの最小の高さから行数を決める（ラベルなし）、余白、isTwoPanels
- DeviceProfile は互換のまま（セルの大きさはキーボードを下ろしたホームの高さから決まり、上流の窓の大きさからの計算と合わない）

リスク: 列数・行数・アイコンの大きさが今と変わらないか（スマホ・折りたたみ・タブレット）。規模: 中。

