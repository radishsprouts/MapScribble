# Xaero's Minimap の内部構造 調査レポート（HUD ミニマップ枠内への重ね描き）

- 作成: 2026-10-26
- 作業区分: コミュニティ運営（community）
- 目的: `MinimapHudRenderer` が Xaero's Minimap の HUD 枠内に手書きメモを正しく重ねられるようにするため、枠の位置・サイズ・形・ズーム・回転・中心座標・表示状態をリフレクションで取得する経路を特定する
- この文書は方式を決めない。採否は Dev-Architect / Dev-Director の判断による

## 0. 結論の要約

必要な 6 項目すべてについて、**難読化されていない公開クラスから取得する経路を現物（jar のバイトコード）で確認できた。** 推測に頼った項目は無い。

要点は 3 つ。

1. **枠の位置と大きさは `xaero.hud.minimap.BuiltInHudModules.MINIMAP.getPushState()`（`PushboxHandler$State`。`public int x, y, w, h`）から取れる。** これは Xaero が毎フレーム HUD の押しのけ処理まで済ませた**結果**であり、設定から自前で再計算する必要がない。クラス名・フィールド名は MC 1.20.1 系と 26.x 系で同一。
2. **座標の単位は GUI 座標（`Window.getGuiScaledWidth()` 基準）である。** 物理ピクセルではない。現在の `MinimapHudRenderer` は `getScreenWidth()`（物理ピクセル）を使っており、ここが二重に誤っている。
3. **1 ブロックあたりの大きさは `枠の幅 × zoom ÷ (getMinimapSize() + 36)`（GUI ピクセル）である。** Xaero の `zoom` 設定値は「1 ブロック = zoom/2 GUI ピクセル」に相当する（UI 倍率が MC と同じ場合）。

加えて、**現在の実装には設計上の欠落が 1 つある**（第 6 節）。`Projection`（`src/main/java/io/github/radishsprouts/mapscribble/geom/Projection.java`）は平行移動と拡大だけを表す record で**回転を表現できない**ため、回転ミニマップ（北固定 OFF）には `Projection` だけでは対応できない。

---

## 1. 対象バージョンと、前提にする Xaero の系列

### MapScribble 側（現物確認）

`S:\Projects\minecraft\MOD\MapScribble\settings.gradle.kts` の `stonecutter { create(rootProject) { ... } }` より:

| ローダー | Minecraft | ビルドスクリプト | マッピング |
|---|---|---|---|
| Fabric | 26.1.2 / 26.2 / 26.3 | `build.fabric-unobf.gradle.kts` | 非難読化（26.1+） |
| Fabric | 1.20.1 / 1.21.1 / 1.21.4 / 1.21.11 | `build.fabric-remap.gradle.kts` | 難読化・Mojang 公式マッピング |
| NeoForge | 1.21.1 / 1.21.4 / 1.21.11 / 26.1.2 / 26.2 / 26.3 | `build.neoforge.gradle.kts` | — |
| Forge | 1.20.1 | `build.forge.gradle.kts` | — |

`stonecutter.gradle.kts` の `stonecutter active "26.1.2-fabric"`、`settings.gradle.kts` の `vcsVersion = "26.1.2-fabric"` より、既定の作業対象は **26.1.2-fabric**。

### Xaero's Minimap 側（現物確認）

公式 Maven（`https://chocolateminecraft.com/maven`）の `xaero/minimap/` 配下に、MapScribble が対応する MC バージョンすべての artifact が存在する。2026-10-26 時点でインデックスに出ていた `xaerominimap-common-*` / `xaerominimap-fabric-*` のうち該当分:

```
xaerominimap-common-1.20.1/   xaerominimap-common-1.21.1/   xaerominimap-common-1.21.4/
xaerominimap-common-1.21.11/  xaerominimap-common-26.1.2/   xaerominimap-common-26.2/
xaerominimap-common-26.3/
```

**前提にすべき系列は、MOD 版 26.x（`xaerominimap-*-<MCバージョン>:26.x.y`）である。** Xaero は MOD 版を MC バージョンと独立に進めており、MC 1.20.1 向けにも 26.6.0 が出ている（下の「調べた jar」参照）。すなわち「MC が古い＝Xaero も古い内部構造」ではない。

### 本調査で実際に読んだ jar（現物）

| 座標 | 用途 | サイズ |
|---|---|---|
| `xaero.minimap:xaerominimap-common-26.1.2:26.6.2` | 26.x 系の代表 | 1,630,800 バイト |
| `xaero.minimap:xaerominimap-common-1.20.1:26.6.0` | 1.20.1 系の代表 | 1,666,769 バイト |

取得元 URL（2026-10-26 閲覧・一次情報）:

- `https://chocolateminecraft.com/maven/xaero/minimap/xaerominimap-common-26.1.2/26.6.2/xaerominimap-common-26.1.2-26.6.2.jar`
- `https://chocolateminecraft.com/maven/xaero/minimap/xaerominimap-common-1.20.1/26.6.0/xaerominimap-common-1.20.1-26.6.0.jar`

解析は `C:\Program Files\Java\jdk-25\bin\javap` の `-p` / `-c` / `-constants` で行った。**Xaero 自身のクラス・フィールド・メソッド名は難読化されていない**（`xaero.hud.minimap.module.MinimapSession` 等がそのまま出る）ため、リフレクションで名前指定して触れる。

### バージョン差（現物確認）

| 項目 | 1.20.1 系（26.6.0） | 26.x 系（26.6.2） |
|---|---|---|
| `xaero.common.XaeroMinimapSession` | 有り（`extends xaero.hud.HudSession`） | 有り（同じ） |
| `XaeroMinimapSession.getCurrentSession()` / `getMinimapProcessor()` | 有り | 有り |
| `xaero.hud.minimap.module.MinimapSession` | 有り（`abstract`） | 有り（`final`） |
| `xaero.hud.module.HudModule` / `ModuleTransform` / `PushboxHandler$State` | 有り（フィールド名同一） | 有り（フィールド名同一） |
| `MinimapProcessor.getRenderState()` → `MinimapPipRenderState` | **無い**（PiP 機構が MC 側に無い） | **有り** |
| `MinimapProcessor.getRenderer()` | 無い。代わりに `getMinimapInterface()` | 有り（`MinimapRenderer` を返す） |
| `MinimapProcessor.onRender(...)` | 第 1 引数に `GuiGraphics` | `GuiGraphics` 引数なし（PiP 経由） |
| `MinimapRenderer.renderMinimap(...)` | `(MinimapSession, GuiGraphics, MinimapProcessor, int, int, int, int, double, int, float, CustomVertexConsumers)` | `(MinimapSession, MinimapProcessor, int, int, int, int, double, float, int, float, XaeroBufferProvider)` |
| `MinimapProfiledConfigOptions` の `SIZE` / `SHAPE` / `NORTH_LOCKED` / `ZOOM` / `FURTHER_ZOOMOUT` / `CAVE_ZOOM` / `ZOOM_ENLARGED` / `FRAME` / `DISPLAY_MINIMAP` | すべて有り | すべて有り |
| `MinimapConfigClientUtils` の `getEffectiveMinimapSize()` / `getEffectiveNorthLocked(int,int)` / `getActualZoom(int)` | すべて有り | すべて有り |
| `GuiUtils.getMinimapScale(ClientConfigManager)` | 有り | 有り |

