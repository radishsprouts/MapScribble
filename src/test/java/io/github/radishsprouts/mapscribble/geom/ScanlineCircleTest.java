package io.github.radishsprouts.mapscribble.geom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * スキャンライン塗りの円クリップの検証（円形のミニマップで、塗りが円の外へ出ないようにするため）。
 * 円の弦で行ごとに詰める実装なので、行の帯が円の内側に収まることを数値で確かめる。
 */
class ScanlineCircleTest {

    /** 円を完全に覆う大きな四角形の輪郭。 */
    private static final double[] BIG_SQUARE_X = {0, 100, 100, 0};
    private static final double[] BIG_SQUARE_Y = {0, 0, 100, 100};

    @Test
    void 円を指定しなければ従来と同じ結果になる() {
        List<int[]> withoutCircle = Scanline.fillRects(BIG_SQUARE_X, BIG_SQUARE_Y, 4, 0, 0, 100, 100);
        List<int[]> explicitNoCircle =
                Scanline.fillRects(BIG_SQUARE_X, BIG_SQUARE_Y, 4, 0, 0, 100, 100, 50, 50, -1);
        assertEquals(withoutCircle.size(), explicitNoCircle.size());
        for (int i = 0; i < withoutCircle.size(); i++) {
            assertArrayEqualsInt(withoutCircle.get(i), explicitNoCircle.get(i));
        }
    }

    @Test
    void 円の外の行には帯ができない() {
        List<int[]> rects = Scanline.fillRects(BIG_SQUARE_X, BIG_SQUARE_Y, 4, 0, 0, 100, 100, 50, 50, 20);
        assertFalse(rects.isEmpty());
        for (int[] r : rects) {
            assertTrue(r[1] >= 30, "円の上に帯が残った: y0=" + r[1]);
            assertTrue(r[3] <= 70, "円の下に帯が残った: y1=" + r[3]);
        }
    }

    @Test
    void 中心の行の帯の幅が直径に等しい() {
        List<int[]> rects = Scanline.fillRects(BIG_SQUARE_X, BIG_SQUARE_Y, 4, 0, 0, 100, 100, 50, 50, 20);
        int width = 0;
        for (int[] r : rects) {
            if (r[1] <= 50 && 50 < r[3]) {
                width = r[2] - r[0];
            }
        }
        // 行はピクセルの中心（50.5）で標本化するので、半弦は sqrt(400 - 0.25) ≒ 19.994 になる
        assertEquals(40, width, 1, "中心の行の幅が直径と合わない: " + width);
    }

    @Test
    void どの帯も円の弦の内側にある() {
        double cx = 50;
        double cy = 50;
        double r = 20;
        List<int[]> rects = Scanline.fillRects(BIG_SQUARE_X, BIG_SQUARE_Y, 4, 0, 0, 100, 100, cx, cy, r);
        for (int[] rect : rects) {
            for (int row = rect[1]; row < rect[3]; row++) {
                double dy = row + 0.5 - cy;
                double half = Math.sqrt(Math.max(0, r * r - dy * dy));
                // 行の標本点の左右 0.5 ピクセルの丸め分だけ余裕を見る
                assertTrue(rect[0] >= cx - half - 1.0,
                        "帯の左端が円の外: x0=" + rect[0] + " row=" + row);
                assertTrue(rect[2] <= cx + half + 1.0,
                        "帯の右端が円の外: x1=" + rect[2] + " row=" + row);
            }
        }
    }

    @Test
    void 塗った面積が円の面積に近い() {
        double r = 30;
        List<int[]> rects = Scanline.fillRects(BIG_SQUARE_X, BIG_SQUARE_Y, 4, 0, 0, 100, 100, 50, 50, r);
        long area = 0;
        for (int[] rect : rects) {
            area += (long) (rect[2] - rect[0]) * (rect[3] - rect[1]);
        }
        double expected = Math.PI * r * r;
        assertTrue(Math.abs(area - expected) / expected < 0.05,
                "面積が円と合わない: " + area + " 期待 " + expected);
    }

    @Test
    void 円の外にある図形は何も塗らない() {
        double[] xs = {0, 10, 10, 0};
        double[] ys = {0, 0, 10, 10};
        List<int[]> rects = Scanline.fillRects(xs, ys, 4, 0, 0, 100, 100, 80, 80, 10);
        assertTrue(rects.isEmpty());
    }

    @Test
    void 円で分かれた行があっても縦のまとめが壊れない() {
        // 上下 2 つの帯に分かれる形（砂時計）を円で切る。円の外の行で縦のまとまりが切れること
        double[] xs = {40, 60, 40, 60};
        double[] ys = {30, 30, 70, 70};
        List<int[]> rects = Scanline.fillRects(xs, ys, 4, 0, 0, 100, 100, 50, 50, 5);
        for (int[] rect : rects) {
            assertTrue(rect[1] >= 45 && rect[3] <= 55, "円の外へ帯が伸びた: " + rect[1] + "〜" + rect[3]);
            assertTrue(rect[0] >= 45 && rect[2] <= 55, "円の外へ帯が伸びた: " + rect[0] + "〜" + rect[2]);
        }
    }

    private static void assertArrayEqualsInt(int[] expected, int[] actual) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i]);
        }
    }
}
