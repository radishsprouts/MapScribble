package io.github.radishsprouts.mapscribble.geom;

import io.github.radishsprouts.mapscribble.model.MapPoint;
import java.util.List;

/** 囲み・線の成立判定と、面積・重心・内外判定。 */
public final class Shapes {
    /** 囲みとして成り立つ面積の下限（画面上の物理ピクセルの平方）。5×5 ピクセル未満は誤クリックとして捨てる。 */
    public static final double MIN_REGION_AREA_PIXELS = 25.0;

    private Shapes() {
    }

    /** 多角形の面積（符号なし）。 */
    public static double area(List<MapPoint> poly) {
        return Math.abs(signedArea2(poly)) / 2.0;
    }

    private static double signedArea2(List<MapPoint> poly) {
        int n = poly.size();
        double sum = 0;
        for (int i = 0; i < n; i++) {
            MapPoint a = poly.get(i);
            MapPoint b = poly.get((i + 1) % n);
            sum += a.x() * b.z() - b.x() * a.z();
        }
        return sum;
    }

    /**
     * 囲みとして成立するか。点が 3 つ未満、または面積が下限未満なら成立しない。
     *
     * @param minArea 面積の下限（ワールド座標の平方）
     */
    public static boolean isValidRegion(List<MapPoint> points, double minArea) {
        return points.size() >= 3 && area(points) >= minArea;
    }

    /** 線として成立するか。点が 2 つ未満、または全部同じ位置なら成立しない。 */
    public static boolean isValidLine(List<MapPoint> points) {
        if (points.size() < 2) {
            return false;
        }
        MapPoint first = points.get(0);
        for (MapPoint p : points) {
            if (p.x() != first.x() || p.z() != first.z()) {
                return true;
            }
        }
        return false;
    }

    /** 多角形の重心（面積の重み付き）。面積がほぼ 0 なら外接矩形の中心。 */
    public static MapPoint centroid(List<MapPoint> poly) {
        int n = poly.size();
        if (n == 0) {
            return new MapPoint(0, 0);
        }
        double a2 = signedArea2(poly);
        Rect box = Rect.of(poly);
        if (n < 3 || Math.abs(a2) < 1e-9) {
            return new MapPoint(box.centerX(), box.centerZ());
        }
        double cx = 0;
        double cz = 0;
        for (int i = 0; i < n; i++) {
            MapPoint a = poly.get(i);
            MapPoint b = poly.get((i + 1) % n);
            double cross = a.x() * b.z() - b.x() * a.z();
            cx += (a.x() + b.x()) * cross;
            cz += (a.z() + b.z()) * cross;
        }
        return new MapPoint(cx / (3.0 * a2), cz / (3.0 * a2));
    }

    /**
     * 点が多角形の内側か（偶奇則。小数の座標のまま判定する）。
     * 辺の上（誤差 1e-9 以内）は内側とみなす。凹んだ形・自己交差した輪郭でも偶奇で決まる。
     */
    public static boolean contains(List<MapPoint> poly, double x, double z) {
        int n = poly.size();
        if (n < 3) {
            return false;
        }
        for (int i = 0; i < n; i++) {
            MapPoint a = poly.get(i);
            MapPoint b = poly.get((i + 1) % n);
            if (Distance.pointToSegment(x, z, a.x(), a.z(), b.x(), b.z()) <= 1e-9) {
                return true;
            }
        }
        boolean inside = false;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            MapPoint a = poly.get(i);
            MapPoint b = poly.get(j);
            boolean crosses = (a.z() > z) != (b.z() > z);
            if (crosses) {
                double xAt = (b.x() - a.x()) * (z - a.z()) / (b.z() - a.z()) + a.x();
                if (x < xAt) {
                    inside = !inside;
                }
            }
        }
        return inside;
    }
}
