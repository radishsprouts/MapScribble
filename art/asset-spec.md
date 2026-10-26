# MapScribble 素材仕様書（作業3）

## 状態（2026-10-20 加工済み）

**加工済み（2026-10-20）。** icon・toggle・ツール9枚（redo は undo の左右反転）を `src/main/resources/assets/mapscribble/` へ出力した。撮り直しが要る素材は無い。元画像の名前変更（`_src` / `_rejN`）も済んでいる。

加工の記録:

- 加工は Pillow の組み込み処理のみ（`getbbox`・`crop`・`ImageOps.pad`（最近傍）・`point`・`ImageChops.difference`・`ImageOps.mirror`）。icon の透過は、四隅の背景色（#D2D2D1）との差が 20 を超える所だけを不透明にした。
- **変えた値: enclose は外接矩形を元画像で 4px 広げてから 14x14 へ縮める。** 外接矩形ぴったりだと縮小後の輪が上辺で開き、閉じた輪にならなかったため。しきい値は 128 のまま。ほかの素材はしきい値・外接矩形とも仕様どおり。
- 出力後の確認: 全 PNG の alpha は 0 か 255 のみ、不透明部の色は #FFFFFF のみ（icon を除く）。enclose は内側の透明部が外と 4 近傍でつながらない（閉じている）。line・enclose・undo・redo・text・eraser・edit は 1 つながり。

（以下は加工前の選別の記録）

- 生成済み: 29枚（icon 2、toggle/enclose/line/text/eraser/edit/undo/visible/hidden 各3）。プログラム描画での代用なし。加工・描き足しなし。
- 選別済み: 採用10枚（下の「採否」）、不採用19枚。全素材で採用あり。
- 未生成: 9 redo（採用した undo の左右反転で作る。生成不要）。
- 未実施: 縮小・2値化・透過・`<名前>_src.png` / `_rejN.png` への名前変更（Dev-Engineer）。
- 選別の方法: 各候補を 1254px の元画像で目視し、図柄の外接幅を 14px（toggle は 28px）へ最近傍で縮めた時の線の太さ（元画像で線幅 ÷ 縮小率）と、内側の隙間・穴が潰れないかで判断した。縮小・2値化を実際に行った結果の確認ではない（その手段が無い）。加工後は「素材一覧」の「加工後の確認点」で Dev-Engineer / 本体が再確認する。

### 候補の生成と保存（2026-10-20、Node スクリプトから MCP を直接呼んで保存。失敗0）

| # | 素材 | quality | 枚数 | ファイル |
|---|---|---|---|---|
| 1 | icon | high | 2 | icon_cand1.png（生成時は medium）, icon_cand2.png |
| 2 | toggle | medium | 3 | toggle_cand1〜3.png |
| 3 | enclose | medium | 3 | enclose_cand1〜3.png |
| 4 | line | medium | 3 | line_cand1〜3.png |
| 5 | text | medium | 3 | text_cand1〜3.png |
| 6 | eraser | medium | 3 | eraser_cand1〜3.png |
| 7 | edit | medium | 3 | edit_cand1〜3.png |
| 8 | undo | medium | 3 | undo_cand1〜3.png |
| 10 | visible | medium | 3 | visible_cand1〜3.png |
| 11 | hidden | （記録なし） | 3 | hidden_cand1〜3.png（visible_src.png＝visible_cand3 を image_url の参照に渡して生成） |

- 候補の命名は `<名前>_candN.png`。採用後に `<名前>_src.png`、不採用は `<名前>_rejN.png` へ整理する（Dev-Engineer）。
- 生成の経緯: 1 icon の1枚目はセッションのツール結果から取り出して icon_cand1 にコピー。残りは Node スクリプトで MCP を呼び、結果を `art/src/` へ保存。
- 11 hidden の quality とプロンプトの実文は、この選別の担当者には記録が渡っていない（下のプロンプト欄は計画時の文面）。実際に使った文面が違えば Dev-Engineer が追記する。

## 採否（2026-10-20）

2値化の推奨しきい値は、全候補が純白・純黒でグレーはほぼ無いので、基本は 128。縮小で線が薄くなる素材だけ 96 を推奨する。

