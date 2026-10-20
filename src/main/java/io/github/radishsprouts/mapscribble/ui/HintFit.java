package io.github.radishsprouts.mapscribble.ui;

import io.github.radishsprouts.mapscribble.draw.DrawingController;
import io.github.radishsprouts.mapscribble.draw.Tool;
import java.util.ArrayList;
import java.util.List;

/**
 * 帯の説明の行が幅に収まるかの判定（純粋な計算。Minecraft に依存しない）。
 * 切れた時の全文ツールチップを出すか、開発環境での文言の幅の点検に使う。
 */
public final class HintFit {
    /** 開発環境の点検で警告する幅（行の幅 {@link BandLayout#HINT_MAX_WIDTH} より少し余裕を見る）。 */
    public static final int WARN_WIDTH = 190;

    private HintFit() {
    }

    /** 帯の説明の行に出うる翻訳キーの全件（hint.* と、HintResolver・一時のお知らせが出す msg.*）。 */
    public static List<String> bandKeys() {
        List<String> keys = new ArrayList<>();
        for (Tool tool : Tool.values()) {
            keys.add(HintResolver.hintKey(tool));
        }
        keys.add("mapscribble.msg.load_failed");
        keys.add("mapscribble.msg.save_failed");
        keys.add("mapscribble.msg.not_ready");
        keys.add("mapscribble.msg.hidden");
        keys.add("mapscribble.msg.empty");
        keys.add(DrawingController.NOTICE_UNDONE);
        keys.add(DrawingController.NOTICE_REDONE);
        keys.add(DrawingController.NOTICE_DELETED);
        keys.add(DrawingController.NOTICE_TOO_SMALL);
        keys.add(DrawingController.NOTICE_TOO_SHORT);
        keys.add(DrawingController.NOTICE_NO_NOTE);
        keys.add(DrawingController.NOTICE_HIDDEN_PICK);
        keys.add("mapscribble.msg.discarded");
        return keys;
    }

    /** 文言の幅が点検の上限を超えているか。 */
    public static boolean tooWide(int width) {
        return width > WARN_WIDTH;
    }

    /** 表示中の文字列が全文と違う（切っている）か。 */
    public static boolean isCut(String full, String shown) {
        return full != null && shown != null && !full.equals(shown);
    }

    /** マウスが説明の行の範囲（x: HINT_X〜+幅、y: HINT_Y-1〜HINT_Y+9）にあるか。 */
    public static boolean overHintRow(double mx, double my) {
        return mx >= BandLayout.HINT_X && mx < BandLayout.HINT_X + BandLayout.HINT_MAX_WIDTH
                && my >= BandLayout.HINT_Y - 1 && my < BandLayout.HINT_Y + 9;
    }

    /** 全文のツールチップを出すか。切っている間だけ、行の上にマウスがある時。 */
    public static boolean showFullText(String full, String shown, double mx, double my) {
        return isCut(full, shown) && overHintRow(mx, my);
    }
}
