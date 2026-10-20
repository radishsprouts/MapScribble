# MapScribble 素材仕様書（作業3）

## 状態（2026-10-20 再開後）

**生成は動く（クレジット補充済み）が、画像ファイルを `art/src/` に保存する手段が Dev-Art-Director に無く、1枚目で中断した。**

- 生成した枚数: 1（1 icon の候補1枚。provider=openai model=gpt-image-2 quality=medium）。採否は未判定。
- 画像生成 MCP は画像を添付で返すだけで、保存先を指定できない。Write は Markdown 限定、Bash も無いため、PNG を `art/src/` へ置けない。MCP の resources/read は1回読むと破棄される。
- 1 icon の生成物は、セッションのツール結果フォルダに残っている（コピー元）: `S:\Projects\NodeJS\ai-company\ai-company-home\projects\S--Projects-minecraft-MOD\3b91bf05-15f6-486b-ad07-23730fc20ef4\tool-results\mcp-image-generator-blob-1792478912016-pyn3e5.png`。内容は、ドット調の破れた地図、赤い手書きの輪、鉛筆、背景は薄灰、文字なし。仕様どおりに見える。`art/src/icon_src.png` への保存は Dev-Engineer が行う。
- 残りの生成と保存は、保存できる手段（Node スクリプトから MCP を直接呼んで結果を `art/src/` へ書く。`skills/tool-usage/mcp-image-generator.md` の手順）を持つ担当が行う。保存できないまま枚数だけ増やすと、クレジットの消費だけが残るため、生成はここで止めた。
- プログラム描画での代用はしていない。11 は 10 の採用画像が無いため未生成。9 は加工で作る。

### 候補の生成と保存（2026-10-20、Node スクリプトから MCP を直接呼んで保存。失敗0）

候補を `art/src/` に保存した（計26枚。うち25枚を今回生成、1枚は上の生成済みをコピー）。加工・描き足しはしていない（縮小・2値化・透過は未実施）。採否は未判定。

| # | 素材 | quality | 枚数 | ファイル |
|---|---|---|---|---|
| 1 | icon | high | 2 | icon_cand1.png（コピー元は上記の生成済み1枚。生成時は medium）, icon_cand2.png |
| 2 | toggle | medium | 3 | toggle_cand1〜3.png |
| 3 | enclose | medium | 3 | enclose_cand1〜3.png |
| 4 | line | medium | 3 | line_cand1〜3.png |
| 5 | text | medium | 3 | text_cand1〜3.png |
| 6 | eraser | medium | 3 | eraser_cand1〜3.png |
| 7 | edit | medium | 3 | edit_cand1〜3.png |
| 8 | undo | medium | 3 | undo_cand1〜3.png |
| 10 | visible | medium | 3 | visible_cand1〜3.png |

- 9 redo（undo の反転で作る）と 11 hidden（10 の採用画像を参照に作る）は未生成。
- 候補の命名は `<名前>_candN.png`。採用後に `<名前>_src.png`、不採用は `<名前>_rejN.png` へ整理する。

選んだ理由の記録は、採否を決めてからここへ追記する。

## 共通

- 生成は白黒（白線・純黒背景）で行い、加工で「黒 → 透明、白 → #FFFFFF」にする。参考画像は支給なし。JourneyMap の図柄・名前は入れない。文字入りにしない（text の「A」は図柄）。
- 各ツール素材は2〜3候補を生成し、16x16 に縮小して読めるものを選ぶ。縮小して潰れる案は落とす。
- 縮小は最近傍。縮小前に、図柄の外接矩形を求めて 14x14（toggle は 28x28）に収まるよう余白を付ける（外周 1px / toggle は 2px を空ける）。
- 2値化: 輝度 128 を目安（細線が欠けるなら 96、ノイズが出るなら 160）。半透明の中間色を残さない。
- 保存先の元画像: `art/src/<名前>_src.png`（採用）、`art/src/<名前>_rejN.png`（不採用）。

## 素材一覧

