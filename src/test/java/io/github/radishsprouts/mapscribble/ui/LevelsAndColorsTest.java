package io.github.radishsprouts.mapscribble.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashSet;
import org.junit.jupiter.api.Test;

class LevelsAndColorsTest {
    @Test
    void 色は10色で_値が重ならず_引ける() {
        assertEquals(10, NoteColor.list().size());
        assertEquals(10, new HashSet<>(NoteColor.list().stream().map(NoteColor::rgb).toList()).size());
        for (NoteColor c : NoteColor.list()) {
            assertEquals(c, NoteColor.of(c.rgb()));
            assertEquals("mapscribble.color." + c.name().toLowerCase(), c.translationKey());
        }
        assertEquals(0xFF6E61, NoteColor.list().get(0).rgb());
        assertEquals(0x1A1A1A, NoteColor.list().get(9).rgb());
        assertNull(NoteColor.of(0x123456));
    }

    @Test
    void 色の一覧は書き換えられない() {
        assertThrows(UnsupportedOperationException.class, () -> NoteColor.list().clear());
    }

    @Test
    void 塗りは最も近い段階に丸める() {
        assertEquals(FillLevel.NONE, FillLevel.nearest(0f));
        assertEquals(FillLevel.LIGHT, FillLevel.nearest(0.15f));
        assertEquals(FillLevel.NORMAL, FillLevel.nearest(0.3f));
        assertEquals(FillLevel.NORMAL, FillLevel.nearest(0.35f));
        assertEquals(FillLevel.STRONG, FillLevel.nearest(0.9f));
    }

    @Test
    void 文字の大きさは最も近い段階に丸める() {
        assertEquals(TextSize.SMALL, TextSize.nearest(0.3f));
        assertEquals(TextSize.MEDIUM, TextSize.nearest(0.75f));
        assertEquals(TextSize.LARGE, TextSize.nearest(5f));
        assertEquals(0.75f, TextSize.MEDIUM.scale());
    }
}
