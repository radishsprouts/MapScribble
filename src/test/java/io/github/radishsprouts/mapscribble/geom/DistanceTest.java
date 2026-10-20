package io.github.radishsprouts.mapscribble.geom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.radishsprouts.mapscribble.model.MapPoint;
import java.util.List;
import org.junit.jupiter.api.Test;

class DistanceTest {
    private static final List<MapPoint> L = List.of(new MapPoint(0, 0), new MapPoint(10, 0), new MapPoint(10, 10));

    @Test
    void 線分の途中への垂線の長さ() {
        assertEquals(3, Distance.pointToSegment(5, 3, 0, 0, 10, 0), 1e-9);
        assertEquals(0, Distance.pointToSegment(5, 0, 0, 0, 10, 0), 1e-9);
    }

    @Test
    void 線分の外側では端点までの距離() {
        assertEquals(5, Distance.pointToSegment(-3, 4, 0, 0, 10, 0), 1e-9);
        assertEquals(5, Distance.pointToSegment(13, 4, 0, 0, 10, 0), 1e-9);
    }

    @Test
    void 長さ0の線分は点との距離() {
        assertEquals(5, Distance.pointToSegment(3, 4, 0, 0, 0, 0), 1e-9);
    }

    @Test
    void 折れ線では一番近い辺までの距離() {
        assertEquals(2, Distance.pointToPolyline(L, 5, 2, false), 1e-9);
        assertEquals(1, Distance.pointToPolyline(L, 11, 5, false), 1e-9);
        assertEquals(0, Distance.pointToPolyline(L, 10, 7, false), 1e-9);
    }

    @Test
    void 閉じる辺は閉じた時だけ数える() {
        // 終点 (10,10) から始点 (0,0) へ戻る斜めの辺の上の点
        assertEquals(0, Distance.pointToPolyline(L, 5, 5, true), 1e-9);
        assertTrue(Distance.pointToPolyline(L, 5, 5, false) > 4.9);
    }

    @Test
    void 点が1つだけなら点との距離_無ければ無限大() {
        assertEquals(5, Distance.pointToPolyline(List.of(new MapPoint(0, 0)), 3, 4, false), 1e-9);
        assertEquals(Double.POSITIVE_INFINITY, Distance.pointToPolyline(List.of(), 3, 4, false));
    }
}
