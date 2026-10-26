# Xaero's Minimap のミニマップへの手書きメモ描画（設計）

- 作業区分: コミュニティ運営（community）
- 担当: Dev-Architect（設計・原因特定）。実装は Dev-Engineer、検証は Dev-QA
- 対象の不具合: 「Xaero's Minimap のミニマップに手書きメモ（囲み区画・線・文字）が表示されない」
- 前提の調査: `docs/design/xaero-minimap-api.md`（Dev-Tech-Researcher）。**この設計書では、調査レポートの主張をそのまま前提にせず、根拠にする取得経路をすべて jar の `javap` で確かめ直した。** 出典は第2節に、確かめられなかったものは第12節に分けて書いた

---

## 1. 現状の壊れ方（確かめた事実）

`src/main/java/io/github/radishsprouts/mapscribble/map/MinimapHudRenderer.java:106-114`:

```java
int winW = mc.getWindow().getScreenWidth();
int winH = mc.getWindow().getScreenHeight();
int guiScale = (int) Math.max(1.0, (double) mc.getWindow().getGuiScale());

// プレイヤー中心座標・標準ズーム倍率でミニマップ投影を作成
Projection proj = new Projection(player.getX(), player.getZ(), 1.0, winW, winH);
if (canvasRenderer != null) {
    canvasRenderer.render(gfx, proj, mc.level.dimension(), guiScale);
}
```

ここから、不具合は次の5つが同時に起きている状態である。

1. **枠の位置を使っていない。** 投影の中心は `Projection` の仕様（`geom/Projection.java:28-34`）により描画面の中央になるため、メモは画面の中央に出る。ミニマップは既定で画面右上にあるので、枠の中にはほぼ何も出ない
2. **ズームを使っていない。** `scale = 1.0`（1 ブロック = 1 物理ピクセル）は、既定のミニマップ（1 ブロックあたり数ピクセル）と合わない。1 ブロック幅の囲みは 1 ピクセルの点になり、見えない
3. **回転を使っていない。** `Projection` は回転を表現できない（`toScreenX(double)` が x だけを受けるため、`geom/Projection.java:28`）。Xaero の既定は北固定 OFF（回転する）なので、向きが合わない
4. **枠内への切り抜きが無い。** `compat/GuiCompat.java` に scissor 系のメソッドは存在しない（全 295 行を確認した）。そのため画面中央に描かれたメモが HUD の上へそのまま出る
5. **ミニマップ限定の絞り込みが無い。** `memo.onMinimap()` を見ていない。JourneyMap 側は `jm/MinimapOverlays.java:80` と `:100` で絞り込んでいるので、挙動が揃っていない

`MinimapHudRenderer.java:30-33` の `xaeroMinimapXField` / `xaeroMinimapYField` / `xaeroMinimapSizeField` は宣言だけで、`initReflection()`（`:44-54`）でも代入されず、どこからも読まれていない。`:115-116` の `catch (Throwable ignored) {}` は毎フレーム黙って例外を捨てるので、失敗しても気付けない。

javadoc（`:24`）は「Xaero's Minimap および FTB Chunks Minimap」と書いているが、実装は Xaero のセッションしか見ていない。

---

## 2. Xaero からの値の取り方（jar で確かめた出典つき）

確認に使った jar（スクラッチパッドに置いた実物）:

- `C:\Users\radis\AppData\Local\Temp\claude\S--Projects-minecraft-MOD-MapScribble\71be49ef-7719-4ec9-b991-8724a79391be\scratchpad\xaero26\mm.jar`（26.3 向け）
- 同じディレクトリの `xaero1201\mm.jar`（1.20.1 向け）

Xaero's Minimap のクラス名・メソッド名は難読化されていないため、リフレクションで参照できる。

### 2.1 両バージョンで同じもの（分岐が不要）

| 使う値 | 取得経路 | 型・単位 | javap で確認した出典 |
|---|---|---|---|
| セッション | `xaero.common.XaeroMinimapSession.getCurrentSession()`（static） | — | `public static xaero.common.XaeroMinimapSession getCurrentSession();`（両方） |
| プロセッサ | `XaeroMinimapSession#getMinimapProcessor()` | — | `public xaero.common.minimap.MinimapProcessor getMinimapProcessor();`（両方） |
| HUD モジュール | `xaero.hud.minimap.BuiltInHudModules.MINIMAP`（static フィールド） | `HudModule<MinimapSession>` | `public static final xaero.hud.module.HudModule<xaero.hud.minimap.module.MinimapSession> MINIMAP;`（両方にクラスあり） |
| ミニマップのセッション | `HudModule#getCurrentSession()` | `MinimapSession` | `public MS getCurrentSession();` |
| 枠の左上・大きさ | `HudModule#getPushState()` → `xaero.hud.pushbox.PushboxHandler$State` の **public** フィールド `x` `y` `w` `h` | int・**GUI 座標** | `public xaero.hud.pushbox.PushboxHandler$State getPushState();` / `public int x; public int y; public int w; public int h;` |
| 枠の一辺 | `MinimapSession#getWidth(double)` | int・GUI 座標 | `public int getWidth(double);` と `public int getHeight(double);`（両方）。`getHeight` は `getWidth` を呼ぶだけで**枠は正方形** |
| ミニマップの UI 倍率 | `xaero.hud.gui.util.GuiUtils.getMinimapScale(ClientConfigManager)`（static） | float | `public static float getMinimapScale(xaero.lib.client.config.ClientConfigManager);`（両方） |
| 設定マネージャ | `xaero.common.HudMod.INSTANCE`（static）→ `getHudConfigs()` → `getClientConfigManager()` | — | `public static xaero.common.HudMod INSTANCE;` / `public xaero.lib.common.config.channel.ConfigChannel getHudConfigs();`（両方）。`ConfigChannel.getClientConfigManager:()Lxaero/lib/client/config/ClientConfigManager;` は呼び出し側のバイトコードの定数プールから確認 |
| 形状（四角 / 円） | `ClientConfigManager#getEffective(MinimapProfiledConfigOptions.SHAPE)` → `Integer` | int | `xaero.common.minimap.render.MinimapRenderer` のバイトコードに `getstatic MinimapProfiledConfigOptions.SHAPE:RangeConfigOption` → `getEffective` → `checkcast java/lang/Integer` → `intValue` の並びがある |
| 北固定か | `xaero.hud.minimap.config.util.MinimapConfigClientUtils.getEffectiveNorthLocked(int size, int shape)`（static） | boolean | `public static boolean getEffectiveNorthLocked(int, int);`（両方）。`MinimapRenderer` のバイトコードで `minimapSize / 2` と SHAPE の値を渡して呼んでいる |
| ミニマップの設定サイズ | `MinimapProcessor#getMinimapSize()` | int・ピクセル相当 | `public int getMinimapSize();`（両方） |
| ズーム | `MinimapProcessor#getMinimapZoom()` | double・倍率 | `public double getMinimapZoom();`（両方） |
| 拡大マップ中か | `MinimapProcessor#isEnlargedMap()` | boolean | `public boolean isEnlargedMap();`（両方） |
| 洞窟モード中か | `MinimapProcessor#isCaveModeDisplayed()` | boolean | `public boolean isCaveModeDisplayed();`（両方） |
| 地図が描いている次元 | `MinimapProcessor#getLastMapDimension()` | `ResourceKey<Level>` | `public net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> getLastMapDimension();`（両方） |
| 画面を開いた時に隠すか | `MinimapSession#getHideMinimapUnderScreen()` / `getHideMinimapUnderF3()` | boolean | 両方で確認 |
| 中心のワールド座標 | `xaero.hud.entity.EntityUtils.getEntityX(Entity, float)` / `getEntityZ(Entity, float)`（static） | double・ブロック | `public static double getEntityX(net.minecraft.world.entity.Entity, float);`（両方） |

**ズームに洞窟・拡大の係数が既に入っている。** `getMinimapZoom()` は `getTargetZoom()` の補間後の値で、`getTargetZoom()` の中で拡大時は `ZOOM_ENLARGED`、洞窟時は `CAVE_ZOOM` を掛けた値になる。したがって**こちらで洞窟・拡大の分岐を書く必要がない**（論点3の根拠）。

### 2.2 回転角

`xaero.common.minimap.render.MinimapRenderer#getRenderAngle(boolean)`（両方に `public double getRenderAngle(boolean);`）の実装は、バイトコードでは次のとおりだった。

