---
id: Tex-y7fe
status: closed
deps: []
links: []
created: 2026-11-25T04:15:41Z
type: task
priority: 2
assignee: Koji Murata
parent: Tex-mdus
---
# 「壁紙とスタイル」のホーム画面プレビュー（get_preview）


「壁紙とスタイル」のホーム画面のプレビューに何も出ない。CustomizationProvider.call が get_preview に null を返しているため。

Launcher3（android-17.0.0_r1）の GridCustomizationsProxy.getPreview と preview/（PreviewSurfaceRenderer、LauncherPreviewRenderer、PreviewLifecycleObserver）にならい、SurfaceControlViewHost にホーム画面を描いて SurfacePackage と Messenger を返す。グリッド・形状・テーマアイコンの選び直しの通知で描き直す。

- 描くもの：ホームのアイコン・フォルダ・ウィジェット、検索欄（壁紙は「壁紙とスタイル」側が描く。キーボードは IME なので描けない）
- 上流は dagger のプレビュー用 Context でモデルを読み直す。CaretLauncher には dagger がないので、derived= で削る

## Notes

**2026-11-25T04:32:12Z**

2026-11-25: 実装した。エミュレータ（Small_Phone、Google の「壁紙とスタイル」）で、ホーム画面のプレビューにホームのアイコン・フォルダ・検索欄（共有ボタン付き）が出ること、スタイル（テーマアイコン）とシェイプを選ぶとその場で描き直されることを確認。上流と同じく選んだ時点で設定が変わるので、✕ で閉じても元に戻らない（上流の PreviewLifecycleObserver → handleUpdate と同じ）。未確認：ウィジェットのプレビュー、2パネル（折りたたみの内側）、実機の Pixel
