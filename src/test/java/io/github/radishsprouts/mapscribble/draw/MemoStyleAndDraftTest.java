package io.github.radishsprouts.mapscribble.draw;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.radishsprouts.mapscribble.model.MapPoint;
import io.github.radishsprouts.mapscribble.model.Memo;
import io.github.radishsprouts.mapscribble.model.MemoKind;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MemoStyleAndDraftTest {
    private static Memo memo(MemoKind kind, List<MapPoint> points) {
        Instant now = Instant.EPOCH;
        return new Memo(UUID.randomUUID(), kind, points, 0xFF6E61, 2, 0.3f, "", 1.5f, true, now, now);
    }

    @Test
    void 線の太さは段階ごとに2_4_7で_範囲外は端に丸める() {
        assertEquals(2.0f, MemoStyle.strokePixels(1));
        assertEquals(4.0f, MemoStyle.strokePixels(2));
        assertEquals(7.0f, MemoStyle.strokePixels(3));
        assertEquals(2.0f, MemoStyle.strokePixels(0));
        assertEquals(2.0f, MemoStyle.strokePixels(-5));
        assertEquals(7.0f, MemoStyle.strokePixels(99));
    }

    @Test
    void 黒のメモだけ白い背景で影を付けない() {
        assertTrue(MemoStyle.isBlack(MemoStyle.BLACK));
        // 上位バイト（アルファ）が付いていても黒と判定する
        assertTrue(MemoStyle.isBlack(0xFF000000 | MemoStyle.BLACK));
        assertFalse(MemoStyle.isBlack(0xFF6E61));
        assertEquals(0xFFFFFF, MemoStyle.textBackgroundRgb(MemoStyle.BLACK));
        assertEquals(0x000000, MemoStyle.textBackgroundRgb(0xFF6E61));
        assertFalse(MemoStyle.textShadow(MemoStyle.BLACK));
        assertTrue(MemoStyle.textShadow(0xFF6E61));
    }

    @Test
    void 不透明度をARGBにする_範囲外は丸める() {
        assertEquals(0xFF6E61 | (0xFF << 24), MemoStyle.argb(0xFF6E61, 1.0f));
        assertEquals(0xFF6E61, MemoStyle.argb(0xFF6E61, 0.0f));
        assertEquals(0x80FF6E61, MemoStyle.argb(0xFF6E61, 0.5f));
        assertEquals(0xFF6E61 | (0xFF << 24), MemoStyle.argb(0xFF6E61, 2.0f));
        assertEquals(0xFF6E61, MemoStyle.argb(0xFF6E61, -1.0f));
        // rgb に余分なビットが付いていても、色の部分だけを使う
        assertEquals(0x80FF6E61, MemoStyle.argb(0x12FF6E61, 0.5f));
    }

    @Test
    void 文字は改行で行に分ける_空なら行なし_末尾の空行は残す() {
        assertArrayEquals(new String[0], MemoStyle.lines(""));
        assertArrayEquals(new String[0], MemoStyle.lines(null));
        assertArrayEquals(new String[] {"abc"}, MemoStyle.lines("abc"));
        assertArrayEquals(new String[] {"a", "b", "c"}, MemoStyle.lines("a\nb\nc"));
        assertArrayEquals(new String[] {"a", ""}, MemoStyle.lines("a\n"));
    }

    @Test
    void アンカーは囲みが重心_線が中ほどの点_文字が置いた点() {
        Memo region = memo(MemoKind.REGION, List.of(new MapPoint(0, 0), new MapPoint(10, 0), new MapPoint(10, 10),
                new MapPoint(0, 10)));
        MapPoint c = MemoStyle.anchor(region);
        assertEquals(5.0, c.x(), 1e-9);
        assertEquals(5.0, c.z(), 1e-9);

        Memo line = memo(MemoKind.LINE, List.of(new MapPoint(0, 0), new MapPoint(1, 1), new MapPoint(2, 2),
                new MapPoint(3, 3), new MapPoint(4, 4)));
        assertEquals(new MapPoint(2, 2), MemoStyle.anchor(line));
        Memo twoPointLine = memo(MemoKind.LINE, List.of(new MapPoint(0, 0), new MapPoint(8, 8)));
        assertEquals(new MapPoint(8, 8), MemoStyle.anchor(twoPointLine));

        Memo text = memo(MemoKind.TEXT, List.of(new MapPoint(7.25, -3.5)));
        assertEquals(new MapPoint(7.25, -3.5), MemoStyle.anchor(text));
    }

    @Test
    void 点が無いメモのアンカーは原点() {
        assertEquals(new MapPoint(0, 0), MemoStyle.anchor(memo(MemoKind.REGION, List.of())));
        assertEquals(new MapPoint(0, 0), MemoStyle.anchor(memo(MemoKind.LINE, List.of())));
        assertEquals(new MapPoint(0, 0), MemoStyle.anchor(memo(MemoKind.TEXT, List.of())));
    }

    @Test
    void 下書きから文字を付けてメモにできる() {
        Draft draft = new Draft(MemoKind.REGION, List.of(new MapPoint(0, 0), new MapPoint(4, 0), new MapPoint(4, 4)),
                0x32D2FF, 3, 0.5f, 2.0f, false);
        Memo m = draft.toMemo("拠点");
        assertEquals(MemoKind.REGION, m.kind());
        assertEquals(draft.points(), m.points());
        assertEquals(0x32D2FF, m.color());
        assertEquals(3, m.width());
        assertEquals(0.5f, m.fillOpacity());
        assertEquals(2.0f, m.textScale());
        assertFalse(m.onMinimap());
        assertEquals("拠点", m.text());
        assertEquals(m.created(), m.updated());
    }

    @Test
    void 下書きから作るたびにidが変わる() {
        Draft draft = new Draft(MemoKind.TEXT, List.of(new MapPoint(1, 1)), 0, 1, 0f, 1f, true);
        assertNotEquals(draft.toMemo("a").id(), draft.toMemo("a").id());
    }

    @Test
    void 下書きの点は元の一覧を変えても影響されず_書き換えもできない() {
        List<MapPoint> src = new ArrayList<>(List.of(new MapPoint(0, 0), new MapPoint(1, 1)));
        Draft draft = new Draft(MemoKind.LINE, src, 0, 1, 0f, 1f, true);
        src.add(new MapPoint(9, 9));
        assertEquals(2, draft.points().size());
        assertThrows(UnsupportedOperationException.class, () -> draft.points().add(new MapPoint(2, 2)));
    }

    @Test
    void メモの文字がnullなら空文字になり_点は書き換えられない() {
        Instant now = Instant.EPOCH;
        Memo m = new Memo(UUID.randomUUID(), MemoKind.TEXT, List.of(new MapPoint(0, 0)), 0, 1, 0f, null, 1f, true,
                now, now);
        assertEquals("", m.text());
        assertThrows(UnsupportedOperationException.class, () -> m.points().add(new MapPoint(1, 1)));
    }
}
