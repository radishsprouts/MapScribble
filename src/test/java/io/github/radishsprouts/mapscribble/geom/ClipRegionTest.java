package io.github.radishsprouts.mapscribble.geom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 切り抜き領域（矩形＋任意の円）の検証。 */
class ClipRegionTest {

    @Test
    void 矩形だけの切り抜きは円の制限を持たない() {
        ClipRegion clip = ClipRegion.rect(10, 20, 110, 220);
        assertFalse(clip.hasCircle());
        assertEquals(100.0, clip.width(), 1e-9);
        assertEquals(200.0, clip.height(), 1e-9);
        // どの行でも左右の端は矩形のまま
        assertEquals(10.0, clip.rowMinX(20), 1e-9);
        assertEquals(110.0, clip.rowMaxX(219), 1e-9);
        // 中心から角までの距離
        assertEquals(Math.hypot(50, 100), clip.coverRadius(), 1e-9);
    }

    @Test
    void 矩形の内外判定は右下の端を含まない() {
        ClipRegion clip = ClipRegion.rect(0, 0, 100, 100);
        assertTrue(clip.contains(0, 0));
        assertTrue(clip.contains(99.9, 99.9));
        assertFalse(clip.contains(100, 50));
        assertFalse(clip.contains(50, 100));
        assertFalse(clip.contains(-0.1, 50));
    }

    @Test
    void 四角いミニマップの枠からは円なしの切り抜きができる() {
        MinimapGeometry square = new MinimapGeometry(200, 150, 50, 2.0, 90.0, false, 0, 0, null);
        ClipRegion clip = ClipRegion.of(square);
        assertFalse(clip.hasCircle());
        assertEquals(150.0, clip.minX(), 1e-9);
        assertEquals(100.0, clip.minY(), 1e-9);
        assertEquals(250.0, clip.maxX(), 1e-9);
        assertEquals(200.0, clip.maxY(), 1e-9);
    }

    @Test
    void 円形のミニマップの枠からは内接円の切り抜きができる() {
        MinimapGeometry circle = new MinimapGeometry(200, 150, 50, 2.0, 90.0, true, 0, 0, null);
        ClipRegion clip = ClipRegion.of(circle);
        assertTrue(clip.hasCircle());
        assertEquals(50.0, clip.radius(), 1e-9);
        assertEquals(200.0, clip.circleX(), 1e-9);
        assertEquals(150.0, clip.circleY(), 1e-9);
        assertEquals(50.0, clip.coverRadius(), 1e-9);

        // 円の内側だけが対象になる（枠の角は外れる）
        assertTrue(clip.contains(200, 150));
        assertTrue(clip.contains(249, 150));
        assertFalse(clip.contains(151, 101));
    }

    @Test
    void 円の行ごとの左右の端が弦になる() {
        ClipRegion clip = new ClipRegion(40, 40, 60, 60, 50, 50, 10);
        // 中心の行は直径いっぱい
        assertEquals(40.0, clip.rowMinX(50), 1e-9);
        assertEquals(60.0, clip.rowMaxX(50), 1e-9);
        // 中心から 6 ずれた行は半弦が 8
        assertEquals(42.0, clip.rowMinX(56), 1e-9);
        assertEquals(58.0, clip.rowMaxX(56), 1e-9);
        // 円に掛からない行は rowMinX >= rowMaxX になり、空だと分かる
        assertTrue(clip.rowMinX(20) >= clip.rowMaxX(20));
        assertTrue(clip.rowMinX(80) >= clip.rowMaxX(80));
    }

    @Test
    void 円が矩形より大きくても矩形の外へは出ない() {
        ClipRegion clip = new ClipRegion(45, 45, 55, 55, 50, 50, 100);
        assertEquals(45.0, clip.rowMinX(50), 1e-9);
        assertEquals(55.0, clip.rowMaxX(50), 1e-9);
    }
}
