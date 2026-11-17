package io.github.radishsprouts.mapscribble.ui;

import io.github.radishsprouts.mapscribble.draw.Tool;
import java.util.ArrayList;
import java.util.List;

/**
 * ツールの帯の配置（単位は GUI ピクセル）。純粋な計算だけで、Minecraft に依存しない。
 * 配置は指定された原点 (bandX, bandY) から相対的に求める。
 *
 * <p>座標は設計書 3.1.1 の表を写した {@link #UPPER}（上段。全道具で同じ）と、道具ごとの下段の組み立てで持つ。
 * 描画・当たり判定・試験は、すべてこの表から作る（表と絵がずれないため）。
 */
public final class BandLayout {
    public static final int DEFAULT_X = 28;
    public static final int DEFAULT_Y = 44;
    public static final int X = DEFAULT_X;
    public static final int Y = DEFAULT_Y;
    public static final int WIDTH = 256;
    public static final int HEIGHT = 41;

    /** 説明の行の、帯の左上からの相対位置。帯を動かすと行も一緒に動く。 */
    public static final int HINT_DX = 3;
    public static final int HINT_DY = 44;
    public static final int HINT_X = X + HINT_DX;
    public static final int HINT_Y = Y + HINT_DY;
    public static final int HINT_MAX_WIDTH = 202;

    /** 上段のボタンの大きさ（18×18）。 */
    public static final int BUTTON = 18;
    /** 下段の y（帯の上端から）と高さ。 */
    public static final int LOWER_Y = 25;
    public static final int LOWER_H = 13;
    /** 設定のある道具が無い時の灰色の1行を描く位置（帯の左上から）。 */
    public static final int NO_SETTINGS_X = 5;
    public static final int NO_SETTINGS_Y = 28;

    /** ボタンの種類。 */
    public enum Kind {
        TOOL, UNDO, REDO, TEXT_VISIBILITY, VISIBILITY, MENU, EXIT, WIDTH, TEXT_SIZE, FILL, COLOR
    }

    /**
     * 帯の中の 1 つの要素。座標は画面（GUI）の絶対座標。
     *
     * @param index TOOL は {@code Tool.ordinal()}、WIDTH は 1〜3 の太さ、TEXT_SIZE は 0〜2 の大きさ、
     *              FILL は 0〜3 の塗り（{@link FillLevel} の順）、COLOR は色の順番（0〜9）。それ以外は 0
     */
    public record Cell(Kind kind, int index, int x, int y, int w, int h) {
        public boolean contains(double px, double py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }

    /** 区切り線（x, y, 幅, 高さ）。 */
    public record Separator(int x, int y, int w, int h) {
    }

    /** 帯の左上からの相対位置の要素（表の1行）。 */
    private record Slot(Kind kind, int index, int x, int y, int w, int h) {
        Cell at(int bx, int by) {
            return new Cell(kind, index, bx + x, by + y, w, h);
        }
    }

    /** 上段（設計書 3.1.1 の表）。図形（x103）は 0.7.0 で入るので、0.4.0 では場所だけ空けてセルを作らない。 */
    private static final List<Slot> UPPER = List.of(
            new Slot(Kind.TOOL, 0, 3, 3, BUTTON, BUTTON),
            new Slot(Kind.TOOL, 1, 23, 3, BUTTON, BUTTON),
            new Slot(Kind.TOOL, 2, 43, 3, BUTTON, BUTTON),
            new Slot(Kind.TOOL, 3, 63, 3, BUTTON, BUTTON),
            new Slot(Kind.TOOL, 4, 83, 3, BUTTON, BUTTON),
            new Slot(Kind.UNDO, 0, 126, 3, BUTTON, BUTTON),
            new Slot(Kind.REDO, 0, 146, 3, BUTTON, BUTTON),
            new Slot(Kind.TEXT_VISIBILITY, 0, 169, 3, BUTTON, BUTTON),
            new Slot(Kind.VISIBILITY, 0, 189, 3, BUTTON, BUTTON),
            new Slot(Kind.MENU, 0, 212, 3, BUTTON, BUTTON),
            // 終了ボタン: 上下 2 段にまたがる縦長（手書きモードの出口）
            new Slot(Kind.EXIT, 0, 235, 3, BUTTON, 35));

    private static final List<int[]> UPPER_SEPARATORS = List.of(
            new int[] {123, 5, 1, 14},
            new int[] {166, 5, 1, 14},
            new int[] {209, 5, 1, 14},
            new int[] {232, 5, 1, 31});

    /** 下段 A 枠の x（太さ・文字の大きさ）。 */
    private static final int[] A_X = {3, 21, 39};
    /** 下段 B 枠の x（塗り）。 */
    private static final int[] B_X = {60, 78, 96, 114};
    private static final int COLOR_X = 135;
    private static final int COLOR_STEP = 9;
    private static final int A_B_SEPARATOR_X = 57;
    private static final int COLOR_SEPARATOR_X = 132;

    /** 下段に出る枠。道具ごとに決まる。 */
    private enum LowerA { NONE, WIDTH, TEXT_SIZE }