**差が出るのは PiP（Picture-in-Picture）関連だけで、本調査で必要な 6 項目はすべて両系列共通の経路で取れる**（第 2 節の「推奨経路」列）。

### Forge 1.20.1 についての注意（二次情報）

Xaero の開発者向け案内（`https://skinmc.net/project/xaeros-minimap` の Developers 節、2026-10-26 閲覧。原文の引用）では、Forge・Mojang 公式マッピング・1.21.1 より前の配布について次のように書かれている。

> `implementation "xaero.minimap:xaerominimap-forge-<minecraft version>:<mod version>:dev"//purposely no deobfuscation!`

これは Xaero の jar に含まれる **Minecraft 側の参照が難読化名のまま**であることを指す。`xaero.*` 自身の名前は難読化されないので、リフレクションでフィールド・メソッドを名前で引く本件には影響しない。ただし**戻り値として受け取る Minecraft の型（`Vec3`、`ResourceKey` 等）を扱う側のマッピング**には注意が要る（`build.forge.gradle.kts` 側の話）。これは jar で確認していないため**二次情報**とする。

---

## 2. 取得経路の一覧（1〜6 の回答）

共通の入口（両系列で確認済み）:

```java
// 入口 A: レガシー互換の静的メソッド（1.20.1 系・26.x 系の両方に存在）
Class<?> legacy = Class.forName("xaero.common.XaeroMinimapSession");
Object legacySession = legacy.getMethod("getCurrentSession").invoke(null);          // null なら未初期化
Object processor = legacy.getMethod("getMinimapProcessor").invoke(legacySession);   // xaero.common.minimap.MinimapProcessor

// 入口 B: HUD モジュール（位置・サイズを取るのに必要）
Class<?> modules = Class.forName("xaero.hud.minimap.BuiltInHudModules");
Object minimapModule = modules.getField("MINIMAP").get(null);                       // xaero.hud.module.HudModule
Object session = minimapModule.getClass().getMethod("getCurrentSession").invoke(minimapModule); // MinimapSession
Object pushState = minimapModule.getClass().getMethod("getPushState").invoke(minimapModule);    // PushboxHandler$State
```

| # | 欲しい値 | 推奨経路（両系列共通） | 型 | 単位 | 確度 |
|---|---|---|---|---|---|
| 1a | 枠の左上 | `BuiltInHudModules.MINIMAP` → `HudModule#getPushState()` → `PushboxHandler$State.x` / `.y` | `int` | **GUI 座標**（左上原点） | 現物確認 |
| 1b | 枠の一辺 | `MinimapSession#getWidth(double guiScale)` / `#getHeight(double)` | `int` | GUI 座標のピクセル | 現物確認 |
| 1c | （26.x のみ・別経路） | `MinimapProcessor#getRenderState()` → `MinimapPipRenderState#x0()/y0()/x1()/y1()` | `int` | GUI 座標 | 現物確認 |
| 2 | 形（円/四角） | `HudMod.INSTANCE#getHudConfigs()#getClientConfigManager()#getEffective(MinimapProfiledConfigOptions.SHAPE)` | `Integer` | **0 = Square / 1 = Circle** | 現物確認 |
| 3 | ズーム（補間後の実値） | `MinimapProcessor#getMinimapZoom()` | `double` | 「1 ブロック = zoom/2 GUI px」の係数（第 3 節） | 現物確認 |
| 4a | 回転するか | `MinimapConfigClientUtils#getEffectiveNorthLocked(int halfSize, int shape)` | `boolean` | `true` = 北固定（回転しない） | 現物確認 |
| 4b | 回転角 | `MinimapRenderer#getRenderAngle(boolean northLocked)`、または同じ式を自前で計算 | `double` | **度**。`northLocked ? 90.0 : -90.0 - camera.yRot()` | 現物確認 |
| 5 | 中心のワールド座標 | `EntityUtils.getEntityX/Y/Z(mc.getCameraEntity(), partialTicks)`（= Xaero と同じ） | `double` | ブロック | 現物確認 |
| 6a | 表示中か（設定） | `getEffective(MinimapProfiledConfigOptions.DISPLAY_MINIMAP)` | `Boolean` | — | 現物確認 |
| 6b | 画面・F3 で隠れているか | `MinimapSession#getHideMinimapUnderScreen()` / `#getHideMinimapUnderF3()` | `boolean` | — | 現物確認 |
| 6c | サーバーが禁止・効果で無効か | `MinimapProcessor#getNoMinimapMessageReceived()`、`Misc.hasEffect(player, Effects.NO_MINIMAP)` / `NO_MINIMAP_HARMFUL` | `boolean` | — | 現物確認 |
| 6d | 拡大マップ中か | `MinimapProcessor#isEnlargedMap()` | `boolean` | — | 現物確認 |
| 6e | 洞窟モード表示中か | `MinimapProcessor#isCaveModeDisplayed()` | `boolean` | — | 現物確認 |

補助的に要るもの:

| 欲しい値 | 経路 | 型 |
|---|---|---|
| ミニマップの内部サイズ | `MinimapProcessor#getMinimapSize()` | `int` |
| 設定上の枠の外形幅 | `MinimapSession#getConfiguredWidth()` | `int` |
| ミニマップの UI 倍率 | `GuiUtils#getMinimapScale(ClientConfigManager)` | `float` |
| 設定マネージャ | `HudMod.INSTANCE`（`public static` フィールド）→ `getHudConfigs()` → `getClientConfigManager()` | — |
| 設定の読み出し | `ClientConfigManager#getEffective(ConfigOption)` | `Object`（`Boolean` / `Integer` 等） |

---

## 3. 単位と式の根拠（バイトコードからの導出）

### 3.1 座標系が GUI 座標であること

`xaero.hud.render.HudRenderer#render` のバイトコード（`javap -c`、26.6.2）:

```
  8: aload 4
 10: invokevirtual #24   // Method net/minecraft/client/Minecraft.getWindow:()Lcom/mojang/blaze3d/platform/Window;
 13: invokevirtual #28   // Method com/mojang/blaze3d/platform/Window.getGuiScaledWidth:()I
 16: istore 5
 ...
 33: invokevirtual #37   // Method com/mojang/blaze3d/platform/Window.getGuiScale:()I
 36: i2d
 37: dstore 7
 39: new #40             // class xaero/hud/render/module/ModuleRenderContext
 ...
 49: invokespecial #42   // Method xaero/hud/render/module/ModuleRenderContext."<init>":(IID)V
```

