package io.github.radishsprouts.mapscribble.geom;

import io.github.radishsprouts.mapscribble.model.MapPoint;
import java.util.List;

/** 点と線分・折れ線の距離。 */
public final class Distance {
    private Distance() {
    }

    /** 点 (px,pz) と線分 a-b の最短距離。a と b が同じ点なら点どうしの距離。 */
    public static double pointToSegment(double px, double pz, double ax, double az, double bx, double bz) {
        double dx = bx - ax;
        double dz = bz - az;
        double len2 = dx * dx + dz * dz;
        double t = len2 == 0 ? 0 : ((px - ax) * dx + (pz - az) * dz) / len2;
        t = Math.max(0, Math.min(1, t));
        double cx = ax + t * dx;
        double cz = az + t * dz;
        return Math.hypot(px - cx, pz - cz);
    }

    /**
     * 点と折れ線の最短距離。
     *
     * @param closed true なら終点から始点へ戻る辺も含める（囲みの輪郭）
     * @return 点が無ければ正の無限大
     */
    public static double pointToPolyline(List<MapPoint> points, double x, double z, boolean closed) {
        int n = points.size();
        if (n == 0) {
            return Double.POSITIVE_INFINITY;
        }
        if (n == 1) {
            return Math.hypot(x - points.get(0).x(), z - points.get(0).z());
        }
        double best = Double.POSITIVE_INFINITY;
        for (int i = 0; i + 1 < n; i++) {
            MapPoint a = points.get(i);
            MapPoint b = points.get(i + 1);
            best = Math.min(best, pointToSegment(x, z, a.x(), a.z(), b.x(), b.z()));
        }
        if (closed) {
            MapPoint a = points.get(n - 1);
            MapPoint b = points.get(0);
            best = Math.min(best, pointToSegment(x, z, a.x(), a.z(), b.x(), b.z()));
        }
        return best;
    }
}
