---
id: Tex-3k5k
status: open
deps: []
links: [Tex-6feu]
created: 2026-11-22T06:42:33Z
type: bug
priority: 3
assignee: Koji Murata
parent: Tex-ogmk
---
# 折りたたみ端末で畳む・開くとホームが詰め直される

GridSpec は今のディスプレイの大きさしか見えないので、折りたたみ端末で外側と内側のディスプレイを切り替えるとグリッドが変わり、GridSizeMigration で詰め直し（入りきらないものは削除）になる。Launcher3 は SystemWindowManagerProxy.estimateInternalDisplayBounds で内部ディスプレイをすべて見て、TYPE_MULTI_DISPLAY の1つのグリッドにする（システムの API）。アプリから両方のディスプレイの大きさを知る方法があるか調べ、なければグリッドごとに配置を持つ（Launcher3 のグリッドごとの DB）ことを検討する。実機で確かめていない。


## Notes

**2026-11-22T11:07:20Z**

調査結果（エミュレータ Pixel 9 Pro Fold, Android 37）:
- 今の挙動: 開くと GridSpec 5x8、畳むと 4x10 になり、畳む・開くたびに GridSizeMigration が詰め直す（GridSpec.from の出力からの推定。ホームでの実際の動きは確かめていない）。
- Launcher3（SystemWindowManagerProxy）が使う WindowManager.getPossibleMaximumWindowMetrics は hidden API で、アプリからはリフレクションでも NoSuchMethodException になり使えない。システムは recents provider にしか答えない（Lawnchair PR #7402 の記述）。
- 公開 API の DisplayManager.getDisplays(DISPLAY_CATEGORY_BUILT_IN_DISPLAYS)（API 36.1 = Android 16 QPR2 から）は、使っていない側のパネルも state=OFF で返す。そのパネルの Display.getRealMetrics で大きさと密度（1080x2424@390 / 2076x2152@390）、getCutout でカットアウト（上 152 / 136）が取れる。createDisplayContext(...).maximumWindowMetrics は今のパネルの大きさを返すので使えない。
- getDisplays() や 36.1 より前の OS では今のパネルしか見えない。Lawnchair（PR #7402, 2026-10）は FEATURE_SENSOR_HINGE_ANGLE がある端末で、見たことのあるパネルの状態を device-protected の prefs に覚え（最大4件、縦横比が同じなら置き換え）、それも supportedBounds に加えている。この場合、初めて開いた（畳んだ）時に一度だけ組み直しになる。
- 両パネルが分かった後の Launcher3 の形: TYPE_MULTI_DISPLAY。開いた状態は2枚並び（1枚は半分の幅）で、グリッドは畳んだ状態と開いた状態の半分の幅の小さい方。TextLauncher は1ページなので2枚並びはそのままでは使えない。
- 他のランチャー: One UI と Smart Launcher（"Independent setups"）は画面ごとに別の配置を持てる。Niagara は広い画面で2列になるリスト。Nova と Kvaesitso は画面ごとの配置を持たないとの記事がある（未確認）。