`ModuleRenderContext` は `public int x, y, w, h; public final int screenWidth, screenHeight; public final double screenScale;`（`javap -p`）。
したがって **`screenWidth`/`screenHeight` は `getGuiScaledWidth()`/`getGuiScaledHeight()`、`screenScale` は `getGuiScale()`** であり、`x`/`y`/`w`/`h` は GUI 座標である。

現在の `MinimapHudRenderer`（`src/main/java/io/github/radishsprouts/mapscribble/map/MinimapHudRenderer.java:106-107`）は `mc.getWindow().getScreenWidth()` / `getScreenHeight()`（物理ピクセル）を使っているので、座標系が一致していない。

### 3.2 枠の位置が `getPushState()` から取れること

同じ `HudRenderer#renderModule` のバイトコード:

```
 50: aload_1
 51: invokevirtual #146  // Method xaero/hud/module/HudModule.getPushState:()Lxaero/hud/pushbox/PushboxHandler$State;
 54: astore 8
 ...
 72: invokevirtual #161  // Method xaero/hud/pushbox/PushboxHandler$State.resetForModule:(Lxaero/hud/module/ModuleSession;IID)L...
 94: invokevirtual #167  // Method xaero/hud/pushbox/PushboxHandler.applyScreenEdges:(L...$State;IID)V
119: invokevirtual #171  // Method xaero/hud/pushbox/PushboxHandler.applyPushboxes:(L...Manager;L...$State;IID)V
122: aload_3
123: aload 8
125: getfield #175       // Field xaero/hud/pushbox/PushboxHandler$State.x:I
128: putfield #178       // Field xaero/hud/render/module/ModuleRenderContext.x:I
131: aload_3
132: aload 8
134: getfield #179       // Field xaero/hud/pushbox/PushboxHandler$State.y:I
137: putfield #182       // Field xaero/hud/render/module/ModuleRenderContext.y:I
140: aload_3
141: aload 6
143: aload_3
144: getfield #157       // Field ModuleRenderContext.screenScale:D
147: invokevirtual #183  // Method xaero/hud/module/ModuleSession.getWidth:(D)I
150: putfield #187       // Field xaero/hud/render/module/ModuleRenderContext.w:I
```

すなわち `ctx.x = pushState.x`、`ctx.y = pushState.y`、`ctx.w = session.getWidth(guiScale)`、`ctx.h = session.getHeight(guiScale)`。
`PushboxHandler$State` は `public int x, y, w, h;` で `HudModule` に保持されるため、**描画フックから後で読める**（`ModuleRenderContext` は毎フレーム捨てられる一時オブジェクトなので読めない）。

**注意（未確認）**: `pushState` は「Xaero が最後にミニマップを描いたフレームの値」である。MapScribble の HUD 描画フックが Xaero のミニマップ描画より**前**に走る場合、1 フレーム古い値になる。1 フレームのずれは画面上ほぼ見えないが、フックの前後関係は実機で確かめていない。

### 3.3 枠の一辺の式

`MinimapSession#getWidth(double)` のバイトコード（26.6.2）:

```
 11: invokestatic #276  // Method xaero/hud/gui/util/GuiUtils.getMinimapScale:(L...ClientConfigManager;)F
 15: fstore 4
 17: invokevirtual #282 // Method getConfiguredWidth:()I
 21: i2f
 22: fload 4
 24: fmul
 25: f2d
 26: dload_1
 27: ddiv
 28: d2i
```

→ `getWidth(scale) = (int)(getConfiguredWidth() * minimapScale / scale)`

`MinimapSession#getConfiguredWidth()`:

```
  1: invokevirtual #230 // getProcessor()
  4: invokevirtual #290 // MinimapProcessor.getMinimapSize():I
  7: iconst_2
  8: idiv
  9: bipush 18
 11: iadd
```

→ `getConfiguredWidth() = getMinimapSize() / 2 + 18`

`MinimapProcessor#getMinimapSize()`:

```
  0: invokestatic #70   // MinimapConfigClientUtils.getEffectiveMinimapSize():I
  3: istore_1
  4: getfield #76       // Field enlargedMap:Z
  8: ifeq 17
 11: sipush 500
 14: goto 20
 17: iload_1
 18: iconst_2
 19: imul
```

→ `getMinimapSize() = enlargedMap ? 500 : getEffectiveMinimapSize() * 2`

**18 は枠（フレーム）の太さの合計**で、片側 9 である（次節の `translate(x+9, y+9)`）。したがって**地図が描かれる内側の領域の一辺は `getMinimapSize() / 2`（= 通常時 `getEffectiveMinimapSize()`）**に相当する。

### 3.4 ズーム（1 ブロック何ピクセルか）

`xaero.common.minimap.render.MinimapRenderer#renderMinimap` のバイトコード（26.6.2。引数は `(this=0, session=1, processor=2, x=3, y=4, screenW=5, screenH=6, screenScale=7-8, minimapScale=9, configuredWidth=10, partial=11, buffers=12)`）:

```
  99: dload 7            // screenScale
 101: fload 9            // minimapScale
 104: ddiv
 106: fstore 18          // f18 = screenScale / minimapScale
 ...
 112: aload_0
 113: aload_2
 114: invokevirtual #154 // MinimapProcessor.getMinimapZoom():D
 117: putfield #7        // this.zoom = ...
 ...
 552: fload 18
 554: fdiv               // 1 / f18
 560: invokevirtual #335 // PoseStack.scale(FFF)
 563: iload_3            // x
 565: fload 18
 567: fmul
 569: istore 49          // slot49 = (int)(x * f18)
 571: iload 4            // y
 576: fmul
 578: istore 50          // slot50 = (int)(y * f18)
 ...
1894: iload 49
1896: bipush 9
1898: iadd
1900: iload 50
1902: bipush 9
1904: iadd
1907: invokevirtual #469 // PoseStack.translate(FFF)   ← 枠の内側へ
1914: fconst_1
1915: fload 9
1917: fdiv               // 1 / minimapScale
1923: invokevirtual #335 // PoseStack.scale(FFF)
1926: iload 15           // minimapSize
1929: fload 9            // minimapScale
1931: fmul
1932: fconst_2
1933: fdiv
1934: fconst_2
1935: fdiv
1936: f2i
1937: istore 64          // specW = (int)(minimapSize * minimapScale / 4)
1943: iload 64
1946: iload 64
1952: invokevirtual #474 // PoseStack.translate(DDD)   ← 中心へ
1957: ldc_w #476         // float 3.0f
1960: fload 9
1962: fmul
1963: f2i
1964: iadd
1965: istore 65          // halfViewW = specW + (int)(3.0f * minimapScale)
1967: istore 66          // halfViewH = halfViewW
```

要素描画ハンドラへ渡すズーム（同メソッド、2082 の呼び出し直前）:

