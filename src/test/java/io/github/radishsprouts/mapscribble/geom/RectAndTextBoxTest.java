package io.github.radishsprouts.mapscribble.geom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.radishsprouts.mapscribble.model.MapPoint;
import java.util.List;
import org.junit.jupiter.api.Test;

class RectAndTextBoxTest {
    private static final double EPS = 1e-9;

    @Test
    void 点の外接矩形() {
        Rect r = Rect.of(List.of(new MapPoint(3, -2), new MapPoint(-1.5, 4), new MapPoint(0, 0)));
        assertEquals(-1.5, r.minX(), EPS);
        assertEquals(-2, r.minZ(), EPS);
        assertEquals(3, r.maxX(), EPS);
        assertEquals(4, r.maxZ(), EPS);
    }

    @Test
    void 点が無ければ原点の矩形() {
        Rect r = Rect.of(List.of());
        assertEquals(new Rect(0, 0, 0, 0), r);
    }

    @Test
    void 点が1つなら幅のない矩形で_その点を含む() {
        Rect r = Rect.of(List.of(new MapPoint(5, 6)));
        assertEquals(0, r.maxX() - r.minX(), EPS);
        assertTrue(r.contains(5, 6));
    }

    @Test
    void 最小と最大が逆でも入れ替えて作られる() {
        Rect r = new Rect(10, 8, 2, 1);
        assertEquals(2, r.minX(), EPS);
        assertEquals(1, r.minZ(), EPS);
        assertEquals(10, r.maxX(), EPS);
        assertEquals(8, r.maxZ(), EPS);
    }

    @Test
    void 重なりの判定は境界が触れるだけでも重なりとする() {
        Rect a = new Rect(0, 0, 10, 10);
        assertTrue(a.intersects(new Rect(5, 5, 15, 15)));
        assertTrue(a.intersects(new Rect(10, 10, 20, 20)));
        assertTrue(a.intersects(new Rect(2, 2, 3, 3)));
        assertFalse(a.intersects(new Rect(10.01, 0, 20, 10)));
        assertFalse(a.intersects(new Rect(0, -5, 10, -0.01)));
    }

    @Test
    void 含む判定は境界の上も含む() {
        Rect a = new Rect(0, 0, 10, 10);
        assertTrue(a.contains(0, 0));
        assertTrue(a.contains(10, 10));
        assertTrue(a.contains(5, 5));
        assertFalse(a.contains(-0.001, 5));
        assertFalse(a.contains(5, 10.001));
    }

    @Test
    void 広げると全方向に余白が付き_合併は両方を含む() {
        Rect a = new Rect(0, 0, 10, 10).expand(2);
        assertEquals(new Rect(-2, -2, 12, 12), a);
        Rect u = new Rect(0, 0, 1, 1).union(new Rect(5, -3, 6, 0));
        assertEquals(new Rect(0, -3, 6, 1), u);
        assertEquals(3.0, u.centerX(), EPS);
        assertEquals(-1.0, u.centerZ(), EPS);
    }

    @Test
    void 文字の範囲はアンカーを中心に_余白と倍率を含む() {
        // 幅 40・2行・行の高さ 9・倍率 1.5・GUI 1px = 0.5 ブロック
        Rect r = TextBox.bounds(100, 200, 40, 2, 9, 1.5f, 0.5);
        double halfW = (40 * 1.5 / 2.0 + TextBox.PADDING_GUI) * 0.5;
        double halfH = (2 * 9 * 1.5 / 2.0 + TextBox.PADDING_GUI) * 0.5;
        assertEquals(100 - halfW, r.minX(), EPS);
        assertEquals(100 + halfW, r.maxX(), EPS);
        assertEquals(200 - halfH, r.minZ(), EPS);
        assertEquals(200 + halfH, r.maxZ(), EPS);
        assertEquals(100, r.centerX(), EPS);
        assertEquals(200, r.centerZ(), EPS);
    }

    @Test
    void 行が0でも1行ぶんの高さを持つ_文字なしでも当たる() {
        Rect zero = TextBox.bounds(0, 0, 0, 0, 9, 1f, 1.0);
        Rect one = TextBox.bounds(0, 0, 0, 1, 9, 1f, 1.0);
        assertEquals(one, zero);
        assertTrue(zero.contains(0, 0));
    }

    @Test
    void 倍率が大きいほど範囲が広がる() {
        Rect small = TextBox.bounds(0, 0, 30, 1, 9, 1f, 1.0);
        Rect big = TextBox.bounds(0, 0, 30, 1, 9, 3f, 1.0);
        assertTrue(big.maxX() > small.maxX());
        assertTrue(big.maxZ() > small.maxZ());
    }
}
