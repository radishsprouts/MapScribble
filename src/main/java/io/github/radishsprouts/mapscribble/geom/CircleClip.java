package io.github.radishsprouts.mapscribble.geom;

/**
 * 線分を円で区間クリップする（純粋な計算）。円形のミニマップで、線・輪郭が円の外へ出ないようにするために使う。
 *
 * <p>線分 P(t) = P0 + t(P1 - P0)（t は 0〜1）と円の交点を 2 次方程式で求め、円の内側に入っている
 * {@code [t0, t1]} の区間へ詰める。線は中心から外へ 1 本の区間しか残らない（円は凸なので）。
 */
public final class CircleClip {
    private CircleClip() {
    }

    /**
     * 線分を円で切る。
     *
     * @return 切った後の {@code {x0, y0, x1, y1}}。線分が完全に円の外なら {@code null}
     */
    public static double[] clipSegment(double x0, double y0, double x1, double y1,
                                       double cx, double cy, double r) {
        if (r <= 0) {
            return null;
        }
        double dx = x1 - x0;
        double dy = y1 - y0;
        double fx = x0 - cx;
        double fy = y0 - cy;
        double a = dx * dx + dy * dy;
        double c = fx * fx + fy * fy - r * r;

        if (a == 0.0) {
            // 長さ 0 の線分。中心からの距離だけで判定する
            return c <= 0 ? new double[] {x0, y0, x1, y1} : null;
        }

        double b = 2.0 * (fx * dx + fy * dy);
        double disc = b * b - 4.0 * a * c;
        if (disc < 0) {
            return null;
        }
        double sq = Math.sqrt(disc);
        double t0 = (-b - sq) / (2.0 * a);
        double t1 = (-b + sq) / (2.0 * a);

        // 線分の範囲（0〜1）と交差させる
        double lo = Math.max(0.0, Math.min(t0, t1));
        double hi = Math.min(1.0, Math.max(t0, t1));
        if (hi <= lo) {
            return null;
        }
        return new double[] {x0 + dx * lo, y0 + dy * lo, x0 + dx * hi, y0 + dy * hi};
    }

    /** 点が円の内側（縁を含む）か。 */
    public static boolean contains(double x, double y, double cx, double cy, double r) {
        if (r <= 0) {
            return false;
        }
        double dx = x - cx;
        double dy = y - cy;
        return dx * dx + dy * dy <= r * r;
    }
}