| # | 素材 | 採用 | 不採用 | 選んだ理由 | しきい値 |
|---|---|---|---|---|---|
| 1 | icon | icon_cand1 → icon_src.png | icon_cand2 → icon_rej1.png | 赤い輪・鉛筆・破れた地図がそろい、赤い輪が地図の淡い地色の上で最もはっきりしている。32x32 に落としても輪が見える。背景は単色の薄灰で透過しやすい。cand2 は地図が茶系で濃く、輪の内側が緑で、輪が地図に埋もれる | 2値化しない |
| 2 | toggle | toggle_cand2 → toggle_src.png | toggle_cand1 → toggle_rej1.png, toggle_cand3 → toggle_rej2.png | 先が左下の斜め鉛筆で、内部の線が中央の点列1本だけと少なく、28x28 に縮めても胴の内側が潰れにくい。cand1 は消しゴム部・口金・先端の内部に細かい段差が多く、cand3 は胴の内側が二重線で、縮小で埋まる | 128 |
| 3 | enclose | enclose_cand3 → enclose_src.png | enclose_cand1 → enclose_rej1.png, enclose_cand2 → enclose_rej2.png | 閉じた輪で、線が最も太く（14px へ縮めても約1px 残る）、縮小で欠けて開く恐れが最小。形は手描き風の丸み。cand1 は線が細く（縮小後約0.7px）、右側にくびれがあり、欠けやすい。cand2 も線が細く、波が大きくて 14px では輪郭が崩れる | 128 |
| 4 | line | line_cand1 → line_src.png | line_cand2 → line_rej1.png, line_cand3 → line_rej2.png | 左下→右上の波線で、波の上下が最もはっきり、線も太い。閉じておらず途切れない。cand3 は波が浅くほぼ斜めの直線で「波線」に見えない。cand2 は線が細く、斜めの段差で薄くなる | 96（斜め線が縮小で薄くなるため） |
| 5 | text | text_cand2 → text_src.png | text_cand1 → text_rej1.png, text_cand3 → text_rej2.png | 「A」の横棒と、横棒の上の穴が大きく、14px に縮めても穴が残って A と読める。線は縮小後約1px。cand1 と cand3 は線が太すぎて穴が小さく、縮小で穴が埋まって塊になる。cand1 は脚の内側に小さい切れ込みもある | 128 |
| 6 | eraser | eraser_cand2 → eraser_src.png | eraser_cand1 → eraser_rej1.png, eraser_cand3 → eraser_rej2.png | 斜めの消しゴムで、左下が塗り、右上が輪郭だけ。輪郭線が3候補のうち最も太く、縁が欠けにくい。cand1 は同じ形で輪郭がやや細い。cand3 は塗りが右上で向きが逆、輪郭側（左下）の線がやや細く、他のツール素材の向きともそろいにくい | 96 |
| 7 | edit | edit_cand2 → edit_src.png | edit_cand1 → edit_rej1.png, edit_cand3 → edit_rej2.png | 左上を指す矢印カーソルで、鉛筆に見えない。線が最も太く、尾の部分が単純で滑らか。cand1 と cand3 は尾の内側に細かいギザがあり、縮小で潰れて尾の形が崩れる | 128 |
| 8 | undo | undo_cand2 → undo_src.png | undo_cand1 → undo_rej1.png, undo_cand3 → undo_rej2.png | 左回りの弧で、鏃が「<」の形に開いて明確。線は太い。cand1 は鏃が塊で、矢と読みにくい。cand3 は輪郭だけの二重線で、縮小・2値化で内側が埋まるか線が欠ける | 128 |
| 10 | visible | visible_cand3 → visible_src.png | visible_cand1 → visible_rej1.png, visible_cand2 → visible_rej2.png | 目の輪郭と瞳の間の隙間が最も広く（縮小後約1px）、瞳が輪郭にくっついて穴にならない。瞳は中くらいの丸で、hidden で斜線を引いた時に瞳と線が重なっても判別しやすい。cand1 は瞳と輪郭の隙間が狭く、目の左右の端に黒い切れ込みがある。cand2 は瞳が大きく隙間が狭い。11 hidden は cand3 の線の太さ（約1px）に合わせて作る | 128 |
| 11 | hidden | hidden_cand3 → hidden_src.png | hidden_cand1 → hidden_rej1.png, hidden_cand2 → hidden_rej2.png | 下の「11 hidden の選別」を参照。要点: 3枚とも visible と同じ目の形・線の太さで、左上から右下の斜線が目を貫いている（外側へ両端が出る）。cand3 は斜線の太さが目の輪郭線に近く、斜線の両側に目の輪郭・瞳との黒い隙間が残り、瞳が2つの塊に分かれるだけで済む。cand1 は斜線が細めで瞳が小片に砕け、輪郭の左上に浮いた破片がある。cand2 は斜線が最も太く、左上の輪郭と斜線が隙間なくつながり、瞳の左下が斜線と一体の塊になる | 128（下の注意を参照） |

### 11 hidden の選別（2026-10-20、visible_src.png と並べて 1254px の元画像で目視）

共通: 3枚とも visible_src.png と同じ約30マス幅の段差のある目の形・輪郭の太さ（輪郭の太さは約2マス）で、斜線が左上から右下へ目を貫き、両端が目の外へ出ている。「visible と同じ目で斜線が1本足されたもの」として基準は満たす。3枚とも、生成AI特有の段差ずれ（斜線の脇に細い黒い刻み線、輪郭の欠け、瞳の欠け）が入っており、visible の整然としたマスにはなっていない。

| 候補 | 斜線 | 目の輪郭 | 瞳 | 14x14 での見込み |
|---|---|---|---|---|
| cand1 | 太さは輪郭と同程度。始点は左上（約235,260）、終点は右下（約1020,1020）。両端が目の外へ出ている | 左上に斜線との交点で欠けと浮いた破片（約450〜490,360）があり、右下の輪郭が短い破片になる | 斜線の両脇で小片2〜3個に砕け、脇に細い黒い刻み線がある | 輪郭の破片と瞳の小片は縮小で消える・斜線に吸われる恐れがある。斜線自体は残る |
| cand2 | 最も太く、約110px 以上。始点（約255,210）〜終点（約1055,1020） | 左上で斜線と輪郭が黒い隙間なくつながる（約330〜430,395）。右下は斜線の脇に破片 | 左下側が斜線と同じ塊になり、右上側に細片が残る | 斜線と輪郭の左上が一体の塊になり、目の輪郭が追えなくなる。最も潰れやすい |
| cand3 | 太さは輪郭に近く、約1px 強で縮む見込み。始点（約235,235）〜終点（約1050,1020）。両端が目の外へ均等に出る | 左上は斜線との間に黒い隙間（約345〜360,425）が残って輪郭の左半分が保たれ、上辺は斜線の手前で切れて斜線の右へ続く。右下は小さな隙間を挟んで輪郭の端が残る | 斜線で2つの塊（右上の細い帯と左下の帯）に分かれるだけで、小片が出ない | 斜線・輪郭・瞳が別々に追える。斜線と輪郭が交差する点以外で一体にならない |

選んだ理由の詳細:

