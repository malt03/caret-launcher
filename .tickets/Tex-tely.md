---
id: Tex-tely
status: open
deps: []
links: []
created: 2026-11-22T00:58:38Z
type: bug
priority: 3
assignee: Koji Murata
parent: Tex-ogmk
---
# アンインストールの落とし先が端末管理の制限を見ていない

DropTargetBar の UninstallDropTarget.supportsDrop はキャッシュの FLAG_SYSTEM だけを見る。

- Launcher3: SecondaryDropTarget.getButtonType は DISALLOW_APPS_CONTROL / DISALLOW_UNINSTALL_APPS（ユーザーごとにキャッシュ）と FLAG_SYSTEM_MASK も見て、getUninstallTarget / resolveActivity を通す。DeleteDropTarget.canRemove は item.id != NO_ID（こちらはドラッグ元で判定しているが、上流の判定がそのまま使える）

直し方: 上流の判定を移す。管理された端末で出てはいけないアンインストールのボタンが出なくなる

