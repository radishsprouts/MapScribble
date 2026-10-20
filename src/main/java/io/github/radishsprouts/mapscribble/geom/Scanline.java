package io.github.radishsprouts.mapscribble.geom;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 多角形の塗りを、横の帯（矩形）の並びに分解する（偶奇則）。
 *
 * <p>GuiGraphicsExtractor に任意の多角形を出すメソッドが無く、矩形の fill だけが使えるため、
 * 1 ピクセルの高さの行ごとに輪郭との交点を組にして矩形にする。同じ x 範囲が縦に続く行は 1 枚にまとめ、
 * fill の呼び出し回数を減らす。行はピクセルの中心（y + 0.5）で標本化するので、隣り合う行の間に隙間は生じない。
 */
public final class Scanline {
    private Scanline() {
    }

    /**
     * @param xs         輪郭の x（ピクセル座標）。終点から始点へは自動で閉じる
     * @param ys         輪郭の y
     * @param n          使う点の数
     * @param clipMinX   描く範囲（左。含む）
     * @param clipMinY   同じく上（含む）
     * @param clipMaxX   同じく右（含まない）
     * @param clipMaxY   同じく下（含まない）
     * @return 矩形 {x0, y0, x1, y1}（x1・y1 は含まない）の一覧。上から下へ、行ごとに左から右へ
     */
    public static List<int[]> fillRects(double[] xs, double[] ys, int n, int clipMinX, int clipMinY, int clipMaxX,
                                        int clipMaxY) {
        List<int[]> result = new ArrayList<>();
        if (n < 3 || clipMaxY <= clipMinY || clipMaxX <= clipMinX) {
            return result;
        }
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < n; i++) {
            minY = Math.min(minY, ys[i]);
            maxY = Math.max(maxY, ys[i]);
        }
        int rowStart = Math.max(clipMinY, (int) Math.ceil(minY - 0.5));
        int rowEnd = Math.min(clipMaxY, (int) Math.ceil(maxY - 0.5));
        int rows = rowEnd - rowStart;
        if (rows <= 0) {
            return result;
        }

        // 1 回目: 行ごとの交点の数を数える
        int[] counts = new int[rows];
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            double y0 = ys[i];
            double y1 = ys[j];
            if (y0 == y1) {
                continue;
            }
            int from = Math.max(rowStart, (int) Math.ceil(Math.min(y0, y1) - 0.5));
            int to = Math.min(rowEnd, (int) Math.ceil(Math.max(y0, y1) - 0.5));
            for (int row = from; row < to; row++) {
                counts[row - rowStart]++;
            }
        }
        int[] offsets = new int[rows + 1];
        for (int r = 0; r < rows; r++) {
            offsets[r + 1] = offsets[r] + counts[r];
        }
        double[] crossings = new double[offsets[rows]];
        int[] filled = new int[rows];

        // 2 回目: 交点の x を行ごとに入れる
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            double x0 = xs[i];
            double y0 = ys[i];
            double x1 = xs[j];
            double y1 = ys[j];
            if (y0 == y1) {
                continue;
            }
            int from = Math.max(rowStart, (int) Math.ceil(Math.min(y0, y1) - 0.5));
            int to = Math.min(rowEnd, (int) Math.ceil(Math.max(y0, y1) - 0.5));
            double slope = (x1 - x0) / (y1 - y0);
            for (int row = from; row < to; row++) {
                double yc = row + 0.5;
                int r = row - rowStart;
                crossings[offsets[r] + filled[r]++] = x0 + (yc - y0) * slope;
            }
        }

        // 行ごとに組にして矩形へ。同じ x 範囲が縦に続けば伸ばす
        Map<Long, int[]> open = new HashMap<>();
        for (int r = 0; r < rows; r++) {
            int row = rowStart + r;
            int lo = offsets[r];
            int hi = offsets[r + 1];
            Arrays.sort(crossings, lo, hi);
            Map<Long, int[]> next = new HashMap<>();
            for (int k = lo; k + 1 < hi; k += 2) {
                int x0 = Math.max(clipMinX, (int) Math.ceil(crossings[k] - 0.5));
                int x1 = Math.min(clipMaxX, (int) Math.ceil(crossings[k + 1] - 0.5));
                if (x1 <= x0) {
                    continue;
                }
                long key = ((long) x0 << 32) | (x1 & 0xffffffffL);
                int[] rect = open.remove(key);
                if (rect != null && rect[3] == row) {
                    rect[3] = row + 1;
                } else {
                    rect = new int[] {x0, row, x1, row + 1};
                    result.add(rect);
                }
                next.put(key, rect);
            }
            open = next;
        }
        return result;
    }
}
