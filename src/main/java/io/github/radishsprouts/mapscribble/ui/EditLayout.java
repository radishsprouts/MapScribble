package io.github.radishsprouts.mapscribble.ui;

import io.github.radishsprouts.mapscribble.model.MemoKind;

/**
 * 文字入力・編集の画面の配置（画面設計書 3.2。単位は GUI ピクセル、座標はパネルの左上が原点）。純粋な計算。
 * 設計書の「縦の並び」の表と、モックアップ（ui-mockup.html の dialog）の計算式に合わせる。
 */
public record EditLayout(MemoKind kind, boolean edit, int guiHeight) {
    public static final int PANEL_WIDTH = 240;
    public static final int MARGIN = 7;
    /** 文字の欄の幅。 */
    public static final int BOX_WIDTH = 222;
    public static final int SWATCH_SIZE = 16;
    public static final int SWATCH_STEP = 18;
    public static final int BUTTON_HEIGHT = 20;
    /** 選択ボタン 2 列の幅と x。 */
    public static final int CYCLE_WIDTH = 109;
    public static final int CYCLE_LEFT_X = 7;
    public static final int CYCLE_RIGHT_X = 120;

    /** 画面の高さがこれ以上なら文字の欄を 5 行、未満なら 3 行にする。 */
    private static final int TALL_SCREEN = 260;

    public int rows() {
        return guiHeight >= TALL_SCREEN ? 5 : 3;
    }

    public int titleY() {
        return 7;
    }

    public int labelY() {
        return 22;
    }

    public int boxY() {
        return 34;
    }

    public int boxHeight() {
        return rows() * 9 + 11;
    }

    /** 「Ctrl+Enter で確定…」「12/200」の行。 */
    public int hintY() {
        return boxY() + boxHeight() + 3;
    }

    /**
     * 左のヒントを描く y。バニラの MultiLineEditBox が文字数カウンタを描く y（欄の下端 + 4。
     * 26.1.2 / 26.2 / 26.3 の extractDecorations で同じ）に揃える。
     */
    public int counterY() {
        return boxY() + boxHeight() + 4;
    }

    /** 「色：赤」の行。 */
    public int colorLabelY() {
        return hintY() + 16;
    }

    public int swatchY() {
        return colorLabelY() + 12;
    }

    /** 選択ボタンの 1 段目。 */
    public int cycleRow1Y() {
        return swatchY() + 24;
    }

    /** 囲みだけにある 2 段目（ミニマップ）。 */
    public int cycleRow2Y() {
        return cycleRow1Y() + 24;
    }

    public boolean hasFill() {
        return kind == MemoKind.REGION;
    }

    /** 下のボタン（削除・確定・キャンセル）の y。 */
    public int buttonsY() {
        int lastRow = hasFill() ? cycleRow2Y() : cycleRow1Y();
        return lastRow + 30;
    }

    public int panelHeight() {
        return buttonsY() + 29;
    }

    /** パネルの左上の x（画面の左右中央）。 */
    public int panelX(int guiWidth) {
        return (guiWidth - PANEL_WIDTH) / 2;
    }

    /** パネルの左上の y（上下中央。上端は最低 4）。 */
    public int panelY() {
        return Math.max(4, Math.round((guiHeight - panelHeight()) / 2f));
    }

    public int deleteX() {
        return 7;
    }

    public int deleteWidth() {
        return 60;
    }

    public int saveX() {
        return 85;
    }

    public int cancelX() {
        return 159;
    }

    public int footerButtonWidth() {
        return 70;
    }
}
