# MapScribble 素材仕様書（作業3）

## 状態（2026-10-20）

**11点とも未生成。** 画像生成 MCP（mcp__image-generator__gpt-image）が全呼び出しで次のエラーを返した。

`openai の画像生成に失敗しました (You have no credits remaining. Add credits to continue using the API ...)`

生成0枚・採用0枚。`art/src/` に画像は無い。プログラム描画での代用はしていない。クレジット補充（金銭の支出）は社長の判断。補充後に下のプロンプトで再開する。

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

## 生成プロンプト（送信済みのものと予定のもの。結果はすべて失敗＝残高なし）

共通の前置き: "Single monochrome icon, pure white thick pixel-art line drawing on a solid pure black background, no gray, no gradient, no anti-aliasing, ... readable at 16x16 pixels, centered with margin. No text."

- 1 icon: "Square app icon, chunky pixel-art feel (32x32 art enlarged 4x). A torn fragment of an old paper map in warm beige with faint green and blue terrain patches, a hand-drawn irregular red circle on it, and a yellow pencil diagonally across the corner. No text, no logos. Flat solid light-gray background easy to remove. Bold outlines, limited palette."
- 2 toggle: 共通 + "A diagonal pencil, tip pointing lower-left, bold 2-pixel strokes on a 32x32 grid look."
- 3 enclose: 共通 + "A hand-drawn wobbly irregular closed loop, one thick line."
- 4 line: 共通 + "A hand-drawn wavy open curve from lower-left to upper-right, not closed, one or two gentle waves."
- 5 text: 共通 + "A bold capital letter A, simple block sans-serif."
- 6 eraser: 共通 + "An eraser tilted diagonally: rounded-corner rectangle, one half solid white, the other half outlined."
- 7 edit: 共通 + "A classic mouse pointer arrow cursor pointing upper-left, solid white."
- 8 undo: 共通 + "A curved arrow curving counter-clockwise, arrowhead at the left end."
- 10 visible: 共通 + "An open eye: almond outline with a solid round pupil."
- 11 hidden（予定）: 10 の採用画像を image_url で参照に渡し、"Same eye, same stroke width and shape as the reference, add one thick diagonal slash from upper-left to lower-right across it."（10 の画像の URL が得られること、または data URI 変換が必要。Bash が無いため変換が要る場合は Dev-Engineer に依頼）

選んだ理由の記録は、生成後にここへ追記する（現時点で生成0枚のため無し）。
