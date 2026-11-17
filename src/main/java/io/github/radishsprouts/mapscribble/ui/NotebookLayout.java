package io.github.radishsprouts.mapscribble.ui;

/**
 * メモの保存先の画面の配置（設計書 3.7。単位は GUI ピクセル）。純粋な計算だけで、Minecraft に依存しない。
 * 座標はパネルの左上が原点。一覧が 6 行の時は、一覧より下の要素が 42 ずつ下がる。
 */
public final class NotebookLayout {
    public static final int PANEL_WIDTH = 300;
    public static final int PANEL_HEIGHT_3 = 226;
    public static final int PANEL_HEIGHT_6 = 268;
    /** この高さ以上の GUI なら一覧を 6 行にする。 */
    public static final int TALL_GUI_HEIGHT = 280;
    public static final int MARGIN = 7;
    public static final int CONTENT_WIDTH = 286;
    public static final int ROW_HEIGHT = 14;
    public static final int BUTTON_HEIGHT = 20;
    public static final int LIST_PAD = 1;
    /** 一覧の行の内側の左右の余白と、行内の x。 */
    public static final int ROW_INNER_PAD = 2;
    public static final int NAME_X = 4;
    public static final int NAME_MAX_WIDTH = 160;
    public static final int COUNT_RIGHT_X = 220;
    public static final int DATE_RIGHT_X = 274;
    public static final int SCROLLBAR_X = 279;
    public static final int SCROLLBAR_WIDTH = 4;

    private static final int EXTRA_TALL = 42;

    private final int rows;

    public NotebookLayout(int guiHeight) {
        this.rows = guiHeight >= TALL_GUI_HEIGHT ? 6 : 3;
    }

    /** 一覧に見せる行数（3 または 6）。 */
    public int visibleRows() {
        return rows;
    }

    private int extra() {
        return rows == 6 ? EXTRA_TALL : 0;
    }

    public int panelHeight() {
        return rows == 6 ? PANEL_HEIGHT_6 : PANEL_HEIGHT_3;
    }

    public int panelX(int guiWidth) {
        return (guiWidth - PANEL_WIDTH) / 2;
    }

    public int panelY(int guiHeight) {
        return (guiHeight - panelHeight()) / 2;
    }

    public int titleY() {
        return 7;
    }

    public int addressY() {
        return 19;
    }

    public int switchY() {
        return 31;
    }

    public int descY() {
        return 54;
    }

    public int labelY() {
        return 67;
    }

    public int listY() {
        return 78;
    }

    public int listHeight() {
        return rows * ROW_HEIGHT + 2;
    }

    public int errorY() {
        return 125 + extra();
    }

    public int renameY() {
        return 136 + extra();
    }

    public int legacyY() {
        return 162 + extra();
    }

    public int moveY() {
        return 173 + extra();
    }

    public int doneY() {
        return 199 + extra();
    }

    // 横方向の配置
    public int renameX() {
        return 7;
    }

    public int renameWidth() {
        return 100;
    }

    public int newX() {
        return 110;
    }

    public int newWidth() {
        return 100;
    }

    public int moveAllX() {
        return 7;
    }

    public int moveVisibleX() {
        return 152;
    }

    public int moveWidth() {
        return 141;
    }

    public int doneX() {
        return 100;
    }

    public int doneWidth() {
        return 100;
    }

    /** 一覧の i 番目（表示上の行）の上端 y（パネルの左上から）。 */
    public int rowY(int visibleIndex) {
        return listY() + LIST_PAD + visibleIndex * ROW_HEIGHT;
    }
}
