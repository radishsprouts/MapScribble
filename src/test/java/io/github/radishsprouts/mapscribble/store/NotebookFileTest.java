package io.github.radishsprouts.mapscribble.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.radishsprouts.mapscribble.store.NotebookFile.Mode;
import io.github.radishsprouts.mapscribble.store.NotebookFile.Notebook;
import io.github.radishsprouts.mapscribble.store.NotebookFile.Notebooks;
import io.github.radishsprouts.mapscribble.store.NotebookFile.ReadResult;
import io.github.radishsprouts.mapscribble.store.NotebookFile.Status;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NotebookFileTest {
    private static final Instant T = Instant.parse("2026-11-17T12:34:56Z");

    private static Notebooks sample() {
        return Notebooks.empty()
                .withMode(Mode.AUTO)
                .withSplitAt(T)
                .withNotebook(new Notebook("nb-1a2b3c4d5e6f7a8b", "", T, "seed"))
                .withNotebook(new Notebook("nb-m20261118-090000", "資源（リセット前）🌲", T, "manual"))
                .withBinding(0x1a2b3c4d5e6f7a8bL, "nb-1a2b3c4d5e6f7a8b")
                .withBinding(0xf0e1d2c3b4a59687L, "nb-m20261118-090000");
    }

    @Test
    void 書いて読み直すと同じ_負のハッシュも16桁の16進で残る(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("notebooks.json");
        NotebookFile.write(file, sample());
        ReadResult r = NotebookFile.read(file);
        assertEquals(Status.OK, r.status());
        assertEquals(sample(), r.notebooks());
        String text = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(text.contains("\"f0e1d2c3b4a59687\""), "ハッシュは数値ではなく16進の文字列");
        assertTrue(r.notebooks().bindings().containsKey(0xf0e1d2c3b4a59687L));
        assertFalse(Files.exists(dir.resolve("notebooks.json.tmp")), "一時ファイルが残らない");
    }

    @Test
    void ファイルが無ければMISSINGで空() {
        ReadResult r = NotebookFile.read(Path.of("no-such-dir-xyz", "notebooks.json"));
        assertEquals(Status.MISSING, r.status());
        assertNull(r.notebooks().mode());
        assertTrue(r.status().canWrite());
    }

    @Test
    void format2は読まず_上書きもしない(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("notebooks.json");
        String original = "{\"format\":2,\"notebooks\":[]}";
        Files.writeString(file, original, StandardCharsets.UTF_8);
        byte[] before = Files.readAllBytes(file);
        assertEquals(Status.UNSUPPORTED, NotebookFile.read(file).status());
        assertThrows(IOException.class, () -> NotebookFile.write(file, sample()));
        org.junit.jupiter.api.Assertions.assertArrayEquals(before, Files.readAllBytes(file));
        assertFalse(Files.exists(dir.resolve("notebooks.json.tmp")));
    }

    @Test
    void formatが無い_知らないmode_壊れたJSONは書き込めない状態になる(@TempDir Path dir) throws IOException {
        assertEquals(Status.UNSUPPORTED, NotebookFile.parse("{\"notebooks\":[]}").status());
        assertEquals(Status.UNSUPPORTED, NotebookFile.parse("{\"format\":1,\"mode\":\"weird\"}").status());
        assertEquals(Status.CORRUPT, NotebookFile.parse("{not json").status());
        assertEquals(Status.CORRUPT, NotebookFile.parse("[1,2]").status());
        Path file = dir.resolve("notebooks.json");
        Files.writeString(file, "{broken", StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> NotebookFile.write(file, sample()));
        assertEquals("{broken", Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void 知らないキーは書き戻しで残る(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("notebooks.json");
        Files.writeString(file, """
                {"format":1,"mode":"user","futureTop":{"a":[1,2]},
                 "notebooks":[{"id":"nb-1","name":"x","created":"2026-11-17T12:34:56Z","origin":"manual","futureNb":7}],
                 "bindings":{}}
                """, StandardCharsets.UTF_8);
        ReadResult r = NotebookFile.read(file);
        assertEquals(Status.OK, r.status());
        NotebookFile.write(file, r.notebooks().withNotebook(new Notebook("nb-2", "y", T, "manual")));
        String text = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(text.contains("futureTop"));
        assertTrue(text.contains("futureNb"));
        ReadResult again = NotebookFile.read(file);
        assertEquals(2, again.notebooks().notebooks().size());
        assertEquals(7, again.notebooks().find("nb-1").extra().get("futureNb").getAsInt());
    }

    @Test
    void 不正なidの項目は捨てられる_フォルダの外へ出せない() {
        String json = """
                {"format":1,"mode":"auto","notebooks":[
                  {"id":"../x","name":"","created":"2026-11-17T12:34:56Z","origin":"manual"},
                  {"id":"nb-A","name":"","created":"2026-11-17T12:34:56Z","origin":"manual"},
                  {"id":"C:","name":"","created":"2026-11-17T12:34:56Z","origin":"manual"},
                  {"id":"nb-..\\\\x","name":"","created":"2026-11-17T12:34:56Z","origin":"manual"},
                  {"id":"nb-a/b","name":"","created":"2026-11-17T12:34:56Z","origin":"manual"},
                  {"id":"nb-","name":"","created":"2026-11-17T12:34:56Z","origin":"manual"},
                  {"id":"nb-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","name":"","created":"2026-11-17T12:34:56Z"},
                  {"id":"nb-ok","name":"","created":"2026-11-17T12:34:56Z","origin":"manual"}
                ],"bindings":{
                  "0000000000000001":"nb-ok","0000000000000002":"nb-missing","XYZ":"nb-ok","1":"nb-ok"}}
                """;
        ReadResult r = NotebookFile.parse(json);
        assertEquals(Status.OK, r.status());
        assertEquals(1, r.notebooks().notebooks().size());
        assertNotNull(r.notebooks().find("nb-ok"));
        assertEquals(1, r.notebooks().bindings().size());
        assertEquals("nb-ok", r.notebooks().bindings().get(1L));
    }

    @Test
    void 名前は64文字までで制御文字を除く() {
        String name = "a\u0000b\n" + "あ".repeat(100);
        String cleaned = NotebookFile.cleanName(name);
        assertEquals(64, cleaned.codePointCount(0, cleaned.length()));
        assertTrue(cleaned.startsWith("abあ"));
        assertEquals("", NotebookFile.cleanName(null));
    }

    @Test
    void 書き込みは一時ファイル経由で既存の内容を壊さない(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("sub").resolve("notebooks.json");
        NotebookFile.write(file, Notebooks.empty());
        NotebookFile.write(file, sample());
        assertEquals(sample(), NotebookFile.read(file).notebooks());
        try (var files = Files.list(file.getParent())) {
            assertEquals(1L, files.count(), "一時ファイルが残らない");
        }
    }
}
