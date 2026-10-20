package io.github.radishsprouts.mapscribble.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BandLayoutTest {
    private static long count(BandLayout.Kind kind) {
        return BandLayout.cells().stream().filter(c -> c.kind() == kind).count();
    }

    @Test
    void ボタンの数は設計書どおり() {
        assertEquals(5, count(BandLayout.Kind.TOOL));
        assertEquals(1, count(BandLayout.Kind.UNDO));
        assertEquals(1, count(BandLayout.Kind.REDO));
        assertEquals(1, count(BandLayout.Kind.VISIBILITY));
        assertEquals(3, count(BandLayout.Kind.WIDTH));
        assertEquals(10, count(BandLayout.Kind.COLOR));
    }

    @Test
    void すべてのボタンは帯の中に収まり_互いに重ならない() {
        var cells = BandLayout.cells();
        for (int i = 0; i < cells.size(); i++) {
            var a = cells.get(i);
            assertTrue(a.x() >= BandLayout.X && a.x() + a.w() <= BandLayout.X + BandLayout.WIDTH, a.toString());
            assertTrue(a.y() >= BandLayout.Y && a.y() + a.h() <= BandLayout.Y + BandLayout.HEIGHT, a.toString());
            for (int j = i + 1; j < cells.size(); j++) {
                var b = cells.get(j);
                boolean overlap = a.x() < b.x() + b.w() && b.x() < a.x() + a.w()
                        && a.y() < b.y() + b.h() && b.y() < a.y() + a.h();
                assertFalse(overlap, a + " と " + b);
            }
        }
    }

    @Test
    void 帯の範囲の判定は左上を含み_右下を含まない() {
        assertTrue(BandLayout.contains(BandLayout.X, BandLayout.Y));
        assertTrue(BandLayout.contains(BandLayout.X + BandLayout.WIDTH - 1, BandLayout.Y + BandLayout.HEIGHT - 1));
        assertFalse(BandLayout.contains(BandLayout.X - 1, BandLayout.Y));
        assertFalse(BandLayout.contains(BandLayout.X + BandLayout.WIDTH, BandLayout.Y));
        assertFalse(BandLayout.contains(BandLayout.X, BandLayout.Y + BandLayout.HEIGHT));
    }

    @Test
    void ボタンの座標からボタンを引ける() {
        var first = BandLayout.cells().get(0);
        var hit = BandLayout.cellAt(first.x() + 1, first.y() + 1);
        assertNotNull(hit);
        assertEquals(first, hit);
        // 帯の外には無い
        assertNull(BandLayout.cellAt(0, 0));
    }

    @Test
    void 色のボタンは10色が順に並ぶ() {
        var colors = BandLayout.cells().stream().filter(c -> c.kind() == BandLayout.Kind.COLOR).toList();
        for (int i = 0; i < colors.size(); i++) {
            assertEquals(i, colors.get(i).index());
        }
    }
}
