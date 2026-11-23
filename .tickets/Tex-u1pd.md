---
id: Tex-u1pd
status: closed
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


## 結果

R8 が、最適化の範囲（packageScope）の kotlin.collections.CollectionsKt__MutableCollectionsJVMKt をデフォルトパッケージに移していた（親の CollectionsKt__IterablesKt はパッケージプライベートのまま kotlin.collections に残る）。app/src/main/keepRules/package-names.keep で -keeppackagenames を指定し、どのクラスもパッケージを移さないようにした。マッピングでデフォルトパッケージのクラスが 0 になり、リリースビルド（デバッグ鍵で署名）が Pixel9ProFold エミュレータで起動し、ウィジェットのリサイズ枠も出ることを確認した。
