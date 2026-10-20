package io.github.radishsprouts.mapscribble.geom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.radishsprouts.mapscribble.model.MapPoint;
import java.util.List;
import org.junit.jupiter.api.Test;

class StrokeSamplerTest {
    private static final Projection ONE = new Projection(0, 0, 1.0, 1000, 1000);

    @Test
    void 最初の点は必ず取り込む() {
        StrokeSampler s = new StrokeSampler();
        assertTrue(s.add(500, 500, ONE));
        assertEquals(1, s.size());
    }

    @Test
    void 画面上で2ピクセル未満の動きは捨てる() {
        StrokeSampler s = new StrokeSampler();
        s.add(500, 500, ONE);
        assertFalse(s.add(501, 500, ONE));
        assertFalse(s.add(501.9, 500, ONE));
        assertTrue(s.add(502, 500, ONE));
        assertEquals(2, s.size());
    }

    @Test
    void 距離は最後に取り込んだ点から測る() {
        StrokeSampler s = new StrokeSampler();
        for (int x = 0; x <= 6; x++) {
            s.add(500 + x, 500, ONE); // 1 ピクセルずつ
        }
        // 500, 502, 504, 506 だけが残る
        assertEquals(4, s.size());
        assertEquals(506 - 500, s.last().x() - s.points().get(0).x(), 1e-9);
    }

    @Test
    void ブロック単位では捨てない_拡大すると細かい点が残る() {
        // 1 ブロック 32 ピクセルの拡大: 0.1 ブロック（3.2 ピクセル）の動きは残る
        Projection zoomed = new Projection(0, 0, 32.0, 1000, 1000);
        StrokeSampler s = new StrokeSampler();
        s.addWorld(0, 0, zoomed);
        assertTrue(s.addWorld(0.1, 0, zoomed));
        // 縮小（1 ブロック 4 ピクセル）では 0.1 ブロック（0.4 ピクセル）は捨てる
        Projection far = new Projection(0, 0, 4.0, 1000, 1000);
        StrokeSampler t = new StrokeSampler();
        t.addWorld(0, 0, far);
        assertFalse(t.addWorld(0.1, 0, far));
    }

    @Test
    void 途中でズームが変わっても距離は今の画面で測る() {
        Projection far = new Projection(0, 0, 1.0, 1000, 1000);
        Projection near = new Projection(0, 0, 10.0, 1000, 1000);
        StrokeSampler s = new StrokeSampler();
        s.addWorld(0, 0, far);
        assertFalse(s.addWorld(1, 0, far), "縮小では 1 ピクセル");
        assertTrue(s.addWorld(1, 0, near), "拡大すると 10 ピクセル");
    }

    @Test
    void 取り込んだ点を読み取り専用で返す() {
        StrokeSampler s = new StrokeSampler();
        s.add(500, 500, ONE);
        List<MapPoint> points = s.points();
        try {
            points.add(new MapPoint(0, 0));
        } catch (UnsupportedOperationException expected) {
            return;
        }
        throw new AssertionError("変更できてはいけない");
    }
}
