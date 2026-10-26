package io.github.radishsprouts.mapscribble.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * ツールの帯の配置（単位は GUI ピクセル）。純粋な計算だけで、Minecraft に依存しない。
 * 配置は指定された原点 (bandX, bandY) から相対的に求める。
 */
public final class BandLayout {
    public static final int DEFAULT_X = 28;
    public static final int DEFAULT_Y = 44;
    public static final int X = DEFAULT_X;
    public static final int Y = DEFAULT_Y;
    public static final int WIDTH = 235;
    public static final int HEIGHT = 41;

    public static final int HINT_X = X + 3;
    public static final int HINT_Y = Y + 44;
    public static final int HINT_MAX_WIDTH = 202;

    /** ボタンの種類。 */
    public enum Kind {
        TOOL, UNDO, REDO, TEXT_VISIBILITY, VISIBILITY, EXIT, WIDTH, TEXT_SIZE, COLOR
    }

    /**
     * 帯の中の 1 つの要素。座標は画面（GUI）の絶対座標。
     *
     * @param index TOOL は {@code Tool.ordinal()}、WIDTH は 1〜3 の太さ、TEXT_SIZE は 0〜2 の大きさ、COLOR は色の順番（0〜9）。それ以外は 0
     */
    public record Cell(Kind kind, int index, int x, int y, int w, int h) {
        public boolean contains(double px, double py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }

    /** 区切り線（x, y, 幅, 高さ）。 */
    public record Separator(int x, int y, int w, int h) {
    }

    private BandLayout() {
    }

    public static List<Cell> buildCells(int bx, int by) {
        List<Cell> cells = new ArrayList<>();
        // 上段 (y+3, 高さ 20)
        for (int i = 0; i < 5; i++) {
            cells.add(new Cell(Kind.TOOL, i, bx + 3 + i * 22, by + 3, 20, 20));
        }
        cells.add(new Cell(Kind.UNDO, 0, bx + 118, by + 3, 20, 20));
        cells.add(new Cell(Kind.REDO, 0, bx + 140, by + 3, 20, 20));
        cells.add(new Cell(Kind.TEXT_VISIBILITY, 0, bx + 165, by + 3, 20, 20));
        cells.add(new Cell(Kind.VISIBILITY, 0, bx + 186, by + 3, 20, 20));
        // 終了ボタン: 上下 2 段にまたがる縦長（手書きモードの出口）
        cells.add(new Cell(Kind.EXIT, 0, bx + 212, by + 3, 20, 35));

        // 下段 (y+25, 高さ 13)
        for (int i = 0; i < 3; i++) {
            cells.add(new Cell(Kind.WIDTH, i + 1, bx + 3 + i * 18, by + 25, 16, 13));
        }
        for (int i = 0; i < 3; i++) {
            cells.add(new Cell(Kind.TEXT_SIZE, i, bx + 60 + i * 18, by + 25, 16, 13));
        }
        for (int i = 0; i < NoteColor.values().length; i++) {
            cells.add(new Cell(Kind.COLOR, i, bx + 118 + i * 9, by + 25, 8, 13));
        }
        return List.copyOf(cells);
    }

    public static List<Separator> buildSeparators(int bx, int by) {
        return List.of(
                new Separator(bx + 114, by + 5, 1, 16),
                new Separator(bx + 163, by + 5, 1, 16),
                new Separator(bx + 57, by + 25, 1, 13),
                new Separator(bx + 115, by + 25, 1, 13),
                new Separator(bx + 210, by + 5, 1, 31));
    }

    public static List<Cell> cells() {
        return buildCells(X, Y);
    }

    public static List<Separator> separators() {
        return buildSeparators(X, Y);
    }

    /** この GUI 座標の点が帯の上か。 */
    public static boolean contains(double gx, double gy) {
        return contains(gx, gy, X, Y);
    }

    public static boolean contains(double gx, double gy, int bx, int by) {
        return gx >= bx && gx < bx + WIDTH && gy >= by && gy < by + HEIGHT;
    }

    /** この GUI 座標の点にある要素。無ければ null。 */
    public static Cell cellAt(double gx, double gy) {
        return cellAt(gx, gy, X, Y);
    }

    public static Cell cellAt(double gx, double gy, int bx, int by) {
        for (Cell c : buildCells(bx, by)) {
            if (c.contains(gx, gy)) {
                return c;
            }
        }
        return null;
    }
}