```
1994: aload_0
1995: getfield #7        // this.zoom
1998: fload 9            // minimapScale
2001: dmul
2002: ldc2_w #461        // double 2.0d
2005: ddiv
2006: dstore 70          // zoomElem = zoom * minimapScale / 2.0
...
2064: dload 24           // ps
2066: dload 26           // pc
2068: dload 70           // zoomElem
2070: iload 65           // halfViewW
2072: iload 66           // halfViewH
2074: iload 64           // specW
2076: iload 64           // specH
2078: iload 32           // circle
2080: fload 9            // optionalScale = minimapScale
2082: invokevirtual #510 // MinimapElementOverMapRendererHandler.prepareRender:(DDDIIIIZF)V
```

ここから比で解く。`specW`（内側の半幅）と `zoomElem`（1 ブロックの長さ）は同じ座標系の値なので:

```
1ブロックの長さ / 内側の半幅 = (zoom * minimapScale / 2) / (minimapSize * minimapScale / 4) = 2 * zoom / minimapSize
```

内側の半幅を GUI 座標で表すと `getMinimapSize() / 4`（3.3 節。`minimapScale == guiScale` のとき）なので:

```
1ブロック（GUI ピクセル） = zoom / 2                                  … minimapScale == guiScale のとき
1ブロック（GUI ピクセル） = 枠の一辺 w × zoom / (getMinimapSize() + 36)  … 一般形（スケール非依存）
```

**一般形を使うことを推奨する。** `w = session.getWidth(guiScale)` と `getMinimapSize()` だけで決まり、`minimapScale` と `guiScale` の比を自分で扱わずに済む。

`zoom` の設定値そのものの意味（`MinimapConfigClientUtils#getActualZoom(int)` のバイトコード）:

```
 11: getstatic #347  // MinimapProfiledConfigOptions.FURTHER_ZOOMOUT
 14: invokevirtual #36  // getEffective(...)
 23: ifeq 43
 26: iinc 0, -1      // zoom--
 29: iload_0
 30: ifne 37
 33: ldc2_w #350     // double 0.5d
 36: dreturn         // 0 になったら 0.5
 37: iload_0
 38: ifge 43
 41: dconst_0
 42: dreturn         // 負なら 0
 43: iload_0
 44: i2d
 45: dreturn         // そのまま
```

つまり設定値 `ZOOM` がほぼそのまま倍率になる（`FURTHER_ZOOMOUT` が有効なら 1 段ずれ、最小 0.5）。
**ただし実際の描画で使われるのは `MinimapProcessor#getMinimapZoom()`**（スムーズズームの補間後の値。`renderMinimap` が `this.zoom` に代入している値そのもの）であり、`getActualZoom` は設定の意味を解釈するための補助である。洞窟モードでは `CAVE_ZOOM`、拡大時は `ZOOM_ENLARGED` が効くが、**`getMinimapZoom()` を使えばこれらの分岐を自分で書く必要はない。**

### 3.5 回転

`renderMinimap` のバイトコード:

```
 149: aload 17
 151: getstatic #184  // MinimapProfiledConfigOptions.SHAPE
 154: invokevirtual #190 // getEffective(...)
 160: invokevirtual #198 // Integer.intValue()
 163: istore 20          // shape
 165: iload 15           // minimapSize
 167: iconst_2
 168: idiv               // minimapSize / 2
 169: iload 20           // shape
 171: invokestatic #201  // MinimapConfigClientUtils.getEffectiveNorthLocked:(II)Z
 174: istore 21          // northLocked
 176: aload_0
 177: iload 21
 179: invokevirtual #205 // getRenderAngle:(Z)D
 182: invokestatic #209  // Math.toRadians:(D)D
 185: dstore 22          // angleRad
 187: ldc2_w #215        // double 3.141592653589793d
 190: dload 22
 192: dsub
 193: invokestatic #217  // Math.sin:(D)D
 196: dstore 24          // ps = sin(PI - angleRad)
 198: ldc2_w #215
 201: dload 22
 203: dsub
 204: invokestatic #220  // Math.cos:(D)D
 207: dstore 26          // pc = cos(PI - angleRad)
 ...
 288: iload 20           // shape
 290: iconst_1
 291: if_icmpne 298
 294: iconst_1
 295: goto 299
 298: iconst_0
 299: istore 32          // circle = (shape == 1)
```

`MinimapRenderer#getRenderAngle(boolean)`:

```
  0: iload_1
  1: ifeq 8
  4: ldc2_w #51   // double 90.0d
  7: dreturn
  8: invokevirtual #53  // getActualAngle():D
```

`MinimapRenderer#getActualAngle()`:

```
  4: getfield #57   // Minecraft.gameRenderer
  7: invokevirtual #63  // GameRenderer.getMainCamera():Camera
 10: invokevirtual #69  // Camera.yRot():F
 13: f2d
 14: dstore_1
 15: ldc2_w #75     // double -90.0d
 18: dload_1
 19: dsub
```

→ **`getRenderAngle(northLocked) = northLocked ? 90.0 : (-90.0 - camera.yRot())`（度）**

`MinimapConfigClientUtils#getEffectiveNorthLocked(int halfSize, int shape)`:

```
  0: iload_0
  1: sipush 180
  4: if_icmple 13
  7: iload_1
  8: ifne 13
 11: iconst_1
 12: ireturn          // halfSize > 180 かつ shape == 0 → 強制で北固定
 13: ...  getEffective(NORTH_LOCKED) → istore_3
 37: getstatic #169   // BuiltInHudModules.MINIMAP
 40: invokevirtual #175 // HudModule.getCurrentSession()
 ...
 55: iload_3
 56: ifne 86
 59: getstatic #183   // MinimapProfiledConfigOptions.KEEP_ENLARGED_UNLOCKED
 ...
 75: invokevirtual #186 // getProcessor()
 80: invokevirtual #190 // MinimapProcessor.isEnlargedMap():Z
 83: ifeq 90
 86: iconst_1
 87: goto 91
 90: iconst_0
```

→ `halfSize > 180 && shape == 0` なら北固定を強制。そうでなければ `NORTH_LOCKED` 設定。ただし設定が OFF でも、`KEEP_ENLARGED_UNLOCKED` が OFF かつ拡大中なら北固定になる。
**第 1 引数に渡すのは `getMinimapSize() / 2`。**

この分岐は jar 同梱の言語ファイル（`assets/xaerominimap/lang/en_us.json`。原文の引用）と一致する。

> `"gui.xaero_box_minimap_size" : "Size of the minimap frame that affects how much is visible. Square-shaped minimap is forced into the non-rotating mode (option \"Lock Minimap North\") when the size is greater than 180. ..."`

### 3.6 形（円 / 四角）

同じ言語ファイルより（原文の引用）:

```
"gui.xaero_minimap_shape" : "Shape",
"gui.xaero_minimap_shape_square" : "Square",
"gui.xaero_minimap_shape_circle" : "Circle",
```

`renderMinimap` の 288-299（上記）で `circle = (shape == 1)` と判定しているため、**`SHAPE`: 0 = Square、1 = Circle**。
3.5 節の「`shape == 0` のときだけ北固定を強制」も、言語ファイルの「Square-shaped minimap is forced into the non-rotating mode」と整合する。

