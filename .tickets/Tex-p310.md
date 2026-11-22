---
id: Tex-p310
status: open
deps: []
links: []
created: 2026-11-22T00:59:03Z
type: chore
priority: 3
assignee: Koji Murata
parent: Tex-vs8t
---
# ライセンスヘッダーと取り込みの記録の食い違い

- compat/launcher3/.../com/android/users/UserType.kt は上流 usertypelib の UserType.kt（Copyright 2026 AOSP）から Parcelable を除いたものだがヘッダーがない。docs/launcher3-porting.md は「上流にソースがない」、third_party/systemui/UPSTREAM.md は「上流のファイルから Parcelable を除いた」と食い違う
- testing/shared/TestProtocol.java の年が 2019（上流は 2018）
- docs/launcher3-porting.md のヘッダーのない上流ファイルの例に shortcuts/ShortcutKey.java（実際は third_party の逐語コピー）を挙げ、accessibility/LauncherAccessibilityDelegate.java と util/PackageUserKey.java が漏れている
- res-textlauncher/values/launcher3.xml の popup_container の id（上流は layout/longpress_options_menu.xml の @+id）に印がない
- UPSTREAM.md にも取り込み方針にも名前のない互換クラス: AppPairIconDrawingParams、AppPairIconGraphic、LauncherComponentProvider、FolderAdaptiveIcon、FolderNameSuggestionLoader、InstanceId、InstanceIdSequence
- UPSTREAM.md の AllAppsRecyclerView の説明（仕事用プロファイル・プライベートスペースを削った）が、実際（記録、cursor-driven、文字の一覧）と違う
- color/、color-night/ の shade_panel_{bg,fg}_color.xml が UPSTREAM.md にない
- provider/CustomizationProvider.kt は BIND_WALLPAPER だけを受け付ける。上流の LauncherCustomizationProvider.enforceCallerPermission は <applicationId>.permission.GRID_CONTROL と署名の許可リストも受け付ける（移行までの暫定）。違いを記録するか合わせる