```
0: iload_1          // 引数 northLocked
1: ifeq  8
4: ldc2_w 90.0d     // 北固定なら 90.0
7: dreturn
8: invokevirtual getActualAngle:()D
```

`getActualAngle()`:

```
mc.gameRenderer.getMainCamera().yRot() を double にして yaw に入れ、
-90.0 - yaw を返す
```

したがって**回転角（度）は次の式で求まる**。

```
renderAngle = northLocked ? 90.0 : (-90.0 - camera.yRot())
```

`MinimapRenderer` のバイトコードは、この値をそのまま `Math.toRadians` に通し、`π - rad` を計算している（`ldc2_w 3.141592653589793d` → `dsub`）。

### 2.3 26.x だけにあるもの（PiP の描画状態）

26.x ではミニマップが PiP（Picture-in-Picture）で描かれ、`xaero.hud.minimap.render.MinimapPipRenderState` に**そのフレームで実際に使った枠の値**が残る。

- `MinimapProcessor#getRenderState()` → `MinimapPipRenderState`
- 拡大中は `MinimapProcessor#getEnlargedRenderState()` → `MinimapPipRenderState$Enlarged`
- フィールド: `private int x, y, width, height; private double scale; private float minimapScale; private int size, boxSize; private float partial; private boolean shouldRender;`
- public getter: `getBoxSize()` `getMinimapScale()` `getScale()` `getSize()` `getPartial()` `x0()` `y0()` `x1()` `y1()`
- `x0()` は `x - getScaledHorizontalPadding()`、`x1()` は `x + boxSize + getScaledHorizontalPadding()`（バイトコードで確認）。つまり **`x` が枠（ボックス）の左上、`boxSize` が枠の一辺**で、`x0/x1` は PiP のパディングを含む外側である

1.20.1 の jar には `MinimapPipRenderState` が存在しない（`jar tf` で 0 件）。

**拡大マップでは枠が画面中央へ移る。** 26.x の `xaero.hud.minimap.module.MinimapRenderer#render` のバイトコードに、`isEnlargedMap()` が true かつ `MinimapProfiledConfigOptions.CENTERED_ENLARGED` が true のとき

```
x = (ModuleRenderContext.screenWidth - ModuleRenderContext.w) / 2
y = (ModuleRenderContext.screenHeight - ModuleRenderContext.w) / 2
```

と置き換える分岐がある（`w` を縦にも使っているのが、枠が正方形であることのもう一つの裏付け）。この置き換え後の値が `MinimapPipRenderState.update(...)` へ渡る。**26.x では RenderState から読めば拡大時の中央寄せまで正しく反映される。**

### 2.4 座標の単位（今回の不具合の一因）

`ModuleRenderContext` の `screenWidth` / `screenHeight` には `Window#getGuiScaledWidth()` / `getGuiScaledHeight()` が、`screenScale` には `getGuiScale()` が入る（前段の調査で確認した経路。本設計では `PushState` と `getWidth(double)` を使うため、ここには依存しない）。**`PushState.x/y` と `MinimapSession#getWidth(double)` は GUI 座標である。** 現在の実装が `getScreenWidth()`（物理ピクセル）を使っているのは単位の取り違えであり、不具合の一因である。

一方 `geom/Projection.java:6` の javadoc は「画面座標は JourneyMap の内部と同じ**物理ピクセル**（GUI 座標 × GUI 倍率）」と定めている。`draw/MapCanvasRenderer.java:110-111` が `pushPose` → `scale2D(1f / guiScale, 1f / guiScale)` で物理ピクセル座標系を作り、`:134-135` で文字だけ GUI 座標に戻している。**この設計でも描画の座標系は物理ピクセルに揃え、Xaero から取った GUI 座標の値へ `guiScale` を掛けて使う。**

---

## 3. 座標変換（この設計で使う式）

記号:

- `guiScale` = `mc.getWindow().getGuiScale()`（double）
- `frameX`, `frameY` = 枠の左上（**GUI 座標**）
- `minimapSize` = `processor.getMinimapSize()`
- `minimapScale` = `GuiUtils.getMinimapScale(ccm)`
- `zoom` = `processor.getMinimapZoom()`
- `renderAngle` = 第2.2節の式（度）
- `centerX`, `centerZ` = `EntityUtils.getEntityX/Z(mc.getCameraEntity(), partialTicks)`（ブロック）

導出される値（すべて**物理ピクセル**）:

```
innerHalfPhys  = minimapSize * minimapScale / 4.0        // 内側（地図が出ている正方形）の半辺
padPhys        = 9.0 * minimapScale                      // 枠の縁の太さ（Xaero の定数 9）
centerPhysX    = frameX * guiScale + padPhys + innerHalfPhys
centerPhysY    = frameY * guiScale + padPhys + innerHalfPhys
blockPxPhys    = zoom * minimapScale / 2.0               // 1 ブロックあたりの物理ピクセル
```

検算: `innerHalfPhys / guiScale` が `(frameSize - 2 * padPhys / guiScale) / 2` と一致する（`frameSize = MinimapSession#getWidth(guiScale)`）。実装では**両方を計算し、2 物理ピクセル以上ずれていたら `MinimapSession#getWidth` 側（枠から引いた値）を採る**（第8.4節）。可視半径は `innerHalfPhys / blockPxPhys = minimapSize / (2 * zoom)` ブロックになる。

回転（第2.2節の式のとおり）:

```
theta = PI - toRadians(renderAngle)
ps    = sin(theta)
pc    = cos(theta)

dx = wx - centerX
dz = wz - centerZ
screenPhysX = centerPhysX + (ps * dx - pc * dz) * blockPxPhys
screenPhysY = centerPhysY + (pc * dx + ps * dz) * blockPxPhys
```

向きの確認（2 ケースで検算した）:

- 北固定（`renderAngle = 90`）→ `ps = 1, pc = 0` → `screenPhysX = centerPhysX + dx * blockPxPhys`、`screenPhysY = centerPhysY + dz * blockPxPhys`。東（+X）が右、南（+Z）が下。北が上で正しい
- 回転・プレイヤーが南を向く（`yaw = 0` → `renderAngle = -90`）→ `ps = -1, pc = 0` → プレイヤーの向き（+Z）が画面の上（-Y）になる。向いている方向が上で正しい

**26.x では `frameX` / `frameY` / `minimapScale` / `innerHalfPhys` を `MinimapPipRenderState` から取る**（`x`, `y`, `getMinimapScale()`, `getBoxSize()/2 - padPhys`）。拡大中は `getEnlargedRenderState()` を使う。

---

## 4. 決定（論点1〜7）

### 論点1: 回転への対応方式

**決定: `PoseStack` の回転では包まない。`geom/Projection` も変えない。回転を含む変換を表す新しい record `geom/ViewTransform` を追加し、`MapCanvasRenderer` が頂点の座標計算でそれを使う。**

```java
/** 画面座標（物理ピクセル）への変換。回転を含む。原点は描画面の左上。 */
public record ViewTransform(double centerX, double centerZ, double blockPx,
                            double originX, double originY, double ps, double pc) {

    /** 全画面マップ用（回転なし・中心は描画面の中央）。 */
    public static ViewTransform of(Projection p) {
        return new ViewTransform(p.centerX(), p.centerZ(), p.scale(),
                p.width() / 2.0, p.height() / 2.0, 1.0, 0.0);
    }

    public double screenX(double wx, double wz) {
        return originX + (ps * (wx - centerX) - pc * (wz - centerZ)) * blockPx;
    }

    public double screenY(double wx, double wz) {
        return originY + (pc * (wx - centerX) + ps * (wz - centerZ)) * blockPx;
    }

    /** 画面上の距離（物理ピクセル）→ ワールドの距離（ブロック）。 */
    public double pixelsToWorld(double pixels) {
        return pixels / blockPx;
    }
}
```

`ps = 1, pc = 0, origin = (width/2, height/2)` のとき `screenX` は `Projection#toScreenX` と同じ式になるので、全画面マップの見た目は変わらない。

採った理由:

