package io.github.radishsprouts.mapscribble.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.radishsprouts.mapscribble.store.NotebookAccess;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class NotebookPanelTest {
    private static NotebookAccess.Info info(String id, String name) {
        return new NotebookAccess.Info(id, name, Instant.parse("2026-11-01T00:00:00Z"), "auto", List.of());
    }

    private static NotebookAccess.State state(NotebookAccess.Mode mode, boolean readable, String current, long seed,
                                              List<NotebookAccess.Info> list) {
        return new NotebookAccess.State(mode, readable, false, current, false, seed, list);
    }

    @Test
    void 状態の判定() {
        List<NotebookAccess.Info> one = List.of(info("a", "メモ帳 1"));
        assertEquals(NotebookPanel.Kind.LOADING, NotebookPanel.kindOf(null, true));
        assertEquals(NotebookPanel.Kind.LOAD_FAILED, NotebookPanel.kindOf(null, false));
        assertEquals(NotebookPanel.Kind.LOAD_FAILED,
                NotebookPanel.kindOf(state(NotebookAccess.Mode.USER, false, "a", 1, one), false));
        assertEquals(NotebookPanel.Kind.UNSPLIT,
                NotebookPanel.kindOf(state(NotebookAccess.Mode.UNDECIDED, true, null, 0, List.of()), false));
        assertEquals(NotebookPanel.Kind.UNSPLIT,
                NotebookPanel.kindOf(state(NotebookAccess.Mode.OFF, true, null, 0, one), false));
        assertEquals(NotebookPanel.Kind.SPLIT_AUTO,
                NotebookPanel.kindOf(state(NotebookAccess.Mode.AUTO, true, "a", 5, one), false));
        assertEquals(NotebookPanel.Kind.SPLIT_USER,
                NotebookPanel.kindOf(state(NotebookAccess.Mode.USER, true, "a", 5, one), false));
        assertEquals(NotebookPanel.Kind.NO_WORLD_ID,
                NotebookPanel.kindOf(state(NotebookAccess.Mode.USER, true, null, 0, one), false));
    }

    @Test
    void 状態ごとに押せるもの() {
        for (NotebookPanel.Kind k : NotebookPanel.Kind.values()) {
            boolean split = k == NotebookPanel.Kind.SPLIT_AUTO || k == NotebookPanel.Kind.SPLIT_USER
                    || k == NotebookPanel.Kind.NO_WORLD_ID;
            assertEquals(split, NotebookPanel.listEnabled(k), k.name());
            assertEquals(split, NotebookPanel.newEnabled(k), k.name());
            assertEquals(split, NotebookPanel.switchOn(k), k.name());
        }
        assertFalse(NotebookPanel.switchEnabled(NotebookPanel.Kind.LOADING));
        assertFalse(NotebookPanel.switchEnabled(NotebookPanel.Kind.LOAD_FAILED));
        assertFalse(NotebookPanel.switchEnabled(NotebookPanel.Kind.NO_WORLD_ID));
        assertTrue(NotebookPanel.switchEnabled(NotebookPanel.Kind.UNSPLIT));
        assertFalse(NotebookPanel.renameEnabled(NotebookPanel.Kind.NO_WORLD_ID));
        assertTrue(NotebookPanel.renameEnabled(NotebookPanel.Kind.SPLIT_USER));
        assertFalse(NotebookPanel.moveAvailable(NotebookPanel.Kind.UNSPLIT));
        assertTrue(NotebookPanel.moveAvailable(NotebookPanel.Kind.SPLIT_AUTO));
    }

    @Test
    void 分ける前のメモの行() {
        assertNull(NotebookPanel.legacyKey(NotebookPanel.Kind.LOADING, 5));
        assertEquals("mapscribble.notebook.presplit.none", NotebookPanel.legacyKey(NotebookPanel.Kind.SPLIT_AUTO, 0));
        assertEquals("mapscribble.notebook.presplit.unsplit", NotebookPanel.legacyKey(NotebookPanel.Kind.UNSPLIT, 3));
        assertEquals("mapscribble.notebook.presplit.writing", NotebookPanel.legacyKey(NotebookPanel.Kind.NO_WORLD_ID, 3));
        assertEquals("mapscribble.notebook.presplit", NotebookPanel.legacyKey(NotebookPanel.Kind.SPLIT_USER, 3));
        assertTrue(NotebookPanel.legacyGray(NotebookPanel.Kind.UNSPLIT));
        assertFalse(NotebookPanel.legacyGray(NotebookPanel.Kind.SPLIT_USER));
    }

    @Test
    void 件数は999までで以降は999プラス() {
        assertEquals("0", NotebookPanel.countLabel(0));
        assertEquals("999", NotebookPanel.countLabel(999));
        assertEquals("999+", NotebookPanel.countLabel(1000));
    }

    @Test
    void 省略() {
        assertEquals("abc", NotebookPanel.ellipsize("abc", 3, String::length));
        assertEquals("ab…", NotebookPanel.ellipsize("abcdef", 3, String::length));
        String mid = NotebookPanel.ellipsizeMiddle("abcdefghij", 5, String::length);
        assertTrue(mid.length() <= 5 && mid.contains("…"), mid);
        assertEquals("abc", NotebookPanel.ellipsizeMiddle("abc", 5, String::length));
    }

    @Test
    void 名前の検証() {
        List<NotebookAccess.Info> list = List.of(info("a", "本拠地"), info("b", "鉱山"));
        assertEquals("mapscribble.notebook.error.empty", NotebookPanel.validateName("   ", list, "a"));
        assertEquals("mapscribble.notebook.error.duplicate", NotebookPanel.validateName("鉱山", list, "a"));
        assertNull(NotebookPanel.validateName("本拠地", list, "a"));
        assertNull(NotebookPanel.validateName(" 新しい ", list, "a"));
        assertEquals(NotebookPanel.NAME_MAX, NotebookPanel.normalizeName("x".repeat(40)).length());
    }

    @Test
    void 既定の名前は使われていない最小の番号() {
        List<NotebookAccess.Info> list = List.of(info("a", "メモ帳 1"), info("b", "メモ帳 3"));
        assertEquals("メモ帳 2", NotebookPanel.defaultName(list, n -> "メモ帳 " + n));
        assertEquals("メモ帳 1", NotebookPanel.defaultName(List.of(), n -> "メモ帳 " + n));
    }

    @Test
    void 日付と使用中の行() {
        assertEquals("2026-11-01", NotebookPanel.dateLabel(Instant.parse("2026-11-01T00:00:00Z"), ZoneId.of("UTC")));
        assertEquals("", NotebookPanel.dateLabel(null, ZoneId.of("UTC")));
        List<NotebookAccess.Info> list = List.of(info("a", "1"), info("b", "2"));
        assertEquals(1, NotebookPanel.inUseIndex(state(NotebookAccess.Mode.USER, true, "b", 1, list)));
        assertEquals(-1, NotebookPanel.inUseIndex(state(NotebookAccess.Mode.USER, true, null, 1, list)));
        assertEquals(-1, NotebookPanel.inUseIndex(null));
    }

    @Test
    void メニューの選択は押せない行を飛ばし端で止まる() {
        boolean[] e = {true, false, true};
        assertEquals(0, MenuLayout.moveSelection(e, -1, 1));
        assertEquals(2, MenuLayout.moveSelection(e, 0, 1));
        assertEquals(2, MenuLayout.moveSelection(e, 2, 1));
        assertEquals(0, MenuLayout.moveSelection(e, 2, -1));
        assertEquals(2, MenuLayout.moveSelection(e, -1, -1));
        assertEquals(-1, MenuLayout.moveSelection(new boolean[] {false}, -1, 1));
    }

    @Test
    void 画面の配置は一覧の行数で下がる() {
        NotebookLayout three = new NotebookLayout(240);
        NotebookLayout six = new NotebookLayout(300);
        assertEquals(3, three.visibleRows());
        assertEquals(6, six.visibleRows());
        assertEquals(226, three.panelHeight());
        assertEquals(268, six.panelHeight());
        assertEquals(three.doneY() + 42, six.doneY());
        assertEquals(three.listY(), six.listY());
        assertEquals(44, three.listHeight());
        assertEquals(86, six.listHeight());
        assertTrue(three.doneY() + NotebookLayout.BUTTON_HEIGHT <= three.panelHeight());
        assertTrue(six.doneY() + NotebookLayout.BUTTON_HEIGHT <= six.panelHeight());
        assertTrue(three.moveY() + NotebookLayout.BUTTON_HEIGHT <= three.doneY());
        assertTrue(three.renameX() + three.renameWidth() <= three.newX());
        assertTrue(three.newX() + three.newWidth() <= NotebookLayout.PANEL_WIDTH);
        assertTrue(three.moveAllX() + three.moveWidth() <= three.moveVisibleX());
        assertTrue(three.moveVisibleX() + three.moveWidth() <= NotebookLayout.PANEL_WIDTH);
        assertEquals(10, three.panelX(320));
    }
}
