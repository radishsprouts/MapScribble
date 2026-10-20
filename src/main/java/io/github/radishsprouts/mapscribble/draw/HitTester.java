package io.github.radishsprouts.mapscribble.draw;

import io.github.radishsprouts.mapscribble.geom.Distance;
import io.github.radishsprouts.mapscribble.geom.Shapes;
import io.github.radishsprouts.mapscribble.geom.TextBox;
import io.github.radishsprouts.mapscribble.model.MapPoint;
import io.github.radishsprouts.mapscribble.model.Memo;
import java.util.List;

/** 消しゴム・編集のクリック判定。重なっていれば最後に描いたもの（一覧の後ろ）を選ぶ。 */
public final class HitTester {
    /** 線・輪郭の近くとみなす距離（画面上の物理ピクセル）。 */
    public static final double TOLERANCE_PIXELS = 6.0;

    /** 文字の幅の測り方（Minecraft のフォント）。純粋な計算にするため、描画側から渡す。 */
    public interface TextMeasure {
        /** 1 行の幅（GUI ピクセル）。 */
        int width(String line);

        /** 1 行の高さ（GUI ピクセル）。 */
        int lineHeight();
    }

    private HitTester() {
    }

    /**
     * @param memos        そのディメンションのメモ（描いた順）
     * @param wx           クリックしたワールド X（小数）
     * @param wz           同じく Z
     * @param worldPerPx   画面の 1 物理ピクセルのワールド上の長さ（1 / blockSize）
     * @param guiScale     GUI 倍率（物理ピクセル / GUI ピクセル）
     * @return 当たったメモ。無ければ null
     */
    public static Memo find(List<Memo> memos, double wx, double wz, double worldPerPx, double guiScale,
                            TextMeasure measure) {
        double tolerance = TOLERANCE_PIXELS * worldPerPx;
        for (int i = memos.size() - 1; i >= 0; i--) {
            Memo memo = memos.get(i);
            if (hits(memo, wx, wz, tolerance, worldPerPx * guiScale, measure)) {
                return memo;
            }
        }
        return null;
    }

    private static boolean hits(Memo memo, double wx, double wz, double tolerance, double worldPerGuiPx,
                                TextMeasure measure) {
        List<MapPoint> points = memo.points();
        switch (memo.kind()) {
            case REGION:
                return Shapes.contains(points, wx, wz)
                        || Distance.pointToPolyline(points, wx, wz, true) <= tolerance;
            case LINE:
                return Distance.pointToPolyline(points, wx, wz, false) <= tolerance;
            case TEXT:
            default:
                if (points.isEmpty()) {
                    return false;
                }
                String[] lines = MemoStyle.lines(memo.text());
                int maxWidth = 0;
                for (String line : lines) {
                    maxWidth = Math.max(maxWidth, measure.width(line));
                }
                MapPoint anchor = points.get(0);
                return TextBox.bounds(anchor.x(), anchor.z(), maxWidth, lines.length, measure.lineHeight(),
                        memo.textScale(), worldPerGuiPx).contains(wx, wz);
        }
    }
}
