---
id: Tex-4o3v
status: closed
deps: []
links: []
created: 2026-11-21T16:39:28Z
type: feature
priority: 2
assignee: Koji Murata
parent: Tex-mdus
tags: [icons]
---
# 通知ドット

通知があるアプリのアイコンに、Launcher3 と同じ通知ドットを付ける。ホーム、フォルダ（フォルダのアイコンにもドット）、全アプリ一覧、検索結果のアイコンが対象。

長押しメニューの通知の行（Launcher3 の NotificationContainer）は入れない。Pixel Launcher にもないため（2026-11-22 決定）。

## Launcher3 からの移植（調べる点）

- 通知の受け取り: notification/NotificationListener（NotificationListenerService）。通知の追加・削除・ランキングの変化を受け取る
- ドットの管理: popup/PopupDataProvider の DotInfo（アプリごと、ショートカットごとの通知キー）、updateNotificationDots
- 描画: BubbleTextView の applyDotState・DotRenderer（icons/DotRenderer は iconloaderlib にある）、FolderIcon のドット（中身のどれかに通知があるとき）、ドットの色（notificationDotColor、アイコンの色から作る）
- 設定: Launcher3 はシステムの「アプリアイコンの通知ドット」（Settings.Secure の notification_badging）がオフならドットを出さない

## 決めること

- 通知へのアクセスの許可: Pixel Launcher はシステムアプリで最初から許可されているが、TextLauncher はユーザーが OS の「通知へのアクセス」で許可する必要がある。設定画面は置かない方針なので、許可を求める入口をどこに置くか（空いている場所の長押しメニュー Tex-skt8 に足す、など）。許可されていない間は、ドットを出さないだけにする

仕様: docs/spec.md に節を足す

## Acceptance Criteria

- [ ] 通知へのアクセスを許可すると、通知があるアプリのアイコン（ホーム、フォルダの中、全アプリ一覧、検索結果）にドットが付く
- [ ] フォルダは、中のどれかに通知があるとフォルダのアイコンにもドットが付く
- [ ] 通知を消すとドットも消える
- [ ] システムの通知ドットの設定をオフにすると、ドットが出ない
- [ ] 許可を求める入口がある


## Notes

**2026-11-23T08:20:24Z**

Launcher3 の通知ドットを移した（NotificationListener・NotificationRepository・DotInfo などは上流のまま、PopupDataProvider・BubbleTextView・FolderIcon に上流のドットのコードを移した）。許可の入口は空いている場所の長押しメニューの「通知ドット」（許可がない間だけ。2026-11-23 決定）で、Launcher3 の設定画面と同じダイアログから OS の通知へのアクセスの画面を開く。エミュレータで、メニューの項目とダイアログ、許可後にホーム・全アプリ一覧・検索結果・フォルダにドットが付くこと、通知を消すとすぐ消えること、許可後に項目が消えることを確かめた。OS の通知ドットの設定をオフにしたときの動き（listener が外れてドットが消え、オンで戻る）は試していない。