1. **塗りが崩れない。** 塗りは `geom/Scanline.java` が多角形を**画面の走査線に沿った横帯の矩形**に分解して作る（`geom/Scanline.java:11-15` の javadoc、戻り値は `List<int[]>` の `{x0,y0,x1,y1}`）。`PoseStack` で回すと、この軸平行矩形の集合が平行四辺形の集合になり、隣り合う帯の間に隙間と階段が出る。頂点を先に回してからスキャンラインを引けば、帯は常に画面の走査線に沿うので隙間が出ない
2. **文字が回らない。** Xaero はウェイポイントの名前をミニマップの回転に合わせて回さない。`PoseStack` で包むと文字まで回り、回転ミニマップでは文字が逆さまになる時間が生じて読めない
3. **逆変換に手を入れずに済む。** 画面座標 → ワールド座標の逆変換は全画面マップの当たり判定だけが使っており（`map/ScreenMapHost.java:208` の `HitTester.find(..., proj.toWorldX(px), proj.toWorldZ(py), proj.pixelsToWorld(1.0), ...)`）、ミニマップは入力を受けないので回転の逆変換は要らない。`Projection` を変えないので `map/xaero/XaeroMapAdapter.java:87`・`jm/MapProjection.java:82`・`map/ftb/FtbChunksAdapter.java:169`・`map/jm/JourneyMapLegacyAdapter.java:140` の 4 つの生成箇所と `ScreenMapHost` は無変更で済む

不採用にした案:

- **(a) `PoseStack` を回転させて包む**: 上の 1・2 のとおり、塗りに隙間が出て文字が回る。切り抜き（scissor）は回転の影響を受けないので切り抜きだけは成立するが、見た目の質が落ちる
- **(b) `Projection` record に回転を持たせる**: `toScreenX(double)` が x だけを受ける形（`geom/Projection.java:28`）なので、回転を入れるには引数を 2 つにする破壊的変更が必要になる。全画面側 4 箇所の生成と `ScreenMapHost`・`HitTester` の逆変換まで巻き込み、回転が要らない全画面マップにも回転の概念が残る
- **(c) ミニマップ専用の描画エンジンを別に書く**: 塗り・線の太さ・文字の背景・菱形の印といった見た目の規則が全画面マップと二重管理になり、片方だけ直す事故が起きる

### 論点2: 枠内への切り抜き（円形を含む）

**決定: 「矩形の scissor」＋「円のときは図形ごとの幾何クリップ」の二段で切る。**

| 対象 | 四角のミニマップ | 円のミニマップ |
|---|---|---|
| 塗り（囲み区画の中） | 矩形 scissor ＋ `Scanline` の clip を内側矩形に | 同じ ＋ **行ごとに円の x 範囲で詰める**（`sqrt(r² - dy²)`） |
| 線・輪郭 | 矩形 scissor | 同じ ＋ 線分を円で区間クリップ |
| 文字・菱形の印 | 矩形 scissor | **背景矩形の 4 隅がすべて円の内側のときだけ描く。** はみ出すときは描かず、代わりに菱形の印を出す |

- MC の scissor は矩形しか受けない（`enableScissor(int,int,int,int)`）。円は scissor では作れないので、図形側で切る
- 矩形 scissor は**必ず掛ける**。幾何クリップに漏れがあっても、HUD の上へメモが出る事故を防ぐ最後の壁になる
- 円の半径は `innerHalfPhys`（第3節）。中心は `(centerPhysX, centerPhysY)`
- `Scanline` に円を受けるオーバーロードを足す（第6.3節）。既存のシグネチャは円なし（`circleR <= 0`）で新しいものへ委譲する

不採用にした案:

- **円の内接正方形だけに描く**: 実装は要らないが、見える範囲が半分（面積で 64%）に狭まり、円の縁に近いメモが消える。ミニマップに出すという目的を損なう
- **文字の行ごとに scissor を掛け替えて円に近づける**: 文字 1 つにつき行数ぶんの `enableScissor`/`disableScissor` が増える。毎フレームの GL 呼び出しが増える割に、文字が縁で切れて読めないという結果は変わらない
- **ステンシル・マスクのテクスチャで円を抜く**: `GuiCompat` の抽象（矩形の `fill` と文字だけ）から外れ、3 ローダー × 4 系統のバージョンでレンダーパイプラインの差を吸収し直すことになる。得られるのは縁 1 ピクセルの滑らかさだけである

### 論点3: 洞窟モード・拡大マップ

**決定: どちらでも描く。** 根拠は次の 3 点。

1. 洞窟モードでも**座標系（XZ）は変わらない**。変わるのは地図の内容（断面の高さ）とズームだけである。`getMinimapZoom()` に `CAVE_ZOOM` が掛かった値が入っている（第2.1節）ので、こちらは何もしなくてもズームが合う
2. 拡大マップでも同じく `ZOOM_ENLARGED` が `getMinimapZoom()` に入っている。枠の位置は 26.x では `getEnlargedRenderState()` から取れば中央寄せまで反映される（第2.3節）
3. メモは XZ 平面の図形で、高さを持たない（`memo` に Y は無い）。断面の高さが変わってもメモの位置は変わらない

ただし**「地図が描いている次元」と「プレイヤーがいる次元」が食い違うときは描かない**（第8.2節）。洞窟モードではなく、Xaero's World Map 連携で別次元の地図を出している場合に起きる。

### 論点4: 文字の描き方

**決定: `memo.textScale()` をそのまま使い、ミニマップ用の縮小係数を入れない。**

- `ui/TextSize.java` の 3 段（`SMALL 0.5f` / `MEDIUM 0.75f` / `LARGE 1.0f`）に対し、`draw/DrawingState.java:42` の既定は `0.75f`。MC の `font.lineHeight` は 9 なので 1 行は 6.75 GUI ピクセルになる
- ミニマップの内側は、既定サイズで 60〜100 GUI ピクセル、最大設定で 250 GUI ピクセル前後ある。6.75 GUI ピクセルの文字は 9〜37 行ぶん入る大きさで、読める
- ミニマップ専用に縮めると、全画面マップで選んだ大きさと一致せず「同じメモが場所によって違う大きさで出る」ことになる。文字の大きさは利用者が `TextSize` で選ぶものなので、その選択をこちらで上書きしない
- 枠に収まらない文字は、第4節・論点2の規則（4 隅が円の内側のときだけ描く）と矩形 scissor で切る。切れて読めなくなる場合は菱形の印に置き換える

文字の表示切替との関係は論点6に書く。

### 論点5: `memo.onMinimap()` が false のメモを描かない場所

**決定: `MapCanvasRenderer` の描画ループの入口で絞り込む。絞り込むかどうかは、新しい引数 `MapFrame#minimapOnly()` で渡す。**

JourneyMap 側（`jm/MinimapOverlays.java:80`、`:100`）はオーバーレイを作る時点で絞り込んでいる。Xaero 側は毎フレーム自分で描くので、作る時点に相当するのが描画ループの入口になる。ここで絞ることで、カリング・頂点計算・キャッシュの更新もまとめて省ける。

### 論点6: どのメモを描くか（文字の表示切替との関係）

**決定: `state.isTextVisible()` の扱いは `MapCanvasRenderer` に任せ、ミニマップ側では何もしない。**

`draw/MapCanvasRenderer.java:107` は既に `boolean showText = state.isTextVisible();` になっており、`:117-125` で文字メモは文字を隠している間に菱形の印（`drawTextMark`、`:210-211` で縁付きの菱形を 2 枚重ねる）を出し、`:136-145` で文字の描画ループ全体を `if (showText)` で囲んでいる。ミニマップは同じ `MapCanvasRenderer` を通すので、この挙動がそのまま効く。

したがって描くメモの判定は次の 3 段だけになる。

1. `state.isVisible()` が false → 何も描かない（`MapCanvasRenderer.java:105` が既に `List.of()` にしている）
2. `minimapOnly` が true のとき、`memo.onMinimap()` が false のメモを飛ばす（論点5）
3. 文字を出すかは `state.isTextVisible()`（`MapCanvasRenderer` の中）

### 論点7: FTB Chunks のミニマップ

**決定: 今回は対象に含めない。`MinimapHudRenderer` の javadoc（`MinimapHudRenderer.java:24`）から「FTB Chunks Minimap」を外し、Xaero 専用であることを明記する。**

理由は技術的な未知であって、作業量ではない。FTB Chunks のミニマップについては、枠の位置・ズーム・回転・形状の取得経路を誰も調べていない（`docs/design/xaero-minimap-api.md` は Xaero だけを扱っている）。取得経路が分からない状態で設計すると、当てずっぽうの実装になる。**順序の問題**であり、先に調査が要る。

Dev-Director へ次を提案する（この設計書の範囲外の別作業）。

1. Dev-Tech-Researcher へ「FTB Chunks のミニマップの枠・ズーム・回転・形状の取得経路」の調査を発注する
2. その結果をもとに、この設計の `MinimapGeometry`（第5節）に FTB 用の実装を 1 つ足す設計を書く

