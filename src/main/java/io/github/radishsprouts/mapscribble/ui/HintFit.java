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

    /** 帯の y がこの値（画面高 − 値）を超えたら、説明の行を帯の上に描く。 */
    public static final int ABOVE_THRESHOLD = 55;
    /** 帯の上に描く時の、帯の y からの相対位置。 */
    public static final int ABOVE_DY = -11;
    /** 背景の余白（左右 2、上 2、文字の y から下 10）。 */
    public static final int BG_PAD_X = 2;
    public static final int BG_PAD_TOP = 2;
    public static final int BG_BOTTOM = 10;
    /** 背景の色（0xCC の帯より濃い 90%。明るい地形の上でも文字の比を 4.5:1 以上に保つ）。 */
    public static final int BG_COLOR = 0xE6101418;

    private HintFit() {
    }

    /**
     * 帯の説明の行に出うる翻訳キーの全件（hint.* と、HintResolver・一時のお知らせが出す msg.*）。
     * 引数を差し込むキー（notebook_switched・moved）は、引数を空にした文言で測る（名前・件数の部分は除く）。
     */
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
        keys.add("mapscribble.msg.loading");
        keys.add(DrawingController.NOTICE_BUSY);
        keys.add(DrawingController.NOTICE_UNDONE);
        keys.add(DrawingController.NOTICE_REDONE);
        keys.add(DrawingController.NOTICE_DELETED);
        keys.add(DrawingController.NOTICE_TOO_SMALL);
        keys.add(DrawingController.NOTICE_TOO_SHORT);
        keys.add(DrawingController.NOTICE_NO_NOTE);
        keys.add(DrawingController.NOTICE_HIDDEN_PICK);
        keys.add("mapscribble.msg.discarded");
        // 0.4.0（設計書 3.9）
        keys.add(HintResolver.NOTICE_NOTEBOOK_SWITCHED);
        keys.add(HintResolver.NOTICE_SPLIT_STARTED);
        keys.add(HintResolver.NOTICE_NO_WORLD_ID);
        keys.add(HintResolver.NOTICE_MOVED);
        keys.add(HintResolver.NOTICE_MOVE_UNDONE);
        keys.add(HintResolver.NOTICE_MOVE_FAILED);
        keys.add(HintResolver.NOTICE_JM010_IMPORTED);
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

    /** 帯が画面の下端近くにあるか（帯の y > 画面高 − 55）。真なら説明の行は帯の上に描く。 */
    public static boolean above(int by, int screenH) {
        return by > screenH - ABOVE_THRESHOLD;
    }

    /** 説明の行の文字の x。帯の x に付いて動く。 */
    public static int hintX(int bx) {
        return bx + BandLayout.HINT_DX;
    }

    /** 説明の行の文字の y。通常は帯の下（帯の y + 44）、下端近くでは帯の上（帯の y − 11）。 */
    public static int hintY(int by, int screenH) {
        return above(by, screenH) ? by + ABOVE_DY : by + BandLayout.HINT_DY;
    }

    /** 説明の行の背景の四角（x1, y1 は含み、x2, y2 は含まない）。 */
    public record Rect(int x1, int y1, int x2, int y2) {
        public boolean contains(double px, double py) {
            return px >= x1 && px < x2 && py >= y1 && py < y2;
        }

        public int width() {
            return x2 - x1;
        }

        public int height() {
            return y2 - y1;
        }
    }

    /** 背景の四角。文字の幅（「…」で切った後のもの）に合わせ、行の幅いっぱいには敷かない。 */
    public static Rect background(int bx, int by, int screenH, int textWidth) {
        int x = hintX(bx);
        int y = hintY(by, screenH);
        return new Rect(x - BG_PAD_X, y - BG_PAD_TOP, x + textWidth + BG_PAD_X, y + BG_BOTTOM);
    }

    /** マウスが説明の行（背景の四角）の上にあるか。 */
    public static boolean overHintRow(double mx, double my, int bx, int by, int screenH, int textWidth) {
        return background(bx, by, screenH, textWidth).contains(mx, my);
    }

    /** 全文のツールチップを出すか。切っている間だけ、行（背景の四角）の上にマウスがある時。 */
    public static boolean showFullText(String full, String shown, double mx, double my, int bx, int by,
                                       int screenH, int textWidth) {
        return isCut(full, shown) && overHintRow(mx, my, bx, by, screenH, textWidth);
    }
}
