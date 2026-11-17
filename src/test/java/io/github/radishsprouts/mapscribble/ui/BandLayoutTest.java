package io.github.radishsprouts.mapscribble.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.radishsprouts.mapscribble.draw.Tool;
import java.util.List;
import org.junit.jupiter.api.Test;

class BandLayoutTest {
    private static long count(Tool tool, BandLayout.Kind kind) {
        return BandLayout.cells(tool).stream().filter(c -> c.kind() == kind).count();
    }

    private static boolean overlap(BandLayout.Cell a, BandLayout.Cell b) {
        return a.x() < b.x() + b.w() && b.x() < a.x() + a.w() && a.y() < b.y() + b.h() && b.y() < a.y() + a.h();
    }

    @Test
    void 上段は全道具で同じ数のボタンがある() {
        for (Tool tool : Tool.values()) {
            assertEquals(5, count(tool, BandLayout.Kind.TOOL), tool.name());
            assertEquals(1, count(tool, BandLayout.Kind.UNDO));
            assertEquals(1, count(tool, BandLayout.Kind.REDO));
            assertEquals(1, count(tool, BandLayout.Kind.TEXT_VISIBILITY));
            assertEquals(1, count(tool, BandLayout.Kind.VISIBILITY));
            assertEquals(1, count(tool, BandLayout.Kind.MENU));
            assertEquals(1, count(tool, BandLayout.Kind.EXIT));
        }
    }

    @Test
    void 下段の中身は道具ごとに決まる() {
        assertEquals(3, count(Tool.ENCLOSE, BandLayout.Kind.WIDTH));
        assertEquals(4, count(Tool.ENCLOSE, BandLayout.Kind.FILL));
        assertEquals(10, count(Tool.ENCLOSE, BandLayout.Kind.COLOR));
        assertEquals(0, count(Tool.ENCLOSE, BandLayout.Kind.TEXT_SIZE));

        assertEquals(3, count(Tool.LINE, BandLayout.Kind.WIDTH));
        assertEquals(0, count(Tool.LINE, BandLayout.Kind.FILL));
        assertEquals(10, count(Tool.LINE, BandLayout.Kind.COLOR));

        assertEquals(3, count(Tool.TEXT, BandLayout.Kind.TEXT_SIZE));
        assertEquals(0, count(Tool.TEXT, BandLayout.Kind.WIDTH));
        assertEquals(0, count(Tool.TEXT, BandLayout.Kind.FILL));
        assertEquals(10, count(Tool.TEXT, BandLayout.Kind.COLOR));

        for (Tool tool : new Tool[] {Tool.ERASER, Tool.EDIT}) {
            assertTrue(BandLayout.buildLowerCells(0, 0, tool).isEmpty(), tool.name());
            assertFalse(BandLayout.hasSettings(tool));
        }
        assertTrue(BandLayout.hasSettings(Tool.ENCLOSE));
    }

    @Test
    void 上段の座標は設計書どおり() {
        var cells = BandLayout.buildCells(0, 0, Tool.ENCLOSE);
        int[] toolX = {3, 23, 43, 63, 83};
        for (int i = 0; i < toolX.length; i++) {
            assertTrue(cells.contains(new BandLayout.Cell(BandLayout.Kind.TOOL, i, toolX[i], 3, 18, 18)), "道具" + i);
        }
        assertTrue(cells.contains(new BandLayout.Cell(BandLayout.Kind.UNDO, 0, 126, 3, 18, 18)));
        assertTrue(cells.contains(new BandLayout.Cell(BandLayout.Kind.REDO, 0, 146, 3, 18, 18)));
        assertTrue(cells.contains(new BandLayout.Cell(BandLayout.Kind.TEXT_VISIBILITY, 0, 169, 3, 18, 18)));
        assertTrue(cells.contains(new BandLayout.Cell(BandLayout.Kind.VISIBILITY, 0, 189, 3, 18, 18)));
        assertTrue(cells.contains(new BandLayout.Cell(BandLayout.Kind.MENU, 0, 212, 3, 18, 18)));
        assertTrue(cells.contains(new BandLayout.Cell(BandLayout.Kind.EXIT, 0, 235, 3, 18, 35)));
    }