本設計の構造は、枠の幾何を求める部分を差し替え可能にしてある（第5節の `MinimapGeometry`）ので、調査が済めば描画側に手を入れずに足せる。

---

## 5. `MinimapHudRenderer` の新しい構造

`map/` の下に、Xaero 固有のリフレクションを閉じ込めた新しいクラスを置き、`MinimapHudRenderer` は「毎フレームの流れ」だけを持つ形にする。

```
map/MinimapHudRenderer.java          … 毎フレームの流れ（描くか決める → 幾何を取る → 切り抜く → 描く）
map/minimap/MinimapGeometry.java     … 枠の幾何の結果（record。新規）
map/minimap/MinimapSource.java       … 幾何を供給するもの（interface。新規）
map/xaero/XaeroMinimapSource.java    … Xaero 用の実装。リフレクションはここだけ（新規）
geom/ViewTransform.java              … 回転を含む画面変換（record。新規）
geom/ClipRegion.java                 … 切り抜き（矩形＋任意の円）（record。新規）
draw/MapFrame.java                   … MapCanvasRenderer への入力をまとめた record（新規）
```

### 5.1 `MinimapGeometry`（record・新規）

```java
/**
 * ミニマップの枠の幾何。長さの単位はすべて物理ピクセル（GUI 座標 × GUI 倍率）。
 *
 * @param centerPhysX   内側領域の中心（物理ピクセル）
 * @param centerPhysY   同じく縦
 * @param innerHalfPhys 内側領域の半辺（物理ピクセル）
 * @param blockPxPhys   1 ブロックあたりの物理ピクセル
 * @param ps            回転行列の sin 成分（第3節）
 * @param pc            回転行列の cos 成分
 * @param circular      円形のミニマップか
 * @param worldX        地図の中心のワールド X（ブロック）
 * @param worldZ        同じく Z
 * @param dimension     地図が描いている次元（ResourceKey<Level>。比較にだけ使う）
 */
public record MinimapGeometry(double centerPhysX, double centerPhysY, double innerHalfPhys,
                              double blockPxPhys, double ps, double pc, boolean circular,
                              double worldX, double worldZ, Object dimension) {
}
```

### 5.2 `MinimapSource`（interface・新規）

```java
public interface MinimapSource {
    /** この環境で使えるか（対象の MOD が入っていて、取得経路が揃っているか）。 */
    boolean isAvailable();

    /** 今このフレームの幾何。描くべきでない時（隠れている・無効・取得できない）は null。 */
    MinimapGeometry geometry(float partialTicks);
}
```

### 5.3 `XaeroMinimapSource`（新規・リフレクションはここだけ）

**解決した `Class` / `Method` / `Field` はインスタンスのフィールドに 1 度だけ持ち、毎フレームは `invoke` / `get` だけを行う。**

```java
public final class XaeroMinimapSource implements MinimapSource {
    private static final Logger LOGGER = ...;

    private boolean resolved;      // 解決を試したか
    private boolean available;     // 使えるか
    private boolean warned;        // 失敗を 1 度だけ記録したか

    // 1 度だけ解決して持つもの（名前はすべて第2節の表のとおり）
    private Method getCurrentSession;      // XaeroMinimapSession.getCurrentSession()
    private Method getMinimapProcessor;    // XaeroMinimapSession#getMinimapProcessor()
    private Object minimapHudModule;       // BuiltInHudModules.MINIMAP の値
    private Method getPushState;           // HudModule#getPushState()
    private Method getModuleCurrentSession; // HudModule#getCurrentSession()
    private Field pushStateX, pushStateY;  // PushboxHandler$State.x / .y
    private Method sessionGetWidth;        // MinimapSession#getWidth(double)
    private Method getHideUnderScreen, getHideUnderF3;
    private Method getMinimapSize, getMinimapZoom, isEnlargedMap, getLastMapDimension;
    private Method getRenderStateMethod, getEnlargedRenderStateMethod; // 26.x のみ。無ければ null
    private Field renderStateX, renderStateY, renderStateShouldRender; // 26.x のみ
    private Method renderStateBoxSize, renderStateMinimapScale;        // 26.x のみ
    private Method getMinimapScale;        // GuiUtils.getMinimapScale(ClientConfigManager)
    private Method getClientConfigManager; // ConfigChannel#getClientConfigManager()
    private Method getEffective;           // ClientConfigManager#getEffective(ConfigOption)
    private Object shapeOption;            // MinimapProfiledConfigOptions.SHAPE の値
    private Method getEffectiveNorthLocked;
    private Method entityGetX, entityGetZ; // EntityUtils.getEntityX/Z(Entity, float)
    private Object hudConfigsOwner;        // HudMod.INSTANCE
    private Method getHudConfigs;
}
```

解決の規則:

- **クラス名の文字列は、Xaero 本体のクラスだけに使う。** `xaero.lib.*`（`ClientConfigManager` 等）は Xaero's Minimap の jar に同梱されておらず、`Class.forName` できないことを確認した（`jar tf` で 0 件、`javap` も空）。したがって `getClientConfigManager()` の戻り値の `getClass()` から、**メソッド名と引数の個数で** `getEffective` を探す
- 1 つでも必須の解決に失敗したら `available = false` にして、以後は `geometry()` が即 `null` を返す
- 解決の失敗は `LOGGER.warn` を**1 度だけ**出す（`warned` フラグ）。毎フレームのログは出さない

毎フレームの流れ（`geometry(partialTicks)`）:

```
1. available が false → null
2. session = getCurrentSession.invoke(null) が null → null         （ワールドに入っていない）
3. processor = getMinimapProcessor.invoke(session)
4. mapDimension = getLastMapDimension.invoke(processor)
5. moduleSession = getModuleCurrentSession.invoke(minimapHudModule) が null → null
6. 26.x: renderState = isEnlargedMap ? getEnlargedRenderState : getRenderState
      shouldRender が false → null                                （Xaero が今フレーム描いていない）
      frameX = renderStateX, frameY = renderStateY（GUI 座標）
      boxSize = renderStateBoxSize（GUI 座標）
      minimapScale = renderStateMinimapScale
   それ以外: pushState = getPushState.invoke(minimapHudModule)
      frameX = pushStateX, frameY = pushStateY
      boxSize = sessionGetWidth.invoke(moduleSession, guiScale)
      minimapScale = getMinimapScale.invoke(null, ccm)
7. minimapSize = getMinimapSize.invoke(processor)
   zoom = getMinimapZoom.invoke(processor)
   shape = getEffective.invoke(ccm, shapeOption)（Integer）
   northLocked = getEffectiveNorthLocked.invoke(null, minimapSize / 2, shape)
8. renderAngle = northLocked ? 90.0 : (-90.0 - mc.gameRenderer.getMainCamera().yRot())
   theta = PI - toRadians(renderAngle); ps = sin(theta); pc = cos(theta)
9. 第3節の式で centerPhysX / centerPhysY / innerHalfPhys / blockPxPhys を出す
10. worldX = entityGetX.invoke(null, mc.getCameraEntity(), partialTicks)
    worldZ = entityGetZ.invoke(null, ...)
11. new MinimapGeometry(...) を返す
```

**`try`/`catch` は `geometry()` の全体を 1 つで囲む。** 例外を捕まえたら `available = false` にし、`LOGGER.warn` を 1 度だけ出して `null` を返す。以後そのセッションでは描かない（画面が落ちない・ログが溢れない）。

### 5.4 `MinimapHudRenderer`（書き換え）

```
render(gfx, partialTicks):
  1. 画面が開いている / level が null / player が null → return        （現行 :62-64 のまま）
  2. controller が null / !state.isVisible() → return                  （現行 :66-69 のまま）
  3. repo が null / !repo.isAvailable() / memos が空 → return          （現行 :71-79 のまま）
  4. geometry = source.geometry(partialTicks)。null → return
  5. geometry.dimension() と mc.level.dimension() が等しくなければ return （第8.2節）
  6. guiScale = mc.getWindow().getGuiScale()
  7. view = new ViewTransform(geometry.worldX(), geometry.worldZ(), geometry.blockPxPhys(),
                              geometry.centerPhysX(), geometry.centerPhysY(),
                              geometry.ps(), geometry.pc())
  8. clip  = ClipRegion.of(geometry)                                    （矩形＋円）
  9. proj  = new Projection(geometry.worldX(), geometry.worldZ(), geometry.blockPxPhys(),
                            2 * geometry.innerHalfPhys(), 2 * geometry.innerHalfPhys())
        → 可視範囲の算出とキャッシュの比較のためだけに使う（回転は view 側が持つ）
 10. GuiCompat.enableScissor(gfx, clip の GUI 座標)
 11. canvasRenderer.render(gfx, new MapFrame(proj, view, clip, (int) guiScale,
                                            mc.level.dimension(), true))
 12. GuiCompat.disableScissor(gfx)                                      （finally で必ず戻す）
```

