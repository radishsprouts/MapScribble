package io.github.radishsprouts.mapscribble.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 1件のメモ。変更は新しいインスタンスを作って置き換える（取り消しの履歴で前後の状態を持つため）。
 *
 * @param points      REGION は輪郭（始点と終点は別の点で、描く時につなぐ）、LINE は折れ線、TEXT は置いた点1つ
 * @param color       0xRRGGBB
 * @param width       線の太さの段階（1〜3）
 * @param fillOpacity 塗りの濃さ（0.0〜1.0。REGION だけが使う）
 * @param text        複数行可。無ければ空文字
 * @param textScale   文字の大きさの倍率
 * @param onMinimap   ミニマップにも出すか
 */
public record Memo(
        UUID id,
        MemoKind kind,
        List<MapPoint> points,
        int color,
        int width,
        float fillOpacity,
        String text,
        float textScale,
        boolean onMinimap,
        Instant created,
        Instant updated) {

    public Memo {
        points = List.copyOf(points);
        text = text == null ? "" : text;
    }
}