    private record Lower(LowerA a, boolean fill, boolean color) {
        boolean any() {
            return a != LowerA.NONE || fill || color;
        }
    }

    private static Lower lowerOf(Tool tool) {
        return switch (tool) {
            case ENCLOSE -> new Lower(LowerA.WIDTH, true, true);
            case LINE -> new Lower(LowerA.WIDTH, false, true);
            case TEXT -> new Lower(LowerA.TEXT_SIZE, false, true);
            case ERASER, EDIT -> new Lower(LowerA.NONE, false, false);
        };
    }

    private BandLayout() {
    }

    /** この道具の下段に、設定のボタンが 1 つでもあるか（無ければ灰色の1行を描く）。 */
    public static boolean hasSettings(Tool tool) {
        return lowerOf(tool).any();
    }

    /** 上段だけ（全道具で同じ）。 */
    public static List<Cell> buildUpperCells(int bx, int by) {
        List<Cell> cells = new ArrayList<>();
        for (Slot s : UPPER) {
            cells.add(s.at(bx, by));
        }
        return List.copyOf(cells);
    }

    /** 選んでいる道具の下段（設定）だけ。 */
    public static List<Cell> buildLowerCells(int bx, int by, Tool tool) {
        Lower lower = lowerOf(tool);
        List<Cell> cells = new ArrayList<>();
        int y = by + LOWER_Y;
        if (lower.a() == LowerA.WIDTH) {
            for (int i = 0; i < 3; i++) {
                cells.add(new Cell(Kind.WIDTH, i + 1, bx + A_X[i], y, 16, LOWER_H));
            }
        } else if (lower.a() == LowerA.TEXT_SIZE) {
            for (int i = 0; i < 3; i++) {
                cells.add(new Cell(Kind.TEXT_SIZE, i, bx + A_X[i], y, 16, LOWER_H));
            }
        }
        if (lower.fill()) {
            for (int i = 0; i < 4; i++) {
                cells.add(new Cell(Kind.FILL, i, bx + B_X[i], y, 16, LOWER_H));
            }
        }
        if (lower.color()) {
            for (int i = 0; i < NoteColor.values().length; i++) {
                cells.add(new Cell(Kind.COLOR, i, bx + COLOR_X + i * COLOR_STEP, y, 8, LOWER_H));
            }
        }
        return List.copyOf(cells);
    }

    /** 上段＋選んでいる道具の下段。 */
    public static List<Cell> buildCells(int bx, int by, Tool tool) {
        List<Cell> cells = new ArrayList<>(buildUpperCells(bx, by));
        cells.addAll(buildLowerCells(bx, by, tool));
        return List.copyOf(cells);
    }

    /** 区切り線。下段の区切り線は道具ごとに出し分ける。 */
    public static List<Separator> buildSeparators(int bx, int by, Tool tool) {
        List<Separator> list = new ArrayList<>();
        for (int[] s : UPPER_SEPARATORS) {
            list.add(new Separator(bx + s[0], by + s[1], s[2], s[3]));
        }
        Lower lower = lowerOf(tool);
        if (lower.a() != LowerA.NONE && lower.fill()) {
            list.add(new Separator(bx + A_B_SEPARATOR_X, by + LOWER_Y, 1, LOWER_H));
        }
        if (lower.color()) {
            list.add(new Separator(bx + COLOR_SEPARATOR_X, by + LOWER_Y, 1, LOWER_H));
        }
        return List.copyOf(list);
    }

    /** 囲みツールを選んだ時の全ボタン（試験用。設定が一番多い道具）。 */
    public static List<Cell> cells() {
        return cells(Tool.ENCLOSE);
    }

    public static List<Cell> cells(Tool tool) {
        return buildCells(X, Y, tool);
    }

    public static List<Separator> separators(Tool tool) {
        return buildSeparators(X, Y, tool);
    }

    /** この GUI 座標の点が、既定の位置の帯の上か。帯を動かした後は {@link #contains(double, double, int, int)} を使う。 */
    public static boolean contains(double gx, double gy) {
        return contains(gx, gy, X, Y);
    }

    public static boolean contains(double gx, double gy, int bx, int by) {
        return gx >= bx && gx < bx + WIDTH && gy >= by && gy < by + HEIGHT;
    }

    /** この GUI 座標の点にある要素。無ければ null。 */
    public static Cell cellAt(double gx, double gy, Tool tool) {
        return cellAt(gx, gy, X, Y, tool);
    }

    public static Cell cellAt(double gx, double gy, int bx, int by, Tool tool) {
        for (Cell c : buildCells(bx, by, tool)) {
            if (c.contains(gx, gy)) {
                return c;
            }
        }
        return null;
    }

    /** メニューのボタンの位置（帯の左上から）。メニューの位置の計算に使う。 */
    public static Cell menuCell(int bx, int by) {
        for (Slot s : UPPER) {
            if (s.kind() == Kind.MENU) {
                return s.at(bx, by);
            }
        }
        throw new IllegalStateException("メニューのボタンが表に無い");
    }
}