`canvasRenderer` は `init(controller)` で作られる**ミニマップ専用のインスタンス**で（`MinimapHudRenderer.java:38-42`）、全画面マップの `ScreenMapHost` が持つものとは別である。キャッシュが互いを無効化しないので、このままにする。

---

## 6. `MapCanvasRenderer` 側の変更

### 6.1 引数を `MapFrame` にまとめる

```java
/**
 * 1 フレームの描画条件。
 *
 * @param projection  可視範囲の算出とキャッシュの比較に使う（回転を含まない）
 * @param view        画面座標への変換（回転を含む）
 * @param clip        切り抜き（物理ピクセル）
 * @param guiScale    GUI 倍率
 * @param dimension   今いる次元
 * @param minimapOnly true なら onMinimap() が false のメモを描かない
 */
public record MapFrame(Projection projection, ViewTransform view, ClipRegion clip,
                       int guiScale, Object dimension, boolean minimapOnly) {

    /** 全画面マップ用（回転なし・切り抜きは描画面いっぱい・すべてのメモ）。 */
    public static MapFrame fullscreen(Projection p, int guiScale, Object dimension) {
        return new MapFrame(p, ViewTransform.of(p), ClipRegion.rect(0, 0, p.width(), p.height()),
                guiScale, dimension, false);
    }
}
```

`draw/MapCanvasRenderer.java:81` の `render(GuiGraphicsExtractor gfx, Projection projection, Object dimension, int guiScale)` を `render(GuiGraphicsExtractor gfx, MapFrame frame)` にする（`//? if >=26.1` の分岐の形はそのまま。引数の型だけが変わる）。

呼び出し元は 2 か所だけである。

- `map/ScreenMapHost.java:195` の `canvasRenderer.render(gfx, proj, dim, guiScale);` → `canvasRenderer.render(gfx, MapFrame.fullscreen(proj, guiScale, dim));`
- `map/MinimapHudRenderer.java:113` → 第5.4節のとおり

### 6.2 中の置き換え

| 現在（`draw/MapCanvasRenderer.java`） | 変更後 |
|---|---|
| `:97-100` `!projection.equals(lastProjection)` でキャッシュ版数を上げる | `frame.view()` を比較する（`lastView`）。回転が変わればスクリーン座標を作り直す必要があるため |
| `:112` `Rect view = projection.visibleWorld().expand(...)` | **回転を含む可視範囲**にする（第6.4節） |
| `:113` `for (Memo memo : memos)` の先頭 | `if (frame.minimapOnly() && !memo.onMinimap()) continue;` を足す（論点5） |
| `:122-123` `drawTextMark(gfx, projection.toScreenX(...), projection.toScreenY(...), ...)` | `frame.view().screenX(c.anchor.x(), c.anchor.z())` / `screenY(...)` にする |
| `:137` の文字ループ | 同じく `minimapOnly` の絞り込みを足す |
| `:159` `ensureScreenPoints(Cached c, Projection projection)` | `ViewTransform` と `ClipRegion` を受ける形にする。頂点は `view.screenX(wx, wz)` / `screenY(wx, wz)` で作る |
| `:176` `Scanline.fillRects(sx, sy, n, 0, 0, (int) projection.width(), (int) projection.height())` | clip を `frame.clip()` の矩形にし、円があれば円も渡す（第6.3節） |
| `:300` `drawText(..., Projection projection, int guiScale, double guiW, double guiH)` | `ViewTransform` と `ClipRegion` を受ける形にする。位置は `view.screenX/Y`、画面外判定は clip の矩形で行う。**円があるときは背景矩形の 4 隅が円の内側にあることを確かめ、外れていれば文字を描かず `drawTextMark` を呼ぶ** |
| `drawSegment`（線分の描画） | 円があるときは、線分を円で区間クリップしてから描く（第6.3節） |

`drawSegment` は `rotate2D(atan2(dy, dx))` で任意の角度の線を描いているので、回転後の座標を渡すだけで正しく描ける（線の見た目の実装には手を入れない）。

### 6.3 `ClipRegion` と円のクリップ

```java
/**
 * 切り抜き。長さの単位は物理ピクセル。
 *
 * @param radius 0 以下なら円の制限なし（矩形だけ）
 */
public record ClipRegion(double minX, double minY, double maxX, double maxY,
                         double circleX, double circleY, double radius) {

    public static ClipRegion rect(double minX, double minY, double maxX, double maxY) { ... }

    public static ClipRegion of(MinimapGeometry g) {
        double r = g.circular() ? g.innerHalfPhys() : -1.0;
        return new ClipRegion(g.centerPhysX() - g.innerHalfPhys(), g.centerPhysY() - g.innerHalfPhys(),
                g.centerPhysX() + g.innerHalfPhys(), g.centerPhysY() + g.innerHalfPhys(),
                g.centerPhysX(), g.centerPhysY(), r);
    }

    public boolean hasCircle() { return radius > 0; }

    /** 点が切り抜きの内側か。 */
    public boolean contains(double x, double y) { ... }

    /** 横一行（y が固定）で描ける x の範囲の右端・左端。円が無ければ矩形のまま。 */
    public double rowMinX(double y) { ... }
    public double rowMaxX(double y) { ... }
}
```

`geom/Scanline.java` には円を受けるオーバーロードを足す。

```java
public static List<int[]> fillRects(double[] xs, double[] ys, int n,
                                    int clipMinX, int clipMinY, int clipMaxX, int clipMaxY,
                                    double circleX, double circleY, double circleR)
```

- 既存の 7 引数版は `circleR = -1` で新しい版へ委譲する（全画面マップの挙動は変わらない）
- 円があるときは、行（`y + 0.5`）ごとに `halfW = sqrt(r² - (y + 0.5 - circleY)²)` を求め、交点から作る x 範囲を `[circleX - halfW, circleX + halfW]` と交差させる
- 「同じ x 範囲が縦に続く行は 1 枚にまとめる」最適化は、円で詰めた**後**の x 範囲で比較する（正しさは保たれ、まとまる行が減るだけ）
- `r² - dy² < 0` の行は帯を作らない

線分の円クリップは `geom/` に小さなユーティリティを足す（`geom/CircleClip.java` 等。線分と円の交差を 2 次方程式で解き、`[t0, t1]` に詰める）。線の太さがあるので、**半径は `radius + lineWidth / 2` ではなく `radius` のまま**にして、縁で線の太さぶん内側に収まるようにする。

### 6.4 回転を含むカリング

`draw/MapCanvasRenderer.java:112` は `projection.visibleWorld()` で可視範囲の矩形を作っているが、回転があるときは画面の矩形がワールド上で傾くため、そのままでは正しくない。**ワールド上の包含矩形**に置き換える。

```
halfDiagPhys = hypot(clip 幅 / 2, clip 高さ / 2)                // 円のときは radius
radiusBlocks = (halfDiagPhys + CULL_MARGIN_PIXELS) / blockPxPhys
view = new Rect(worldX - radiusBlocks, worldZ - radiusBlocks,
                worldX + radiusBlocks, worldZ + radiusBlocks)
```

`CULL_MARGIN_PIXELS` は現行の `draw/MapCanvasRenderer.java:31` の `12.0` をそのまま使う。回転の角度に依らず正しく、角度が変わってもこの矩形は変わらないので、毎フレーム作り直しても安い。全画面マップ（回転なし）でも同じ式で包含できる（対角を使うぶん、現行よりわずかに広く拾う。見た目への影響はなく、拾いすぎた分は後段の座標計算と scissor で落ちる）。

---

## 7. `GuiCompat` へ足すもの（切り抜き）

`compat/GuiCompat.java` には scissor 系のメソッドが無いので足す。既存の分岐の形（`//? if >=26.1 {` / `//?} else {`）に合わせる。`nextLayer`（`:118-121`）が同じ形の 2 分岐なので、それに倣う。

