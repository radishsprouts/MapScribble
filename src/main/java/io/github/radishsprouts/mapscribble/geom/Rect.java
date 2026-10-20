package io.github.radishsprouts.mapscribble.geom;

import io.github.radishsprouts.mapscribble.model.MapPoint;
import java.util.List;

/** ワールド座標の外接矩形。 */
public record Rect(double minX, double minZ, double maxX, double maxZ) {

    public Rect {
        if (minX > maxX) {
            double t = minX;
            minX = maxX;
            maxX = t;
        }
        if (minZ > maxZ) {
            double t = minZ;
            minZ = maxZ;
            maxZ = t;
        }
    }

    /** 点の外接矩形。点が無ければ原点の矩形。 */
    public static Rect of(List<MapPoint> points) {
        if (points.isEmpty()) {
            return new Rect(0, 0, 0, 0);
        }
        double minX = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (MapPoint p : points) {
            minX = Math.min(minX, p.x());
            minZ = Math.min(minZ, p.z());
            maxX = Math.max(maxX, p.x());
            maxZ = Math.max(maxZ, p.z());
        }
        return new Rect(minX, minZ, maxX, maxZ);
    }

    public boolean intersects(Rect o) {
        return minX <= o.maxX && o.minX <= maxX && minZ <= o.maxZ && o.minZ <= maxZ;
    }

    public boolean contains(double x, double z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    public Rect expand(double margin) {
        return new Rect(minX - margin, minZ - margin, maxX + margin, maxZ + margin);
    }

    public Rect union(Rect o) {
        return new Rect(Math.min(minX, o.minX), Math.min(minZ, o.minZ), Math.max(maxX, o.maxX), Math.max(maxZ, o.maxZ));
    }

    public double centerX() {
        return (minX + maxX) / 2.0;
    }

    public double centerZ() {
        return (minZ + maxZ) / 2.0;
    }
}
