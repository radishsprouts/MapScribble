package io.github.radishsprouts.mapscribble.geom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.radishsprouts.mapscribble.model.MapPoint;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScanlineTest {
    private static final int W = 200;
    private static final int H = 120;

    /** 矩形の一覧を、ピクセルごとの塗られた回数に直す。 */
    private static int[][] coverage(List<int[]> rects) {
        int[][] grid = new int[H][W];
        for (int[] r : rects) {
            for (int y = r[1]; y < r[3]; y++) {
                for (int x = r[0]; x < r[2]; x++) {
                    grid[y][x]++;
                }
            }
        }
        return grid;
    }

    private static List<int[]> fill(double[] xs, double[] ys) {
        return Scanline.fillRects(xs, ys, xs.length, 0, 0, W, H);
    }

    /** 各ピクセルの中心が偶奇則で内側かを、直接判定した結果と帯の塗りが一致することを確かめる。 */
    private static void assertMatchesEvenOdd(double[] xs, double[] ys) {
        List<MapPoint> poly = new ArrayList<>();
        for (int i = 0; i < xs.length; i++) {
            poly.add(new MapPoint(xs[i], ys[i]));
        }
        int[][] grid = coverage(fill(xs, ys));
        int mismatches = 0;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                assertTrue(grid[y][x] <= 1, "帯が重なっている (" + x + "," + y + ")");
                boolean inside = Shapes.contains(poly, x + 0.5, y + 0.5);
                if (inside != (grid[y][x] == 1)) {
                    mismatches++;
                }
            }
        }
        assertEquals(0, mismatches, "塗りの隙間・はみ出し（ピクセル数）");
    }

    @Test
    void 凹形_U字は切れ込みを塗らず_隙間もない() {
        // 逆 U 字: 外枠 (20,10)-(180,100)、切れ込み x 70..130 を下から y 20 まで
        // （行の中心 y.5 が頂点の高さに載ると境界の扱いが分かれるので、20.6 にしてある）
        double[] xs = {20.3, 180.3, 180.3, 130.7, 130.7, 70.2, 70.2, 20.3};
        double[] ys = {10.4, 10.4, 100.6, 100.6, 20.6, 20.6, 100.6, 100.6};
        assertMatchesEvenOdd(xs, ys);
        int[][] grid = coverage(fill(xs, ys));
        assertEquals(0, grid[50][100], "切れ込みの中は塗らない");
        assertEquals(1, grid[50][40], "左の腕は塗る");
        assertEquals(1, grid[50][160], "右の腕は塗る");
        assertEquals(1, grid[15][100], "上の梁は塗る");
    }

    @Test
    void 自己交差した輪郭も偶奇で塗る() {
        // 蝶ネクタイ
        double[] xs = {20.3, 180.7, 180.7, 20.3};
        double[] ys = {10.4, 100.6, 10.4, 100.6};
        assertMatchesEvenOdd(xs, ys);
    }

    @Test
    void 斜めの辺の三角形も隙間がない() {
        double[] xs = {10.2, 190.8, 100.5};
        double[] ys = {5.3, 30.7, 115.9};
        assertMatchesEvenOdd(xs, ys);
    }

    @Test
    void 同じ幅が縦に続く行は1枚の矩形にまとまる() {
        double[] xs = {10, 50, 50, 10};
        double[] ys = {10, 10, 60, 60};
        List<int[]> rects = fill(xs, ys);
        assertEquals(1, rects.size());
        assertEquals(10, rects.get(0)[0]);
        assertEquals(10, rects.get(0)[1]);
        assertEquals(50, rects.get(0)[2]);
        assertEquals(60, rects.get(0)[3]);
    }

    @Test
    void 描く範囲の外は切り捨てる() {
        double[] xs = {-50, 400, 400, -50};
        double[] ys = {-50, -50, 300, 300};
        List<int[]> rects = fill(xs, ys);
        int[][] grid = coverage(rects);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                assertEquals(1, grid[y][x]);
            }
        }
        for (int[] r : rects) {
            assertTrue(r[0] >= 0 && r[1] >= 0 && r[2] <= W && r[3] <= H);
        }
    }

    @Test
    void 点が3つ未満なら何も塗らない() {
        assertTrue(Scanline.fillRects(new double[] {0, 10}, new double[] {0, 10}, 2, 0, 0, W, H).isEmpty());
    }

    @Test
    void 範囲の外にある多角形は何も塗らない() {
        double[] xs = {300, 400, 350};
        double[] ys = {10, 10, 50};
        assertTrue(fill(xs, ys).isEmpty());
    }
}
