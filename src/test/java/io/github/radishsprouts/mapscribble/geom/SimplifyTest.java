package io.github.radishsprouts.mapscribble.geom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.radishsprouts.mapscribble.model.MapPoint;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SimplifyTest {
    @Test
    void 直線は始点と終点だけに間引かれる() {
        List<MapPoint> line = new ArrayList<>();
        for (int i = 0; i <= 100; i++) {
            line.add(new MapPoint(i * 0.5, i * 0.25));
        }
        List<MapPoint> result = Simplify.rdp(line, 0.1);
        assertEquals(2, result.size());
        assertEquals(line.get(0), result.get(0));
        assertEquals(line.get(100), result.get(1));
    }

    @Test
    void 円は許容誤差の内側で形を保ったまま点が減る() {
        List<MapPoint> circle = new ArrayList<>();
        int n = 360;
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            circle.add(new MapPoint(100 * Math.cos(a), 100 * Math.sin(a)));
        }
        double tolerance = 0.75;
        List<MapPoint> result = Simplify.rdp(circle, tolerance);
        assertTrue(result.size() < n / 4, "点が十分に減る: " + result.size());
        assertTrue(result.size() >= 8, "円の形が残る: " + result.size());
        // 元の全部の点が、間引いた折れ線から許容誤差以内にある
        for (MapPoint p : circle) {
            assertTrue(Distance.pointToPolyline(result, p.x(), p.z(), false) <= tolerance + 1e-9);
        }
    }

    @Test
    void 許容を小さくすると円の点は増える() {
        List<MapPoint> circle = new ArrayList<>();
        for (int i = 0; i < 360; i++) {
            double a = 2 * Math.PI * i / 360;
            circle.add(new MapPoint(100 * Math.cos(a), 100 * Math.sin(a)));
        }
        assertTrue(Simplify.rdp(circle, 0.05).size() > Simplify.rdp(circle, 2.0).size());
    }

    @Test
    void ジグザグは振れ幅が許容より大きければ全部残り_小さければ消える() {
        List<MapPoint> zigzag = new ArrayList<>();
        for (int i = 0; i <= 20; i++) {
            zigzag.add(new MapPoint(i * 10, i % 2 == 0 ? 0 : 5));
        }
        assertEquals(zigzag.size(), Simplify.rdp(zigzag, 0.75).size());
        assertEquals(2, Simplify.rdp(zigzag, 10).size());
    }

    @Test
    void 点が2つ以下ならそのまま返す() {
        assertEquals(0, Simplify.rdp(List.of(), 1).size());
        assertEquals(1, Simplify.rdp(List.of(new MapPoint(1, 1)), 1).size());
        assertEquals(2, Simplify.rdp(List.of(new MapPoint(1, 1), new MapPoint(2, 2)), 1).size());
    }

    @Test
    void 点が非常に多くても再帰で溢れない() {
        List<MapPoint> many = new ArrayList<>();
        for (int i = 0; i < 200_000; i++) {
            many.add(new MapPoint(i, Math.sin(i * 0.01) * 50));
        }
        assertTrue(Simplify.rdp(many, 0.5).size() > 2);
    }
}