### 3.7 ワールド座標 → 画面オフセットの公式

`xaero.hud.minimap.element.render.over.MinimapElementOverMapRendererHandler` には **`public static boolean translatePosition(PoseStack, int, int, int, int, double, double, double, double, double, boolean, double[])`** がある（`javap -p`）。先頭のバイトコード:

```
  3: dload 7     // pc
  5: dload 9     // dx
  7: dmul
  8: dload 5     // ps
 10: dload 11    // dz
 12: dmul
 13: dadd
 14: dload 13    // zoom
 16: dmul
 17: dstore 18   // ty = (pc*dx + ps*dz) * zoom
 19: dload 5     // ps
 21: dload 9     // dx
 23: dmul
 24: dload 7     // pc
 26: dload 11    // dz
 28: dmul
 29: dsub
 30: dload 13    // zoom
 32: dmul
 33: dstore 20   // tx = (ps*dx - pc*dz) * zoom
 35: dload 20
 37: dstore 22   // x 用（以降 halfViewW でクランプ）
 39: dload 18
 41: dstore 24   // y 用（以降 halfViewH でクランプ）
```

したがって:

```
screenDX = ( ps * dx - pc * dz ) * zoomElem
screenDY = ( pc * dx + ps * dz ) * zoomElem
    ps = sin(PI - toRadians(renderAngle))
    pc = cos(PI - toRadians(renderAngle))
    dx = targetX - centerX,  dz = targetZ - centerZ
```

**検算（北固定）**: `renderAngle = 90` → `angleRad = PI/2` → `PI - PI/2 = PI/2` → `ps = 1, pc = 0`
→ `screenDX = dx * zoom`、`screenDY = dz * zoom`。X が右、Z が下。期待どおり。

**検算（回転・yaw = 0、南向き）**: `renderAngle = -90` → `PI - (-PI/2) = 3PI/2` → `ps = -1, pc = 0`
→ `screenDX = -dx * zoom`、`screenDY = -dz * zoom`。南（+Z）が画面上（y 負）、東（+X）が画面左。南を向いたとき東は左手側なので、期待どおり。

`translatePosition` は `public static` なのでリフレクションで直接呼ぶこともできるが、`PoseStack` を渡して行列を動かす副作用があるため、**式を自前で実装するほうが扱いやすい**（上の 2 行で足りる）。

### 3.8 中心のワールド座標

`renderMinimap` のバイトコード:

```
 301: getfield #17      // Minecraft.mc
 305: invokevirtual #109 // Minecraft.getCameraEntity():Entity
 308: fload 11           // partialTicks
 310: invokestatic #264  // xaero/hud/entity/EntityUtils.getEntityX:(Lnet/minecraft/world/entity/Entity;F)D
 313: dstore 33
 ... getEntityY → dstore 35, getEntityZ → dstore 37
 343: dload 33
 345: dstore 39          // centerX
 347: dload 37
 349: dstore 41          // centerZ
 ...
 355: getfield #276      // Minecraft.level
 358: invokevirtual #280 // ClientLevel.dimensionType():DimensionType
 361: invokevirtual #286 // DimensionType.coordinateScale():D
 364: dstore 43
 ...
 381: iload 28           // ワールドマップのチャンクを使うか
 383: ifeq 460
 ...
 397: getfield #295      // SupportMods.worldmapSupport
 400: invokevirtual #299 // SupportXaeroWorldmap.getMapDimensionScale():D
 403: dstore 43
 ...
 439: dload 43
 441: dload 48
 443: ddiv
 444: dstore 46          // 倍率の比
 446: dload 39
 448: dload 46
 450: ddiv
 451: dstore 39          // centerX /= 比
 453: dload 41
 455: dload 46
 457: ddiv
 458: dstore 41          // centerZ /= 比
 ...
 472: new #321           // class net/minecraft/world/phys/Vec3
 476: dload 39
 478: dload 35
 480: dload 41
 482: invokespecial #323 // Vec3.<init>(DDD)
```

→ 中心は **`mc.getCameraEntity()` の補間済み座標**（プレイヤーそのものではない。スペクテイト中・乗り物視点では対象が変わる）。
→ さらに「ワールドマップのチャンクを表示する」設定（`DISPLAY_WORLD_MAP_CHUNKS`）が有効で、ネザー等の `coordinateScale` が異なる次元では**座標が次元倍率の比で割られる**。

現在の実装（`MinimapHudRenderer.java:111`）は `player.getX()` / `player.getZ()`（補間なし・プレイヤー固定）を使っている。

**推奨**: 通常は `mc.getCameraEntity()` の補間座標を使う。`DISPLAY_WORLD_MAP_CHUNKS` による次元倍率の補正は、ネザーで Xaero と MapScribble のメモがずれる原因になりうるが、**この補正が実際に効く条件（Xaero's World Map の導入有無に依存する）は実機で確かめていない。未確認。**

---

## 4. `MinimapHudRenderer` の直し方（具体の手順）

現在の実装の問題は 4 つある（`src/main/java/io/github/radishsprouts/mapscribble/map/MinimapHudRenderer.java`）。

| 行 | 現状 | 問題 |
|---|---|---|
| 30-33 | `xaeroMinimapXField` / `xaeroMinimapYField` / `xaeroMinimapSizeField` を宣言 | 一度も代入・使用されていない。そもそも `XaeroMinimapSession` にこれらのフィールドは存在しない（3.2 節のとおり位置は `HudModule#getPushState()` 側にある） |
| 106-107 | `getScreenWidth()` / `getScreenHeight()`（物理ピクセル） | Xaero は GUI 座標（`getGuiScaledWidth()`）で位置を持つ。単位が合わない |
| 111 | `new Projection(player.getX(), player.getZ(), 1.0, winW, winH)` | 中心が画面中央（ミニマップ中心でない）、倍率が固定 1.0、領域が画面全体 |
| （無し） | クリッピングが無い | 枠外へ描画が漏れる |

### 手順 1: リフレクションの初期化を差し替える

`initReflection()` で取るものを、次に置き換える。

