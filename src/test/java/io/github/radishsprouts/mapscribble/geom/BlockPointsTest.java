package io.github.radishsprouts.mapscribble.geom;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.radishsprouts.mapscribble.model.MapPoint;
import java.util.List;
import org.junit.jupiter.api.Test;

class BlockPointsTest {
    @Test
    void 小数の点は含まれるブロックへ丸め_連続する重複は1つにまとめる() {
        List<int[]> r = BlockPoints.round(List.of(
                new MapPoint(0.2, 0.3), new MapPoint(0.9, 0.1), new MapPoint(1.0, 0.0), new MapPoint(-0.1, 2.5)), false);
        assertEquals(3, r.size());
        assertArrayEquals(new int[] {0, 0}, r.get(0));
        assertArrayEquals(new int[] {1, 0}, r.get(1));
        assertArrayEquals(new int[] {-1, 2}, r.get(2));
    }

    @Test
    void 囲みは終点が始点と同じブロックなら終点を捨てる() {
        List<MapPoint> pts = List.of(new MapPoint(0.1, 0.1), new MapPoint(5.5, 0.5), new MapPoint(5.5, 5.5),
                new MapPoint(0.9, 0.9));
        assertEquals(3, BlockPoints.round(pts, true).size());
        assertEquals(4, BlockPoints.round(pts, false).size());
    }

    @Test
    void 線は行って戻る点列になる() {
        List<int[]> line = List.of(new int[] {0, 0}, new int[] {1, 0}, new int[] {2, 0}, new int[] {3, 0});
        List<int[]> loop = BlockPoints.outAndBack(line);
        // p0 p1 p2 p3 p2 p1（p0 へは多角形が閉じる時に戻る）
        assertEquals(6, loop.size());
        assertArrayEquals(new int[] {3, 0}, loop.get(3));
        assertArrayEquals(new int[] {2, 0}, loop.get(4));
        assertArrayEquals(new int[] {1, 0}, loop.get(5));
    }
}