- 基準「10 と同じ目の形・線の太さ」: 3枚とも満たすが、cand3 は斜線の太さが輪郭に最も近い。cand2 は斜線だけ太く、visible の線の太さからずれる
- 基準「縮小・2値化で斜線と目の輪郭が潰れて一体にならない」: 14px へ縮めると 1px は元画像で約90px。斜線と輪郭・瞳の間の黒い隙間は、幅が約40px以上あれば約0.5px 以上で残るが、10px 前後の刻み線は消えて一体になる。cand3 は斜線と輪郭の間の隙間が最も広く保たれ、瞳が小片にならない。cand1 は刻み線と破片が多く、縮小で斜線と瞳が一体になる。cand2 は元画像の時点で一体になっている
- 3枚とも、縮小しても斜線が目の外へ出ていて「目に斜線が引かれている」と読めること、斜線と目が交差する部分は縮小で必ず黒い隙間が無くなること（これは設計上避けられない）を前提に比べた

しきい値の推奨（hidden_src.png）:

- **128** を推奨する。画像は純白・純黒でグレーはほぼ無く、しきい値による差は出にくい。**96 に下げない**（斜線の脇の細い刻み線が白に寄り、斜線と瞳が一体になりやすい）。**160 に上げない**（斜線の端と輪郭の細片が欠ける）
- 縮小は最近傍の指定だが、最近傍は 90px に1点だけを拾うので、10px 前後の刻み線や破片は拾う・拾わないが位置で偶然決まる。縮小後に Dev-Engineer が確認し、斜線が途切れる・斜線と輪郭の左上が一体の塊になる場合は、しきい値ではなく縮小前の外接矩形の取り方（余白 1px）と縮小の位置合わせを変えて再度試す。それでも読めなければ撮り直し（visible_src.png と hidden_cand3 を同時に参照に渡し、「斜線の脇に刻み線を作らず、斜線と目の輪郭の間に太い黒い隙間を取る」と指示）にする

### 撮り直し

なし（hidden も cand3 を採用）。ただし、hidden の加工後の確認で 14x14 の読み取りが通らなかった場合は、上の方針で hidden だけ撮り直す。

### 9 redo と 11 hidden の注意

- 9 redo は undo_src.png の加工後の画像を左右反転して作る（生成しない）。
- **11 hidden の参照画像は `art/src/visible_src.png`（採用した visible_cand3.png の名前変更後。名前変更前は `art/src/visible_cand3.png`）。** プロンプトで言い換えず、必ず image_url に渡す。作る順は visible（済）→ hidden（済、候補3枚）。hidden の線の太さ・目の形・瞳の大きさは visible に合わせ、斜線の太さも目の輪郭線と同じにする。

## 共通

- 生成は白黒（白線・純黒背景）で行い、加工で「黒 → 透明、白 → #FFFFFF」にする。参考画像は支給なし。JourneyMap の図柄・名前は入れない。文字入りにしない（text の「A」は図柄）。
- 各ツール素材は2〜3候補を生成し、16x16 に縮小して読めるものを選ぶ。縮小して潰れる案は落とす。
- 縮小は最近傍。縮小前に、図柄の外接矩形を求めて 14x14（toggle は 28x28）に収まるよう余白を付ける（外周 1px / toggle は 2px を空ける）。
- 2値化: 輝度 128 を目安（細線が欠けるなら 96、ノイズが出るなら 160）。半透明の中間色を残さない。素材ごとの推奨は上の「採否」。
- 保存先の元画像: `art/src/<名前>_src.png`（採用）、`art/src/<名前>_rejN.png`（不採用）。

## 素材一覧

