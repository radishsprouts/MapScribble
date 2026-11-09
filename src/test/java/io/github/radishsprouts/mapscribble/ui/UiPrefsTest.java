package io.github.radishsprouts.mapscribble.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.radishsprouts.mapscribble.draw.DrawingState;
import io.github.radishsprouts.mapscribble.draw.Tool;
import io.github.radishsprouts.mapscribble.geom.ToggleButtonPlacement;
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
        a.setTextVisible(false);
        a.setBandPosition(150, 200);
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
        assertEquals(false, b.isTextVisible());
        assertEquals(150, b.bandX());
        assertEquals(200, b.bandY());
    }

    @Test
    void 旧バージョンの設定ファイルの文字の表示設定を引き継ぐ() {
        DrawingState s = new DrawingState();
        UiPrefs.apply("{\"regionTextVisible\":false}", s);
        assertFalse(s.isTextVisible());
    }

    @Test
    void 新旧両方のキーがあれば新しいキーを優先する() {
        DrawingState s = new DrawingState();
        UiPrefs.apply("{\"regionTextVisible\":false,\"textVisible\":true}", s);
        assertTrue(s.isTextVisible());
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
    void 動かした切替ボタンの位置を保存して読み直すと同じ値になる() {
        DrawingState a = new DrawingState();
        a.setToggleButton("xaero", new ToggleButtonPlacement.Saved(true, false, 79, 6));
        a.setToggleButton("ftb_chunks", new ToggleButtonPlacement.Saved(false, true, 10, 30));
        Path file = dir.resolve(UiPrefs.FILE_NAME);
        assertTrue(UiPrefs.save(file, a));

        DrawingState b = new DrawingState();
        UiPrefs.load(file, b);
        assertEquals(new ToggleButtonPlacement.Saved(true, false, 79, 6), b.toggleButton("xaero"));
        assertEquals(new ToggleButtonPlacement.Saved(false, true, 10, 30), b.toggleButton("ftb_chunks"));
        assertNull(b.toggleButton("journeymap_legacy"));
    }

    @Test
    void 動かしていなければtoggleButtonsのキーを書かない() {
        assertFalse(UiPrefs.toJson(new DrawingState()).contains("toggleButtons"));
        DrawingState s = new DrawingState();
        s.setToggleButton("xaero", new ToggleButtonPlacement.Saved(true, false, 79, 6));
        assertTrue(UiPrefs.toJson(s).contains("\"toggleButtons\":{\"xaero\":{\"right\":true,\"bottom\":false,\"dx\":79,\"dy\":6}}"));
    }

    @Test
    void 戻すとキーが消える() {
        DrawingState s = new DrawingState();
        s.setToggleButton("xaero", new ToggleButtonPlacement.Saved(true, false, 79, 6));
        s.setToggleButton("xaero", null);
        assertNull(s.toggleButton("xaero"));
        assertFalse(UiPrefs.toJson(s).contains("toggleButtons"));
    }

    @Test
    void toggleButtonsの無い旧ファイルは今までどおり読める() {
        DrawingState s = new DrawingState();
        UiPrefs.apply("{\"tool\":\"LINE\",\"bandX\":150,\"bandY\":200}", s);
        assertEquals(Tool.LINE, s.tool());
        assertEquals(150, s.bandX());
        assertTrue(s.toggleButtons().isEmpty());
    }

    @Test
    void 壊れたtoggleButtonsはその地図MODだけ既定にし_他の設定は読む() {
        DrawingState s = new DrawingState();
        UiPrefs.apply("{\"width\":3,\"toggleButtons\":{"
                + "\"xaero\":{\"right\":\"yes\",\"bottom\":false,\"dx\":1,\"dy\":1},"
                + "\"ftb_chunks\":{\"right\":true,\"bottom\":false,\"dx\":-5,\"dy\":1},"
                + "\"journeymap_legacy\":{\"right\":true,\"bottom\":true,\"dx\":12,\"dy\":34}}}", s);
        assertEquals(3, s.width());
        assertNull(s.toggleButton("xaero"));
        assertNull(s.toggleButton("ftb_chunks"));
        assertEquals(new ToggleButtonPlacement.Saved(true, true, 12, 34), s.toggleButton("journeymap_legacy"));
    }

    @Test
    void toggleButtonsが物でなくても例外を出さず他の設定は読む() {
        for (String broken : new String[] {"1", "\"x\"", "[]", "null", "{\"xaero\":5}", "{\"xaero\":{}}",
                "{\"xaero\":{\"right\":true,\"bottom\":true,\"dx\":\"a\",\"dy\":2}}"}) {
            DrawingState s = new DrawingState();
            UiPrefs.apply("{\"width\":3,\"toggleButtons\":" + broken + "}", s);
            assertEquals(3, s.width(), broken);
            assertTrue(s.toggleButtons().isEmpty(), broken);
        }
    }

    @Test
    void 画面より大きい距離はエラーにせず保持する() {
        DrawingState s = new DrawingState();
        UiPrefs.apply("{\"toggleButtons\":{\"xaero\":{\"right\":true,\"bottom\":false,\"dx\":99999999999,\"dy\":6}}}", s);
        assertEquals(Integer.MAX_VALUE, s.toggleButton("xaero").dx());
        assertEquals(0, ToggleButtonPlacement.resolveX(s.toggleButton("xaero"), 320));
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
