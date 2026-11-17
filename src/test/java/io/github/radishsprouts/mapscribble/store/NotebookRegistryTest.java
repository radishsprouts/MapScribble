package io.github.radishsprouts.mapscribble.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.radishsprouts.mapscribble.store.NotebookFile.Mode;
import io.github.radishsprouts.mapscribble.store.NotebookFile.Notebook;
import io.github.radishsprouts.mapscribble.store.NotebookFile.Notebooks;
import io.github.radishsprouts.mapscribble.store.NotebookFile.Status;
import io.github.radishsprouts.mapscribble.store.NotebookRegistry.ManualSelection;
import io.github.radishsprouts.mapscribble.store.NotebookRegistry.Resolution;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NotebookRegistryTest {
    private static final Instant T = Instant.parse("2026-11-17T12:34:56Z");

    private static DebouncedWriter writer() {
        return new DebouncedWriter(60_000, (file, snapshot) -> { });
    }

    /** 提案を書いて確定する（実際の呼び出し側と同じ順）。 */
    private static Notebooks apply(NotebookRegistry reg, Path dir, Notebooks proposal) throws Exception {
        try (DebouncedWriter w = writer()) {
            Notebooks written = NotebookRegistry.persist(dir, proposal, w).get(5, TimeUnit.SECONDS);
            reg.commit(dir, written);
            return written;
        }
    }

    private static void writeRaw(Path dir, String json) throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("notebooks.json"), json, StandardCharsets.UTF_8);
    }

    @Test
    void 記録が無ければ直下_どのハッシュでも新しいメモ帳を作らない(@TempDir Path dir) {
        NotebookRegistry reg = new NotebookRegistry();
        assertEquals(Status.MISSING, reg.status(dir));
        Resolution r = reg.resolveFull(dir, 123L, null);
        assertNull(r.notebookId());
        assertNull(r.proposal());
    }

    @Test
    void 読めない記録は直下で提案もしない_ファイルも変えない(@TempDir Path dir) throws Exception {
        writeRaw(dir, "{\"format\":2}");
        byte[] before = Files.readAllBytes(dir.resolve("notebooks.json"));
        NotebookRegistry reg = new NotebookRegistry();
        assertEquals(Status.UNSUPPORTED, reg.status(dir));
        Resolution r = reg.resolveFull(dir, 123L, null);
        assertNull(r.notebookId());
        assertNull(r.proposal());
        assertNull(reg.proposeSplit(dir, 1L, 2L, T));
        assertNull(reg.proposeManual(dir, "x", T));
        assertNull(reg.proposeJm010(dir, "Example~Server_1a2b", T));
        assertEquals(new String(before, StandardCharsets.UTF_8),
                Files.readString(dir.resolve("notebooks.json"), StandardCharsets.UTF_8));
    }

    @Test
    void 分ける提案は2つのハッシュにメモ帳を作り_書いてから使う(@TempDir Path dir) throws Exception {
        NotebookRegistry reg = new NotebookRegistry();
        Notebooks p = reg.proposeSplit(dir, 0x11L, 0x22L, T);
        assertNotNull(p);
        assertEquals(Mode.AUTO, p.mode());
        assertEquals(T, p.splitAt());
        assertEquals(2, p.notebooks().size());
        assertEquals("nb-0000000000000011", p.bindings().get(0x11L));
        // 書き込みが終わるまではメモリ上は変わらず直下
        assertNull(reg.resolve(dir, 0x22L, null));
        assertNull(reg.proposeSplit(dir, 0x11L, 0x22L, T), "書き込み中は重ねて提案しない");
        apply(reg, dir, p);
        assertEquals("nb-0000000000000011", reg.resolve(dir, 0x11L, null));
        assertEquals("nb-0000000000000022", reg.resolve(dir, 0x22L, null));
        // 読み直しても同じ（別のレジストリ＝次のセッション）
        assertEquals("nb-0000000000000022", new NotebookRegistry().resolve(dir, 0x22L, null));
    }

    @Test
    void 書けなかったらメモリ上も分けないまま(@TempDir Path dir) throws Exception {
        NotebookRegistry reg = new NotebookRegistry();
        Notebooks p = reg.proposeSplit(dir, 0x11L, 0x22L, T);
        assertNotNull(p);
        reg.abandon(dir);
        assertNull(reg.notebooks(dir).mode());
        assertNull(reg.resolve(dir, 0x22L, null));
        assertNull(reg.proposeSplit(dir, 0x11L, 0x22L, T), "失敗した後は繰り返さない");
    }

    @Test
    void すでに決まっているアドレスでは分ける提案をしない(@TempDir Path dir) {
        for (String mode : new String[] {"auto", "user", "off"}) {
            Path sub = dir.resolve(mode);
            try {
                writeRaw(sub, "{\"format\":1,\"mode\":\"" + mode + "\"}");
            } catch (IOException e) {
                throw new AssertionError(e);
            }
            assertNull(new NotebookRegistry().proposeSplit(sub, 1L, 2L, T), mode);
        }
    }

    @Test
    void resolveは4_4の順_off_ハッシュ0_紐付け済み(@TempDir Path dir) throws Exception {
        NotebookRegistry reg = new NotebookRegistry();
        apply(reg, dir, reg.proposeSplit(dir, 0x11L, 0x22L, T));
        assertNull(reg.resolve(dir, 0L, null), "ハッシュ0は選ばない");
        assertEquals("nb-0000000000000011", reg.resolve(dir, 0x11L, null));
        // off にすると全部直下
        Notebooks off = reg.notebooks(dir).withMode(Mode.OFF);
        apply(reg, dir, off);
        assertNull(reg.resolve(dir, 0x11L, null));
        assertNull(reg.resolve(dir, 0x99L, null));
        assertNull(reg.resolveFull(dir, 0x99L, null).proposal());
    }

    @Test
    void 手で選んだものが最優先_ハッシュ0でも_直下を選ぶこともできる(@TempDir Path dir) throws Exception {
        NotebookRegistry reg = new NotebookRegistry();
        apply(reg, dir, reg.proposeSplit(dir, 0x11L, 0x22L, T));
        assertEquals("nb-0000000000000022",
                reg.resolve(dir, 0x11L, new ManualSelection(0x11L, "nb-0000000000000022")));
        assertEquals("nb-0000000000000022", reg.resolve(dir, 0L, new ManualSelection(0L, "nb-0000000000000022")));
        assertNull(reg.resolve(dir, 0x11L, new ManualSelection(0x11L, null)), "直下を選んだ");
        assertEquals("nb-0000000000000011",
                reg.resolve(dir, 0x11L, new ManualSelection(0x33L, "nb-0000000000000022")), "別のハッシュの選択は無効");
        assertEquals("nb-0000000000000011",
                reg.resolve(dir, 0x11L, new ManualSelection(0x11L, "nb-nothere")), "無いメモ帳は選べない");
        assertEquals("nb-0000000000000011",
                reg.resolve(dir, 0x11L, new ManualSelection(0x11L, "../x")), "不正な id は選べない");
    }

    @Test
    void 読めない記録でも手で選んだメモ帳はidの形が正しければ使う(@TempDir Path dir) throws Exception {
        writeRaw(dir, "{broken");
        NotebookRegistry reg = new NotebookRegistry();
        assertEquals("nb-abc", reg.resolve(dir, 5L, new ManualSelection(5L, "nb-abc")));
        assertNull(reg.resolve(dir, 5L, new ManualSelection(5L, "../evil")), "フォルダの外は選べない");
        assertNull(reg.resolve(dir, 5L, null));
    }

    @Test
    void 分けているアドレスの新しいハッシュは書いてから使う(@TempDir Path dir) throws Exception {
        NotebookRegistry reg = new NotebookRegistry();
        apply(reg, dir, reg.proposeSplit(dir, 0x11L, 0x22L, T));
        Resolution r = reg.resolveFull(dir, 0x33L, null);
        assertNull(r.notebookId(), "書き込みが終わるまでは直下");
        assertNotNull(r.proposal());
        assertEquals("nb-0000000000000033", r.proposal().bindings().get(0x33L));
        assertNull(reg.resolveFull(dir, 0x33L, null).proposal(), "書き込み中は重ねて提案しない");
        apply(reg, dir, r.proposal());
        assertEquals("nb-0000000000000033", reg.resolve(dir, 0x33L, null));
    }

    @Test
    void autoの上限8を超えたら自動の作成を止める(@TempDir Path dir) throws Exception {
        NotebookRegistry reg = new NotebookRegistry();
        apply(reg, dir, reg.proposeSplit(dir, 1L, 2L, T));
        for (long h = 3; h <= NotebookRegistry.AUTO_LIMIT; h++) {
            Resolution r = reg.resolveFull(dir, h, null);
            assertNotNull(r.proposal(), "hash " + h);
            apply(reg, dir, r.proposal());
            assertEquals(NotebookRegistry.seedId(h), reg.resolve(dir, h, null));
        }
        assertEquals(NotebookRegistry.AUTO_LIMIT, reg.notebooks(dir).bindings().size());
        // 9個目
        Resolution over = reg.resolveFull(dir, 100L, null);
        assertNull(over.notebookId());
        assertTrue(over.proposal().autoStopped());
        assertFalse(over.proposal().bindings().containsKey(100L));
        apply(reg, dir, over.proposal());
        assertNull(reg.resolve(dir, 100L, null));
        assertNull(reg.resolveFull(dir, 101L, null).proposal(), "止めた後は提案しない");
        assertEquals(NotebookRegistry.seedId(1L), reg.resolve(dir, 1L, null), "紐付け済みは使える");
        assertTrue(new NotebookRegistry().notebooks(dir).autoStopped(), "記録に残る");
    }

    @Test
    void userでは上限で止めない(@TempDir Path dir) throws Exception {
        NotebookRegistry reg = new NotebookRegistry();
        apply(reg, dir, Notebooks.empty().withMode(Mode.USER));
        for (long h = 1; h <= NotebookRegistry.AUTO_LIMIT + 3; h++) {
            Resolution r = reg.resolveFull(dir, h, null);
            assertNotNull(r.proposal(), "hash " + h);
            assertFalse(r.proposal().autoStopped());
            apply(reg, dir, r.proposal());
        }
    }

    @Test
    void 手で作る提案は切り替えず_idが重なれば連番(@TempDir Path dir) throws Exception {
        NotebookRegistry reg = new NotebookRegistry();
        Notebooks a = reg.proposeManual(dir, "資源", T);
        assertNotNull(a);
        assertEquals("nb-m20261117-123456", a.notebooks().get(0).id());
        assertNull(a.mode(), "作っただけでは分けない");
        apply(reg, dir, a);
        Notebooks b = reg.proposeManual(dir, "資源2", T);
        assertEquals("nb-m20261117-123456-2", b.notebooks().get(1).id());
        reg.cancel(dir);
        Files.createDirectories(dir.resolve("nb-m20261117-123456-2"));
        Notebooks c = reg.proposeManual(dir, "資源3", T);
        assertEquals("nb-m20261117-123456-3", c.notebooks().get(1).id(), "フォルダだけ残っていても避ける");
    }

    @Test
    void 取り込み用のメモ帳はorigin_jm010で_modeを変えない(@TempDir Path dir) throws Exception {
        NotebookRegistry reg = new NotebookRegistry();
        Notebooks p = reg.proposeJm010(dir, "Example~Server_1a2b", T);
        assertNotNull(p);
        Notebook nb = p.notebooks().get(0);
        assertEquals("nb-jexample-server-1a2b", nb.id());
        assertEquals("jm010", nb.origin());
        assertEquals("Example~Server_1a2b", nb.name());
        assertNull(p.mode());
        assertTrue(p.bindings().isEmpty());
        apply(reg, dir, p);
        assertNull(reg.resolve(dir, 0x11L, null), "未決のままなので自動では選ばれない");
        assertNull(reg.proposeJm010(dir, "Example~Server_1a2b", T), "同じものは足さない");
        Notebooks second = reg.proposeJm010(dir, "Example~Server_9f8e", T);
        assertEquals("nb-jexample-server-9f8e", second.notebooks().get(1).id());
    }

    @Test
    void 取り込みのidは40文字に切り_重なれば連番() {
        String longName = "A".repeat(80);
        String id = NotebookRegistry.jm010Id(longName, x -> false);
        assertTrue(NotebookFile.ID_PATTERN.matcher(id).matches());
        assertEquals(3 + 40, id.length());
        String dup = NotebookRegistry.jm010Id(longName, x -> x.equals(id));
        assertTrue(NotebookFile.ID_PATTERN.matcher(dup).matches());
        assertTrue(dup.endsWith("-2"));
        assertEquals(3 + 40, dup.length());
        String weird = NotebookRegistry.jm010Id("../..\\C:", x -> false);
        assertTrue(NotebookFile.ID_PATTERN.matcher(weird).matches(), weird);
    }

    @Test
    void persistは読み直せなければ失敗として返す(@TempDir Path dir) throws Exception {
        // 書き込み先の場所にファイルがあってフォルダを作れない
        Path blocker = dir.resolve("blocker");
        Files.writeString(blocker, "x");
        NotebookRegistry reg = new NotebookRegistry();
        Notebooks p = reg.proposeManual(blocker.resolve("addr"), "x", T);
        try (DebouncedWriter w = writer()) {
            var future = NotebookRegistry.persist(blocker.resolve("addr"), p, w);
            try {
                future.get(5, TimeUnit.SECONDS);
                throw new AssertionError("失敗するはず");
            } catch (java.util.concurrent.ExecutionException expected) {
                assertTrue(expected.getCause() instanceof IOException);
            }
        }
    }
}