| # | 元画像 | 最終ファイル（assets/mapscribble/ から） | 大きさ | 縮小・2値化 | 収める範囲 | 透過 | 加工後の確認点 |
|---|---|---|---|---|---|---|---|
| 1 | art/src/icon_src.png | icon.png | 128x128 | 色付きのまま。背景（単色の薄灰）を透過、縮小は最近傍。2値化しない | 外周 4px 空け | 背景のみ透過 | 輪・鉛筆・地図が判別できる。32x32 に落としても赤い輪が見える。文字なし |
| 2 | art/src/toggle_src.png | textures/gui/toggle.png | 32x32 | 最近傍で 28x28 に縮小して中央配置、しきい値 128 | 28x28（外周 2px 空け）、線 2px | 黒を透過 | 先が左下の斜め鉛筆と分かる。グレー無し |
| 3 | art/src/enclose_src.png | textures/gui/tool/enclose.png | 16x16 | 14x14 へ最近傍、しきい値 128 | 14x14（外周 1px） | 黒を透過 | 閉じた輪になっている（欠けて開かない）。線 1px |
| 4 | art/src/line_src.png | textures/gui/tool/line.png | 16x16 | 同上（しきい値 96） | 同上 | 同上 | 左下→右上の波線。閉じていない。途切れない |
| 5 | art/src/text_src.png | textures/gui/tool/text.png | 16x16 | 同上 | 同上 | 同上 | 「A」と読める。横棒が潰れない |
| 6 | art/src/eraser_src.png | textures/gui/tool/eraser.png | 16x16 | 同上（しきい値 96） | 同上 | 同上 | 斜めの消しゴムで片側が塗り。縁が欠けない |
| 7 | art/src/edit_src.png | textures/gui/tool/edit.png | 16x16 | 同上 | 同上 | 同上 | 矢印カーソル（鉛筆に見えない） |
| 8 | art/src/undo_src.png | textures/gui/tool/undo.png | 16x16 | 同上 | 同上 | 同上 | 左回りで鏃が分かる |
| 9 | 8 と同じ（undo を左右反転） | textures/gui/tool/redo.png | 16x16 | undo.png の加工後の画像を左右反転 | 同上 | 同上 | 右回りで鏃が分かる |
| 10 | art/src/visible_src.png | textures/gui/tool/visible.png | 16x16 | 同上 | 同上 | 同上 | 目と分かる。瞳が潰れて穴にならない |
| 11 | art/src/hidden_src.png | textures/gui/tool/hidden.png | 16x16 | 同上（しきい値 128、96・160 にしない） | 同上 | 同上 | 10 と同じ目の形・線の太さ。左上→右下の斜線が目を貫く。斜線が途切れず、斜線と目の輪郭の左上が一体の塊にならない |
| 12 | art/src/exit_cand1.png（**採用 2026-10-27**。`exit_src.png` へ改名） | textures/gui/tool/exit.png | 16x16 | 14x14 へ面積平均（BOX）で縮小、しきい値 128（下の「追加素材 12〜14」） | 14x14（外周 1px） | 黒を透過 | 太い×。斜めの線は階段状で太さ約 2px。鉛筆・ドア・矢印・輪に見えない |
| 13 | art/src/text_visible_cand2.png（**採用 2026-10-27**。`text_visible_src.png` へ改名。枠が点線になる点は許容） | textures/gui/tool/text_visible.png | 16x16 | 14x14 へ最近傍、しきい値 128 | 同上 | 同上 | 外周の枠（点線）と、中央の大文字「A」が分かれて見える。A に穴（横棒の上の隙間）がある |
| 14 | **撮り直し中。** 土台は 13 の `text_visible.png`（変えない）。斜線は新規生成する `art/src/slash_cand1〜3.png`（下の「text_hidden の撮り直し」）。暫定だった text_hidden_cand3 は不採用 | textures/gui/tool/text_hidden.png | 16x16 | 斜線を 10x10 へ最近傍、しきい値 128。`text_visible.png` の上の (3,3) へ貼り合わせる（合成） | 枠の内側（枠・角と接しない） | 同上 | 13 と枠・A が同じ（斜線と重なる画素以外は画素単位で一致）。斜線 1px・10 画素が途切れず、枠の画素と接しない |

## 生成プロンプト

共通の前置き: "Single monochrome icon, pure white thick pixel-art line drawing on a solid pure black background, no gray, no gradient, no anti-aliasing, ... readable at 16x16 pixels, centered with margin. No text."

- 1 icon（quality=high で2枚、1枚目は medium、参照なし）: "Square app icon, chunky pixel-art feel (32x32 art enlarged 4x). A torn fragment of an old paper map in warm beige with faint green and blue terrain patches, a hand-drawn irregular red circle on it, and a yellow pencil diagonally across the corner. No text, no logos. Flat solid light-gray background easy to remove. Bold outlines, limited palette."
- 2 toggle: 共通 + "A diagonal pencil, tip pointing lower-left, bold 2-pixel strokes on a 32x32 grid look."
- 3 enclose: 共通 + "A hand-drawn wobbly irregular closed loop, one thick line."
- 4 line: 共通 + "A hand-drawn wavy open curve from lower-left to upper-right, not closed, one or two gentle waves."
- 5 text: 共通 + "A bold capital letter A, simple block sans-serif."
- 6 eraser: 共通 + "An eraser tilted diagonally: rounded-corner rectangle, one half solid white, the other half outlined."
- 7 edit: 共通 + "A classic mouse pointer arrow cursor pointing upper-left, solid white."
- 8 undo: 共通 + "A curved arrow curving counter-clockwise, arrowhead at the left end."
- 10 visible: 共通 + "An open eye: almond outline with a solid round pupil."
- 11 hidden（3枚生成済み、参照は art/src/visible_src.png＝visible_cand3）: 計画時の文面は "Same eye, same stroke width and shape as the reference, add one thick diagonal slash from upper-left to lower-right across it."。実際に使った文面・quality の記録は選別担当に渡っていない。違いがあれば Dev-Engineer が追記する。斜線の太さは目の輪郭線に合わせる。

## 追加素材 12〜14（2026-10-26、Dev-Engineer が生成・加工。採否は 2026-10-27 に Dev-Art-Director が付けた。下の「追加素材 12〜14 の採否と撮り直し」）

担当の指示（Dev-UI-Designer `docs/design/fix-toggle-and-text-visibility.md` 3.7・4.5 の図柄、Dev-Art-Director の参照・プロンプト）のとおりに実行した。図柄・プロンプト・参照は決めていない。下の「暫定」は text_hidden を作るために先頭参照が要ったので、加工後の読み取りで選んだ仮の選択であり、**12 と 13 は暫定どおり採用、14 は撮り直しになった**（古い `tool/region_text_visible.png` と `region_text_hidden.png` は、撮り直しの確定後に削除した。2026-10-27）。

### 生成の記録（成功 12 回・失敗 0 回）

- 手段: Node スクリプトから画像生成 MCP（`image-generator`、ツール名 `gpt-image`、HTTP の Streamable HTTP、`tools/call`）を直接呼び、結果の画像を `art/src/<名前>_candN.png` へ保存。認証の値は書かない（設定ファイルから実行時に読んだ）。モデルの版は MCP が決めるので未確認（ツール説明は「OpenAI の gpt-image」）
- quality: 全て medium
- 参照画像の渡し方: `art/src/` の元画像（1254x1254）を Pillow の `resize`（最近傍）で長辺 1024 の PNG に縮め（色は純白・純黒のまま）、`data:image/png;base64,<base64>` の文字列にして `image_url` の配列へ渡した（先頭が1枚目）。ローカルパスは渡していない。縮小した参照 PNG は作業用で、リポジトリには置かない
- 作る順: exit、text_visible → （text_visible の候補を暫定採用）→ text_hidden

