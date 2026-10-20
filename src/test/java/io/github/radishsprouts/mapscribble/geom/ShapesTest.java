package io.github.radishsprouts.mapscribble.geom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.radishsprouts.mapscribble.model.MapPoint;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShapesTest {
    private static List<MapPoint> poly(double... xz) {
        List<MapPoint> list = new ArrayList<>();
        for (int i = 0; i < xz.length; i += 2) {
            list.add(new MapPoint(xz[i], xz[i + 1]));
        }
        return list;
    }

    // ---- 内外判定（偶奇則） ----

    @Test
    void 凸の四角形の内外() {
        List<MapPoint> square = poly(0, 0, 10, 0, 10, 10, 0, 10);
        assertTrue(Shapes.contains(square, 5, 5));
        assertTrue(Shapes.contains(square, 0.001, 9.999));
        assertFalse(Shapes.contains(square, -0.001, 5));
        assertFalse(Shapes.contains(square, 10.001, 5));
        assertFalse(Shapes.contains(square, 5, 11));
    }

    @Test
    void 凹んだ形では切れ込みの中が外側になる() {
        // L 字（右上が欠けている）
        List<MapPoint> l = poly(0, 0, 10, 0, 10, 4, 4, 4, 4, 10, 0, 10);
        assertTrue(Shapes.contains(l, 2, 8));
        assertTrue(Shapes.contains(l, 8, 2));
        assertFalse(Shapes.contains(l, 8, 8));
        assertTrue(Shapes.contains(l, 3.9, 3.9));
        assertFalse(Shapes.contains(l, 4.5, 4.5));
    }

    @Test
    void 自己交差した輪郭は偶奇で決まる() {
        // 蝶ネクタイ: 左右の三角が内側、上下は外側
        List<MapPoint> bow = poly(0, 0, 2, 2, 2, 0, 0, 2);
        assertTrue(Shapes.contains(bow, 1.8, 1));
        assertTrue(Shapes.contains(bow, 0.2, 1));
        assertFalse(Shapes.contains(bow, 1, 1.8));
        assertFalse(Shapes.contains(bow, 1, 0.2));

        // 五芒星（一筆書き）: 中央の五角形は 2 回囲まれるので偶奇則では外側、先端の三角は内側
        List<MapPoint> star = new ArrayList<>();
        int[] order = {0, 2, 4, 1, 3};
        for (int k : order) {
            double a = Math.PI / 2 + 2 * Math.PI * k / 5;
            star.add(new MapPoint(10 * Math.cos(a), 10 * Math.sin(a)));
        }
        assertFalse(Shapes.contains(star, 0, 0));
        assertTrue(Shapes.contains(star, 0, 8.5));
    }

    @Test
    void 境界の上は内側として扱う() {
        List<MapPoint> square = poly(0, 0, 10, 0, 10, 10, 0, 10);
        assertTrue(Shapes.contains(square, 5, 0), "辺の上");
        assertTrue(Shapes.contains(square, 10, 5), "辺の上");
        assertTrue(Shapes.contains(square, 0, 0), "頂点");
        assertTrue(Shapes.contains(square, 10, 10), "頂点");
        // 終点から始点へ戻る辺（閉じる辺）の上
        assertTrue(Shapes.contains(poly(0, 0, 10, 0, 10, 10), 5, 5), "閉じる辺の上");
    }

    @Test
    void 点が3つ未満なら内側にならない() {
        assertFalse(Shapes.contains(poly(0, 0, 10, 10), 5, 5));
        assertFalse(Shapes.contains(poly(), 0, 0));
    }

    // ---- 成立判定・面積・重心 ----

    @Test
    void 囲みの成立判定() {
        assertFalse(Shapes.isValidRegion(poly(0, 0, 10, 10), 1), "点が2つ");
        assertFalse(Shapes.isValidRegion(poly(0, 0, 5, 5, 10, 10), 1), "一直線（面積 0）");
        assertFalse(Shapes.isValidRegion(poly(0, 0, 1, 0, 1, 1), 1), "面積 0.5 は下限未満");
        assertTrue(Shapes.isValidRegion(poly(0, 0, 10, 0, 10, 10), 1));
    }

    @Test
    void 線の成立判定() {
        assertFalse(Shapes.isValidLine(poly()));
        assertFalse(Shapes.isValidLine(poly(3, 3)));
        assertFalse(Shapes.isValidLine(poly(3, 3, 3, 3)), "全部同じ位置");
        assertTrue(Shapes.isValidLine(poly(3, 3, 3, 4)));
    }

    @Test
    void 面積と重心() {
        List<MapPoint> square = poly(0, 0, 10, 0, 10, 10, 0, 10);
        assertEquals(100, Shapes.area(square), 1e-9);
        MapPoint c = Shapes.centroid(square);
        assertEquals(5, c.x(), 1e-9);
        assertEquals(5, c.z(), 1e-9);
        // 巻き方向が逆でも同じ
        List<MapPoint> reversed = new ArrayList<>(square);
        java.util.Collections.reverse(reversed);
        assertEquals(100, Shapes.area(reversed), 1e-9);
        assertEquals(5, Shapes.centroid(reversed).x(), 1e-9);
        // 面積 0 の時は外接矩形の中心
        MapPoint flat = Shapes.centroid(poly(0, 0, 10, 0, 20, 0));
        assertEquals(10, flat.x(), 1e-9);
        assertEquals(0, flat.z(), 1e-9);
    }
}
