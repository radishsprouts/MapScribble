package io.github.radishsprouts.mapscribble.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HintFitTest {
    private static final int X = BandLayout.HINT_X;
    private static final int Y = BandLayout.HINT_Y;

    @Test
    void 切っていない間は出さない() {
        assertFalse(HintFit.showFullText("abc", "abc", X + 5, Y + 3));
    }

    @Test
    void 切っている間で行の上なら出す() {
        assertTrue(HintFit.showFullText("abcdef", "abc…", X + 5, Y + 3));
    }

    @Test
    void 行の範囲の境界() {
        assertTrue(HintFit.overHintRow(X, Y - 1));
        assertTrue(HintFit.overHintRow(X + 201, Y + 8));
        assertFalse(HintFit.overHintRow(X - 1, Y));
        assertFalse(HintFit.overHintRow(X + 202, Y));
        assertFalse(HintFit.overHintRow(X, Y - 2));
        assertFalse(HintFit.overHintRow(X, Y + 9));
    }

    @Test
    void 切っていても行の外なら出さない() {
        assertFalse(HintFit.showFullText("abcdef", "abc…", X + 5, Y + 20));
    }

    @Test
    void 幅の上限は190を超えたら警告() {
        assertFalse(HintFit.tooWide(190));
        assertTrue(HintFit.tooWide(191));
    }

    @Test
    void 点検の対象キーはhintの5件とmsgを含む() {
        var keys = HintFit.bandKeys();
        assertTrue(keys.contains("mapscribble.hint.enclose"));
        assertTrue(keys.contains("mapscribble.hint.edit"));
        assertTrue(keys.contains("mapscribble.msg.empty"));
        assertTrue(keys.contains("mapscribble.msg.save_failed"));
        assertEquals(keys.size(), keys.stream().distinct().count());
    }
}
