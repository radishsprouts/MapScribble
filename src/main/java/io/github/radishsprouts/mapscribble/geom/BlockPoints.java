package io.github.radishsprouts.mapscribble.geom;

import io.github.radishsprouts.mapscribble.model.MapPoint;
import java.util.ArrayList;
import java.util.List;

/** ミニマップのオーバーレイ用に、小数の点をブロック座標へ丸める。 */
public final class BlockPoints {
    private BlockPoints() {
    }

    /**
     * 点をブロック座標（含まれるブロック。floor）へ丸め、同じブロックが続く点を 1 つにまとめる。
     *
     * @param closed true なら、終点が始点と同じブロックの時に終点を捨てる（囲みは閉じた多角形として渡すため）
     * @return {x, z} の一覧
     */
    public static List<int[]> round(List<MapPoint> points, boolean closed) {
        List<int[]> result = new ArrayList<>();
        for (MapPoint p : points) {
            int x = (int) Math.floor(p.x());
            int z = (int) Math.floor(p.z());
            if (!result.isEmpty()) {
                int[] last = result.get(result.size() - 1);
                if (last[0] == x && last[1] == z) {
                    continue;
                }
            }
            result.add(new int[] {x, z});
        }
        if (closed && result.size() > 1) {
            int[] first = result.get(0);
            int[] last = result.get(result.size() - 1);
            if (first[0] == last[0] && first[1] == last[1]) {
                result.remove(result.size() - 1);
            }
        }
        return result;
    }

    /**
     * 線を「行って戻る」点列にする（p0…pn…p1。p0 への戻りは多角形が閉じる時に自動でつながる）。
     * 塗りの濃さを 0 にした多角形として渡すと、輪郭だけが線として見える。
     */
    public static List<int[]> outAndBack(List<int[]> line) {
        List<int[]> result = new ArrayList<>(line);
        for (int i = line.size() - 2; i >= 1; i--) {
            result.add(line.get(i));
        }
        return result;
    }
}
