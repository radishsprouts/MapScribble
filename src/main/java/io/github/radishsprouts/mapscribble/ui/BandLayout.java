package io.github.radishsprouts.mapscribble.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * ツールの帯の配置（画面設計書 3.1。単位は GUI ピクセル）。純粋な計算だけで、Minecraft に依存しない。
 * 帯は画面の大きさ・GUI 倍率にかかわらず左上 (28, 44) に固定する。配置を求めるのは起動時の 1 回だけで、
 * 毎フレームは作ってある一覧を引くだけ。
 */
public final class BandLayout {
    public static final int X = 28;
    public static final int Y = 44;
    public static final int WIDTH = 208;
    public static final int HEIGHT = 57;

    /** 説明の行（帯の左上から (3, 44)、幅 202 まで）。 */
    public static final int HINT_X = X + 3;
    public static final int HINT_Y = Y + 44;
    public static final int HINT_MAX_WIDTH = 202;

    /** ボタンの種類。 */
    public enum Kind {
        TOOL, UNDO, REDO, VISIBILITY, WIDTH, COLOR
    }

    /**
     * 帯の中の 1 つの要素。座標は画面（GUI）の絶対座標。
     *
     * @param index TOOL は {@code Tool.ordinal()}、WIDTH は 1〜3 の太さ、COLOR は色の順番（0〜9）。それ以外は 0
     */
    public record Cell(Kind kind, int index, int x, int y, int w, int h) {
        public boolean contains(double px, double py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }

    /** 区切り線（x, y, 幅, 高さ）。 */
    public record Separator(int x, int y, int w, int h) {
    }

    private static final List<Cell> CELLS = buildCells();
    private static final List<Separator> SEPARATORS = List.of(
            new Separator(X + 114, Y + 5, 1, 16),
            new Separator(X + 171, Y + 5, 1, 16),
            new Separator(X + 70, Y + 27, 1, 12));

    private BandLayout() {
    }

    private static List<Cell> buildCells() {
        List<Cell> cells = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            cells.add(new Cell(Kind.TOOL, i, X + 3 + i * 22, Y + 3, 20, 20));
        }
        cells.add(new Cell(Kind.UNDO, 0, X + 118, Y + 3, 20, 20));
        cells.add(new Cell(Kind.REDO, 0, X + 140, Y + 3, 20, 20));
        cells.add(new Cell(Kind.VISIBILITY, 0, X + 183, Y + 3, 20, 20));
        for (int i = 0; i < 3; i++) {
            cells.add(new Cell(Kind.WIDTH, i + 1, X + 3 + i * 22, Y + 26, 20, 14));
        }
        for (int i = 0; i < NoteColor.values().length; i++) {
            cells.add(new Cell(Kind.COLOR, i, X + 74 + i * 13, Y + 27, 12, 12));
        }
        return List.copyOf(cells);
    }

    public static List<Cell> cells() {
        return CELLS;
    }

    public static List<Separator> separators() {
        return SEPARATORS;
    }

    /** この GUI 座標の点が帯の上か。 */
    public static boolean contains(double gx, double gy) {
        return gx >= X && gx < X + WIDTH && gy >= Y && gy < Y + HEIGHT;
    }

    /** この GUI 座標の点にある要素。無ければ null。 */
    public static Cell cellAt(double gx, double gy) {
        for (Cell c : CELLS) {
            if (c.contains(gx, gy)) {
                return c;
            }
        }
        return null;
    }
}
