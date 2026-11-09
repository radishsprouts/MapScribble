package io.github.radishsprouts.mapscribble.geom;

import java.util.List;

/**
 * 自前描画の切替ボタン（Xaero / FTB Chunks / 旧 JourneyMap）の位置の計算。Minecraft に依存しない（画面設計書 3.5.1）。
 *
 * <p>位置は「近い画面の端からの距離」で覚える。右半分なら右端から、左半分なら左端から、縦も同じ。
 * 画面の大きさや GUI 倍率が変わっても、端に沿った地図 MOD の表示の隣に置いたままになる。
 */
public final class ToggleButtonPlacement {
    /** ボタンの一辺（GUI ピクセル）。 */
    public static final int SIZE = 20;
    /** クリックとドラッグを分けるしきい値（画面ピクセル）。 */
    public static final int DRAG_THRESHOLD_PX = 4;

    /** 保存のキー（地図 MOD ごと）。 */
    public static final String KEY_XAERO = "xaero";
    public static final String KEY_FTB_CHUNKS = "ftb_chunks";
    public static final String KEY_JOURNEYMAP_LEGACY = "journeymap_legacy";
    public static final List<String> KEYS = List.of(KEY_XAERO, KEY_FTB_CHUNKS, KEY_JOURNEYMAP_LEGACY);

    private ToggleButtonPlacement() {
    }

    /**
     * 保存する位置。right が true なら dx は右端からの距離、false なら左端から。bottom と dy も同じ（縦）。
     * dx / dy は 0 以上。
     */
    public record Saved(boolean right, boolean bottom, int dx, int dy) {
        public Saved {
            if (dx < 0 || dy < 0) {
                throw new IllegalArgumentException("dx / dy は 0 以上");
            }
        }
    }

    /** 左上の座標を 0〜(画面の大きさ − ボタンの大きさ) に丸める。画面がボタンより小さければ 0。 */
    public static int clamp(int value, int screenSize) {
        return Math.max(0, Math.min(Math.max(0, screenSize - SIZE), value));
    }

    /** 左上 (x, y) に置いたボタンを、近い端からの距離にして返す。座標は先に画面内へ丸める。 */
    public static Saved fromPosition(int x, int y, int screenW, int screenH) {
        int cx = clamp(x, screenW);
        int cy = clamp(y, screenH);
        boolean right = cx + SIZE / 2.0 >= screenW / 2.0;
        boolean bottom = cy + SIZE / 2.0 >= screenH / 2.0;
        int dx = right ? Math.max(0, screenW - SIZE - cx) : cx;
        int dy = bottom ? Math.max(0, screenH - SIZE - cy) : cy;
        return new Saved(right, bottom, dx, dy);
    }

    /** 保存値から表示する左上の X。画面内に丸める（保存値は変えない）。 */
    public static int resolveX(Saved saved, int screenW) {
        int raw = saved.right() ? (int) Math.max(Integer.MIN_VALUE, (long) screenW - SIZE - saved.dx()) : saved.dx();
        return clamp(raw, screenW);
    }

    /** 保存値から表示する左上の Y。画面内に丸める（保存値は変えない）。 */
    public static int resolveY(Saved saved, int screenH) {
        int raw = saved.bottom() ? (int) Math.max(Integer.MIN_VALUE, (long) screenH - SIZE - saved.dy()) : saved.dy();
        return clamp(raw, screenH);
    }

    /**
     * 離した位置を保存する値にする。既定の位置と同じなら null（動かしていないものとして、キーを書かない）。
     */
    public static Saved toSaveOrNull(int x, int y, int screenW, int screenH, int defaultX, int defaultY) {
        if (clamp(x, screenW) == clamp(defaultX, screenW) && clamp(y, screenH) == clamp(defaultY, screenH)) {
            return null;
        }
        return fromPosition(x, y, screenW, screenH);
    }

    /**
     * 押した点から、横か縦のどちらかが 4 画面ピクセル以上離れたか。両方を GUI 座標の整数に切り捨ててから比べる
     * （押した時の座標は小数、描画中の座標は整数で来るので、そのまま比べると倍率が大きい時に動かしていなくても
     * ドラッグになる）。
     */
    public static boolean isDrag(double pressX, double pressY, double curX, double curY, int guiScale) {
        long dx = Math.abs((long) Math.floor(curX) - (long) Math.floor(pressX));
        long dy = Math.abs((long) Math.floor(curY) - (long) Math.floor(pressY));
        return Math.max(dx, dy) * Math.max(1, guiScale) >= DRAG_THRESHOLD_PX;
    }

    /** 掴んだ点を保った左上の座標: カーソル − (押した時のカーソルとボタン左上の差)。画面内に丸める。 */
    public static int follow(double cursor, int grabOffset, int screenSize) {
        return clamp((int) Math.floor(cursor) - grabOffset, screenSize);
    }

    /** 押した時の、カーソルとボタン左上の差（切り捨てた GUI 座標どうし）。 */
    public static int grabOffset(double press, int buttonPos) {
        return (int) Math.floor(press) - buttonPos;
    }
}
