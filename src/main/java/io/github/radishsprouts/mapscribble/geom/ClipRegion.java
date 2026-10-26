package io.github.radishsprouts.mapscribble.geom;

/**
 * 描画の切り抜き（矩形と、任意の円）。長さの単位はすべて<strong>物理ピクセル</strong>。
 *
 * <p>Minecraft の scissor は矩形しか受けないので、円形のミニマップでは矩形の scissor に加えて
 * 図形の側（塗りの行・線分・文字）を円で切る。円の有無は {@link #hasCircle()} で分ける。
 *
 * @param minX   矩形の左（含む）
 * @param minY   矩形の上（含む）
 * @param maxX   矩形の右（含まない）
 * @param maxY   矩形の下（含まない）
 * @param circleX 円の中心 x
 * @param circleY 円の中心 y
 * @param radius 円の半径。0 以下なら円の制限なし（矩形だけ）
 */
public record ClipRegion(double minX, double minY, double maxX, double maxY,
                         double circleX, double circleY, double radius) {

    /** 矩形だけの切り抜き。 */
    public static ClipRegion rect(double minX, double minY, double maxX, double maxY) {
        return new ClipRegion(minX, minY, maxX, maxY, 0.0, 0.0, -1.0);
    }

    /** ミニマップの枠から作る（円形なら内側領域の半辺を半径にする）。 */
    public static ClipRegion of(MinimapGeometry g) {
        double half = g.innerHalfPhys();
        double r = g.circular() ? half : -1.0;
        return new ClipRegion(g.centerPhysX() - half, g.centerPhysY() - half,
                g.centerPhysX() + half, g.centerPhysY() + half,
                g.centerPhysX(), g.centerPhysY(), r);
    }

    public boolean hasCircle() {
        return radius > 0;
    }

    public double width() {
        return maxX - minX;
    }

    public double height() {
        return maxY - minY;
    }

    /** 中心から角までの距離（物理ピクセル）。回転を含むカリングに使う。 */
    public double coverRadius() {
        return hasCircle() ? radius : Math.hypot(width() / 2.0, height() / 2.0);
    }

    /** 点が切り抜きの内側か。 */
    public boolean contains(double x, double y) {
        if (x < minX || x >= maxX || y < minY || y >= maxY) {
            return false;
        }
        if (!hasCircle()) {
            return true;
        }
        double dx = x - circleX;
        double dy = y - circleY;
        return dx * dx + dy * dy <= radius * radius;
    }

    /**
     * 横一行（y を固定）で描ける x の左端。円が無ければ {@link #minX()}。
     * 円があってその行が円に掛からない場合は {@link #maxX()} 以上の値を返すので、
     * {@code rowMinX(y) >= rowMaxX(y)} でその行が空であることが分かる。
     */
    public double rowMinX(double y) {
        if (!hasCircle()) {
            return minX;
        }
        double half = circleHalfWidth(y);
        if (half < 0) {
            return maxX;
        }
        return Math.max(minX, circleX - half);
    }

    /** 横一行（y を固定）で描ける x の右端（含まない）。円が無ければ {@link #maxX()}。 */
    public double rowMaxX(double y) {
        if (!hasCircle()) {
            return maxX;
        }
        double half = circleHalfWidth(y);
        if (half < 0) {
            return minX;
        }
        return Math.min(maxX, circleX + half);
    }

    /** その行で円が覆う横幅の半分。円に掛からない行では負を返す。 */
    private double circleHalfWidth(double y) {
        double dy = y - circleY;
        double h2 = radius * radius - dy * dy;
        if (h2 <= 0) {
            return -1.0;
        }
        return Math.sqrt(h2);
    }
}