```java
// セッション（表示状態・サイズの判定に使う）
Class<?> modulesClass   = Class.forName("xaero.hud.minimap.BuiltInHudModules");
Field minimapModuleField = modulesClass.getField("MINIMAP");                 // xaero.hud.module.HudModule
Class<?> hudModuleClass = Class.forName("xaero.hud.module.HudModule");
Method getCurrentSession = hudModuleClass.getMethod("getCurrentSession");    // → MinimapSession
Method getPushState      = hudModuleClass.getMethod("getPushState");         // → PushboxHandler$State

Class<?> pushStateClass = Class.forName("xaero.hud.pushbox.PushboxHandler$State");
Field pushX = pushStateClass.getField("x");   // public int
Field pushY = pushStateClass.getField("y");

Class<?> sessionClass = Class.forName("xaero.hud.minimap.module.MinimapSession");
Method sessionGetWidth   = sessionClass.getMethod("getWidth", double.class);
Method sessionGetProcessor = sessionClass.getMethod("getProcessor");
Method hideUnderScreen   = sessionClass.getMethod("getHideMinimapUnderScreen");
Method hideUnderF3       = sessionClass.getMethod("getHideMinimapUnderF3");

Class<?> procClass = Class.forName("xaero.common.minimap.MinimapProcessor");
Method getMinimapZoom = procClass.getMethod("getMinimapZoom");   // double
Method getMinimapSize = procClass.getMethod("getMinimapSize");   // int
Method isEnlargedMap  = procClass.getMethod("isEnlargedMap");    // boolean
Method noMinimapMsg   = procClass.getMethod("getNoMinimapMessageReceived"); // boolean

// 設定（形・表示）
Class<?> hudModClass = Class.forName("xaero.common.HudMod");
Field hudModInstance = hudModClass.getField("INSTANCE");
Method getHudConfigs = hudModClass.getMethod("getHudConfigs");
Method getClientConfigManager =
    Class.forName("xaero.lib.common.config.channel.ConfigChannel").getMethod("getClientConfigManager");
Class<?> cfgMgrClass = Class.forName("xaero.lib.client.config.ClientConfigManager");
Class<?> cfgOptClass = Class.forName("xaero.lib.common.config.option.ConfigOption");
Method getEffective = cfgMgrClass.getMethod("getEffective", cfgOptClass);

Class<?> optsClass = Class.forName("xaero.hud.minimap.common.config.option.MinimapProfiledConfigOptions");
Object OPT_SHAPE           = optsClass.getField("SHAPE").get(null);
Object OPT_DISPLAY_MINIMAP = optsClass.getField("DISPLAY_MINIMAP").get(null);

// 北固定の判定
Class<?> cfgUtilClass = Class.forName("xaero.hud.minimap.config.util.MinimapConfigClientUtils");
Method getEffectiveNorthLocked = cfgUtilClass.getMethod("getEffectiveNorthLocked", int.class, int.class);
```

既存の `xaero.common.XaeroMinimapSession#getCurrentSession()` も**両系列に存在するので残してよい**が、位置が取れないため `BuiltInHudModules` 経路が必須。`BuiltInHudModules` の取得に失敗した場合のフォールバックとして `XaeroMinimapSession#getMinimapProcessor()` を使う形にすると、Xaero 側の将来の改名に 1 段強くなる。

### 手順 2: 描画すべきかを判定する

次のすべてを満たすときだけ描く。

```java
if (session == null) return;                                    // ワールド未参加
if (!(Boolean) getEffective.invoke(cfgMgr, OPT_DISPLAY_MINIMAP)) return;
if ((Boolean) noMinimapMsg.invoke(processor)) return;            // サーバーが禁止
if ((Boolean) hideUnderScreen.invoke(session) && mc.screen != null) return;
if ((Boolean) hideUnderF3.invoke(session) && <F3 オーバーレイ表示中>) return;
```

`NO_MINIMAP` / `NO_MINIMAP_HARMFUL` の効果の判定（`xaero.common.effect.Effects` + `xaero.common.misc.Misc#hasEffect`）も Xaero 本体は見ているが（3.5 節の `MinimapRenderer#render` 冒頭のバイトコード）、**ここまで真似るかは実装の判断**。真似ない場合は「効果でミニマップが消えている間もメモが残る」挙動になる。

洞窟モード（`isCaveModeDisplayed()`）のときに描くかどうかも仕様判断が要る（洞窟モードでは地表のメモと地図の内容が合わない）。**この判断は Dev-Architect / Dev-Director に委ねる。**

### 手順 3: 枠の矩形と中心を求める（GUI 座標）

```java
Object minimapModule = minimapModuleField.get(null);
Object pushState = getPushState.invoke(minimapModule);
int frameX = pushX.getInt(pushState);                 // GUI 座標・枠の左上 X
int frameY = pushY.getInt(pushState);
double guiScale = mc.getWindow().getGuiScale();
int frameW = (Integer) sessionGetWidth.invoke(session, guiScale);   // GUI 座標・枠の一辺

int minimapSize = (Integer) getMinimapSize.invoke(processor);

// 枠（フレーム）の太さ。3.3 節より片側 9（GUI 座標では frameW/configuredWidth 倍される）
double border = 9.0 * frameW / (minimapSize / 2.0 + 18.0);

// 地図が描かれる内側の矩形（GUI 座標）
double innerX0 = frameX + border;
double innerY0 = frameY + border;
double innerSide = frameW - border * 2.0;
double centerPxX = innerX0 + innerSide / 2.0;
double centerPxY = innerY0 + innerSide / 2.0;
```

### 手順 4: 1 ブロックあたりの大きさを求める

```java
double zoom = (Double) getMinimapZoom.invoke(processor);
// 3.4 節の一般形。GUI ピクセル／ブロック
double blockPxGui = frameW * zoom / (minimapSize + 36.0);
```

`Projection` は物理ピクセル基準（`Projection.java` の javadoc「画面座標は JourneyMap の内部と同じ**物理ピクセル**（GUI 座標 × GUI 倍率）」）なので、`Projection` に渡すときは `guiScale` を掛ける。

```java
double blockPxPhys = blockPxGui * guiScale;
```

### 手順 5: 中心のワールド座標を求める

```java
Entity cam = mc.getCameraEntity();
float partial = <描画の partialTicks>;
double cx = Mth.lerp(partial, cam.xOld, cam.getX());   // EntityUtils.getEntityX と同じ意味
double cz = Mth.lerp(partial, cam.zOld, cam.getZ());
```

`xaero.hud.entity.EntityUtils#getEntityX(Entity, float)` をリフレクションで呼んでも同じだが、**Xaero と同じ値になることを保証したいならリフレクションで呼ぶほうが確実**（Xaero 側の実装が変わっても追従する）。

### 手順 6: 回転を扱う

```java
int shape = (Integer) getEffective.invoke(cfgMgr, OPT_SHAPE);          // 0=Square, 1=Circle
boolean circle = (shape == 1);
boolean northLocked = (Boolean) getEffectiveNorthLocked.invoke(null, minimapSize / 2, shape);
double angleDeg = northLocked ? 90.0 : (-90.0 - mc.gameRenderer.getMainCamera().yRot());
double a = Math.toRadians(angleDeg);
double ps = Math.sin(Math.PI - a);
double pc = Math.cos(Math.PI - a);
```

**北固定（`northLocked == true`）の場合は `ps == 1, pc == 0` なので、既存の `Projection` がそのまま使える。**

```java
Projection proj = new Projection(cx, cz, blockPxPhys, innerSide * guiScale, innerSide * guiScale);
```

ただし `Projection` は画面原点を領域の中央に置く設計（`toScreenX` が `width / 2.0 + ...`）なので、**描画前に `PoseStack` を内側矩形の左上へ平行移動する**（`centerPxX - innerSide/2`, `centerPxY - innerSide/2` ぶん）必要がある。既存の `MapCanvasRenderer#render(gfx, proj, dimension, guiScale)` は領域の左上が原点である前提で呼ばれているので、呼び出し側で `pose().translate(...)` を掛ける。