```java
//? if >=26.1 {
public static void enableScissor(GuiGraphicsExtractor g, int minX, int minY, int maxX, int maxY) {
    g.enableScissor(minX, minY, maxX, maxY);
}

public static void disableScissor(GuiGraphicsExtractor g) {
    g.disableScissor();
}
//?} else {
/*public static void enableScissor(GuiGraphics g, int minX, int minY, int maxX, int maxY) {
    g.enableScissor(minX, minY, maxX, maxY);
}

public static void disableScissor(GuiGraphics g) {
    g.disableScissor();
}
*///?}
```

根拠（自分で確認した）:

- 26.3: `javap net.minecraft.client.gui.GuiGraphicsExtractor` に `public void enableScissor(int, int, int, int);` と `public void disableScissor();` がある（`C:\Users\radis\.gradle\caches\fabric-loom\26.3\minecraft-client.jar`）
- 1.21.11: Mojang 公式マッピングに `112:113:void enableScissor(int,int,int,int) -> enableScissor` と `117:118:void disableScissor() -> disableScissor`（`C:\Users\radis\.gradle\caches\fabric-loom\1.21.11\layered\working_dir\layered_hash_2198\mojang\client.txt`）
- 1.20.1: 同じマッピングで `net.minecraft.client.gui.GuiGraphics` の下に `263:273:void enableScissor(int,int,int,int) -> enableScissor` と `276:282:void disableScissor() -> disableScissor`

**`>=1.21.11` の分岐は要らない。** `GuiCompat` の 3 分岐（`:8-18`）は `pose()` の API 差（`pushMatrix` / `pushPose`）のためにあるが、scissor は 1.20.1 から 26.x まで同じシグネチャなので、クラス名の差（`GuiGraphicsExtractor` / `GuiGraphics`）だけを吸収すればよい。

**scissor の引数は GUI 座標である**（MC が内部で GUI 倍率を掛ける）。物理ピクセルの `ClipRegion` から渡すときは `guiScale` で割り、外側へ丸める（`floor` / `ceil`）。1 ピクセル内側に丸めると縁のメモが欠けるため。

```
sx0 = (int) Math.floor(clip.minX() / guiScale)
sy0 = (int) Math.floor(clip.minY() / guiScale)
sx1 = (int) Math.ceil(clip.maxX() / guiScale)
sy1 = (int) Math.ceil(clip.maxY() / guiScale)
```

`disableScissor` は `try` / `finally` で必ず呼ぶ。描画中に例外が出たまま scissor が残ると、HUD 全体が切り抜かれたままになる。

---

## 8. 必ず設計する状態

### 8.1 ミニマップが無効・隠れている

| 状態 | 判定 | 振る舞い |
|---|---|---|
| ワールドに入っていない | `getCurrentSession()` が `null` | 描かない |
| ミニマップのセッションが無い | `HudModule#getCurrentSession()` が `null` | 描かない |
| 26.x で今フレーム描いていない | `MinimapPipRenderState` の `shouldRender` が false | 描かない |
| 画面（インベントリ等）が開いている | `ScreenCompat.currentScreen() != null`（現行 `MinimapHudRenderer.java:62`） | 描かない |
| F3 のデバッグ画面 | 26.x の `MinimapRenderer#render` は `Minecraft.debugEntries.isOverlayVisible()` で `return` している（バイトコードで確認）。`MinimapSession#getHideMinimapUnderF3()` が true のときに同じ判定を行う | 描かない |
| ミニマップ自体を無効にしている | `shouldRender` が false（26.x）。〜1.21.x では**判定の経路を確認できていない**（第12節） | 26.x は描かない。〜1.21.x は第12節の未確認項目として実機で確かめる |
| メモが無い・非表示 | 現行 `MinimapHudRenderer.java:67`・`:77` のまま | 描かない |

26.x の `shouldRender` は private フィールドで getter が無いので、`Field#setAccessible(true)` で読む。読めなかった場合は「描いている」とみなして続ける（`shouldRender` が取れないことを理由に機能全体を止めない）。

### 8.2 ディメンションが違う

2 つの食い違いを別に扱う。

1. **メモの次元**: `MapCanvasRenderer` が `frame.dimension()` の変化でキャッシュを捨てる（現行 `draw/MapCanvasRenderer.java:87-93`）。リポジトリは次元ごとのメモを返すので、他次元のメモは入ってこない
2. **地図が描いている次元**: `MinimapProcessor#getLastMapDimension()` が `mc.level.dimension()` と等しくないときは**描かない**（第5.4節の 5）。Xaero は `renderMinimap` の中で毎フレーム `setLastMapDimension` するので、通常は一致する。食い違うのは Xaero's World Map 連携で別次元の地図を出しているときで、そのときメモを重ねると地形と合わない位置に出る

`getLastMapDimension()` が `null` を返した場合は、比較を省いて描く（Xaero がまだ 1 フレームも描いていない起動直後に起きうる）。

### 8.3 リフレクションが失敗した

- **Xaero が入っていない**: `Class.forName("xaero.common.XaeroMinimapSession")` が失敗する。`available = false` にして、以後 `geometry()` は即 `null`。ログは出さない（入っていないのは異常ではない）
- **入っているが取得経路が変わった**（Xaero の更新・XaeroPlus 等の改変版）: 解決できなかった対象の名前を含めて `LOGGER.warn` を**1 度だけ**出し、`available = false`。例外を投げ上げない
- **毎フレームの `invoke` が例外を投げた**: `geometry()` を囲む `try` で捕まえ、`available = false`、`LOGGER.warn` を 1 度だけ。描画は飛ばす
- **XaeroPlus との併用**: XaeroPlus は Xaero のクラスを mixin で拡張する形なので、クラス名・メソッド名は変わらない見込み。ただし確かめていない（第12節）。この設計はどの段で失敗しても「描かないだけ」で済むので、併用で壊れても画面は落ちない

`MinimapHudRenderer.java:115-116` の `catch (Throwable ignored) {}`（毎フレーム黙って捨てる）は、この規則に置き換える。**捨てるのをやめて 1 度だけ記録する**のが要点である。

### 8.4 値が明らかにおかしい

次のいずれかなら描かない（0 除算・巨大な座標でのクラッシュを防ぐ）。

- `blockPxPhys <= 0`（`zoom` か `minimapScale` が 0）
- `innerHalfPhys < 1`（枠が無いほど小さい）
- `innerHalfPhys` が `MinimapSession#getWidth` から引いた値と 2 物理ピクセル以上ずれている → **`getWidth` 側（枠から引いた値）を採る**（`getMinimapSize()` の意味が将来変わっても枠からはみ出さない）
- `ps` / `pc` / `worldX` / `worldZ` が `NaN` / 無限

---

## 9. 変更するファイルと箇所

`path:line` は**自分で開いて確かめた現在の行**である。

### 9.1 書き換える

