package io.github.radishsprouts.mapscribble.draw;

import io.github.radishsprouts.mapscribble.geom.Shapes;
import io.github.radishsprouts.mapscribble.model.MapPoint;
import io.github.radishsprouts.mapscribble.model.Memo;
import io.github.radishsprouts.mapscribble.model.MemoKind;

/** 地図上のメモの見た目の値（画面設計書 3.3）。全画面マップとミニマップで同じ値を使う。 */
public final class MemoStyle {
    /** 「黒」のメモの色。この色だけは白い背景・影なしで描く。 */
    public static final int BLACK = 0x1A1A1A;
    /** 線の太さの段階（1〜3）ごとの画面上の太さ（物理ピクセル）。 */
    private static final float[] STROKE_PIXELS = {2.0f, 4.0f, 7.0f};
    /** 文字の背景の不透明度。 */
    public static final float TEXT_BACKGROUND_OPACITY = 0.80f;

    private MemoStyle() {
    }

    /** 線の太さの段階 → 画面上の太さ（物理ピクセル）。範囲外の段階は端に丸める。 */
    public static float strokePixels(int width) {
        int index = Math.max(1, Math.min(STROKE_PIXELS.length, width)) - 1;
        return STROKE_PIXELS[index];
    }

    public static boolean isBlack(int color) {
        return (color & 0xFFFFFF) == BLACK;
    }

    /** 文字の背景色（RGB）。黒のメモだけ白、それ以外は黒。 */
    public static int textBackgroundRgb(int color) {
        return isBlack(color) ? 0xFFFFFF : 0x000000;
    }

    /** 文字に影を付けるか（黒のメモだけ付けない）。 */
    public static boolean textShadow(int color) {
        return !isBlack(color);
    }

    /** 文字・線のアンカー: 囲みは重心、線は点列の中ほどの点、文字は置いた点。 */
    public static MapPoint anchor(Memo memo) {
        if (memo.points().isEmpty()) {
            return new MapPoint(0, 0);
        }
        if (memo.kind() == MemoKind.REGION) {
            return Shapes.centroid(memo.points());
        }
        if (memo.kind() == MemoKind.LINE) {
            return memo.points().get(memo.points().size() / 2);
        }
        return memo.points().get(0);
    }

    /** 文字を行に分ける。空文字なら行なし。 */
    public static String[] lines(String text) {
        if (text == null || text.isEmpty()) {
            return new String[0];
        }
        return text.split("\n", -1);
    }

    /** 0xRRGGBB と 0.0〜1.0 の不透明度 → 0xAARRGGBB。 */
    public static int argb(int rgb, float opacity) {
        int alpha = Math.max(0, Math.min(255, Math.round(opacity * 255f)));
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }
}
