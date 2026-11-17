MapScribble 過去版メモ 採取データ (回帰試験素材)
採取日: 2026-11-17 / 作業区分: community / 設計書: docs/design/v0.4-server-split.md 7.1-7.3

採取環境
- 開発用クライアント: Minecraft 26.1.2 Fabric (インスタンス MCP 26.1.2 Fabric) + JourneyMap 26.1.2-6.0.10+fabric (Gradle キャッシュの既存 jar。ダウンロードしていない)
- 開発用サーバー: localhost:25565 (サーバー名 "MCP Debug" -> 0.1.0 のフォルダ名 MCP~Debug / 0.2.0 以降のフォルダ名 localhost_25565)
- シングル: ワールド名 "New World" (クリエイティブ / コマンド有効)
- ディメンション: minecraft:overworld と minecraft:the_nether
- 各ファイルのパスは「ゲームフォルダからの相対パス」を保っている。

使用した jar
- 0.1.0: S:/Projects/minecraft/MOD/MapScribble/build/libs/0.1.0/mapscribble-0.1.0+26.1.2.jar (手元のビルド成果物, md5 069ebcbd1781b9a727c17eb81396d94c)
- 0.2.0: S:/Projects/minecraft/MOD/MapScribble/build/libs/0.2.0/mapscribble-0.2.0+26.1.2-fabric.jar (手元のビルド成果物, md5 61a595c3992ae2ee53d3bb37935b361a)
- 0.3.0: タグ v0.3.0 (e0e259e) から scratchpad の git worktree で ./gradlew :26.1.2-fabric:build -x test --offline を実行して自作 (md5 ad8efd20a955ba57064e5fcfd9b12594)
- CurseForge 等からのダウンロードは行っていない。

保存先の版差 (重要)
- 0.1.0: journeymap/data/{mp|sp}/<ワールド>/addon-data/mapscribble/<dim>.json  (JourneyMap の IClientAPI.getDataPath 由来)
- 0.2.0 / 0.3.0: mapscribble/servers/<address>/<dim>.json (マルチ) と mapscribble/saves/<ワールド>/<dim>.json (シングル)
- どの版も format=1 / 配列キー memos / kind は REGION・LINE・TEXT。ファイル名は minecraft__overworld.json / minecraft__the_nether.json。
- 0.2.0 以降の既定色は 0.1.0・0.3.0 の橙 #FF9500 ではなく 0.2.0 のみ赤系 #FF6E61 (0.2.0 の採取分は全部この色)。

0.1.0 -> 0.3.0 更新後に見えるか (設計書 7.1 の期待)
- 結果: 見えない (期待どおり)。0.1.0 で描いたメモを持つ同じ MP / SP ワールドを 0.3.0 で開き、手書きモード ON の JourneyMap 全画面地図を確認した。
  マルチのオーバーワールド・ネザー、シングルのオーバーワールド・ネザーのいずれでも、0.1.0 のメモ (囲み・線・文字) は地図に出なかった。
  0.3.0 は journeymap/data/ 以下を見ず、mapscribble/ 以下だけを見るため。
- 注意: 0.3.0 の移行処理は journeymap/addon-data/mapscribble/ を見る実装だが、0.1.0 の本当の保存先は journeymap/data/{mp|sp}/<ワールド>/addon-data/mapscribble/ で場所が違う (git の最近の Jm010Import はこの食い違いの修正と思われる。本採取では未確認)。

ファイル一覧 (SHA-256 / 件数 / 種類)