| ファイルと箇所 | 変更 |
|---|---|
| `src/main/java/io/github/radishsprouts/mapscribble/map/MinimapHudRenderer.java:24` | javadoc から「FTB Chunks Minimap」を外し、Xaero 専用と書く（論点7） |
| 同 `:30-33` | 未使用のフィールド 4 つ（`xaeroGetMinimapMethod` / `xaeroMinimapXField` / `xaeroMinimapYField` / `xaeroMinimapSizeField`）を削除。リフレクションは `XaeroMinimapSource` へ移す |
| 同 `:44-54` | `initReflection()` を削除（`XaeroMinimapSource` が持つ） |
| 同 `:56-60` | `render` の引数に `float partialTicks` を足す |
| 同 `:81-83` | `initReflection()` の呼び出しを外し、`MinimapSource` 経由にする |
| 同 `:86-117` | `renderXaeroMinimapOverlay` を第5.4節の流れに置き換える。`:106-111` の `getScreenWidth()` / `new Projection(player.getX(), player.getZ(), 1.0, winW, winH)` を捨てる。`:115-116` の `catch (Throwable ignored) {}` を第8.3節の規則にする |
| `src/main/java/io/github/radishsprouts/mapscribble/draw/MapCanvasRenderer.java:81` | `render` の引数を `MapFrame` にする（`//? if >=26.1` の形は維持） |
| 同 `:97-100` | キャッシュ版数の比較を `ViewTransform` で行う |
| 同 `:112` | 可視範囲を回転を含む包含矩形にする（第6.4節） |
| 同 `:113` の直後 | `minimapOnly` のときに `memo.onMinimap()` が false のメモを飛ばす |
| 同 `:122-123` | `projection.toScreenX/Y` を `view.screenX/Y` にする |
| 同 `:130` | `drawActiveStroke` に `MapFrame` を渡す（中の `projection.toScreenX/Y` も `view` に替える） |
| 同 `:134-145` | 文字ループに `minimapOnly` の絞り込みを足す。`guiW` / `guiH` は clip の矩形から出す |
| 同 `:159-176` | `ensureScreenPoints` を `ViewTransform` / `ClipRegion` を受ける形にし、`Scanline.fillRects` の clip を `frame.clip()` にする（円があれば円も渡す） |
| 同 `:300-330` | `drawText` を `ViewTransform` / `ClipRegion` を受ける形にし、円のとき 4 隅の判定を入れる（外れていれば `drawTextMark`） |
| `src/main/java/io/github/radishsprouts/mapscribble/geom/Scanline.java:30` | 円を受ける 10 引数のオーバーロードを足し、既存の 7 引数版はそれへ委譲する |
| `src/main/java/io/github/radishsprouts/mapscribble/compat/GuiCompat.java:118-124` の近く | `enableScissor` / `disableScissor` を 2 分岐で足す（第7節） |
| `src/main/java/io/github/radishsprouts/mapscribble/map/ScreenMapHost.java:195` | `canvasRenderer.render(gfx, proj, dim, guiScale)` を `canvasRenderer.render(gfx, MapFrame.fullscreen(proj, guiScale, dim))` にする |
| `src/main/java/io/github/radishsprouts/mapscribble/MapScribble.java:131-135` | Fabric のフックから `partialTicks` を渡す（`>=26.1` は `deltaTracker.getGameTimeDeltaPartialTick(false)`、それ以外は `tickDelta`） |
| 同 `:153-163` | NeoForge の 3 分岐も同じく渡す（`>=26.1` と `>=1.21.11` は `DeltaTracker`、それ以外は `float partialTick`） |
| 同 `:233-234` | Forge（1.20.1）の `registerAbove` も `partialTick` を渡す |

### 9.2 新しく作る

| ファイル | 中身 |
|---|---|
| `src/main/java/io/github/radishsprouts/mapscribble/geom/ViewTransform.java` | 第4節・論点1の record |
| `src/main/java/io/github/radishsprouts/mapscribble/geom/ClipRegion.java` | 第6.3節の record |
| `src/main/java/io/github/radishsprouts/mapscribble/geom/CircleClip.java` | 線分を円で区間クリップする純粋な計算 |
| `src/main/java/io/github/radishsprouts/mapscribble/draw/MapFrame.java` | 第6.1節の record |
| `src/main/java/io/github/radishsprouts/mapscribble/map/minimap/MinimapGeometry.java` | 第5.1節の record |
| `src/main/java/io/github/radishsprouts/mapscribble/map/minimap/MinimapSource.java` | 第5.2節の interface |
| `src/main/java/io/github/radishsprouts/mapscribble/map/xaero/XaeroMinimapSource.java` | 第5.3節。リフレクションはここだけ |

### 9.3 バージョン分岐（stonecutter）が要る箇所・要らない箇所

| 箇所 | 分岐 | 理由 |
|---|---|---|
| `GuiCompat` の `enableScissor` / `disableScissor` | **要る**（`>=26.1` の 2 分岐） | 引数の型が `GuiGraphicsExtractor` / `GuiGraphics` で違う。メソッド名・引数は 1.20.1〜26.x で同じなので `>=1.21.11` の分岐は要らない |
| `MapCanvasRenderer#render` / `drawText` 等の引数の型 | **要る**（既にある `//? if >=26.1` をそのまま使う） | 同じ理由 |
| `MinimapHudRenderer#render` の引数の型 | **要る**（既にある分岐） | 同じ理由 |
| `MapScribble` のフックから `partialTicks` を取る所 | **要る**（`>=26.1` / `>=1.21.11` / else の 3 分岐。既にある） | `DeltaTracker#getGameTimeDeltaPartialTick(boolean)` は 1.21 以降。1.20.1 は `float` が直接来る |
| `XaeroMinimapSource` の中身 | **要らない** | すべてリフレクション。26.x の `MinimapPipRenderState` は「クラスが取れたら使う」実行時の分岐で扱う（クラスが無い 1.20.1 では `PushState` 経路へ落ちる）。この形なら Xaero 側の版差・XaeroPlus のような改変版にも、ソースを分けずに対応できる |
| `ViewTransform` / `ClipRegion` / `CircleClip` / `MinimapGeometry` / `MapFrame` / `Scanline` | **要らない** | MC の API に触らない純粋な計算と record |

---

## 10. 負荷の出る箇所と抑え方

ミニマップのオーバーレイは**毎フレーム**動く。`rules/14-low-load.md` に沿って、負荷の出る箇所と抑え方を列挙する。

| 箇所 | 負荷 | 抑え方 |
|---|---|---|
| リフレクションの解決（`Class.forName` / `getMethod` / `getField`） | 文字列検索とセキュリティチェックで重い | **1 度だけ行い、`Method` / `Field` / static フィールドの値をインスタンスに持つ**（第5.3節）。毎フレームは `invoke` / `get` だけ。失敗したら `available = false` で以後は 1 行で抜ける |
| 毎フレームの `invoke` | 1 回あたりは小さいが数が増える | 1 フレームあたり十数回に収める（第5.3節の流れがそのまま回数）。同じ値を 2 度取らない（`processor` / `ccm` は 1 フレーム内で使い回す）。`Field#setAccessible(true)` は解決時に 1 度だけ呼ぶ |
| 画面外のメモの処理 | メモが増えると線形に増える | **ワールド座標の包含矩形で早く落とす**（第6.4節）。可視半径は `innerHalfPhys / blockPxPhys` ブロックで、ミニマップは全画面マップよりずっと狭いため、ほとんどのメモはここで落ちる。`minimapOnly` の絞り込み（論点5）もループの入口で行い、落ちるメモの頂点計算・キャッシュ更新をしない |
| スクリーン座標と塗りの作り直し | 回転ミニマップでは毎フレーム角度が変わるので、`ViewTransform` が毎フレーム変わり、頂点とスキャンラインの矩形を作り直すことになる | 作り直す対象を**画面に残っているメモだけ**にする（カリングの後で `ensureScreenPoints` を呼ぶ現行の順序を保つ）。ワールド座標の包含矩形・行分割・文字幅のキャッシュ（`Cached.bbox` / `lines` / `lineWidths` / `maxLineWidth`）は `ViewTransform` に依らないので**作り直さない**。北固定のときは `ViewTransform` が変わらないので、スクリーン座標も作り直されない |
| `Scanline` の矩形の枚数 | `fill` の呼び出し回数がそのまま GL の負荷になる | 「同じ x 範囲が続く行を 1 枚にまとめる」既存の最適化を保つ（`geom/Scanline.java:11-15`）。clip をミニマップの内側矩形に絞ることで、枚数そのものが減る（画面全体を clip にしている現在よりも減る） |
| 円のクリップ | 行ごとに `sqrt` を 1 回 | 行の数は内側の一辺（物理ピクセル）ぶんで、ミニマップでは最大でも 500 行程度。`sqrt` は行ごと 1 回に留め、矩形ごと・点ごとには呼ばない。四角のミニマップでは 1 度も呼ばない（`radius <= 0` で分岐） |
| scissor | `enableScissor` / `disableScissor` で GL の状態が変わる | 1 フレームに**1 組だけ**掛ける（メモごと・図形ごとに掛け替えない）。文字のために掛け替えない（論点2の不採用案） |
| 描くものが無いフレーム | 無駄な前処理 | 既存の早期 `return`（`MinimapHudRenderer.java:62-79`）を保ち、`geometry()` の取得より**前**にメモの有無を見る。Xaero が入っていない環境では `available = false` の 1 行で終わる |
| ログ | 毎フレームの `warn` はそれ自体が重く、ログが溢れる | 失敗の記録は 1 度だけ（第8.3節） |

**この設計で機能を削った箇所は無い。** 円形ミニマップでも内側の円いっぱいまで描き、回転でも全部のメモを描き、文字の大きさも落としていない。

---

## 11. 受け入れ基準

実機（開発用クライアント）で次を確かめる。1〜12 は 1 つでも満たさなければ直っていない。