| # | 素材 | 枚数 | ファイル | 渡した参照（この順） |
|---|---|---|---|---|
| 12 | exit | 3 | exit_cand1〜3.png | text_src.png（線の太さ・粒度）、undo_src.png |
| 13 | text_visible | 3 | text_visible_cand1〜3.png | text_src.png（A の太さのみ。大きさは写さない）、visible_src.png |
| 14 | text_hidden | 3 | text_hidden_cand1〜3.png | text_visible_cand2.png（暫定採用の元画像・1枚目）、hidden_src.png（斜線の作法） |

プロンプトの実文（共通の前置き + 指定の文）:

- 前置き: "Single monochrome icon, pure white thick pixel-art line drawing on a solid pure black background, no gray, no gradient, no anti-aliasing, readable at 16x16 pixels, centered with margin. No text."
- 12 exit: "Same stroke thickness and pixel grid as the attached icons. A bold X: two diagonal strokes crossing at the center, each stroke 2 pixels thick on a 16x16 grid look, stair-stepped diagonals, fits a 14x14 area. No pencil, no door, no arrow, no circle."
- 13 text_visible: "Same stroke weight and pixel grid as the attached icons, but do not copy the attached letter's size. A square frame drawn as a 1-pixel dashed outline (2 pixels on, 2 pixels off) fitting 14x14, and inside it, centered, a bold capital letter A about 8 pixels wide and 10 pixels tall. The frame must be clearly separate from the A, with a visible black gap."
- 14 text_hidden: "Same icon as the first attached image, unchanged. Add exactly one 1-pixel-thick stair-stepped diagonal slash from upper-left to lower-right across it, in the same way the second attached image adds a slash to the eye. Keep a black gap between the slash and the frame and the A where possible."

### 暫定の選択と読み取り（1254px の元画像と、14x14 へ加工した結果で確認）

| 素材 | 暫定 | 理由 | 他の候補 |
|---|---|---|---|
| exit | cand1 | 3枚とも太い×。cand1 は斜め線の水平方向の幅が 3 マスで安定し、14x14 へ縮めても線の太さが一定（垂直方向で約 2px）。中心だけ太くなる | cand2 は線が細く、縮小後に線が 1px 以下になる。cand3 は太さは足りるが縮小で斜め線の段差が飛ぶ |
| text_visible | cand2 | A が左右対称で、横棒の上の穴が残る。枠と A の間に黒い隙間が取れる | cand1 は枠が 1 マス点の並びで、A の左脚が崩れる。cand3 は A が左右非対称で、枠が縦横で不揃い |
| text_hidden | cand3 | 3枚とも枠・斜線の通り方は加工後ほぼ同じ。cand3 は斜線が元画像で最も細く（約 1 マス幅）、仕様の「太さ 1px・階段状」に最も近い | cand1・cand2 は斜線が 2 マス幅で、加工後に A の脚と一体になる所が多い |

### 加工の値（`proc.py` 相当の手順。Pillow の組み込み処理のみ: `point`・`getbbox`・`crop`・`resize`・`ImageOps.expand`・`Image.merge`。画素・図形の描画は無い）

共通: 輝度 > しきい値 を図柄とみなし、その外接矩形を切り出し、14x14 へ縮め、外周に 1px を足して 16x16 にする。黒は alpha 0、白は RGB #FFFFFF・alpha 255 の単色（半透明は残さない）。

| 素材 | 元画像 | 外接矩形（左,上,右,下 / 大きさ） | 縮小 | しきい値 | 不透明 px |
|---|---|---|---|---|---|
| exit | exit_cand1.png | (228,195,1033,983) / 805x788 | 面積平均（BOX）で 14x14 に伸縮 | 128 | 76 |
| text_visible | text_visible_cand2.png | (159,159,1092,1090) / 933x931 | 最近傍で 14x14 に伸縮 | 128 | 54 |
| text_hidden | text_hidden_cand3.png | (159,160,1092,1090) / 933x930 | 最近傍で 14x14 に伸縮 | 128 | 63 |

- **変えた値**: ① 縦横が1〜4%しか違わないので、余白で縦横比を合わせず 14x14 へ伸縮した（余白を足すと枠が 14 マスに届かず上下が 1 マス欠ける）。② **exit だけ最近傍でなく面積平均（BOX）にした。** 元画像の 1 マスが約 40px で、図柄の幅が約 20 マス。14 へ最近傍で縮めると斜め線の段差が飛び、線が切れて見える（最近傍の結果は下）。面積平均＋同じしきい値 128 なら、斜め線が一定の太さで連続した
- exit を最近傍・しきい値 128 で縮めた場合（採用しなかった結果。# が不透明）:
  ```
  ................
  .#............#.
  ..##........##..
  ..###......###..
  ...##......##...
  .....##..##.....
  .....######.....
  ......####......
  ......####......
  .....######.....
  .....##..##.....
  ...##......##...
  ..###......###..
  ..##........##..
  .#............#.
  ................
  ```
- 出力後の確認: 3枚とも 16x16・RGBA。alpha は 0 か 255 のみ、不透明部の色は #FFFFFF のみ、外周 1px に不透明な画素は無い（Python で全画素を調べた）
- 確認用の拡大画像（最近傍で 8 倍、図柄は白・背景は黒。128x128）: `art/src/exit_zoom.png`、`art/src/text_visible_zoom.png`、`art/src/text_hidden_zoom.png`

### 加工後に残る課題（Dev-Art-Director が採否を決める時の材料）

