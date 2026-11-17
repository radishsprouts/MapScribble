package io.github.radishsprouts.mapscribble.ui;

/**
 * メニューの位置と大きさ（設計書 3.6。単位は GUI ピクセル）。純粋な計算だけで、Minecraft に依存しない。
 */
public final class MenuLayout {
    public static final int WIDTH = 150;
    public static final int ROW_HEIGHT = 14;
    public static final int PAD_Y = 3;
    /** 項目の数の上限。8 つを超える機能は項目を足さず「表示の設定」の画面へ寄せる。 */
    public static final int MAX_ITEMS = 8;
    /** 帯の下端からメニューの上端までの隙間。 */
    public static final int GAP = 2;
    /** 行の左右の余白（ラベルは x+5、補足は右端−5）。 */
    public static final int TEXT_PAD = 5;

    /** メニューの外枠。 */
    public record Rect(int x, int y, int w, int h) {
        public boolean contains(double px, double py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }

    private MenuLayout() {
    }

    public static int height(int itemCount) {
        return PAD_Y * 2 + ROW_HEIGHT * itemCount;
    }

    /**
     * 帯の位置とメニューの項目数から、メニューの外枠を求める。
     * 右端をメニューのボタンの右端に揃え、帯の下端＋2 に開く。下へはみ出す時は帯の上へ、上にも入らない時は画面の下端に揃える。
     */
    public static Rect place(int bandX, int bandY, int itemCount, int screenW, int screenH) {
        int n = Math.max(0, Math.min(MAX_ITEMS, itemCount));
        int h = height(n);
        BandLayout.Cell button = BandLayout.menuCell(bandX, bandY);
        int x = button.x() + button.w() - WIDTH;
        x = Math.max(0, Math.min(Math.max(0, screenW - WIDTH), x));
        int y = bandY + BandLayout.HEIGHT + GAP;
        if (y + h > screenH) {
            y = bandY - GAP - h;
            if (y < 0) {
                y = Math.max(0, screenH - h);
            }
        }
        return new Rect(x, y, WIDTH, h);
    }

    /** この点にある行の番号（0 始まり）。行の外なら -1。 */
    public static int rowAt(Rect rect, int itemCount, double px, double py) {
        if (!rect.contains(px, py)) {
            return -1;
        }
        double rel = py - rect.y() - PAD_Y;
        if (rel < 0) {
            return -1;
        }
        int row = (int) (rel / ROW_HEIGHT);
        return row < itemCount ? row : -1;
    }

    /**
     * キーで選ぶ行を動かす。開いた直後は何も選んでいない（-1）。押せない項目は飛ばす。端では動かさない。
     *
     * @param enabled 各項目が押せるか
     * @param current 今選んでいる行（-1 は選んでいない）
     * @param delta   +1 は ↓、-1 は ↑
     * @return 新しく選ぶ行。動けなければ current のまま
     */
    public static int moveSelection(boolean[] enabled, int current, int delta) {
        int step = delta >= 0 ? 1 : -1;
        int i = current < 0 ? (step > 0 ? -1 : enabled.length) : current;
        for (int next = i + step; next >= 0 && next < enabled.length; next += step) {
            if (enabled[next]) {
                return next;
            }
        }
        return current;
    }

    /** 行 {@code row} の上端の y。 */
    public static int rowY(Rect rect, int row) {
        return rect.y() + PAD_Y + row * ROW_HEIGHT;
    }
}
