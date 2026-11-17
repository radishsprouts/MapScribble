package io.github.radishsprouts.mapscribble.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HintFitTest {
    private static final int BX = BandLayout.X;
    private static final int BY = BandLayout.Y;
    private static final int H = 240;
    private static final int W = 100;

    @Test
    void 切っていない間は出さない() {
        assertFalse(HintFit.showFullText("abc", "abc", BX + 5, BY + 46, BX, BY, H, W));
    }

    @Test
    void 切っている間で行の上なら出す() {
        assertTrue(HintFit.showFullText("abcdef", "abc…", BX + 5, BY + 46, BX, BY, H, W));
    }

    @Test
    void 判定の範囲は背景の四角() {
        // 通常: 文字の x = BX+3、y = BY+44。四角は x BX+1〜BX+3+W+2、y BY+42〜BY+54
        assertTrue(HintFit.overHintRow(BX + 1, BY + 42, BX, BY, H, W));
        assertTrue(HintFit.overHintRow(BX + 3 + W + 1, BY + 53, BX, BY, H, W));
        assertFalse(HintFit.overHintRow(BX, BY + 44, BX, BY, H, W));
        assertFalse(HintFit.overHintRow(BX + 3 + W + 2, BY + 44, BX, BY, H, W));
        assertFalse(HintFit.overHintRow(BX + 5, BY + 41, BX, BY, H, W));
        assertFalse(HintFit.overHintRow(BX + 5, BY + 54, BX, BY, H, W));
    }

    @Test
    void 行の幅いっぱいには敷かない_文字の幅に合わせる() {
        HintFit.Rect r = HintFit.background(BX, BY, H, W);
        assertEquals(W + 4, r.width());
        assertEquals(12, r.height());
        assertFalse(HintFit.overHintRow(BX + 3 + 150, BY + 46, BX, BY, H, W));
    }

    @Test
    void 切っていても行の外なら出さない() {
        assertFalse(HintFit.showFullText("abcdef", "abc…", BX + 5, BY + 70, BX, BY, H, W));
    }

    @Test
    void 帯を動かすと判定の範囲も動く() {
        int bx = 100;
        int by = 60;
        assertTrue(HintFit.overHintRow(bx + 5, by + 46, bx, by, H, W));
        assertFalse(HintFit.overHintRow(BX + 5, BY + 46, bx, by, H, W));
    }

    @Test
    void 位置のしきい値は画面高240で_185は帯の下_186は帯の上() {
        assertFalse(HintFit.above(185, 240));
        assertTrue(HintFit.above(186, 240));
        assertEquals(185 + 44, HintFit.hintY(185, 240));
        assertEquals(186 - 11, HintFit.hintY(186, 240));
        // 画面高 240 での帯の y の最大（240 − 41 = 199）
        assertEquals(199 - 11, HintFit.hintY(199, 240));
        assertEquals(BX + 3, HintFit.hintX(BX));
    }

    @Test
    void 帯の上に描く時の背景は帯との間が1px空く() {
        HintFit.Rect r = HintFit.background(BX, 199, H, W);
        assertEquals(199 - 13, r.y1());
        assertEquals(199 - 1, r.y2());
        // 帯の下に描いた時の背景の下端は 帯の y + 54 まで
        assertEquals(185 + 54, HintFit.background(BX, 185, H, W).y2());
    }

    @Test
    void 帯の上に描いた時も判定は背景の四角() {
        assertTrue(HintFit.overHintRow(BX + 5, 199 - 8, BX, 199, H, W));
        assertFalse(HintFit.overHintRow(BX + 5, 199 + 44, BX, 199, H, W));
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

    @Test
    void 点検の対象に0_4_0のお知らせと読み込み中を含む() {
        var keys = HintFit.bandKeys();
        for (String k : new String[] {"notebook_switched", "split_started", "no_world_id", "moved", "move_undone",
                "move_failed", "jm010_imported", "move_busy", "loading"}) {
            assertTrue(keys.contains("mapscribble.msg." + k), k);
        }
    }
}
