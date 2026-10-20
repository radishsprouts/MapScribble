package io.github.radishsprouts.mapscribble.geom;

import io.github.radishsprouts.mapscribble.model.MapPoint;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 一筆の点の取り込み。前に取り込んだ点から画面上で {@link #MIN_MOVE_PIXELS} 未満しか動いていない点は捨てる。
 * ブロック単位では捨てない（拡大して細かく描いた形を残すため）。
 * 距離は取り込んだ点をワールド座標で持ち、その時の投影で画面に直して測る（途中でズームしても正しい）。
 */
public final class StrokeSampler {
    /** 画面上（物理ピクセル）でこれ未満の動きは捨てる。 */
    public static final double MIN_MOVE_PIXELS = 2.0;

    private final List<MapPoint> points = new ArrayList<>();

    /**
     * 画面の点を取り込む。
     *
     * @return 取り込んだら true、近すぎて捨てたら false
     */
    public boolean add(double px, double py, Projection projection) {
        return addWorld(projection.toWorldX(px), projection.toWorldZ(py), projection);
    }

    public boolean addWorld(double wx, double wz, Projection projection) {
        if (!points.isEmpty()) {
            MapPoint last = points.get(points.size() - 1);
            double dx = (wx - last.x()) * projection.scale();
            double dz = (wz - last.z()) * projection.scale();
            if (dx * dx + dz * dz < MIN_MOVE_PIXELS * MIN_MOVE_PIXELS) {
                return false;
            }
        }
        points.add(new MapPoint(wx, wz));
        return true;
    }

    public int size() {
        return points.size();
    }

    public boolean isEmpty() {
        return points.isEmpty();
    }

    /** 取り込んだ点（読み取り専用）。 */
    public List<MapPoint> points() {
        return Collections.unmodifiableList(points);
    }

    public MapPoint last() {
        return points.isEmpty() ? null : points.get(points.size() - 1);
    }

    public void clear() {
        points.clear();
    }
}
