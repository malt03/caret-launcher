---
id: Tex-htxz
status: closed
deps: [Tex-cklf]
links: []
created: 2026-11-21T09:18:13Z
type: task
priority: 2
assignee: Koji Murata
parent: Tex-neft
tags: [refactor]
---
# BubbleTextView と FolderIcon のメソッドを互換クラスへ戻す

HomeItemViews.kt に、上流の BubbleTextView / FolderIcon にあるメソッドの書き直しがある。
- ReorderableState と Reorderable の実装（84-93、131-135、198-202 行目）: BubbleTextView.java:1529-1544、FolderIcon.java:768-783 の getTranslateDelegate / setReorderBounceScale
- アイコンを縦に中央へ置く onMeasure（137-142、204-209 行目）: BubbleTextView.onMeasure / FolderIcon.onMeasure の getIconCenterVertically
- AppIconView の iconDrawable / isIconVisible / onDraw（108-162 行目）: BubbleTextView の mIcon / mIsIconVisible / setIcon / setIconVisible と重なっている。どちらか 1 つにする
BubbleTextView 互換クラスは Reorderable を実装していないので、上流どおり実装させる。

## Acceptance Criteria

- [ ] HomeItemViews.kt に上流メソッドの書き直しが残っていない
- [ ] 並び替えの揺れ・押しのけ、長押し中のアイコン非表示、押したときの縮みが今と同じ


## Notes

**2026-11-21T09:43:33Z**

HomeItemViews.kt の上流メソッドの書き直しをなくした。BubbleTextView と FolderIcon の互換クラスに Reorderable（getTranslateDelegate / setReorderBounceScale）と縦の中央寄せの onMeasure を上流から移し、AppIconView は BubbleTextView の setIcon / setIconVisible / 描画をそのまま使う（独自の iconDrawable の描画と状態の受け渡しはなくした）。ラベルなしはアプリ側で getCellSpecMaxTextLineCount = 0 と WorkspaceProfile の iconCenterVertically = true として渡す。HomeItemView インターフェースは不要になったので消した。エミュレータでホーム・フォルダ・検索結果の表示、長押しで元のアイコンが消えること、押しのけの並び替えを確認
