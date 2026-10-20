package io.github.radishsprouts.mapscribble.geom;

/** 文字メモの表示範囲の見積もり（当たり判定用）。 */
public final class TextBox {
    /** 文字の背景の余白（GUI ピクセル）。 */
    public static final int PADDING_GUI = 2;

    private TextBox() {
    }

    /**
     * アンカーを中心にした文字の矩形（ワールド座標）。
     *
     * @param anchorX        文字の中心のワールド X
     * @param anchorZ        同じく Z
     * @param maxLineWidth   一番長い行の幅（GUI ピクセル）
     * @param lines          行数
     * @param lineHeight     1 行の高さ（GUI ピクセル。Minecraft の既定は 9）
     * @param scale          文字の大きさの倍率
     * @param worldPerGuiPx  GUI の 1 ピクセルのワールド上の長さ（ブロック）
     */
    public static Rect bounds(double anchorX, double anchorZ, int maxLineWidth, int lines, int lineHeight,
                              float scale, double worldPerGuiPx) {
        double halfW = (maxLineWidth * scale / 2.0 + PADDING_GUI) * worldPerGuiPx;
        double halfH = (Math.max(1, lines) * lineHeight * scale / 2.0 + PADDING_GUI) * worldPerGuiPx;
        return new Rect(anchorX - halfW, anchorZ - halfH, anchorX + halfW, anchorZ + halfH);
    }
}