v0.1.0/mp/journeymap/data/mp/MCP~Debug/addon-data/mapscribble/minecraft__overworld.json
  sha256 10f0889363ca805b7728d24fddcd5f9c747223538b3ea40154c56798e071b36c
  4 件: REGION(#FF6E61) "拠点エリア Base 🏠", REGION(#5C9DFF) (text空), LINE(#7CFC00) "線 Green line 🌲", TEXT(#FF9500) "こんにちは Hello 😀"

v0.1.0/mp/journeymap/data/mp/MCP~Debug/addon-data/mapscribble/minecraft__the_nether.json
  sha256 e74a8824b835d3b20862b3caf340e2f1287258aec48a9eaa466f17b2e5959ae6
  3 件: TEXT(#C584FF) "ネザー Nether text", LINE(#FF6FD0) "Nether line", REGION(#FF9500) "ネザー区画 Nether zone"

v0.1.0/sp/journeymap/data/sp/New World/addon-data/mapscribble/minecraft__overworld.json
  sha256 7963e8d3d1b5ff234381a679150e572e724295075079b9e2eba720c9fbac6e6f
  3 件: REGION(#FF9500) "SP区画 SP zone 🏠", LINE(#FF9500) "SP線 SP line", TEXT(#FF9500) "SP文字 SP text"

v0.1.0/sp/journeymap/data/sp/New World/addon-data/mapscribble/minecraft__the_nether.json
  sha256 d64d914ad751b09a82cf4794aff51d8e1759ab84e6422a679598acb13e95a713
  3 件: REGION(#FF9500) "SPネザー区画 SP nether zone", LINE(#FF9500) "SPネザー線 SP nether line", TEXT(#FF9500) "SPネザー文字 SP nether text"

v0.2.0/mp/mapscribble/servers/localhost_25565/minecraft__overworld.json
  sha256 543c495d92fdf6c600b555bce198032e86815744b60f1338c47e0aab42005500
  3 件: REGION(#FF6E61) "v020 MP区画 zone", LINE(#FF6E61) "v020 MP線 line", TEXT(#FF6E61) "v020 MP文字 text 😀"

v0.2.0/mp/mapscribble/servers/localhost_25565/minecraft__the_nether.json
  sha256 97a1d72b7cb16b186eea0c5f274351ebfbf48c18a3602a0b4c3de268d0492573
  3 件: REGION(#FF6E61) "v020 MPネザー区画", LINE(#FF6E61) "v020 MPネザー線", TEXT(#FF6E61) "v020 MPネザー文字 😀"

v0.2.0/sp/mapscribble/saves/New World/minecraft__overworld.json
  sha256 cdb769bbec27fe4606ecd5e038db405739c89545bc73e425b23915642d73d973
  3 件: REGION(#FF6E61) "v020 SP区画 zone", LINE(#FF6E61) "v020 SP線 line", TEXT(#FF6E61) "v020 SP文字 text"

v0.2.0/sp/mapscribble/saves/New World/minecraft__the_nether.json
  sha256 4ecaad9a84e470f68dbd4b566947ee795bfcd6ba2e65ea83cd5f624bb463b09e
  3 件: REGION(#FF6E61) "v020 SPネザー区画", LINE(#FF6E61) "v020 SPネザー線", TEXT(#FF6E61) "v020 SPネザー文字 😀"

v0.3.0/mp/mapscribble/servers/localhost_25565/minecraft__overworld.json
  sha256 917477d207f1c5dfd09cccf23c7287ce5cf1b853393cee5532945d360c2bdfe3
  3 件: TEXT(#FF9500) "v030 MP文字 text 😀", LINE(#FF9500) "v030 MP線 line", REGION(#FF9500) "v030 MP区画 zone"

v0.3.0/mp/mapscribble/servers/localhost_25565/minecraft__the_nether.json
  sha256 65cf84fd4bfaaddaeebd48f09b81c7656e79e55bed66cbbeb9171e7118d9710b
  3 件: REGION(#FF9500) "v030 MPネザー区画", LINE(#FF9500) "v030 MPネザー線", TEXT(#FF9500) "v030 MPネザー文字"

v0.3.0/sp/mapscribble/saves/New World/minecraft__overworld.json
  sha256 0eff089aeb0e152ee053bf2eebe271079d6415540e2ff34fcb270e16a8c7f118
  3 件: REGION(#FF9500) "v030 SP区画 zone", LINE(#FF9500) "v030 SP線 line", TEXT(#FF9500) "v030 SP文字 text"

v0.3.0/sp/mapscribble/saves/New World/minecraft__the_nether.json
  sha256 f47686fc7dd7443840a67becbcad0bba27ba8c6f799ca4a6d2ae305322c654ac
  3 件: TEXT(#FF9500) "v030 SPネザー文字 😀", LINE(#FF9500) "v030 SPネザー線", REGION(#FF9500) "v030 SPネザー区画"


その他の記録
- 0.1.0 マルチのオーバーワールドの 2 件目 REGION は文字が空のまま保存された (色スウォッチ操作後に文字欄の入力が外れた)。空文字の囲みの回帰素材として有用。onMinimap=false も含む。
- 0.3.0 sp / mp は絵文字入りの文字メモを含む。0.2.0 mp / sp のネザー文字も絵文字入り。
