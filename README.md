# MapScribble

JourneyMap・Xaero's World Map・FTB Chunks の全画面マップに、手書きでメモを描ける Fabric / Forge / NeoForge 対応のクライアント MOD です（Minecraft 1.20.1 / 1.20.6 / 1.21.1 / 1.21.4 / 1.21.11 / 26.1.2 / 26.2 / 26.3）。[English summary is at the bottom.](#english-summary)

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
- ミニマップにも表示できます（メモごとに表示・非表示を選べます）。対応するのは JourneyMap と Xaero's Minimap のミニマップで、FTB Chunks のミニマップには対応していません
- Velocity などのプロキシの配下で、サーバーごとにメモを分けられます（0.4.0 から。[複数サーバー（Velocity）](#複数サーバーvelocity)）
- 表示の文言は10言語あります（日本語・英語・ドイツ語・スペイン語・フランス語・韓国語・ポルトガル語（ブラジル）・ロシア語・中国語（簡体字・繁体字））

## 対応環境と必要な MOD

- 対応する Minecraft とローダーの組み合わせは次の表のとおりです（全21構成）。Minecraft の版ごとに jar が別で、○の無い組み合わせの jar はありません
- Java は Minecraft の版で決まります（表の右端）
- Fabric 版は Fabric Loader 0.15.0 以上が必要です（`fabric.mod.json` の宣言。Minecraft の版に対応した Fabric Loader を使ってください）
- Forge 版・NeoForge 版の jar は、表にある Minecraft の版だけで読み込まれます（たとえば 1.21.11 用の jar は 1.21.11 でのみ読み込まれます）

| Minecraft | Fabric | Forge | NeoForge | Java |
|---|:-:|:-:|:-:|:-:|
| 1.20.1 | ○ | ○ | - | 17 |
| 1.20.6 | ○ | ○ | ○ | 21 |
| 1.21.1 | ○ | - | ○ | 21 |
| 1.21.4 | ○ | - | ○ | 21 |
| 1.21.11 | ○ | ○ | ○ | 21 |
| 26.1.2 | ○ | ○ | ○ | 25 |
| 26.2 | ○ | ○ | ○ | 25 |
| 26.3 | ○ | ○ | ○ | 25 |

地図 MOD は次の3つに対応していて、**いずれか1つが入っていれば動きます**（MapScribble はどの地図 MOD も必須の依存として宣言していません）。複数入れている場合は、開いた全画面マップに合わせて切り替わります。

| 地図 MOD | 全画面マップ | ミニマップ |
|---|:-:|:-:|
| JourneyMap | ○ | ○ |
| Xaero's World Map / Xaero's Minimap | ○ | ○ |
| FTB Chunks | ○ | - |

- JourneyMap は、公式のプラグイン API（JourneyMap API 2.0 系）経由で連携します。ミニマップへは JourneyMap のオーバーレイとして出します。この API の配布が無い Minecraft 1.20.6 向けの jar（JourneyMap 5.10 系）だけは例外で、全画面マップにはリフレクションで描きますが、**JourneyMap のミニマップには出しません**
- Xaero は、全画面マップ（Xaero's World Map）とミニマップ（Xaero's Minimap）の両方に描けます。ミニマップへは MapScribble が自前で描きます
- FTB Chunks は全画面マップだけです。**FTB Chunks のミニマップには対応していません**。FTB Library 26.1 以降の新しいパッケージ構成と、それより前の構成の両方に対応しています

Fabric 版では Fabric API が必要です（これは必須の依存です）。開発とテストで使っている版は下表のとおりです。

| Minecraft | Fabric API | Forge | NeoForge | JourneyMap |
|---|---|---|---|---|
| 1.20.1 | 0.92.12+1.20.1 | 47.4.28 | - | 6.0.7 |
| 1.20.6 | 0.100.8+1.20.6 | 50.2.14 | 20.6.144 | 5.10.0 |
| 1.21.1 | 0.116.17+1.21.1 | - | 21.1.264 | 6.0.9 |
| 1.21.4 | 0.119.4+1.21.4 | - | 21.4.161 | 6.0.0-beta.47 |
| 1.21.11 | 0.141.6+1.21.11 | 61.2.4 | 21.11.48 | 6.0.0 |
| 26.1.2 | 0.155.3+26.1.2 | 64.1.5 | 26.1.2.122 | 6.0.10 |
| 26.2 | 0.161.0+26.2 | 65.1.5 | 26.2.0.93 | 6.0.10 |
| 26.3 | 0.165.0+26.3 | 66.1.0 | 26.3.0.97 | 6.0.10 |

Forge・NeoForge の列は、その構成のビルドに使う Forge・NeoForge 自体の版です。JourneyMap の列は、開発用クライアントへ入れて確かめている JourneyMap 本体の版です（Xaero's World Map / Xaero's Minimap / FTB Chunks の版はこの表に挙げていません）。地図 MOD は、使うローダー用の jar を入れてください。

- **クライアントだけに入れる MOD です。サーバー側の導入は要りません**（Velocity 配下のサーバーでも同じです）
- JourneyMap の API は MapScribble の jar に同梱していません。JourneyMap 本体が入っていれば動きます
- Xaero's World Map / Xaero's Minimap / FTB Chunks との連携はリフレクションで行うので、これらの MOD のクラスも同梱していません。入っていない地図 MOD の分は、何も読み込まずに黙って無効になります

## 使い方

### 手書きモードに入る

1. 地図 MOD の全画面マップを開く（JourneyMap / Xaero's World Map / FTB Chunks のどれでも構いません）
2. 鉛筆のボタンを押す（ホバーで「手書きモードにする」と出ます）。もう一度押すと「手書きモードを終える」になり、通常の操作（ドラッグで地図を動かす）に戻ります

ボタンの位置は地図 MOD によって変わります。

| 地図 MOD | 鉛筆のボタンの位置 |
|---|---|
| JourneyMap（公式プラグインで連携する時） | 全画面マップの右の列（JourneyMap のテーマのボタンとして並びます） |
| JourneyMap（1.20.6 向けの jar など、公式プラグインが動かない時） | 画面の右上。JourneyMap の閉じるボタンの左隣 |
| Xaero's World Map / FTB Chunks | 画面の右上 |

### 鉛筆のボタンを動かす（画面の右上に出るボタンだけ）

地図 MOD の表示（方角表示など）に鉛筆のボタンが重なる時は、ボタンを動かせます。動かせるのは、MapScribble が画面に直接描く上の表の「画面の右上」のボタン（Xaero's World Map・FTB Chunks・JourneyMap の公式プラグインが動かない時）です。**JourneyMap の公式プラグインで右の列に並ぶボタンは、位置を JourneyMap が決めるので動かせません。**

| 操作 | 結果 |
|---|---|
| 左ボタンを押したまま動かして離す | ボタンがカーソルに付いてきて、離した所に置かれます（手書きモードは切り替わりません） |
| Shift を押しながら左クリック（動かさずに離す） | ボタンを掴みます。ボタンを押さなくてもカーソルに付いてくるので、次に左クリックした所に置かれます（ドラッグできない時の手段） |
| 右クリック | 元の位置へ戻します |
| 動かしている間に Esc（掴んでいる間は右クリックも） | 動かす前の位置に戻して取り消します。地図の画面は閉じません |

- 動かさずに左ボタンを離した時だけ、手書きモードが切り替わります（押した瞬間ではなく、離した時に切り替わります）。押した点から 4 画面ピクセル以上動かすとドラッグになります
- ボタンは画面の外へ出ません。ツールの帯と重なった時は帯が手前に出て、重なった部分のクリックは帯が受けます（動かしている間だけ、ボタンが帯より手前に見えます）
- 位置は地図 MOD ごとに別々に、近い画面の端からの距離で覚えます（`<.minecraft>/config/mapscribble-ui.json` の `toggleButtons`）。画面の大きさや GUI の大きさを変えても、端に沿った表示の隣に置いたままになります。元の位置に置いた時と、右クリックで戻した時は、記録を消します

手書きモードの切替キーも用意していますが、**既定では未割り当て**です。使うには Minecraft の「設定 → 操作設定」の分類「MapScribble」にある「手書きモードの切替」にキーを割り当ててください。

手書きモードの間だけ、画面の左上にツールの帯が出ます。帯は、ボタンの無い余白を左ドラッグすると画面内の好きな位置へ動かせます（画面の外へは出ません）。置いた位置は次回も覚えています。

帯の下には、1行の説明の行が出ます。選んでいる道具の説明のほか、メモを消した・元に戻した・使うメモ帳が変わったといった知らせを数秒だけ出します。説明の行は、明るい地形の上でも読めるよう暗い背景を敷き、クリックは受けません（地図や地図 MOD のボタンの操作を邪魔しません）。帯を画面の下端近くへ動かした時は、帯の上に出ます。

帯に並ぶボタンは次のとおりです。

- 上段: 道具5つ（囲み・線・文字・消しゴム・編集）／元に戻す・やり直す／文字だけの表示・非表示／メモ全体の表示・非表示／メニュー／手書きモードを終える（右端の、上下2段にまたがる縦長のボタン）
- 下段: 選んでいる道具に合わせて変わります
  - 囲み: 線の太さ3段／塗りの濃さ4段（なし・うすい・ふつう・こい）／色10色
  - 線: 線の太さ3段／色10色
  - 文字: 文字の大きさ3段（小・中・大）／色10色
  - 消しゴム・編集: 設定は無く、「この道具に設定はありません」と出ます

### 道具

| 道具 | キー（手書きモード中の全画面マップ上） | 操作 |
|---|---|---|
| 囲み | 1 | ドラッグして範囲を囲み、離すと文字入力の画面が開く |
| 線 | 2 | ドラッグした跡を線として残す |
| 文字 | 3 | クリックした場所に文字を置く |
| 消しゴム | 4 | クリックしたメモを消す |
| 編集 | 5 | クリックしたメモの文字や色を直す（削除もここから） |

キーは操作設定で変えられます。囲みが小さすぎる場合や、線が短すぎる場合は、メモを作らずに取り消します。

### 色・太さ・文字の大きさ

帯の下段で、線の太さ（細・中・太）、囲みの塗りの濃さ、文字の大きさ（小・中・大）、色（赤・橙・黄・黄緑・水色・青・紫・桃・白・黒の10色）を選びます。何が出るかは、選んでいる道具で変わります。最後に選んだ道具・色・太さ・塗りの濃さ・文字の大きさ・ミニマップへ出すかどうかは、ゲームを再起動しても覚えています。

### 文字入力の画面

囲み・線・文字を描き終えると、文字入力の画面が開きます（背後に地図が暗く見えます）。

- 文字は複数行で、1000文字までです。Enter は改行です
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
- 履歴は100件までで、ディメンションや接続先、サーバー（プロキシの先）が切り替わると破棄されます

### 表示の切替

帯の上段には、表示の切替が2つ並びます。

- **文字だけの表示・非表示**: 囲み・線・文字メモの文字をすべて隠します（囲みの輪郭や線は残ります）。全画面マップとミニマップの両方に効きます
- **メモ全体の表示・非表示**（目のボタン）: 自分のメモを地図に出す・隠すを切り替えます。隠している間も、囲み・線・文字は描けます（描くと表示に戻ります）

### メニュー（メモの保存先）

帯の上段のメニューのボタンを押すと、縦の一覧が開きます。今の項目は「保存先…」で、メモの保存先の画面（次の節）を開きます。

- Esc を押すと、メニューだけが閉じます（手書きモードは終わりません）。メニューの外をクリックしても閉じ、そのクリックは地図には届きません
- 「保存先…」は、マルチプレイの時だけ押せます。シングルプレイでは灰色で「このワールド専用」と出ます（シングルプレイのメモは、ワールドごとに分かれているためです）

### ミニマップへの表示

メモごとに、文字入力の画面の「ミニマップ」で表示・非表示を選べます（既定は表示）。全画面マップの描画は、どの地図 MOD でも MapScribble が自前で行います。ミニマップへの出し方は地図 MOD によって違います。

| ミニマップ | 出し方 |
|---|---|
| JourneyMap | JourneyMap のオーバーレイ（多角形）として出します |
| Xaero's Minimap | ミニマップの枠・大きさ・ズーム・回転・形を読み取り、MapScribble が枠の中へ自前で描きます |
| FTB Chunks | 非対応です（全画面マップだけに出ます） |

## メモの保存先

`.minecraft`（ゲームのフォルダ）の中の `mapscribble/` に、接続先ごと・ディメンションごとのファイルで保存します。地図 MOD のフォルダは使いません。

シングルプレイ:

```
<.minecraft>/mapscribble/saves/<ワールドフォルダ名>/<namespace>__<path>.json
例: <.minecraft>/mapscribble/saves/New World/minecraft__overworld.json
```

マルチプレイ:

```
<.minecraft>/mapscribble/servers/<接続先のアドレス>/<namespace>__<path>.json
例: <.minecraft>/mapscribble/servers/mc.example.com_25565/minecraft__the_nether.json
```

プロキシの配下でサーバーごとに分けている接続先（0.4.0 から）では、アドレスのフォルダの下に、メモ帳ごとのフォルダ `nb-…/` ができます。中身は、アドレスのフォルダ直下と同じ形のディメンションごとのファイルです。どのメモ帳を使っているかの記録は、同じアドレスのフォルダの `notebooks.json` に入ります。

```
<.minecraft>/mapscribble/servers/<接続先のアドレス>/nb-…/<namespace>__<path>.json
<.minecraft>/mapscribble/servers/<接続先のアドレス>/notebooks.json
```

- マルチプレイのフォルダ名は、接続に使ったアドレスの `:` を `_` に置き換え、英数と `.` `_` `-` 以外の文字も `_` にしたものです
- シングルプレイでもマルチプレイでもない（どちらの情報も取れない）時は `saves/unknown` を使います
- メモは自分だけのものです。サーバーには何も送らず、ほかのプレイヤーにも見えません
- 変更から約1秒後にまとめて書き出します。ゲームを閉じる時（タイトルへ戻らずにウィンドウを閉じた時も）は、書き出し待ちのメモを書き切ってから終わります
- MapScribble が知らない形式のファイルは、読み込まず、上書きもしません（その間は帯に「メモを読み込めません（ファイルはそのまま）」と出ます）
- 0.1.0 では JourneyMap のデータフォルダの中（マルチは `<.minecraft>/journeymap/data/mp/<サーバーのフォルダ>/addon-data/mapscribble/`、シングルは `<.minecraft>/journeymap/data/sp/<ワールドのフォルダ>/addon-data/mapscribble/`）に保存していました。0.4.0 は、そのフォルダを探して引き継ぎます（0.2.0・0.3.0 では引き継げていませんでした）。該当するフォルダが1つで、新しい保存先に同じ名前のファイルが無い場合は、その保存先を初めて開いた時にコピーします。同じアドレスに 0.1.0 のフォルダが2つ以上ある時（プロキシ配下で JourneyMap が分けていた時）は、混ざらないよう、フォルダごとに「メモ帳」として取り込み、帯の説明の行でお知らせします（メモ帳は、保存先の画面で選べます）。どちらも旧フォルダのファイルはそのまま残します
- 道具・色・太さ・文字の大きさ・帯の位置といった UI の設定は、メモとは別に `<.minecraft>/config/mapscribble-ui.json` に保存します

## 複数サーバー（Velocity）

保存先は、**接続に使ったアドレス**（クライアントがつないだ先）で分かれます。地図 MOD の区別には乗っていないので、JourneyMap・Xaero・FTB Chunks のどれを使っていても同じ分かれ方です。ディメンションは、どの構成でもファイル単位で分かれます。

### 0.4.0 から: プロキシの先のサーバーごとに分ける

Velocity などのプロキシでは、同じアドレスのまま、中のサーバーが変わります。0.3.0 までは、サーバーが変わってもメモは同じフォルダのままでした。0.4.0 では、**同じ接続の中で、同じディメンションのワールドのシード（のハッシュ）が変わったのを見たら、そのアドレスを自動でサーバーごとに分け始めます**。サーバーごとに「メモ帳」ができ、メモはそのサーバーのメモ帳に保存されます。

- 普通のサーバー（シードが変わらない）とシングルプレイの動きは、今までと同じです。分けるのは、シードの変化を見たアドレスと、利用者が分けると選んだアドレスだけです
- サーバーがシードを隠していて値が取れない時は、自動では分けません（今までと同じ保存先に書きます）
- 入るたびにシードが変わるサーバーで、メモ帳が増え続けないよう、1つのアドレスで自動で作るメモ帳は8個までです。超えたら自動で分けるのを止めます
- 自動で分けても、利用者が止めることも、手で分けることもできます（次の「保存先の画面」）
- 同じシードの2つのサーバーは、見分けがつかないので自動では分けられません。その場合は、保存先の画面で手で切り替えてください

### 今までのメモは消えません

分ける前にアドレスのフォルダ直下に保存されていたメモ（0.2.0・0.3.0 で描いたもの）は、**消えず・動かさず・名前も変えません**。分けた後も、**どのサーバーでも今までどおり見え、編集もできます**（編集は元のファイルへ書きます）。地図の見た目は変えていません。消しゴム・編集でメモにカーソルを合わせた時だけ、「分ける前のメモ（どのサーバーにも出ます）」と出ます。

分ける前のメモを、使っているメモ帳へ移したい時は、保存先の画面から移せます（移すのは利用者が選んだ時だけです）。

### 保存先の画面

メニューの「保存先…」から開きます（マルチプレイの時だけ）。

- **この接続先をサーバーごとに分ける**: オン・オフを切り替えます。オフにしても、作ったメモ帳は消えません（もう一度オンにすれば戻ります）
- **メモ帳の一覧**: 行をクリックすると、使うメモ帳がその場で切り替わります（使用中は青い行）。件数と作った日が出ます
- **名前を変える**: 使用中のメモ帳の名前を変えます（24文字まで）。Enter で決め、Esc で取りやめます
- **新しいメモ帳**: 新しいメモ帳を作り、このワールドで使います
- **分ける前のメモ**: 件数が出ます。「全部を移す」（この接続先の全ディメンション）と「見えている分を移す」（開いた時に地図に見えていた範囲）で、使用中のメモ帳へ移せます。確認の画面が出て、移したメモは、ほかのサーバーには出なくなります。移した後は、画面の「元に戻す」か、地図で Ctrl+Z で戻せます
- メモ帳を削除する操作はありません

分ける前のメモを移す時は、移す前の元のファイルを、同じフォルダに `<ファイル名>.before-split.bak`（例: `minecraft__overworld.json.before-split.bak`）として1回だけ残します。移し先へ書いて読み直せたことを確かめてから、元から除きます。途中で失敗した時は、元のファイルのまま残ります。

### 古い版へ戻した時

0.3.0 に戻しても、**分ける前のメモ（直下のファイル）はそのまま見えます**。メモ帳に描いたメモと、メモ帳へ移したメモは、古い版では見えません（ファイルは残っていて、0.4.0 に戻せば見えます。移したメモの元は `.before-split.bak` にも残っています）。

## その他の既知の制限

- 全画面マップの右の列のボタンが、JourneyMap 6.0.10 によってアドオンのボタンとして二重に登録されることがあります。MapScribble は自分のボタンだけを1つにしています
- FTB Chunks のミニマップにはメモを出しません（FTB Chunks は全画面マップだけの対応です）
- Minecraft 1.20.6 向けの jar では、JourneyMap のミニマップにメモを出しません（JourneyMap API 2.0 系の配布が無いため）
- Webmap（ブラウザで見る地図）にはメモを出しません

## 開発（ビルド）

Gradle を動かす JDK は 25 です。各構成のコンパイルと実行に使う JDK（Minecraft 1.20.1 は 17、1.20.6 と 1.21.x は 21、26.x は 25）は、無ければ Gradle の toolchain が自動で取得します。複数の Minecraft 版・ローダーを1つのソースからビルドするために [Stonecutter](https://stonecutter.kikugie.dev/) を使っています。開発機は Windows なので、コマンドは PowerShell の形を主に書きます。

構成の一覧は `settings.gradle.kts` に、構成ごとの依存の版は `versions/<構成>/gradle.properties` にあります（`fabric_api_version` / `neoforge_version` / `forge_version` / `jm_api_version`、開発用クライアントへ入れる JourneyMap 本体の `jm_version`、Forge・NeoForge の `mods.toml` へ差し込む Minecraft の対応範囲 `mc_version_range`）。`versions/` の下はビルド結果と実行ディレクトリだけを git から除外し、この `gradle.properties` は git で管理します（無いとビルドが構成の版を解決できません）。依存を上げる時はこのファイルを直します。

```powershell
.\gradlew.bat buildAndCollect
```

21構成（上の「対応環境と必要な MOD」の表のとおり。Fabric 8・Forge 6・NeoForge 7）の jar が、すべて `build/libs/<MOD版>/` に集まります。jar 名は `mapscribble-<MOD版>+<Minecraft版>-<ローダー>.jar` なので、同じ Minecraft 版のローダー違いの jar が上書きし合うことはありません。

```
build/libs/0.4.0/mapscribble-0.4.0+1.20.1-fabric.jar
build/libs/0.4.0/mapscribble-0.4.0+1.20.1-forge.jar
build/libs/0.4.0/mapscribble-0.4.0+1.20.6-fabric.jar
build/libs/0.4.0/mapscribble-0.4.0+1.20.6-forge.jar
build/libs/0.4.0/mapscribble-0.4.0+1.20.6-neoforge.jar
build/libs/0.4.0/mapscribble-0.4.0+1.21.1-fabric.jar
build/libs/0.4.0/mapscribble-0.4.0+1.21.1-neoforge.jar
build/libs/0.4.0/mapscribble-0.4.0+1.21.4-fabric.jar
build/libs/0.4.0/mapscribble-0.4.0+1.21.4-neoforge.jar
build/libs/0.4.0/mapscribble-0.4.0+1.21.11-fabric.jar
build/libs/0.4.0/mapscribble-0.4.0+1.21.11-forge.jar
build/libs/0.4.0/mapscribble-0.4.0+1.21.11-neoforge.jar
build/libs/0.4.0/mapscribble-0.4.0+26.1.2-fabric.jar
build/libs/0.4.0/mapscribble-0.4.0+26.1.2-forge.jar
build/libs/0.4.0/mapscribble-0.4.0+26.1.2-neoforge.jar
build/libs/0.4.0/mapscribble-0.4.0+26.2-fabric.jar
build/libs/0.4.0/mapscribble-0.4.0+26.2-forge.jar
build/libs/0.4.0/mapscribble-0.4.0+26.2-neoforge.jar
build/libs/0.4.0/mapscribble-0.4.0+26.3-fabric.jar
build/libs/0.4.0/mapscribble-0.4.0+26.3-forge.jar
build/libs/0.4.0/mapscribble-0.4.0+26.3-neoforge.jar
```

単体テスト（JUnit）は次のとおりです。

```powershell
.\gradlew.bat test
```

実際のクライアントを起動する client gametest は、Fabric 版の 26.1.2 / 26.2 / 26.3 で、版ごとに実行します（Fabric API の client gametest API を使うので、ほかの構成では動かしません）。タスクはプロジェクト名 `<Minecraft版>-fabric` で指定します。実行ディレクトリは `versions/<構成>/build/run/clientGameTest/` で、スクリーンショットは同じ場所の `screenshots/` に出ます。

```powershell
.\gradlew.bat :26.1.2-fabric:runClientGameTest
.\gradlew.bat :26.2-fabric:runClientGameTest
.\gradlew.bat :26.3-fabric:runClientGameTest
```

gametest を回すときは、メモリを絞らないと足りなくなることがあるので、Gradle の JVM のメモリを指定します（他の重い処理と同時に動かさないでください）。

```powershell
.\gradlew.bat :26.1.2-fabric:runClientGameTest '-Dorg.gradle.jvmargs=-Xms64m -Xmx700m'
```

Linux（CI を含む）で実行する場合は次のとおりです。

```bash
./gradlew buildAndCollect test
./gradlew :26.1.2-fabric:runClientGameTest
```

## 開発（検証の構成）

- **複数サーバーの検証構成**（手元用）: `G:\minecraft_local_test\velocity_jm\`。Velocity と Folia 2台（`lis` / `res`）の構成で、本番の MapModCompanion を写したものです。ワールドは新規に作っています。複数サーバーでメモの保存先がどう分かれるかの確認に使います
- **開発用クライアント**のインスタンス: `G:\minecraft\instances\Fabric Dev 26.1.2\minecraft`

## GitHub Actions

`.github/workflows/` に3本あります。

- `build.yml`: push・プルリクエスト・手動で、21構成のビルドと単体テスト、jar に JourneyMap API のクラスが混ざっていないことの検査、Fabric 版 26.1.2 / 26.2 / 26.3 の client gametest を実行します
- `release.yml`: `v*` のタグ（または手動実行）で GitHub Release を最新のリリースとして公開し（下書き・プレリリースにはしません）、21個の jar を添付します
- `curseforge.yml`: リリースから呼ばれる、CurseForge へのアップロード用の定義です。必要な設定が無ければ何もせずに終わります

## ライセンス

MIT（[LICENSE](LICENSE)）。JourneyMap の API は MapScribble の jar に同梱していません（JourneyMap は Techbrew の製品で、MapScribble はその公式 MOD ではありません）。Xaero's World Map / Xaero's Minimap・FTB Chunks についても同じで、これらのクラスは同梱しておらず、MapScribble はそれぞれの公式 MOD ではありません。

## English summary

MapScribble lets you draw handwritten notes on the fullscreen map of JourneyMap, Xaero's World Map or FTB Chunks: outline an area by hand and attach text to it (the main feature), draw lines, or place text. Notes are stored only on your own client, under `<.minecraft>/mapscribble/saves/<world folder>/` in singleplayer and `<.minecraft>/mapscribble/servers/<server address>/` in multiplayer, one file per dimension.

- Minecraft 1.20.1 / 1.20.6 / 1.21.1 / 1.21.4 / 1.21.11 / 26.1.2 / 26.2 / 26.3 on Fabric, Forge and NeoForge (21 combinations; see the support table above), Java 17 / 21 / 25 depending on the Minecraft version. Fabric builds need Fabric API. No map mod is declared as a hard dependency: any one of JourneyMap, Xaero's World Map or FTB Chunks is enough (client side only; nothing to install on the server).
- Minimaps: JourneyMap (as JourneyMap overlays) and Xaero's Minimap (drawn by MapScribble inside the minimap frame) are supported. FTB Chunks' minimap is not. The 1.20.6 builds do not use the JourneyMap API (no 2.0 distribution for that version), so they draw on the JourneyMap fullscreen map by reflection and do not show notes on the JourneyMap minimap.
- Turn on scribble mode with the pencil button: in the right column of the fullscreen map with JourneyMap's official plugin, or at the top right of the screen with Xaero's World Map and FTB Chunks. Tool keys are 1 to 5 (outline, line, text, eraser, edit), and Ctrl+Z / Ctrl+Y undo and redo. The mode-toggle key is unassigned by default (Controls, category MapScribble).
- The pencil button that MapScribble draws itself (Xaero's World Map, FTB Chunks, JourneyMap without its official plugin) can be moved: drag it with the left button, or Shift+click it and click again to place it; right-click resets it, and Esc cancels a move. Releasing the button without moving still toggles scribble mode. The position is remembered per map mod as a distance from the nearest screen edge. The buttons in JourneyMap's right column cannot be moved.
- The tool band can be dragged anywhere on the screen by its empty area, and remembers where you put it. The top row holds the five tools, undo / redo, a text-only visibility toggle, an overall visibility toggle, the menu button and the exit button. The bottom row changes with the selected tool (line width, fill opacity for outlines, text size, and ten colors). Note text can be up to 1000 characters. A hint line below the band shows what the selected tool does and short notices; it sits above the band when the band is near the bottom edge of the screen. UI texts are available in 10 languages.
- The menu button opens a list with "Notebooks…" (multiplayer only; in singleplayer notes are already kept per world).
- Notes are separated by the address you connected to, not by the map mod's own per-server folders. Since 0.4.0, on a proxy network (Velocity and the like), when the world seed hash of the same dimension changes within one connection, that address is split automatically: each backend server gets its own notebook (a folder `nb-…` under the address folder, recorded in `notebooks.json`). Ordinary servers and singleplayer behave as before. You can turn the split on or off, switch notebooks, create new ones and rename them on the Notebooks screen (menu button, "Notebooks…"). No notebook is ever deleted.
- Your existing notes are not removed: notes saved before the split stay in the address folder and show up on every server there, and you can still edit them. On the Notebooks screen you can move them into the notebook in use ("Move all" or "Move visible"); a `.before-split.bak` copy of the original file is kept the first time, and the move can be undone. If you go back to 0.3.0, the notes in the address folder are still shown; notes that live in notebooks are not visible in older versions (the files stay on disk).
- Notes drawn in 0.1.0 (saved inside JourneyMap's data folder) are now imported when you first open the matching save location. If one address had two or more 0.1.0 folders, each is imported as its own notebook.
- Build: `./gradlew buildAndCollect` produces one jar per Minecraft version and loader (21 configurations) in `build/libs/<mod version>/`. License: MIT.