1. **text_visible・text_hidden の枠は、仕様の「線2px・間隔2px」の破線にならず、1 画素の点の並びに近い。** 元画像の枠の破線は周期が約 2 画素に相当し（枠 1 辺に 4.5 周期）、仕様の 3.5 周期（2 画素オン・2 画素オフ）と合わない。3 候補とも同じ。点の並びとして枠は読めるが、「破線の枠」としては点線に見える。直すなら、生成のプロンプトへ「一辺を 14 マスとし、マスは一辺 80px の正方形ブロックとして描く」のように格子を指定して撮り直す（採否の決定は Dev-Art-Director）
2. **text_hidden の A は、text_visible の A と同じ形にならなかった。** 「unchanged」と指示したが、生成では A の頂点が潰れ、右脚が斜線と一体になる。3 候補とも同じ傾向。14x14 では A の頭の幅・右脚が text_visible と違って見える
3. text_hidden の斜線は 14x14 で 1 画素幅の階段になったが、A の脚・横棒と交差する所で A の画素と重なる（交差は設計上避けられない）。枠の左上の角とは 1 画素の隙間がある

## 追加素材 12〜14 の採否と撮り直し（2026-10-27、Dev-Art-Director）

### 判定の方法と、確認できていないこと

- 見たもの: 加工後の 16x16 を 8 倍に拡大した確認用画像 3 枚（`*_zoom.png`）と、元画像（1254px）。既存アイコンとの比較は、既存の元画像（`text_src.png`・`visible_src.png`・`hidden_src.png`・`undo_src.png`）で作風（階段状の画素・白単色・線の太さ約 1.5〜2px）を見て行った
- **見られなかったもの: 16x16 の実体そのもの。** 実体は図柄が白・背景が透明なので、白地の表示では見えない。拡大画像は実体から作られたものとして信じた。**帯に並べた実際の見え方（既存アイコンとの隣り合わせ・GUI 倍率2の実寸）は未確認**で、Dev-QA または実機で確かめる（UI 検証の合否は `skills/ui-verification` のとおり）

### 採否

| # | 素材 | 採否 | 理由 |
|---|---|---|---|
| 12 | exit | **採用（exit_cand1）** | 太さ約 2px の×が一定の太さで連続し、四隅まで途切れない。14x14 の枠の中に収まり、外周 1px の余白がある。鉛筆・ドア・矢印・輪に見えず、仕様（太い×・階段状・斜めの線の太さ 2px）と合う。面積平均で縮めたため、既存（最近傍）より斜線の段差がやや滑らかだが、2 値化後の画素は白単色・alpha 0/255 で、帯に並べて浮く差ではない。中心がやや太く見える点は×として自然で、許容する |
| 13 | text_visible | **採用（text_visible_cand2）。ただし枠が点線になる点は許容（課題1）** | 枠と A の間に黒い隙間があり、A は左右対称で、横棒の上の穴が 2x2 ほど残って A と読める。枠付きの小さな A なので、枠なしの大きな A である `tool/text.png` と並べても見分けがつく（仕様 4.5 の「text.png と見分けがつく」を満たす）。枠は 1 画素オン・1 画素オフの点線で、仕様の 2px オン・2px オフの破線ではない。**許容する理由**: 16x16 では 2・2 の破線も点線も「点の並びの枠」としてしか見えず、仕様の目的（地図上の文字の背景＝ラベルの矩形を連想させる）は点線でも達成される。点線のほうが A との分離が保たれる。**仕様との差は Dev-UI-Designer へ報告し、追認を求める**（仕様の変更は Dev-UI-Designer の権限） |
| 14 | text_hidden | **不採用（暫定の text_hidden_cand3 を含む 3 枚とも）。撮り直し** | 課題2 のとおり A が text_visible と別の形になる（頂点が潰れ、右脚が斜線と一体）。この 2 枚は「同じ絵の表示・非表示」として対で使うので、A が別物では「文字が隠れた」ではなく「別の絵に変わった」と見える。課題2 は「3 候補とも同じ」で、候補を選び直しても直らず、プロンプトの再現性の問題。許容できる差ではない |

課題1〜3 の扱い:

| 課題 | 判断 |
|---|---|
| 1 枠が点線 | 許容（上の 13）。text_hidden も同じ枠にするので、撮り直しでも枠は変えない |
| 2 text_hidden の A が違う | **撮り直す**（下の方法で、A を text_visible と同じ画素にする） |
| 3 斜線が A の脚・横棒と交差する所で重なる | 許容。A の右脚は急な傾き、斜線は 45 度なので、重なる領域は限られる。重なった画素は白のまま（A の一部と斜線の一部が一体になるのは、既存 `hidden.png` の目と斜線でも同じ） |

### text_hidden の撮り直し（制作担当向けの指示）

**原因**: 前回は、`text_visible_cand2` を参照に渡して「unchanged（そのまま）」と指示したが、生成 AI は参照から**絵を描き直す**ので、A が変わる。プロンプトの言い方を変えても、描き直す方式のままでは同じことが起きる。**方式を変える。A と枠は描き直さず、採用済みの `text_visible.png` をそのまま土台にし、斜線だけを新しく生成して貼り合わせる。**

**画像生成 AI の入力に参照を渡すかについて**: 斜線 1 本の素材に、既存アイコンと見た目を揃える図柄は無く、揃えるのは線の太さ（1 画素）だけで、これは格子の指定で決める。参照画像を渡すと、`hidden_src.png` なら目が、`text_visible_cand2.png` なら枠と A が一緒に描かれて斜線だけにならない。そのため斜線の生成は参照なしにする（参照を渡せないのではなく、渡すと目的の図柄が出なくなるため。`skills/reference-material` の例外の理由として、ここに記録する）。土台の `text_visible.png` と見た目が揃うことは、貼り合わせる側（土台は変えない）で保証する。