**回転する場合（`northLocked == false`）は、`Projection` だけでは表現できない**（第 6 節）。選択肢は 2 つある。どちらを採るかは設計判断であり、ここでは決めない。

- (a) `PoseStack` をミニマップ中心で `angleDeg` 回転させ、`Projection` には北固定と同じ値を渡す。既存の `MapCanvasRenderer` に手を入れずに済むが、**文字（メモのテキスト）も一緒に回ってしまう**。Xaero 本体はウェイポイントのアイコンを回転の外側で描いている（`MinimapElementOverMapRendererHandler` が座標だけ変換してアイコン自体は回さない）ので、同じ見た目にするなら文字は回転の外で描く必要がある。
- (b) `Projection` に回転（`ps`, `pc`）を加えた新しい投影型を導入し、`MapCanvasRenderer` の座標変換をそれに通す。全画面マップ（回転なし）とミニマップ（回転あり）で同じ描画コードを共有できるが、`Projection` を使っている全箇所（`XaeroMapAdapter`、`jm/MapProjection` 等）への影響を確認する必要がある。

### 手順 7: クリッピング

**四角（`shape == 0`）の場合**: 内側矩形で scissor を掛ける。MC の scissor は GUI 座標を受けるので、`innerX0, innerY0, innerX0 + innerSide, innerY0 + innerSide` をそのまま渡す（`GuiGraphics#enableScissor(int,int,int,int)` 相当。26.x では `GuiGraphicsExtractor` 経由の API 名が異なるため `compat/GuiCompat.java` に逃がすのが素直）。

**円（`shape == 1`）の場合**: scissor は矩形しか扱えないため、矩形の scissor だけでは角が漏れる。Xaero 本体は円形のテクスチャマスクで描いている（`MinimapRendererHelper#drawTexturedElipseInsideRectangle`）。MapScribble 側の選択肢は次のいずれか。**どれを採るかは設計判断。**

- 矩形 scissor を掛けた上で、各頂点・各スキャンラインを中心からの距離 `innerSide/2` で切る（`MapCanvasRenderer` はスキャンライン塗りなので、塗り区間の端を円で詰める処理を足せる）
- 枠の内側に収まる正方形（一辺 `innerSide / √2`）だけに描画を限る（安全だが見える範囲が狭くなる）

なお Xaero 自身は、はみ出した要素を**枠の縁に貼り付ける**処理をしている（3.7 節の `translatePosition` の後半、`halfViewW` / `halfViewH` でのクランプと円の `sqrt` 補正）。手書きメモは点ではなく図形なので、同じクランプは適さない。

### 手順 8: 26.x での描画順（未検証）

26.x のミニマップ本体は PiP（`MinimapPipRenderState` を `GuiRenderState#addPicturesInPictureState` へ積む）で描かれる。一方、Xaero 自身も枠の上に描くものは `MinimapRenderer#renderOutsidePip(...)` で PiP の外に描いている（3.2 節で引用した `MinimapRenderer#render` の 309 行目）。

したがって **MapScribble が HUD 描画フックで枠内に描く方式自体は 26.x でも成立する見込みだが、PiP の合成と MapScribble のフックの前後関係は実機で確かめていない。未検証。** 手書きメモがミニマップの地図の下に隠れる場合は、描画フックの位置（HUD レイヤの順序）を見直す必要がある。

---

## 5. 現物確認した javap 出力の対応表

レポート中の主張と、その根拠になった出力の対応。すべて上記 2 つの jar に対する `javap` の実行結果である。

| 主張 | 根拠 |
|---|---|
| `PushboxHandler$State` が `public int x, y, w, h` | `javap -p 'xaero.hud.pushbox.PushboxHandler$State'`（両系列で同一の出力） |
| `ctx.x = pushState.x` 等 | `javap -c xaero.hud.render.HudRenderer` の `renderModule`（122-163） |
| GUI 座標であること | `javap -c xaero.hud.render.HudRenderer` の `render`（8-49） |
| `getWidth(scale) = configuredWidth * minimapScale / scale` | `javap -c xaero.hud.minimap.module.MinimapSession`（`getWidth`） |
| `getConfiguredWidth() = getMinimapSize()/2 + 18` | 同上（`getConfiguredWidth`） |
| `getMinimapSize() = enlarged ? 500 : effectiveSize*2` | `javap -c xaero.common.minimap.MinimapProcessor`（`getMinimapSize`） |
| `specW = minimapSize*minimapScale/4`、枠の内側へ `+9` | `javap -c xaero.common.minimap.render.MinimapRenderer`（`renderMinimap` 1890-1967） |
| `zoomElem = zoom*minimapScale/2` | 同上（1994-2006） |
| 座標変換の式 | `javap -c xaero.hud.minimap.element.render.over.MinimapElementOverMapRendererHandler`（`translatePosition` 0-41） |
| `getRenderAngle` の式 | `javap -c xaero.common.minimap.render.MinimapRenderer`（`getRenderAngle` / `getActualAngle`） |
| `SHAPE` 0=Square / 1=Circle | `renderMinimap`（288-299）＋ jar 同梱 `assets/xaerominimap/lang/en_us.json` |
| `getEffectiveNorthLocked` の分岐 | `javap -c xaero.hud.minimap.config.util.MinimapConfigClientUtils` |
| 中心が `getCameraEntity()` の補間座標 | `renderMinimap`（301-349, 472-485） |
| `getActualZoom` の式 | `javap -c xaero.hud.minimap.config.util.MinimapConfigClientUtils`（`getActualZoom(int)`） |
| `XaeroMinimapSession.getCurrentSession()` が両系列に存在 | `javap -p xaero.common.XaeroMinimapSession`（両 jar） |
| 1.20.1 に `getRenderState()` が無い | `javap -p xaero.common.minimap.MinimapProcessor`（1.20.1 jar の公開メソッド一覧） |
| 設定オプション名が両系列で同一 | `javap -p xaero.hud.minimap.common.config.option.MinimapProfiledConfigOptions`（両 jar） |

---

## 6. 文書と jar では決着しなかった論点（PoC / 実機確認が要る）

