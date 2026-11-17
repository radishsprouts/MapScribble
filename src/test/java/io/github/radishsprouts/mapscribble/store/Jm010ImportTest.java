package io.github.radishsprouts.mapscribble.store;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.radishsprouts.mapscribble.store.Jm010Import.Plan;
import io.github.radishsprouts.mapscribble.store.NotebookFile.Notebooks;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Jm010ImportTest {
    private static final Instant T = Instant.parse("2026-11-17T12:34:56Z");
    private static final String FILE = "minecraft__overworld.json";

    private static Path jmDir(Path gameDir, String kind, String folder) throws Exception {
        Path d = gameDir.resolve("journeymap/data").resolve(kind).resolve(folder).resolve("addon-data/mapscribble");
        Files.createDirectories(d);
        return d;
    }

    private static void write(Path file, String text) throws Exception {
        Files.write(file, text.getBytes(StandardCharsets.UTF_8));
    }

    private static DebouncedWriter writer() {
        return new DebouncedWriter(60_000, (file, snapshot) -> { });
    }

    @Test
    void 名前の規則はJourneyMapと同じ() {
        assertEquals("Example~Server", Jm010Import.journeyMapName("Example Server", "1.2.3.4"));
        assertEquals("1.2.3.4", Jm010Import.journeyMapName("", "1.2.3.4"));
        assertEquals("~", Jm010Import.journeyMapName("角鯖", "x"));
        assertEquals("", Jm010Import.journeyMapName(null, null));
    }

    @Test
    void 候補が1つなら直下へ元を残してコピーし印を置く(@TempDir Path root) throws Exception {
        Path game = root.resolve("game");
        Path src = jmDir(game, "mp", "Example~Server_1a2b");
        write(src.resolve(FILE), "{\"a\":1}");
        jmDir(game, "mp", "Other_1111");
        Path addr = game.resolve("mapscribble/servers/example.com");

        Plan plan = Jm010Import.planMultiplayer(game, addr, "Example Server", "example.com");
        assertEquals(1, plan.candidates().size());
        assertFalse(plan.asNotebooks());
        assertTrue(Jm010Import.importIntoRoot(plan, addr));

        assertEquals("{\"a\":1}", Files.readString(addr.resolve(FILE)));
        assertEquals("{\"a\":1}", Files.readString(src.resolve(FILE)), "元は残る");
        assertTrue(Files.exists(addr.resolve(Jm010Import.MARKER)));
        assertTrue(Jm010Import.planMultiplayer(game, addr, "Example Server", "example.com").isEmpty(),
                "印があれば列挙しない");
        try (var s = Files.list(addr)) {
            assertEquals(2, s.count(), "一時ファイルが残らない");
        }
    }

    @Test
    void 直下に同じ名前があれば上書きしない(@TempDir Path root) throws Exception {
        Path game = root.resolve("game");
        Path src = jmDir(game, "mp", "S_1");
        write(src.resolve(FILE), "old");
        Path addr = game.resolve("mapscribble/servers/s");
        Files.createDirectories(addr);
        write(addr.resolve(FILE), "mine");

        Plan plan = Jm010Import.planMultiplayer(game, addr, "S", "s");
        Jm010Import.importIntoRoot(plan, addr);
        assertEquals("mine", Files.readString(addr.resolve(FILE)));
        assertEquals("old", Files.readString(src.resolve(FILE)));
    }

    @Test
    void 候補が無ければ何も作らない(@TempDir Path root) throws Exception {
        Path game = root.resolve("game");
        Path addr = game.resolve("mapscribble/servers/s");
        assertTrue(Jm010Import.planMultiplayer(game, addr, "S", "s").isEmpty());
        Files.createDirectories(game.resolve("journeymap/data/mp/S_1"));
        assertTrue(Jm010Import.planMultiplayer(game, addr, "S", "s").isEmpty(), "addon-data が無いものは候補にしない");
        assertFalse(Files.exists(addr));
    }

    @Test
    void jsonでないものと入れ子のフォルダは取り込まない(@TempDir Path root) throws Exception {
        Path game = root.resolve("game");
        Path src = jmDir(game, "mp", "S_1");
        write(src.resolve(FILE), "x");
        write(src.resolve("note.txt"), "t");
        Files.createDirectories(src.resolve("sub"));
        write(src.resolve("sub").resolve(FILE), "deep");
        Path addr = game.resolve("mapscribble/servers/s");
        Jm010Import.importIntoRoot(Jm010Import.planMultiplayer(game, addr, "S", "s"), addr);
        assertTrue(Files.exists(addr.resolve(FILE)));
        assertFalse(Files.exists(addr.resolve("note.txt")));
        assertFalse(Files.exists(addr.resolve("sub")));
    }

    @Test
    void シングルはセーブ名と同じ名前のフォルダだけ(@TempDir Path root) throws Exception {
        Path game = root.resolve("game");
        Path src = jmDir(game, "sp", "My World");
        write(src.resolve(FILE), "sp");
        jmDir(game, "sp", "My World 2");
        Path save = game.resolve("mapscribble/saves/My World");
        Plan plan = Jm010Import.planSingleplayer(game, save, "My World");
        assertEquals(1, plan.candidates().size());
        assertTrue(Jm010Import.importIntoRoot(plan, save));
        assertEquals("sp", Files.readString(save.resolve(FILE)));
    }

    @Test
    void 候補が2つならメモ帳として取り込み_直下へは入れない(@TempDir Path root) throws Exception {
        Path game = root.resolve("game");
        byte[] bytes = "{\"x\":1}".getBytes(StandardCharsets.UTF_8);
        Path a = jmDir(game, "mp", "Net~Lobby_1");
        Path b = jmDir(game, "mp", "Net~Survival_2");
        Files.write(a.resolve(FILE), bytes);
        Files.write(b.resolve(FILE), bytes);
        Path addr = game.resolve("mapscribble/servers/net");
        Plan plan = Jm010Import.planMultiplayer(game, addr, "Net", "net");
        assertTrue(plan.asNotebooks());

        NotebookRegistry reg = new NotebookRegistry();
        try (DebouncedWriter w = writer()) {
            int n = Jm010Import.importAsNotebooks(reg, addr, plan, w, Runnable::run, T).get(5, TimeUnit.SECONDS);
            assertEquals(2, n);
        }
        Notebooks nbs = reg.notebooks(addr);
        assertEquals(2, nbs.notebooks().size());
        assertEquals(null, nbs.mode(), "mode は未決のまま");
        String idA = Jm010Import.idOf(nbs, "Net~Lobby_1");
        String idB = Jm010Import.idOf(nbs, "Net~Survival_2");
        assertNotNull(idA);
        assertNotNull(idB);
        assertArrayEquals(bytes, Files.readAllBytes(addr.resolve(idA).resolve(FILE)));
        assertArrayEquals(bytes, Files.readAllBytes(addr.resolve(idB).resolve(FILE)));
        assertArrayEquals(bytes, Files.readAllBytes(a.resolve(FILE)), "元は残る");
        assertFalse(Files.exists(addr.resolve(FILE)), "直下へは入れない");
        assertTrue(Files.exists(addr.resolve(Jm010Import.MARKER)));
        assertTrue(Files.exists(addr.resolve(idA).resolve(Jm010Import.MARKER)));
        assertTrue(Jm010Import.planMultiplayer(game, addr, "Net", "net").isEmpty(), "2回目は何もしない");
        // notebooks.json が読み直せる
        assertEquals(2, NotebookFile.read(addr.resolve(NotebookFile.FILE_NAME)).notebooks().notebooks().size());
    }
}