**手順**

1. 斜線の素材を画像生成 AI で作る（`art/src/slash_cand1.png`〜`slash_cand3.png`。quality は medium、3 枚）。プロンプトの実文:
   "Single monochrome pixel-art icon, pure white on a solid pure black background, no gray, no gradient, no anti-aliasing. Exactly one thin diagonal line from the upper-left to the lower-right at a perfect 45 degree angle, drawn as a 10x10 pixel-art grid scaled up: every pixel is a solid square block of the same size (about 100x100 px), the line uses exactly 10 blocks, one per row, each touching the next only at its corner. Nothing else in the image: no frame, no letters, no shapes. Centered with margin. No text."
2. 3 枚のうち、次を満たす 1 枚を選ぶ（満たす物が無ければ撮り直し）: 斜線が 1 本だけ・途切れない・各行 1 ブロック・外接矩形の縦横比が 1:1 に近い（±10% 以内）・ブロックの大きさが揃っている。選ばなかった物は `slash_rejN.png`、採用は `slash_src.png`
3. 加工: 斜線の外接矩形を切り出し、**10x10 へ最近傍**で縮める（しきい値 128。ブロックが 10 個なら 1 ブロックが 1 画素になる）。黒は alpha 0、白は #FFFFFF・alpha 255
4. **貼り合わせ（合成）**: `src/main/resources/assets/mapscribble/textures/gui/tool/text_visible.png`（採用済み・変更しない）の上に、10x10 の斜線を **左上が (3,3)** になる位置で重ねる（Pillow の `Image.alpha_composite` または `paste`（マスク付き）。重なった画素は白のまま）。出力を `tool/text_hidden.png`（16x16・RGBA）にする
   - **斜線の画素を数式・図形描画で作らない**（`ImageDraw.line` や画素への代入で斜線を描くことは `rules/13-no-procedural-image.md` で禁止）。斜線は、生成 AI が作った画像を縮めた物だけを使う。画像の貼り合わせは同規則の対象外（「複数の生成画像の合成」）
   - (3,3)〜(12,12) は、枠（外周 1px、1〜14 の範囲）の内側で、四隅の L 字の角と 1 画素の隙間（(2,2) と (13,13) が空く）が残る位置
5. 確認（Python で全画素を調べ、結果を書く）:
   - `text_hidden.png` の、斜線と重なる 10 画素を除く全画素が、`text_visible.png` と同じ（A・枠が一致）
   - 斜線の 10 画素が全部不透明で、斜めに連続している（8 近傍で 1 つながり）
   - 斜線のうち枠の画素と隣り合う（8 近傍）画素が無い
   - alpha は 0 か 255 のみ、不透明部の色は #FFFFFF のみ
   - 8 倍拡大の確認用画像を `art/src/text_hidden_zoom.png` へ作り直す
6. 結果を `art/asset-spec.md` へ追記し、Dev-Art-Director へ再判定を依頼する

**第二案（斜線の貼り合わせが通らない時のみ）**: 生成を 2 回（計 6 枚）やっても、手順 2 の条件を満たす斜線が出ない時は、斜線の貼り合わせをやめ、`text_visible` と `text_hidden` を**同じ 1 枚の生成画像の中に並べて描かせ**（左が枠＋A、右が同じ枠＋同じ A＋斜線 1 本）、2 つに切り出して 14x14 へ縮める。同じ画像の中で描かせれば、A が 2 回別々に描き直されるのを避けられる。その場合は text_visible も同じ生成から取り直す（点線の枠の問題も同時に格子の指定で試す: 「一辺 28 マスの格子で、枠は 4 マスオン・4 マスオフ」）。**それでも通らない時は、Dev-Director へ上申する**（仕様の見直しは Dev-UI-Designer が行う）

### 名前の整理（Dev-Engineer）

- 採用: `exit_cand1.png` → `exit_src.png`、`text_visible_cand2.png` → `text_visible_src.png`
- 不採用: `exit_cand2.png`・`exit_cand3.png` → `exit_rej1.png`・`exit_rej2.png`、`text_visible_cand1.png`・`text_visible_cand3.png` → `text_visible_rej1.png`・`text_visible_rej2.png`、`text_hidden_cand1〜3.png` → `text_hidden_rej1〜3.png`
- 撮り直しの斜線: 採用 `slash_src.png`、不採用 `slash_rejN.png`
- `tool/region_text_visible.png`・`tool/region_text_hidden.png` は、設計書 4.5 のとおり `text_hidden.png` の確定後に実装側が削除した（2026-10-27。`ui/ToolBand.java` の参照は新しい `text_visible.png` / `text_hidden.png` だけ）

### 文字メモの印（菱形）の寸法についての見解（2026-10-27、実装担当からの確認への回答）

確認の内容: 設計書 4.3 の「対角 8 画面ピクセル（中心から各方向 4 画面ピクセル）」を、実装（`draw/MapCanvasRenderer.java` の `MARK_RADIUS = 4`）は「本体を中心から各方向 4 ピクセル、縁をその外側に 1 ピクセル」（外形は中心から各方向 5、対角 10）と解釈した。縁を含めて外形を対角 8 にするなら `MARK_RADIUS` は 3 になる。

見解（寸法の決定は Dev-UI-Designer の仕様。ここは見た目の判断材料）:

