---
id: Tex-fco2
status: open
deps: []
links: []
created: 2026-11-22T00:59:03Z
type: task
priority: 3
assignee: Koji Murata
parent: Tex-vs8t
---
# 全アプリ一覧のハプティックと補間の違いを Launcher3 に合わせるか印を付ける

- 閉じるときにも SWIPE_THRESHOLD_INDICATOR を鳴らす（上流の AllAppsTransitionController.setStateWithAnimation は NORMAL → ALL_APPS だけ）。docs/spec.md 6.2 には書いてあるが、コードのコメントは上流を引いていて TextLauncher: の印がない。鳴らす場所も LauncherActivity で、上流は AllAppsTransitionController
- ジェスチャーでない開き方（openAllApps、Tex-skt8 で使う）で TAP_HIGH_EMPHASIS を鳴らしていない
- ジェスチャーでない開閉（戻るでの閉じ、メニューからの開き）で、奥行きと縮小を、すでに EMPHASIZED / ACCELERATE をかけた開き具合から出している（DECELERATED_EASE(EMPHASIZED(t))）。上流は縦の位置だけに EMPHASIZED / ACCELERATE をかけ（applyNormalToAllAppsAnimConfig / applyAllAppsToNormalConfig）、ANIM_DEPTH と ANIM_WORKSPACE_SCALE は素の進み具合。ジェスチャーとフリックは上流どおり

直し方: 線形の進み具合を動かし、性質ごとに補間をかける。ハプティックは AllAppsTransitionController へ移し、閉じるときの分に印を付ける（Tex-rpfa と合わせる）