    @Test
    void 上段の区切り線は全道具で同じ() {
        for (Tool tool : Tool.values()) {
            var seps = BandLayout.buildSeparators(0, 0, tool);
            assertTrue(seps.contains(new BandLayout.Separator(123, 5, 1, 14)));
            assertTrue(seps.contains(new BandLayout.Separator(166, 5, 1, 14)));
            assertTrue(seps.contains(new BandLayout.Separator(209, 5, 1, 14)));
            assertTrue(seps.contains(new BandLayout.Separator(232, 5, 1, 31)));
        }
    }

    @Test
    void 下段の座標と区切り線は設計書どおり() {
        var enclose = BandLayout.buildCells(0, 0, Tool.ENCLOSE);
        int[] aX = {3, 21, 39};
        for (int i = 0; i < 3; i++) {
            assertTrue(enclose.contains(new BandLayout.Cell(BandLayout.Kind.WIDTH, i + 1, aX[i], 25, 16, 13)));
        }
        int[] bX = {60, 78, 96, 114};
        for (int i = 0; i < 4; i++) {
            assertTrue(enclose.contains(new BandLayout.Cell(BandLayout.Kind.FILL, i, bX[i], 25, 16, 13)));
        }
        for (int i = 0; i < 10; i++) {
            assertTrue(enclose.contains(new BandLayout.Cell(BandLayout.Kind.COLOR, i, 135 + 9 * i, 25, 8, 13)));
        }
        var encloseSeps = BandLayout.buildSeparators(0, 0, Tool.ENCLOSE);
        assertTrue(encloseSeps.contains(new BandLayout.Separator(57, 25, 1, 13)));
        assertTrue(encloseSeps.contains(new BandLayout.Separator(132, 25, 1, 13)));

        var lineSeps = BandLayout.buildSeparators(0, 0, Tool.LINE);
        assertFalse(lineSeps.contains(new BandLayout.Separator(57, 25, 1, 13)));
        assertTrue(lineSeps.contains(new BandLayout.Separator(132, 25, 1, 13)));

        var textCells = BandLayout.buildCells(0, 0, Tool.TEXT);
        for (int i = 0; i < 3; i++) {
            assertTrue(textCells.contains(new BandLayout.Cell(BandLayout.Kind.TEXT_SIZE, i, aX[i], 25, 16, 13)));
        }

        for (Tool tool : new Tool[] {Tool.ERASER, Tool.EDIT}) {
            var seps = BandLayout.buildSeparators(0, 0, tool);
            assertEquals(4, seps.size(), "上段の区切り線だけ");
        }
    }

    @Test
    void 色の見本は囲み_線_文字のどれでも同じ場所に出る() {
        var enclose = BandLayout.buildLowerCells(0, 0, Tool.ENCLOSE).stream()
                .filter(c -> c.kind() == BandLayout.Kind.COLOR).toList();
        for (Tool tool : new Tool[] {Tool.LINE, Tool.TEXT}) {
            var colors = BandLayout.buildLowerCells(0, 0, tool).stream()
                    .filter(c -> c.kind() == BandLayout.Kind.COLOR).toList();
            assertEquals(enclose, colors, tool.name());
        }
        for (int i = 0; i < enclose.size(); i++) {
            assertEquals(i, enclose.get(i).index());
        }
    }

    @Test
    void 図形の場所は空けてあり_押しても何も無い() {
        for (Tool tool : Tool.values()) {
            assertNull(BandLayout.cellAt(103 + 5, 3 + 5, 0, 0, tool));
            assertNull(BandLayout.cellAt(103, 3, 0, 0, tool));
        }
    }

