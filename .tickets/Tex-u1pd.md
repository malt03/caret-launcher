---
id: Tex-u1pd
status: open
deps: []
links: []
created: 2026-11-23T02:39:38Z
type: bug
priority: 1
assignee: Koji Murata
parent: Tex-ogmk
tags: [build]
---
# リリースビルドが起動時に IllegalAccessError で落ちる

assembleRelease の APK（デバッグ鍵で署名）が起動直後に落ちる。androidx.startup の InitializationProvider → ProcessLifecycleInitializer で java.lang.IllegalAccessError: Class kotlin.collections.j extended by class i6 is inaccessible。Compose を入れる前の b6caea4 でも同じなので、以前からある。app/build.gradle.kts の release の optimization { packageScope = androidx.**, kotlin.**, kotlinx.** }（AGP 9 の一部だけ最適化する設定）で、最適化の範囲内と範囲外のクラスの間でパッケージプライベートの継承が壊れているように見える。Pixel9ProFold エミュレータ（Android 17）で確認。