- **実装の解釈（本体の半径 4・縁は外側へ 1 ピクセル）を支持する。**
- 設計書 4.3 の表は「大きさ」と「縁」が別の行で、「大きさ」の行に「縁を含む」とは書かれていない。AC-73 の「対角 8 画面ピクセル、縁 1 画面ピクセル」も、2 つを並べた書き方で、縁は大きさの外に付くと読むのが自然
- 見た目の理由: 半径 3 の菱形の塗りは 25 ピクセル、半径 4 は 41 ピクセルで、半径 3 では本体が小さく、地図の地形の上でメモの色を見分けにくい。縁が黒（黒のメモは白）で本体の外を囲むなら、本体は大きいほうが色が読める
- 16x16 の帯のアイコンとの整合: 印は地図の上に**画面ピクセル（物理ピクセル）固定**で描くもの、帯のアイコンは **GUI ピクセル**で描くものなので、座標系が違い、大きさを比べて揃える関係にない。印と帯のアイコンの整合を、半径の 3 と 4 の違いで論じる必要は無い
- ただし文言が 2 通りに読めたこと自体が仕様の曖昧さなので、Dev-UI-Designer へ「`対角 8` は縁を含まない本体の寸法」と設計書に明記するか、外形で数えるなら 3 にするかの確認を依頼する。確定するまでは実装の解釈のまま（半径 4）で止めておいてよい

## 今後の追加生成

追加・撮り直しが要る時は、上のプロンプトと同じ前置きを使い、採用画像（`_src.png`）を参照に渡して線の太さをそろえる。icon の撮り直しは icon_src.png を参照にする。

### text_hidden の撮り直しの結果（2026-10-27、Dev-Engineer）

手順 2〜5 を実行した。生成は本体スレッドが済ませた 3 枚（1254x1254）を使い、画素の数え上げは Python（Pillow）で行った。ビルド・ゲーム内確認は行っていない。

**手順 2: 3 枚の判定（白＝階調 128 以上の画素を数えた）**

| 項目 | cand1 | cand2 | cand3 |
|---|---|---|---|
| 外接矩形（幅×高さ） | 646x645 | 844x832 | 691x690 |
| 縦横比（幅/高さ） | 1.002 | 1.014 | 1.001 |
| ブロックの列数（外接幅÷ブロック幅） | 9（ブロック幅 71〜72） | 10（ブロック幅 84〜85） | 9（ブロック幅 76〜77） |
| ブロックの高さ | 71〜72 | 82〜84 | 76〜77 |
| 8 近傍の連結成分数（生の画素） | 3（左上の 6 ブロック・次の 2 ブロック・最後の 1 ブロックの間に 1 画素の切れ目） | 2（9 番目と 10 番目の角が 1 画素届かない） | 1 |
| 白以外の中間階調の画素数（32〜223） | 2627 | 3317 | 2848 |
| 判定 | 不採用（ブロックが 9 個で、10x10 へ縮めると 1 ブロック 1 画素にならない） | **採用**（10 ブロック・各行 1 ブロック・縦横比 1.014 で ±10% 以内・ブロックの大きさが揃う） | 不採用（ブロックが 9 個） |

- cand2 の生の画素で連結成分が 2 になったのは、9 番目（x 921〜1005, y 871〜953）と 10 番目（x 1006〜1089, y 954〜1036）の角の画素が、生成時の縁のぶれで 1 画素欠けているため。ブロックの並び（10 列・10 行・各行 1 ブロック）では連続しており、10x10 へ最近傍で縮めると各ブロックの中心を取るので切れ目は残らない（手順 5 の確認 2 で 10 画素が 8 近傍で 1 つながりになることを確認した）
- 名前の整理: 採用 `slash_cand2.png` → `slash_src.png`、不採用 `slash_cand1.png` → `slash_rej1.png`、`slash_cand3.png` → `slash_rej2.png`

**手順 3・4: 加工と貼り合わせ**: `slash_src.png` の白（階調 128 以上）の外接矩形（x 246〜1089、y 205〜1036、844x832）を切り出し、10x10 へ最近傍で縮めた（縮小後の階調は 0・1・253・254・255 のみ）。しきい値 128 で、黒は alpha 0、白は #FFFFFF・alpha 255 にし、土台 `tool/text_visible.png`（変更していない）の左上 (3,3) へ `alpha_composite` で重ねて、`tool/text_hidden.png`（16x16・RGBA）を出力した。斜線の画素は数式・図形描画・画素への代入で作っていない。

**手順 5: 確認の結果（全 256 画素を Python で調べた）**

1. 斜線の 10 画素 (3,3)〜(12,12) の対角を除く 246 画素が `text_visible.png` と完全に一致した（不一致 0）。**合格**
2. 斜線の 10 画素が全部不透明（#FFFFFF・alpha 255）で、8 近傍で 1 つながり。**合格**
3. 斜線の画素と、枠（外周の帯 x または y が 1・14 にある不透明画素 32 個）が 8 近傍で隣り合う組は 0。(2,2) と (13,13) は alpha 0 のまま。**合格**
4. alpha は 0 と 255 のみ、不透明部の色は (255,255,255) のみ。**合格**

- 参考: 斜線の位置のうち (6,6)・(9,9)・(11,11) は土台の A の画素と重なり、白のまま残る（確認 1 の「重なる 10 画素」に含まれる。土台の A の画素は変わっていない）。斜線は中央の A とは接する・重なる（斜線が A を横切る図柄のため。仕様の確認 3 は「枠」との接触のみを対象にしている）
- 土台に対して変わった画素は 7 個（(3,3)・(4,4)・(5,5)・(7,7)・(8,8)・(10,10)・(12,12) が新たに不透明になった）
- 確認用の 8 倍拡大 `art/src/text_hidden_zoom.png`（128x128・グレースケール・既存の `text_visible_zoom.png` と同じ形式）を作り直した
- 未確認: 帯に並べた実際の見え方（ゲーム内）。Dev-Art-Director の再判定を依頼する
