package io.github.radishsprouts.mapscribble.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.radishsprouts.mapscribble.model.MapPoint;
import io.github.radishsprouts.mapscribble.model.Memo;
import io.github.radishsprouts.mapscribble.model.MemoKind;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MemoFileTest {
    static Memo memo(String text, MapPoint... points) {
        return new Memo(UUID.randomUUID(), MemoKind.REGION, List.of(points), 0x12AB34, 2, 0.25f, text, 1.5f, true,
                Instant.parse("2026-10-20T01:02:03Z"), Instant.parse("2026-10-20T04:05:06Z"));
    }

    @Test
    void 往復で小数第2位まで保たれる() {
        Memo m = memo("1行目\n2行目", new MapPoint(123.456, -67.8), new MapPoint(124.1, -66.254));
        MemoFile.ReadResult r = MemoFile.parse(MemoFile.toJson(List.of(m)));
        assertEquals(MemoFile.Status.OK, r.status());
        Memo back = r.memos().get(0);
        assertEquals(new MapPoint(123.46, -67.8), back.points().get(0));
        assertEquals(new MapPoint(124.1, -66.25), back.points().get(1));
        assertEquals(m.id(), back.id());
        assertEquals(m.color(), back.color());
        assertEquals(m.text(), back.text());
        assertEquals(m.created(), back.created());
        assertEquals(m.updated(), back.updated());
        assertEquals(m.fillOpacity(), back.fillOpacity());
    }

    @Test
    void 色はRRGGBB文字列で書く() {
        String json = MemoFile.toJson(List.of(memo("", new MapPoint(0, 0))));
        assertTrue(json.contains("\"color\":\"#12AB34\""), json);
        assertTrue(json.contains("\"format\":1"), json);
    }

    @Test
    void 知らないformatは読まない() {
        MemoFile.ReadResult r = MemoFile.parse("{\"format\":2,\"memos\":[]}");
        assertEquals(MemoFile.Status.UNSUPPORTED, r.status());
        assertFalse(r.canWrite());
    }

    @Test
    void 壊れたJSONは読まない() {
        assertEquals(MemoFile.Status.CORRUPT, MemoFile.parse("{\"format\":1,\"memos\":[").status());
        assertEquals(MemoFile.Status.CORRUPT, MemoFile.parse("{\"format\":1,\"memos\":[{\"id\":\"x\"}]}").status());
        assertFalse(MemoFile.parse("[]").canWrite());
    }

    @Test
    void ディメンションごとのファイル名() {
        assertEquals("minecraft__overworld.json", MemoFile.fileName("minecraft:overworld"));
        assertEquals("minecraft__the_nether.json", MemoFile.fileName("minecraft:the_nether"));
        assertEquals("mod__a_b.json", MemoFile.fileName("mod:a/b"));
        assertEquals(Path.of("d", "minecraft__the_end.json"), MemoFile.resolve(Path.of("d"), "minecraft:the_end"));
    }

    @Test
    void 一時ファイル経由で置き換える(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("sub").resolve("minecraft__overworld.json");
        MemoFile.writeAtomic(file, "first");
        MemoFile.writeAtomic(file, "second");
        assertEquals("second", Files.readString(file));
        assertFalse(Files.exists(dir.resolve("sub").resolve("minecraft__overworld.json.tmp")));
    }

    @Test
    void ファイルが無ければ空で書ける() {
        MemoFile.ReadResult r = MemoFile.read(Path.of("no-such-dir", "none.json"));
        assertEquals(MemoFile.Status.MISSING, r.status());
        assertTrue(r.canWrite());
    }
}