    @Test
    void 帯の大きさは256x41() {
        assertEquals(256, BandLayout.WIDTH);
        assertEquals(41, BandLayout.HEIGHT);
        assertEquals(28, BandLayout.DEFAULT_X);
        assertEquals(44, BandLayout.DEFAULT_Y);
        assertEquals(BandLayout.X + 3, BandLayout.HINT_X);
        assertEquals(BandLayout.Y + 44, BandLayout.HINT_Y);
        assertEquals(202, BandLayout.HINT_MAX_WIDTH);
    }

    /** プランの決まり 1 の試験（AC124）。0.7.0 で道具と下段の種類を足したら、同じ試験がその状態も回る。 */
    @Test
    void すべての道具の状態で_セルは帯の中に収まり_互いに重ならない() {
        int[][] origins = {{BandLayout.X, BandLayout.Y}, {0, 0}, {120, 80}};
        for (int[] o : origins) {
            for (Tool tool : Tool.values()) {
                List<BandLayout.Cell> cells = BandLayout.buildCells(o[0], o[1], tool);
                for (int i = 0; i < cells.size(); i++) {
                    var a = cells.get(i);
                    String label = tool + " " + a;
                    assertTrue(a.x() >= o[0] && a.x() + a.w() <= o[0] + BandLayout.WIDTH, label);
                    assertTrue(a.y() >= o[1] && a.y() + a.h() <= o[1] + BandLayout.HEIGHT, label);
                    for (int j = i + 1; j < cells.size(); j++) {
                        assertFalse(overlap(a, cells.get(j)), label + " と " + cells.get(j));
                    }
                }
                for (BandLayout.Separator s : BandLayout.buildSeparators(o[0], o[1], tool)) {
                    assertTrue(s.x() >= o[0] && s.x() + s.w() <= o[0] + BandLayout.WIDTH, tool + " " + s);
                    assertTrue(s.y() >= o[1] && s.y() + s.h() <= o[1] + BandLayout.HEIGHT, tool + " " + s);
                }
            }
        }
    }

    @Test
    void 帯の幅は264以下_高さは41以下() {
        assertTrue(BandLayout.WIDTH <= 264);
        assertTrue(BandLayout.HEIGHT <= 41);
    }

    @Test
    void 項目を上限の8つにしたメニューが_既定の位置の帯から開いても320x240に収まる() {
        var r = MenuLayout.place(BandLayout.X, BandLayout.Y, MenuLayout.MAX_ITEMS, 320, 240);
        assertEquals(108, r.x());
        assertEquals(87, r.y());
        assertEquals(150, r.w());
        assertEquals(118, r.h());
        assertTrue(r.x() >= 0 && r.x() + r.w() <= 320);
        assertTrue(r.y() >= 0 && r.y() + r.h() <= 240);
        assertTrue(r.y() + r.h() <= 206, "JourneyMap の下部ツールバー（y 206）に届かない");
        assertEquals(258, r.x() + r.w());
        assertEquals(205, r.y() + r.h());
    }

    @Test
    void メニューは1項目で高さ20_右端がメニューのボタンの右端に揃う() {
        var r = MenuLayout.place(BandLayout.X, BandLayout.Y, 1, 320, 240);
        assertEquals(20, r.h());
        assertEquals(BandLayout.X + 230, r.x() + r.w());
        assertEquals(BandLayout.Y + 43, r.y());
    }

    @Test
    void メニューは画面からはみ出す時に位置を寄せる() {
        // 下へはみ出す → 帯の上へ
        var below = MenuLayout.place(28, 200, 1, 320, 240);
        assertEquals(200 - 2 - 20, below.y());
        // 左へはみ出す
        var left = MenuLayout.place(0, 44, 1, 100, 240);
        assertEquals(0, left.x());
        // 右へはみ出す
        var right = MenuLayout.place(320 - 256, 44, 1, 320, 240);
        assertTrue(right.x() + right.w() <= 320);
        // 上にも入らない → 画面の下端に揃える
        var tiny = MenuLayout.place(28, 10, 8, 320, 100);
        assertEquals(100 - 118 < 0 ? 0 : 100 - 118, tiny.y());
    }

