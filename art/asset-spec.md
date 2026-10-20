# MapScribble 素材仕様書（作業3）

## 状態（2026-10-20 候補の選別まで完了）

**候補26枚の選別が終わった。撮り直しが要る素材は無い。次は Dev-Engineer による加工（縮小・2値化・透過・名前変更）と、11 hidden の生成。**

- 生成済み: 26枚（icon 2、toggle/enclose/line/text/eraser/edit/undo/visible 各3）。プログラム描画での代用なし。加工・描き足しなし。
- 選別済み: 採用9枚（下の「採否」）、不採用17枚。全素材で採用あり。
- 未生成: 9 redo（採用した undo の左右反転で作る。生成不要）、11 hidden（採用した visible_cand3 を image_url の参照に渡して生成する。参照を渡せる形（data URI か URL）への変換は Dev-Engineer）。
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

- 候補の命名は `<名前>_candN.png`。採用後に `<名前>_src.png`、不採用は `<名前>_rejN.png` へ整理する（Dev-Engineer）。
- 生成の経緯: 1 icon の1枚目はセッションのツール結果から取り出して icon_cand1 にコピー。残りは Node スクリプトで MCP を呼び、結果を `art/src/` へ保存。

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

### 撮り直し

なし。

### 9 redo と 11 hidden の注意

- 9 redo は undo_src.png の加工後の画像を左右反転して作る（生成しない）。
- **11 hidden の参照画像は `art/src/visible_src.png`（採用した visible_cand3.png の名前変更後。名前変更前は `art/src/visible_cand3.png`）。** プロンプトで言い換えず、必ず image_url に渡す。作る順は visible（済）→ hidden。hidden の線の太さ・目の形・瞳の大きさは visible に合わせ、斜線の太さも目の輪郭線と同じにする。

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
| 11 | art/src/hidden_src.png | textures/gui/tool/hidden.png | 16x16 | 同上 | 同上 | 同上 | 10 と同じ目の形・線の太さ。左上→右下の斜線が目を貫く |

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
- 11 hidden（未生成）: 採用した visible（visible_cand3）を image_url で参照に渡し、"Same eye, same stroke width and shape as the reference, add one thick diagonal slash from upper-left to lower-right across it."（参照は data URI か URL が要る。変換は Dev-Engineer）。斜線の太さは目の輪郭線に合わせる。

## 今後の追加生成

追加・撮り直しが要る時は、上のプロンプトと同じ前置きを使い、採用画像（`_src.png`）を参照に渡して線の太さをそろえる。icon の撮り直しは icon_src.png を参照にする。
