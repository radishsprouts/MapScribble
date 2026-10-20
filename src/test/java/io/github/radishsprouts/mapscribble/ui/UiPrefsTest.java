package io.github.radishsprouts.mapscribble.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.radishsprouts.mapscribble.draw.DrawingState;
import io.github.radishsprouts.mapscribble.draw.Tool;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UiPrefsTest {
    @TempDir
    Path dir;

    @Test
    void 保存して読み直すと同じ値になる() {
        DrawingState a = new DrawingState();
        a.setTool(Tool.TEXT);
        a.setColor(NoteColor.BLUE.rgb());
        a.setWidth(3);
        a.setFillOpacity(FillLevel.STRONG.opacity());
        a.setTextScale(TextSize.LARGE.scale());
        a.setOnMinimap(!a.onMinimap());
        Path file = dir.resolve(UiPrefs.FILE_NAME);
        assertTrue(UiPrefs.save(file, a));

        DrawingState b = new DrawingState();
        UiPrefs.load(file, b);
        assertEquals(Tool.TEXT, b.tool());
        assertEquals(NoteColor.BLUE.rgb(), b.color());
        assertEquals(3, b.width());
        assertEquals(FillLevel.STRONG.opacity(), b.fillOpacity());
        assertEquals(TextSize.LARGE.scale(), b.textScale());
        assertEquals(a.onMinimap(), b.onMinimap());
    }

    @Test
    void ファイルが無ければ既定のまま() {
        DrawingState s = new DrawingState();
        String before = UiPrefs.toJson(s);
        UiPrefs.load(dir.resolve("none.json"), s);
        assertEquals(before, UiPrefs.toJson(s));
    }

    @Test
    void 壊れたJSONは何も変えない() throws Exception {
        Path file = dir.resolve("bad.json");
        Files.writeString(file, "{ これはJSONではない");
        DrawingState s = new DrawingState();
        String before = UiPrefs.toJson(s);
        UiPrefs.load(file, s);
        assertEquals(before, UiPrefs.toJson(s));
    }

    @Test
    void 不正な項目だけ無視し_読める項目は反映する() {
        DrawingState s = new DrawingState();
        UiPrefs.apply("{\"tool\":\"NOPE\",\"color\":\"red\",\"width\":2,\"onMinimap\":false}", s);
        assertEquals(new DrawingState().tool(), s.tool());
        assertEquals(new DrawingState().color(), s.color());
        assertEquals(2, s.width());
        assertFalse(s.onMinimap());
    }

    @Test
    void 保存できない場所ならfalse() {
        DrawingState s = new DrawingState();
        // ファイルを親ディレクトリにしようとして必ず失敗させる
        Path blocker = dir.resolve("file");
        try {
            Files.writeString(blocker, "x");
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        assertFalse(UiPrefs.save(blocker.resolve("sub").resolve("x.json"), s));
    }
}
