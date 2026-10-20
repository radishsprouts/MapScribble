package io.github.radishsprouts.mapscribble.draw;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UndoHistoryTest {
    @Test
    void 上限は100件で_古いものから捨てる() {
        UndoHistory<Integer> h = new UndoHistory<>();
        for (int i = 1; i <= 150; i++) {
            h.push(i);
        }
        assertEquals(100, h.undoSize());
        // 新しい順に 150..51 が取り出せ、1..50 は捨てられている
        for (int expected = 150; expected >= 51; expected--) {
            assertEquals(expected, h.popUndo());
        }
        assertNull(h.popUndo());
        assertFalse(h.canUndo());
    }

    @Test
    void 上限の定数は100() {
        assertEquals(100, UndoHistory.LIMIT);
    }

    @Test
    void 取り消してやり直せる() {
        UndoHistory<String> h = new UndoHistory<>();
        h.push("a");
        h.push("b");
        assertEquals("b", h.popUndo());
        assertTrue(h.canRedo());
        assertEquals("b", h.popRedo());
        assertFalse(h.canRedo());
        assertEquals("b", h.popUndo());
        assertEquals("a", h.popUndo());
        assertNull(h.popUndo());
    }

    @Test
    void 新しい操作を積むとやり直しの履歴は捨てられる() {
        UndoHistory<String> h = new UndoHistory<>();
        h.push("a");
        h.push("b");
        h.popUndo();
        h.push("c");
        assertFalse(h.canRedo());
        assertNull(h.popRedo());
        assertEquals("c", h.popUndo());
        assertEquals("a", h.popUndo());
    }

    @Test
    void やり直しで戻した分も上限を超えない() {
        UndoHistory<Integer> h = new UndoHistory<>(3);
        h.push(1);
        h.push(2);
        h.push(3);
        h.popUndo();
        h.popUndo();
        h.popRedo();
        h.popRedo();
        assertEquals(3, h.undoSize());
    }

    @Test
    void 履歴を空にできる() {
        UndoHistory<Integer> h = new UndoHistory<>();
        h.push(1);
        h.popUndo();
        h.push(2);
        h.clear();
        assertFalse(h.canUndo());
        assertFalse(h.canRedo());
    }
}
