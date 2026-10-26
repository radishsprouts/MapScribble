package io.github.radishsprouts.mapscribble.geom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

/** 線分の円による区間クリップの検証（円形のミニマップで、線が円の外へ出ないようにするため）。 */
class CircleClipTest {

    @Test
    void 両端が外で中央が内側の線分は円の中の区間に詰まる() {
        // 中心 (50, 50)・半径 10 の円を、y = 50 の水平線が貫く
        double[] cut = CircleClip.clipSegment(0, 50, 100, 50, 50, 50, 10);
        assertNotNull(cut);
        assertEquals(40.0, cut[0], 1e-9);
        assertEquals(50.0, cut[1], 1e-9);
        assertEquals(60.0, cut[2], 1e-9);
        assertEquals(50.0, cut[3], 1e-9);
        // 残った長さは直径
        assertEquals(20.0, Math.hypot(cut[2] - cut[0], cut[3] - cut[1]), 1e-9);
    }

    @Test
    void 円の外の線分は捨てられる() {
        assertNull(CircleClip.clipSegment(0, 0, 100, 0, 50, 50, 10));
        assertNull(CircleClip.clipSegment(0, 0, 0, 100, 50, 50, 10));
        // 円に接するだけの線分も、面積が無いので捨てる
        assertNull(CircleClip.clipSegment(0, 40, 100, 40, 50, 50, 10));
    }

    @Test
    void 円の中に収まる線分はそのまま残る() {
        double[] cut = CircleClip.clipSegment(48, 48, 52, 52, 50, 50, 10);
        assertNotNull(cut);
        assertEquals(48.0, cut[0], 1e-9);
        assertEquals(48.0, cut[1], 1e-9);
        assertEquals(52.0, cut[2], 1e-9);
        assertEquals(52.0, cut[3], 1e-9);
    }

    @Test
    void 片方だけ外へ出た線分は出た側だけ詰まる() {
        // 中心から右へ、円の外まで伸びる線分
        double[] cut = CircleClip.clipSegment(50, 50, 100, 50, 50, 50, 10);
        assertNotNull(cut);
        assertEquals(50.0, cut[0], 1e-9);
        assertEquals(60.0, cut[2], 1e-9);
    }

    @Test
    void 半径が0以下なら切る対象にしない() {
        assertNull(CircleClip.clipSegment(0, 0, 10, 10, 5, 5, 0));
        assertNull(CircleClip.clipSegment(0, 0, 10, 10, 5, 5, -1));
        assertFalse(CircleClip.contains(5, 5, 5, 5, 0));
    }

    @Test
    void 長さ0の線分は中心からの距離だけで判定する() {
        assertNotNull(CircleClip.clipSegment(55, 50, 55, 50, 50, 50, 10));
        assertNull(CircleClip.clipSegment(65, 50, 65, 50, 50, 50, 10));
    }

    @Test
    void 残った区間の両端は必ず円の内側にある() {
        Random random = new Random(3);
        for (int i = 0; i < 2000; i++) {
            double x0 = random.nextDouble() * 200 - 50;
            double y0 = random.nextDouble() * 200 - 50;
            double x1 = random.nextDouble() * 200 - 50;
            double y1 = random.nextDouble() * 200 - 50;
            double[] cut = CircleClip.clipSegment(x0, y0, x1, y1, 50, 50, 25);
            if (cut == null) {
                continue;
            }
            // 1e-6 の余裕は、2 次方程式の解の丸め分
            assertTrue(CircleClip.contains(cut[0], cut[1], 50, 50, 25.000001),
                    "始点が円の外に残った: " + cut[0] + ", " + cut[1]);
            assertTrue(CircleClip.contains(cut[2], cut[3], 50, 50, 25.000001),
                    "終点が円の外に残った: " + cut[2] + ", " + cut[3]);
        }
    }

    @Test
    void 点の内外判定は縁を含む() {
        assertTrue(CircleClip.contains(50, 50, 50, 50, 10));
        assertTrue(CircleClip.contains(60, 50, 50, 50, 10));
        assertFalse(CircleClip.contains(60.001, 50, 50, 50, 10));
    }
}
