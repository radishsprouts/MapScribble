# MapScribble

JourneyMap の全画面マップに、手書きでメモを描ける Fabric MOD です（Minecraft 26.1.2 / 26.2 / 26.3）。[English summary is at the bottom.](#english-summary)

主な機能は、**地図の区画を手書きで囲み、文字を付ける**ことです。ほかに、線を引く・文字だけを置くこともできます。メモは自分のクライアントにだけ保存され、ほかのプレイヤーとは共有されません。

## できること

| メモの種類 | 描き方 | 地図での見え方 |
|---|---|---|
| 囲み（主機能） | 地図をドラッグして区画をなぞる。指を離すと始点と終点がつながって閉じる | 手書きの輪郭線、半透明の塗り、中央に文字 |
| 線 | ドラッグした跡をそのまま残す（閉じない） | 手書きの線。文字を付けた時は線の中ほどに出る |
| 文字 | 地図をクリックした場所に文字だけを置く | 文字だけ |

- 囲み・線・文字のそれぞれに、色（10色）と文字を付けられます。囲みは塗りの濃さ（なし／うすい／ふつう／こい）も選べます
- 線の太さは細・中・太の3段階です
- なぞった形は小数のワールド座標で保存するので、地図を拡大しても、なぞったとおりの滑らかな形で表示されます
- ミニマップにも表示できます（メモごとに表示・非表示を選べます）

## 対応環境と必要な MOD

- Minecraft 26.1.2 / 26.2 / 26.3（Fabric のみ。NeoForge には対応していません）
- Java 25
- Fabric Loader 0.19.5 以上
- 次の MOD が必要です。開発とテストで使っている版は下表のとおりです

| Minecraft | Fabric API | JourneyMap |
|---|---|---|
| 26.1.2 | 0.155.3+26.1.2 | 6.0.10（`26.1.2-6.0.10+fabric`） |
| 26.2 | 0.161.0+26.2 | 6.0.10（`26.2-6.0.10+fabric`） |
| 26.3 | 0.161.0+26.3 | 6.0.10（`26.3-6.0.10+fabric`） |

- **クライアントだけに入れる MOD です。サーバー側の導入は要りません**（Velocity 配下のサーバーでも同じです）
- JourneyMap の API は MapScribble の jar に同梱していません。JourneyMap 本体が入っていれば動きます

## 使い方

### 手書きモードに入る

1. JourneyMap の全画面マップを開く
2. 画面の右の列にある鉛筆のボタンを押す（ホバーで「手書きモードにする」と出ます）。もう一度押すと「手書きモードを終える」になり、通常の操作（ドラッグで地図を動かす）に戻ります

手書きモードの切替キーも用意していますが、**既定では未割り当て**です。使うには Minecraft の「設定 → 操作設定」の分類「MapScribble」にある「手書きモードの切替」にキーを割り当ててください。

手書きモードの間だけ、画面の左上にツールの帯が出ます。

### 道具

| 道具 | キー（手書きモード中の全画面マップ上） | 操作 |
|---|---|---|
| 囲み | 1 | ドラッグして範囲を囲み、離すと文字入力の画面が開く |
| 線 | 2 | ドラッグした跡を線として残す |
| 文字 | 3 | クリックした場所に文字を置く |
| 消しゴム | 4 | クリックしたメモを消す |
| 編集 | 5 | クリックしたメモの文字や色を直す（削除もここから） |

キーは操作設定で変えられます。囲みが小さすぎる場合や、線が短すぎる場合は、メモを作らずに取り消します。

### 色・太さ

帯の2段目で、線の太さ（細・中・太）と、色（赤・橙・黄・黄緑・水色・青・紫・桃・白・黒の10色）を選びます。最後に選んだ色・太さは次回も覚えています。

### 文字入力の画面

囲み・線・文字を描き終えると、文字入力の画面が開きます（背後に地図が暗く見えます）。

- 文字は複数行で、200文字までです。Enter は改行です
- 色、塗りの濃さ（囲みだけ）、文字の大きさ（小・中・大）、ミニマップに出すかどうかを選べます
- Ctrl+Enter か「確定」ボタンで保存します。Esc か「キャンセル」で取り消します（新しいメモなら、描いた形ごと取り消されます）
- 文字のメモは、文字が空のままでは確定できません。囲みと線は、文字なしで確定できます

### 消しゴム・編集

- 消しゴムは、クリックしたメモを消します。消したあとは Ctrl+Z で戻せます
- 編集は、クリックしたメモの文字・色などを変える画面を開きます。この画面から削除もできます
- メモの上にカーソルを置くと、消す・編集する対象の文字の1行目が表示されます

### 元に戻す・やり直す

- 元に戻す: Ctrl+Z（帯のボタンでも）
- やり直す: Ctrl+Y または Ctrl+Shift+Z（帯のボタンでも）
- 履歴は100件までで、ディメンションや接続先が切り替わると破棄されます

### 表示の切替

帯の右端の目のボタンで、自分のメモを地図に出す・隠すを切り替えます。隠している間も、囲み・線・文字は描けます（描くと表示に戻ります）。

### ミニマップへの表示

メモごとに、文字入力の画面の「ミニマップ」で表示・非表示を選べます（既定は表示）。全画面マップの描画は MapScribble が自前で行い、ミニマップには JourneyMap のオーバーレイとして出します。

## メモの保存先

JourneyMap のデータフォルダの中の `addon-data/mapscribble/` に、ディメンションごとのファイルで保存します。

```
<JourneyMap のデータフォルダ>/addon-data/mapscribble/<namespace>__<path>.json
例: minecraft__overworld.json, minecraft__the_nether.json
```

- シングルプレイとマルチプレイ、サーバーごとに、JourneyMap が分けているフォルダごとに別々になります（JourneyMap が地図を分けるのと同じ単位です）
- メモは自分だけのものです。サーバーには何も送らず、ほかのプレイヤーにも見えません
- 変更から約1秒後にまとめて書き出します
- MapScribble が知らない形式のファイルは、読み込まず、上書きもしません（その間は帯に「メモを読み込めません（ファイルはそのまま）」と出ます）

## 複数サーバー（Velocity）

MapScribble は独自のサーバー判定を持たず、JourneyMap がサーバーを区別する仕組みにそのまま乗っています。JourneyMap の地図が別になるサーバー同士は、メモも別になります。

**既知の制限:** Velocity で資源サーバー（res）から一般サーバー（lis）へ移った時、JourneyMap 自身が切り替わりに気付かず、res のメモが lis に表示されます。JourneyMap の地図も res のフォルダを使い続けるので、地図とメモの対応はずれません。切断して入り直すと正しくなります（lis から res へ移る時は、正しく分かれます）。JourneyMap 側の挙動で、MapScribble では直していません。

## その他の既知の制限

- 全画面マップの右の列のボタンが、JourneyMap 6.0.10 によってアドオンのボタンとして二重に登録されることがあります。MapScribble は自分のボタンだけを1つにしています
- Webmap（ブラウザで見る地図）にはメモを出しません

## 開発（ビルド）

Java 25 が必要です。複数の Minecraft 版を1つのソースからビルドするために [Stonecutter](https://stonecutter.kikugie.dev/) を使っています。開発機は Windows なので、コマンドは PowerShell の形を主に書きます。

```powershell
.\gradlew.bat buildAndCollect
```

3つの jar が `build/libs/<MOD版>/` に集まります。

```
build/libs/0.1.0/mapscribble-0.1.0+26.1.2.jar
build/libs/0.1.0/mapscribble-0.1.0+26.2.jar
build/libs/0.1.0/mapscribble-0.1.0+26.3.jar
```

単体テスト（JUnit）は次のとおりです。

```powershell
.\gradlew.bat test
```

実際のクライアントを起動する client gametest は、版ごとに実行します。

```powershell
.\gradlew.bat :26.1.2:runClientGameTest
.\gradlew.bat :26.2:runClientGameTest
.\gradlew.bat :26.3:runClientGameTest
```

gametest を回すときは、メモリを絞らないと足りなくなることがあるので、Gradle の JVM のメモリを指定します（他の重い処理と同時に動かさないでください）。

```powershell
.\gradlew.bat :26.1.2:runClientGameTest '-Dorg.gradle.jvmargs=-Xms64m -Xmx700m'
```

Linux（CI を含む）で実行する場合は次のとおりです。

```bash
./gradlew buildAndCollect test
./gradlew :26.1.2:runClientGameTest
```

## 開発（検証の構成）

- **複数サーバーの検証構成**（手元用）: `G:\minecraft_local_test\velocity_jm\`。Velocity と Folia 2台（`lis` / `res`）の構成で、本番の MapModCompanion を写したものです。ワールドは新規に作っています。複数サーバーでメモの保存先が分かれることの確認に使います
- **開発用クライアント**のインスタンス: `G:\minecraft\instances\Fabric Dev 26.1.2\minecraft`

## GitHub Actions

`.github/workflows/` に3本あります。

- `build.yml`: push・プルリクエスト・手動で、3版のビルドと単体テスト、jar に JourneyMap API のクラスが混ざっていないことの検査、3版の client gametest を実行します
- `release.yml`: `v*` のタグ（または手動実行）で GitHub Release を作り、3つの jar を添付します
- `curseforge.yml`: リリースから呼ばれる、CurseForge へのアップロード用の定義です。必要な設定が無ければ何もせずに終わります

## ライセンス

MIT（[LICENSE](LICENSE)）。JourneyMap の API は MapScribble の jar に同梱していません（JourneyMap は Techbrew の製品で、MapScribble はその公式 MOD ではありません）。

## English summary

MapScribble lets you draw handwritten notes on the JourneyMap fullscreen map: outline an area by hand and attach text to it (the main feature), draw lines, or place text. Notes are stored only on your own client, per dimension and per server, under JourneyMap's `addon-data/mapscribble/` folder.

- Minecraft 26.1.2 / 26.2 / 26.3, Fabric only, Java 25. Requires Fabric API and JourneyMap (client side only; nothing to install on the server).
- Turn on scribble mode with the pencil button in the right column of the fullscreen map. Tool keys are 1 to 5 (outline, line, text, eraser, edit), and Ctrl+Z / Ctrl+Y undo and redo. The mode-toggle key is unassigned by default (Controls, category MapScribble).
- Known limitation: on a Velocity network, moving from the resource server to the general server can leave JourneyMap unaware of the switch, so the resource server's notes are shown on the general server (JourneyMap's own map stays on the old folder too). Reconnecting fixes it. This is JourneyMap's behavior and is not worked around in this mod.
- Build: `./gradlew buildAndCollect` produces one jar per Minecraft version in `build/libs/<mod version>/`. License: MIT.
