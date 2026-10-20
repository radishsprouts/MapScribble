package io.github.radishsprouts.mapscribble.draw;

import io.github.radishsprouts.mapscribble.model.MapPoint;
import io.github.radishsprouts.mapscribble.model.Memo;
import io.github.radishsprouts.mapscribble.model.MemoKind;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 確定した一筆（まだメモとして保存していない下書き）。文字入力の画面（作業6）はこれを受け取り、
 * 文字などを足して {@link DrawingController#add(Memo)} する。
 *
 * @param points 間引き済みの点（REGION は輪郭、LINE は折れ線、TEXT は 1 点）
 * @param width  線の太さの段階（1〜3）
 */
public record Draft(
        MemoKind kind,
        List<MapPoint> points,
        int color,
        int width,
        float fillOpacity,
        float textScale,
        boolean onMinimap) {

    public Draft {
        points = List.copyOf(points);
    }

    /** 文字を付けてメモにする。 */
    public Memo toMemo(String text) {
        Instant now = Instant.now();
        return new Memo(UUID.randomUUID(), kind, points, color, width, fillOpacity, text, textScale, onMinimap, now, now);
    }
}