1. **北固定 ON・四角のミニマップで**、全画面マップで描いた囲み区画が、ミニマップの枠の中の正しい位置・正しい大きさで出る。プレイヤーが歩くと地形と一緒に動き、ずれていかない
2. **北固定 OFF（Xaero の既定・回転する）で**、プレイヤーが向きを変えるとメモが地形と同じだけ回る。向きを一周させても地形との重なりがずれない
3. **円形のミニマップで**、メモが円の外（黒い縁・その先の HUD）へはみ出さない。円の縁に接するメモは、縁のところで切れて見える
4. **ズームを変えると**（Xaero の既定のズームキー）、メモの大きさが地図と同じ比率で変わる
5. **洞窟モードで**、メモが地形と同じ位置に出る
6. **拡大マップ（既定で `Z` 長押し等）で**、枠が画面中央へ移ってもメモが枠の中に追従する
7. **`onMinimap()` が false のメモ**が、ミニマップには出ず、全画面マップには出る
8. **文字の表示切替を OFF にすると**、ミニマップでも文字が消え、文字メモの位置に菱形の印が残る（`drawTextMark` の挙動がミニマップでも効く）
9. **ミニマップが出ていないとき**（インベントリを開く・F3・ミニマップを無効にする）に、メモだけが画面に残らない
10. **Xaero を入れていない環境で**、ログに例外・警告が出ず、メモが HUD に出ない
11. **ネザー・エンドへ移動しても**、他の次元のメモが出ない
12. **Xaero's World Map 連携で別次元の地図を出している間**、メモを描かない
13. 1.20.1 / 1.21.x / 26.x のいずれでも 1〜12 が成り立つ（Fabric / Forge / NeoForge のうちビルドしている組み合わせで）
14. メモを 50 件ほど置いた状態で、ミニマップを出していないときと比べて fps が目に見えて落ちない

既存のテスト（`src/test/java/io/github/radishsprouts/mapscribble/`）では、`geom/ViewTransform`・`geom/ClipRegion`・`geom/CircleClip`・`Scanline` の円クリップが純粋な計算なので単体テストを書ける。次を含めること。

- `ViewTransform.of(projection)` の `screenX/screenY` が `Projection#toScreenX/toScreenY` と一致する（回転なしで退化すること）
- `renderAngle = 90`（北固定）で東が右・南が下になる
- `renderAngle = -90`（yaw 0）で、プレイヤーの向きが上になる
- 円クリップが、円の外の行で帯を作らない。円の中心の行で帯の幅が直径に等しい
- 線分の円クリップが、両端が外・中央が内側の線分を 1 本の区間に詰める

---

## 12. 未確認のこと（実機で確かめる）

**この設計の根拠のうち、jar で確かめられなかったものと、実機でしか確かめられないものを分けて挙げる。** Dev-Engineer は、実装中にここが違っていたら Dev-Director 経由で差し戻してよい。

| 項目 | 状態 | 確かめ方 / 外れたときの対処 |
|---|---|---|
| `SHAPE` の値と形の対応（どの値が円か） | **未確認**。`RangeConfigOption`（整数）で、26.x の `MinimapRenderer` のバイトコードに `SHAPE == 1` との比較がある。1 が円かどうかは分からない | 実機で四角 / 円を切り替え、値をログに出して確かめる。実装では `circular = (shape != 0)` を既定にし、逆だったら反転する。**外れていても矩形 scissor は効くので、HUD へはみ出す事故にはならない** |
| 枠の縁の太さ 9（`padPhys = 9 * minimapScale`） | 前段の調査でバイトコードから導出した値だが、**今回は定数の宣言そのものを確認できていない**（26.x の `MinimapPipRenderState` に `HORIZONTAL_PADDING` / `VERTICAL_PADDING` があるが、値を読めていない） | 第8.4節の検算（`getWidth` から引いた値と比べる）が食い違いを吸収する。実機でメモが枠の縁と数ピクセルずれていたら、この値を疑う |
| `innerHalfPhys = minimapSize * minimapScale / 4` | 前段の調査の導出。`getWidth` から引いた値と一致することを検算した | 第8.4節のとおり、ずれたら `getWidth` 側を採る |
| `blockPxPhys = zoom * minimapScale / 2` | 前段の調査の導出。可視半径が `minimapSize / (2 * zoom)` ブロックになることと整合する | 実機で、ミニマップの端に見えている地形の座標（F3）とメモの位置を比べる。ずれるならこの係数を疑う |
| `MinimapSession#getWidth(double)` の引数が GUI 倍率か | **未確認**（`double` を 1 つ受けることだけを確認した） | `guiScale` を渡し、返る値が実際の枠の一辺（GUI 座標）と合うかを実機で見る |
| 26.x の HUD 要素のフックが PiP のミニマップより**後**に描かれるか | **未確認**。26.x ではミニマップが PiP の別パスで描かれる。現在のフックは `VanillaHudElements.SLEEP` の後（`MapScribble.java:131-133`） | 実機でメモがミニマップの下に隠れるかを見る。隠れるなら `HudElementRegistry.addLast` に替える（分岐は `>=26.1` のみ）。それでも隠れるなら、Xaero の PiP の後に走るフックを探す必要があり、その時点で Dev-Director へ差し戻す |
| 〜1.21.x で「ミニマップを無効にしている」ことの判定 | **未確認**。26.x は `shouldRender` で分かるが、それ以前の経路を特定できていない | 実機でミニマップを無効にし、メモだけが残るかを見る。残るなら、`MinimapSession` / `MinimapProcessor` から無効を表す値を探す（`getHideMinimapUnderScreen` / `getHideMinimapUnderF3` は別の設定である） |
| 1.20.1 系で拡大マップのとき `PushState.x/y` が中央寄せ後の値になるか | **未確認**。26.x は `MinimapPipRenderState` で中央寄せ後の値が読めることを確認した | 実機で拡大し、メモが枠に追従するかを見る。追従しないなら、`CENTERED_ENLARGED` を読んで第2.3節の式で自分で中央へ寄せる |
| `MinimapSession#getMinimap()` の有無 | **未確認**。`javap` の出力に `public xaero.hud.minimap.Minimap getMinimap();` の 1 行があったが、どのクラスのものか特定できていない | この設計では使わない（回転角は第2.2節の式で自前に計算する。式はバイトコードで確認済み） |
| XaeroPlus を併用したときにクラス名・メソッド名が変わらないか | **未確認** | 実機で併用して確かめる。変わっていたら第8.3節のとおり「描かない＋警告 1 回」で終わる（落ちない） |
| `getLastMapDimension()` が通常は `mc.level.dimension()` と一致するか | **未確認**（`setLastMapDimension` が `renderMinimap` の中で呼ばれることは前段の調査の指摘。今回は setter / getter の存在だけを確認した） | 実機で、同じ次元にいるのにメモが出ないなら、この比較を疑う（比較を外せば描ける） |
| 文字が実際に読めるか（ミニマップの大きさに対して） | 実機でしか分からない | 既定サイズ・最小サイズのミニマップで、`SMALL` / `MEDIUM` / `LARGE` の文字を見る。読めないという判断になった場合は、文字の大きさの既定ではなく**ミニマップに文字を出すかどうかの切り替え**を足す側で解く（`memo.onMinimap()` と同じ粒度）。文字を勝手に縮めない |

---

## 13. 作業の順序（Dev-Engineer 向け）

1. `geom/ViewTransform` / `geom/ClipRegion` / `geom/CircleClip` と `Scanline` の円クリップを足し、**単体テストを先に通す**（第11節の 5 項目）。ここは MC に触らないので、どのバージョンでもすぐ試せる
2. `draw/MapFrame` を足し、`MapCanvasRenderer` を `MapFrame` を受ける形に替える。`ScreenMapHost.java:195` を `MapFrame.fullscreen(...)` にして、**全画面マップの見た目が変わっていないこと**を先に確かめる（回転なしで退化するので変わらないはず）
3. `GuiCompat` に scissor を足す
4. `map/minimap/MinimapGeometry` / `MinimapSource` と `map/xaero/XaeroMinimapSource` を足す。この段階で、取れた値をログに 1 度だけ出して第12節の未確認項目を潰す
5. `MinimapHudRenderer` を第5.4節の流れに置き換える。`MapScribble` のフックから `partialTicks` を渡す
6. 第11節の受け入れ基準で確かめる（Dev-QA の検証に出す前に、1〜12 を自分で一通り見る）

**この設計は、Dev-Engineer の実装と Dev-QA の検証を経るまで正しかったとは言えない。** 第12節の未確認項目が外れていた場合は、Dev-Director 経由で差し戻してよい。