| # | 論点 | なぜ決着しないか |
|---|---|---|
| 1 | `Projection` が回転を表現できない | `Projection.java` は `(centerX, centerZ, scale, width, height)` の record で、回転の成分が無い。回転ミニマップに対応するには手順 6 の (a)/(b) のどちらかを選ぶ必要があり、**これは設計判断**（Dev-Architect） |
| 2 | MapScribble の HUD フックと Xaero のミニマップ描画の前後関係 | jar からは分からない。`pushState` が 1 フレーム古くなるか、PiP の地図に隠れるかは実機でしか確かめられない |
| 3 | 円形ミニマップのクリッピング方式 | 実装の選択肢が複数あり、どれが見た目として許容できるかは実機で見る必要がある |
| 4 | 次元倍率（ネザー）でのずれ | `DISPLAY_WORLD_MAP_CHUNKS` と Xaero's World Map の導入有無に依存する分岐があり、条件を実機で再現しないと確かめられない |
| 5 | 洞窟モード・拡大マップ中にメモを描くか | 仕様判断であり、調査では決められない |
| 6 | リフレクションのフォールバック方針 | Xaero が将来クラス・フィールド名を変えたとき、落ちるのではなく「ミニマップへの描画だけ無効」になるようにしたい。どの粒度で try/catch を切るかは実装の判断 |
| 7 | FTB Chunks Minimap 側 | `MinimapHudRenderer` の javadoc は「Xaero's Minimap および FTB Chunks Minimap」と書いているが、**本調査は Xaero だけを対象にした。FTB Chunks については何も確認していない** |

## 7. 確認できていないこと（明記）

- **実機で動かしていない。** 本調査は jar のバイトコードと jar 同梱の言語ファイルの読解だけで、Minecraft を起動して値を確かめていない。第 3 節の式は検算（北固定・yaw=0 の 2 ケース）まで。
- **MC 1.21.1 / 1.21.4 / 1.21.11 / 26.2 / 26.3 向けの jar は読んでいない。** 読んだのは `common-26.1.2:26.6.2` と `common-1.20.1:26.6.0` の 2 つ。1.21.x 系は「PiP が無い」点で 1.20.1 系に近いはずだが、**確認していない**。NeoForge / Forge 向け artifact（`xaerominimap-neoforge-*` / `xaerominimap-forge-*`）も読んでいない。
- **`MinimapSafeModeRenderer`（FBO が使えない環境）での挙動を確認していない。** `Minimap#usingFBO()` が false のとき、位置・ズームの扱いが同じかは未確認。
- **XaeroPlus（`rfresh2/XaeroPlus`）が入っている環境での挙動を確認していない。** XaeroPlus は `minimapScaleMultiplier` / `minimapSizeMultiplier` でミニマップの倍率とサイズを Mixin で書き換える（`common/src/main/java/xaeroplus/mixin/client/MixinMinimapRenderer.java` の `modifyMinimapSizeConstantI` 等。`https://github.com/rfresh2/XaeroPlus` の `26.3` ブランチ、2026-10-26 閲覧）。手順 4 の一般形は `frameW` と `getMinimapSize()` の実測値から比で求めるので比較的頑健なはずだが、**XaeroPlus 併用時にずれないことは確かめていない**。
- **Xaero's World Map（`xaero.map.gui.GuiMap`）側は本調査の対象外。** 既存の `XaeroMapAdapter`（`src/main/java/io/github/radishsprouts/mapscribble/map/xaero/XaeroMapAdapter.java`）が使っている `cameraX` / `cameraZ` / `scale` / `lastNonNullViewedDimensionId` / `mouseDownPosX` / `mouseDownPosY` の実在は確認していない（World Map の jar を取得していない）。

## 8. 法務に回すべき論点（部門内で断定しない）

Xaero's Minimap の配布ライセンスと、公式 Maven から取得した jar を逆アセンブルして内部構造を調べる行為の可否について、**開発部門では判断しない。** 次の事実関係だけを示す。

- Xaero は開発者向けに Maven リポジトリ（`https://chocolateminecraft.com/maven`）と依存座標を公開しており、他 MOD から連携する用途を想定していると読める（`https://skinmc.net/project/xaeros-minimap` の Developers 節、2026-10-26 閲覧。二次情報）
- 本調査は公式 Maven から `compileOnly` 用途として案内されている `xaerominimap-common-*` を取得し、`javap` で公開 API とバイトコードを読んだ
- MapScribble は Xaero のコードを同梱・再配布せず、実行時のリフレクションで参照するだけである
- Xaero's Minimap 自身のライセンス文書（CurseForge / Modrinth の表記、jar 同梱の LICENSE の有無）は**確認していない**

解釈が要る論点なので、**Dev-Director 経由で法務室（LG-IP）への内部発注を提案する。**

## 9. 出典

一次情報（現物）:

- `xaero.minimap:xaerominimap-common-26.1.2:26.6.2`（`https://chocolateminecraft.com/maven/xaero/minimap/xaerominimap-common-26.1.2/26.6.2/xaerominimap-common-26.1.2-26.6.2.jar`、2026-10-26 取得）の `javap -p` / `javap -c` 出力、および同梱 `assets/xaerominimap/lang/en_us.json`
- `xaero.minimap:xaerominimap-common-1.20.1:26.6.0`（`https://chocolateminecraft.com/maven/xaero/minimap/xaerominimap-common-1.20.1/26.6.0/xaerominimap-common-1.20.1-26.6.0.jar`、2026-10-26 取得）の `javap -p` 出力
- Xaero の Maven インデックス `https://chocolateminecraft.com/maven/xaero/minimap/`（2026-10-26 閲覧）
- MapScribble の `settings.gradle.kts` / `stonecutter.gradle.kts` / `src/main/java/io/github/radishsprouts/mapscribble/map/MinimapHudRenderer.java` / `geom/Projection.java` / `draw/MapCanvasRenderer.java` / `map/xaero/XaeroMapAdapter.java`

二次情報:

- XaeroPlus（`https://github.com/rfresh2/XaeroPlus`、`26.3` ブランチ、2026-10-26 閲覧）の `common/src/main/java/xaeroplus/mixin/client/MixinMinimapRenderer.java`、`MixinMinimapFBORenderer.java`、`MixinHudMinimapRenderer.java`、`MixinMinimapPipRenderer.java`、`MixinMinimapModuleRenderer.java`、`common/src/main/java/xaeroplus/util/GuiMapHelper.java`、`common/src/main/java/xaeroplus/feature/render/MapRenderWindow.java`
  - Xaero の内部クラス名・メソッドシグネチャを突き止める入口として使った。**レポートの主張はすべて jar で裏取り済み**で、XaeroPlus のコードを根拠にした主張は無い
- `https://skinmc.net/project/xaeros-minimap`（2026-10-26 閲覧）の Developers 節 — Maven 座標と Forge の非難読化に関する記述

### 検証環境

| 項目 | 値 |
|---|---|
| OS | Windows 11 Pro 10.0.26300 |
| 解析ツール | `C:\Program Files\Java\jdk-25\bin\javap`（JDK 25） |
| 調査日 | 2026-10-26 |
| 調査対象 jar の取得方法 | `curl` で公式 Maven から直接取得（スクラッチパッド配下。リポジトリへは置いていない） |

### PoC について

**実行可能な PoC は書いていない。** 本調査は既存 jar の静的解析だけで 6 項目すべてに答えが出たため、捨てる前提の実装を作る必要がなかった。実機での検証（第 6 節の論点 2・3・4）は、MapScribble 本体への変更を伴うため Dev-Engineer の実装と Dev-QA の検証に委ねる。
