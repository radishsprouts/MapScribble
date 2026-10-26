package io.github.radishsprouts.mapscribble.draw;

import io.github.radishsprouts.mapscribble.geom.Distance;
import io.github.radishsprouts.mapscribble.geom.Rect;
import io.github.radishsprouts.mapscribble.geom.Shapes;
import io.github.radishsprouts.mapscribble.geom.TextBox;
import io.github.radishsprouts.mapscribble.model.MapPoint;
import io.github.radishsprouts.mapscribble.model.Memo;
import java.util.List;

/** 消しゴム・編集のクリック判定。重なっていれば最後に描いたもの（一覧の後ろ）を選ぶ。 */
public final class HitTester {
    /** 線・輪郭の近くとみなす距離（画面上の物理ピクセル）。 */
    public static final double TOLERANCE_PIXELS = 6.0;

    /** 文字を隠している間の文字メモの当たり判定: 印の中心から各方向へ（GUI ピクセル）。 */
    public static final double HIDDEN_TEXT_HIT_HALF_GUI = 12.0;

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
        return find(memos, wx, wz, worldPerPx, guiScale, measure, true);
    }

    /**
     * @param textVisible 文字を出しているか。隠している間、文字メモは文字の矩形ではなく、印（菱形）の位置を中心にした
     *                    {@link #HIDDEN_TEXT_HIT_HALF_GUI} GUI ピクセル四方で当たりを取る（見えない矩形に当たらないように）
     */
    public static Memo find(List<Memo> memos, double wx, double wz, double worldPerPx, double guiScale,
                            TextMeasure measure, boolean textVisible) {
        double tolerance = TOLERANCE_PIXELS * worldPerPx;
        for (int i = memos.size() - 1; i >= 0; i--) {
            Memo memo = memos.get(i);
            if (hits(memo, wx, wz, tolerance, worldPerPx * guiScale, measure, textVisible)) {
                return memo;
            }
        }
        return null;
    }

    private static boolean hits(Memo memo, double wx, double wz, double tolerance, double worldPerGuiPx,
                                TextMeasure measure, boolean textVisible) {
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
                MapPoint anchor = points.get(0);
                if (!textVisible) {
                    double half = HIDDEN_TEXT_HIT_HALF_GUI * worldPerGuiPx;
                    return new Rect(anchor.x() - half, anchor.z() - half, anchor.x() + half, anchor.z() + half)
                            .contains(wx, wz);
                }
                String[] lines = MemoStyle.lines(memo.text());
                int maxWidth = 0;
                for (String line : lines) {
                    maxWidth = Math.max(maxWidth, measure.width(line));
                }
                return TextBox.bounds(anchor.x(), anchor.z(), maxWidth, lines.length, measure.lineHeight(),
                        memo.textScale(), worldPerGuiPx).contains(wx, wz);
        }
    }
}