    @Test
    void すべての帯の位置で_メニューの全体が画面に収まる() {
        for (int by = 0; by <= 240 - BandLayout.HEIGHT; by += 7) {
            for (int bx = 0; bx <= 320 - BandLayout.WIDTH; bx += 5) {
                var r = MenuLayout.place(bx, by, MenuLayout.MAX_ITEMS, 320, 240);
                assertTrue(r.x() >= 0 && r.x() + r.w() <= 320, bx + "," + by);
                assertTrue(r.y() >= 0 && r.y() + r.h() <= 240, bx + "," + by);
            }
        }
    }

    @Test
    void メニューの行を点から引ける() {
        var r = MenuLayout.place(BandLayout.X, BandLayout.Y, 2, 320, 240);
        assertEquals(-1, MenuLayout.rowAt(r, 2, r.x() + 10, r.y() + 1));
        assertEquals(0, MenuLayout.rowAt(r, 2, r.x() + 10, r.y() + 3));
        assertEquals(0, MenuLayout.rowAt(r, 2, r.x() + 10, r.y() + 16));
        assertEquals(1, MenuLayout.rowAt(r, 2, r.x() + 10, r.y() + 17));
        assertEquals(-1, MenuLayout.rowAt(r, 2, r.x() + 10, r.y() + 3 + 28));
        assertEquals(-1, MenuLayout.rowAt(r, 2, r.x() - 1, r.y() + 5));
    }

    @Test
    void 帯の範囲の判定は左上を含み_右下を含まない() {
        assertTrue(BandLayout.contains(BandLayout.X, BandLayout.Y));
        assertTrue(BandLayout.contains(BandLayout.X + BandLayout.WIDTH - 1, BandLayout.Y + BandLayout.HEIGHT - 1));
        assertFalse(BandLayout.contains(BandLayout.X - 1, BandLayout.Y));
        assertFalse(BandLayout.contains(BandLayout.X + BandLayout.WIDTH, BandLayout.Y));
        assertFalse(BandLayout.contains(BandLayout.X, BandLayout.Y + BandLayout.HEIGHT));

        int bx = 50;
        int by = 120;
        assertTrue(BandLayout.contains(bx, by, bx, by));
        assertTrue(BandLayout.contains(bx + BandLayout.WIDTH - 1, by + BandLayout.HEIGHT - 1, bx, by));
        assertFalse(BandLayout.contains(bx - 1, by, bx, by));
        // 帯を動かした後は、既定の位置の判定ではなく動かした位置で判定する
        assertFalse(BandLayout.contains(bx + 5, by + 5, BandLayout.X, BandLayout.Y));
        assertTrue(BandLayout.contains(bx + 5, by + 5, bx, by));
    }

    @Test
    void ボタンの座標からボタンを引ける() {
        var first = BandLayout.cells(Tool.ENCLOSE).get(0);
        var hit = BandLayout.cellAt(first.x() + 1, first.y() + 1, Tool.ENCLOSE);
        assertNotNull(hit);
        assertEquals(first, hit);
        assertNull(BandLayout.cellAt(0, 0, Tool.ENCLOSE));
    }

    @Test
    void 設定の無い道具の下段の灰色の文字の場所は帯の余白で_押してもボタンに当たらない() {
        for (Tool tool : new Tool[] {Tool.ERASER, Tool.EDIT}) {
            assertNull(BandLayout.cellAt(BandLayout.X + BandLayout.NO_SETTINGS_X + 10,
                    BandLayout.Y + BandLayout.NO_SETTINGS_Y + 2, tool));
        }
    }
}