| # | 元画像 | 最終ファイル（assets/mapscribble/ から） | 大きさ | 縮小・2値化 | 収める範囲 | 透過 | 加工後の確認点 |
|---|---|---|---|---|---|---|---|
| 1 | art/src/icon_src.png | icon.png | 128x128 | 色付きのまま。背景（単色の薄灰）を透過、縮小は最近傍。2値化しない | 外周 4px 空け | 背景のみ透過 | 輪・鉛筆・地図が判別できる。32x32 に落としても赤い輪が見える。文字なし |
| 2 | art/src/toggle_src.png | textures/gui/toggle.png | 32x32 | 最近傍で 28x28 に縮小して中央配置、しきい値 128 | 28x28（外周 2px 空け）、線 2px | 黒を透過 | 先が左下の斜め鉛筆と分かる。グレー無し |
| 3 | art/src/enclose_src.png | textures/gui/tool/enclose.png | 16x16 | 14x14 へ最近傍、しきい値 128 | 14x14（外周 1px） | 黒を透過 | 閉じた輪になっている（欠けて開かない）。線 1px |
| 4 | art/src/line_src.png | textures/gui/tool/line.png | 16x16 | 同上 | 同上 | 同上 | 左下→右上の波線。閉じていない。途切れない |
| 5 | art/src/text_src.png | textures/gui/tool/text.png | 16x16 | 同上 | 同上 | 同上 | 「A」と読める。横棒が潰れない |
| 6 | art/src/eraser_src.png | textures/gui/tool/eraser.png | 16x16 | 同上 | 同上 | 同上 | 斜めの消しゴムで片側が塗り。縁が欠けない |
| 7 | art/src/edit_src.png | textures/gui/tool/edit.png | 16x16 | 同上 | 同上 | 同上 | 矢印カーソル（鉛筆に見えない） |
| 8 | art/src/undo_src.png | textures/gui/tool/undo.png | 16x16 | 同上 | 同上 | 同上 | 左回りで鏃が分かる |
| 9 | 8 と同じ（undo を左右反転） | textures/gui/tool/redo.png | 16x16 | undo.png の加工後の画像を左右反転 | 同上 | 同上 | 右回りで鏃が分かる |
| 10 | art/src/visible_src.png | textures/gui/tool/visible.png | 16x16 | 同上 | 同上 | 同上 | 目と分かる。瞳が潰れて穴にならない |
| 11 | art/src/hidden_src.png | textures/gui/tool/hidden.png | 16x16 | 同上 | 同上 | 同上 | 10 と同じ目の形・線の太さ。左上→右下の斜線が目を貫く |

## 生成プロンプト

共通の前置き: "Single monochrome icon, pure white thick pixel-art line drawing on a solid pure black background, no gray, no gradient, no anti-aliasing, ... readable at 16x16 pixels, centered with margin. No text."

- 1 icon（送信済み・1枚生成、quality=medium、参照なし）: "Square app icon, chunky pixel-art feel (32x32 art enlarged 4x). A torn fragment of an old paper map in warm beige with faint green and blue terrain patches, a hand-drawn irregular red circle on it, and a yellow pencil diagonally across the corner. No text, no logos. Flat solid light-gray background easy to remove. Bold outlines, limited palette."
- 2 toggle: 共通 + "A diagonal pencil, tip pointing lower-left, bold 2-pixel strokes on a 32x32 grid look."
- 3 enclose: 共通 + "A hand-drawn wobbly irregular closed loop, one thick line."
- 4 line: 共通 + "A hand-drawn wavy open curve from lower-left to upper-right, not closed, one or two gentle waves."
- 5 text: 共通 + "A bold capital letter A, simple block sans-serif."
- 6 eraser: 共通 + "An eraser tilted diagonally: rounded-corner rectangle, one half solid white, the other half outlined."
- 7 edit: 共通 + "A classic mouse pointer arrow cursor pointing upper-left, solid white."
- 8 undo: 共通 + "A curved arrow curving counter-clockwise, arrowhead at the left end."
- 10 visible: 共通 + "An open eye: almond outline with a solid round pupil."
- 11 hidden（予定）: 10 の採用画像を image_url で参照に渡し、"Same eye, same stroke width and shape as the reference, add one thick diagonal slash from upper-left to lower-right across it."（参照は data URI か URL が要る。変換は Dev-Engineer）
