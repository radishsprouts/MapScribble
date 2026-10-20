package io.github.radishsprouts.mapscribble.geom;

import io.github.radishsprouts.mapscribble.model.MapPoint;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Ramer–Douglas–Peucker 法による折れ線の間引き。再帰を使わず、点が多くてもスタックを溢れさせない。 */
public final class Simplify {
    /** 間引きの許容誤差（画面上の物理ピクセル）。 */
    public static final double TOLERANCE_PIXELS = 0.75;

    private Simplify() {
    }

    /**
     * 折れ線を間引く。始点と終点は必ず残る。
     *
     * @param tolerance 許容誤差（ワールド座標の距離）。残した線から元の点までの距離がこれ以内になる
     */
    public static List<MapPoint> rdp(List<MapPoint> points, double tolerance) {
        int n = points.size();
        if (n <= 2) {
            return new ArrayList<>(points);
        }
        boolean[] keep = new boolean[n];
        keep[0] = true;
        keep[n - 1] = true;
        Deque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[] {0, n - 1});
        while (!stack.isEmpty()) {
            int[] range = stack.pop();
            int first = range[0];
            int last = range[1];
            if (last <= first + 1) {
                continue;
            }
            MapPoint a = points.get(first);
            MapPoint b = points.get(last);
            double maxDist = -1;
            int index = -1;
            for (int i = first + 1; i < last; i++) {
                MapPoint p = points.get(i);
                double d = Distance.pointToSegment(p.x(), p.z(), a.x(), a.z(), b.x(), b.z());
                if (d > maxDist) {
                    maxDist = d;
                    index = i;
                }
            }
            if (maxDist > tolerance) {
                keep[index] = true;
                stack.push(new int[] {first, index});
                stack.push(new int[] {index, last});
            }
        }
        List<MapPoint> result = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (keep[i]) {
                result.add(points.get(i));
            }
        }
        return result;
    }
}
