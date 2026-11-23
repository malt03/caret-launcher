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


## Notes

**2026-11-23T09:06:57Z**

2026-11-23: res-textlauncher/values の色 18 個が、上流の基本の values の値ではなく values-v31 の値を持っている（ヘッダーの「values-v31 for the accent colors」だけで、名前ごとの印はない。minSdk 37 では使われない）。AppTheme* 6つは親を外している（印なし）。materialColor* と max_depth_blur_radius_enhanced は quickstep/res と systemui dynamiccolors から来ていることをコメントでしか書いていない。Interpolators（systemui の animationlib）が third_party/systemui/UPSTREAM.md にない。PopupData・PopupCategory（PopupContainer.kt 318-334 行）が PopupDataMapper.kt から来ていることが記録にない

**2026-11-23T12:17:52Z**

2026-11-23 整理: 済んだもの — values の色と AppTheme*、popup_container の id、shade_panel_* の色は TextLauncher: の印を付け、check-upstream.py が values_root= で突き合わせる（Tex-hpdq）。Interpolators は systemui の ref=、名前のなかった互換クラス（AppPairIcon*、FolderAdaptiveIcon、FolderNameSuggestionLoader、InstanceId*、LauncherComponentProvider）は UPSTREAM.md の ref=/derived= に載った。PopupData・PopupCategory は PopupContainer.kt の最後に出どころの印付きでまとめた（Tex-n0ut）。残り: UserType.kt のヘッダー、TestProtocol.java の年、docs の「上流にヘッダーがないもの」の例（ShortcutKey.java は逐語コピー、LauncherAccessibilityDelegate.java が漏れている）、UPSTREAM.md の AllAppsRecyclerView の説明、CustomizationProvider の許可
