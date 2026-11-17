package io.github.radishsprouts.mapscribble.store;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.radishsprouts.mapscribble.model.MapPoint;
import io.github.radishsprouts.mapscribble.model.Memo;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** メモ帳を開いている時の、2ファイルを同時に開く MemoStore（設計書 3章・5章）。 */
class MemoStoreNotebookTest {
    private static Memo memo(String text) {
        return MemoFileTest.memo(text, new MapPoint(1, 1), new MapPoint(5, 5), new MapPoint(1, 5));
    }

    private static Memo withText(Memo a, String text) {
        return new Memo(a.id(), a.kind(), a.points(), a.color(), a.width(), a.fillOpacity(), text, a.textScale(),
                a.onMinimap(), a.created(), a.updated());
    }

    private static void write(Path file, Memo... memos) throws IOException {
        MemoFile.writeAtomic(file, MemoFile.toJson(List.of(memos)));
    }

    /** 移す操作を呼び、結果が返るまで待つ（結果は書き込み用スレッドから返る）。 */
    private static MoveResult moveSync(MemoStore store, java.util.Collection<UUID> ids, MoveDirection direction) {
        java.util.concurrent.CompletableFuture<MoveResult> future = new java.util.concurrent.CompletableFuture<>();
        store.move(ids, direction, future::complete);
        try {
            return future.get(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static byte[] bytes(Path file) throws IOException {
        return Files.readAllBytes(file);
    }

    @Test
    void 分ける前のメモとメモ帳のメモが両方見えて_分ける前が先に並ぶ(@TempDir Path dir) throws IOException {
        Path legacyFile = dir.resolve("minecraft__overworld.json");
        Path nbFile = dir.resolve("nb-1").resolve("minecraft__overworld.json");
        Memo old = memo("old");
        Memo fresh = memo("fresh");
        write(legacyFile, old);
        write(nbFile, fresh);
        MemoStore store = new MemoStore(5000);
        store.open(nbFile, legacyFile);
        assertEquals(List.of(old, fresh), store.memos());
        assertTrue(store.isLegacy(old.id()));
        assertFalse(store.isLegacy(fresh.id()));
        store.shutdown();
    }

    @Test
    void 開くだけでは元のファイルは1バイトも変わらず_一時ファイルも増えない(@TempDir Path dir) throws IOException {
        Path legacyFile = dir.resolve("minecraft__overworld.json");
        Path nbFile = dir.resolve("nb-1").resolve("minecraft__overworld.json");
        write(legacyFile, memo("a"), memo("b"));
        byte[] before = bytes(legacyFile);
        MemoStore store = new MemoStore(5000);
        store.open(nbFile, legacyFile);
        store.close();
        store.shutdown();
        assertArrayEquals(before, bytes(legacyFile));
        assertFalse(Files.exists(nbFile), "メモ帳のファイルは作らない");
        try (var s = Files.list(dir)) {
            assertEquals(1, s.count(), "直下に増えたものが無い（nb フォルダも作らない）");
        }
    }

    @Test
    void 分ける前のメモの編集は直下のファイルへ書かれメモ帳は作られない(@TempDir Path dir) throws IOException {
        Path legacyFile = dir.resolve("minecraft__overworld.json");
        Path nbFile = dir.resolve("nb-1").resolve("minecraft__overworld.json");
        Memo a = memo("a");
        Memo b = memo("b");
        write(legacyFile, a, b);
        MemoStore store = new MemoStore(5000);
        store.open(nbFile, legacyFile);
        store.update(withText(a, "changed"));
        store.flush();
        List<Memo> onDisk = MemoFile.read(legacyFile).memos();
        assertEquals("changed", onDisk.get(0).text());
        assertEquals(b, onDisk.get(1));
        assertFalse(Files.exists(nbFile));
        store.shutdown();
    }

    @Test
    void 新しいメモはメモ帳へ書かれ_直下は変わらない(@TempDir Path dir) throws IOException {
        Path legacyFile = dir.resolve("minecraft__overworld.json");
        Path nbFile = dir.resolve("nb-1").resolve("minecraft__overworld.json");
        write(legacyFile, memo("a"));
        byte[] before = bytes(legacyFile);
        MemoStore store = new MemoStore(5000);
        store.open(nbFile, legacyFile);
        Memo n = memo("n");
        store.add(n);
        store.flush();
        assertEquals(List.of(n), MemoFile.read(nbFile).memos());
        assertEquals(new String(before, StandardCharsets.UTF_8), Files.readString(legacyFile));
        store.shutdown();
    }

    @Test
    void 分ける前のメモを消して取り消しても直下へ戻りメモ帳へ移らない(@TempDir Path dir) throws IOException {
        Path legacyFile = dir.resolve("minecraft__overworld.json");
        Path nbFile = dir.resolve("nb-1").resolve("minecraft__overworld.json");
        Memo a = memo("a");
        write(legacyFile, a);
        MemoStore store = new MemoStore(5000);
        store.open(nbFile, legacyFile);
        store.remove(a.id());
        assertTrue(store.memos().isEmpty());
        store.add(a);
        store.flush();
        assertEquals(List.of(a), MemoFile.read(legacyFile).memos());
        assertFalse(Files.exists(nbFile));
        assertTrue(store.isLegacy(a.id()));
        store.shutdown();
    }

    @Test
    void 同じidが両方にあれば主ファイルだけが見える(@TempDir Path dir) throws IOException {
        Path legacyFile = dir.resolve("minecraft__overworld.json");
        Path nbFile = dir.resolve("nb-1").resolve("minecraft__overworld.json");
        Memo a = memo("a");
        write(legacyFile, a);
        write(nbFile, withText(a, "primary"));
        MemoStore store = new MemoStore(5000);
        store.open(nbFile, legacyFile);
        assertEquals(1, store.memos().size());
        assertEquals("primary", store.memos().get(0).text());
        assertFalse(store.isLegacy(a.id()));
        store.shutdown();
    }

    @Test
    void 分ける前のファイルが壊れていれば表示せず触らず_主ファイルだけ使える(@TempDir Path dir) throws IOException {
        Path legacyFile = dir.resolve("minecraft__overworld.json");
        Path nbFile = dir.resolve("nb-1").resolve("minecraft__overworld.json");
        Files.writeString(legacyFile, "{ 壊れた");
        byte[] before = bytes(legacyFile);
        List<String> problems = new ArrayList<>();
        MemoStore store = new MemoStore(5000);
        store.setProblemHandler(problems::add);
        store.open(nbFile, legacyFile);
        assertEquals(1, problems.size());
        assertTrue(store.isAvailable());
        store.add(memo("n"));
        store.flush();
        store.shutdown();
        assertEquals(new String(before, StandardCharsets.UTF_8), Files.readString(legacyFile));
        assertEquals(1, MemoFile.read(nbFile).memos().size());
    }

    @Test
    void 主ファイルが読めなくても分ける前のメモは見えるが書けない(@TempDir Path dir) throws IOException {
        Path legacyFile = dir.resolve("minecraft__overworld.json");
        Path nbFile = dir.resolve("nb-1").resolve("minecraft__overworld.json");
        Memo a = memo("a");
        write(legacyFile, a);
        Files.createDirectories(nbFile.getParent());
        Files.writeString(nbFile, "{\"format\":2,\"memos\":[]}");
        byte[] nbBefore = bytes(nbFile);
        MemoStore store = new MemoStore(5000);
        store.open(nbFile, legacyFile);
        assertEquals(List.of(a), store.memos());
        assertFalse(store.isAvailable());
        store.add(memo("x"));
        store.flush();
        store.shutdown();
        assertEquals(new String(nbBefore, StandardCharsets.UTF_8), Files.readString(nbFile));
    }

    @Test
    void 開き直しと閉じるたびに世代が増える(@TempDir Path dir) {
        MemoStore store = new MemoStore(5000);
        long g0 = store.generation();
        store.open(dir.resolve("a.json"));
        long g1 = store.generation();
        store.open(dir.resolve("a.json"));
        long g2 = store.generation();
        store.close();
        assertTrue(g1 > g0 && g2 > g1 && store.generation() > g2);
        store.shutdown();
    }

    @Test
    void 移す_メモ帳へ書いてから元から除き_bakは1回だけ_戻すと両方が元と同じ(@TempDir Path dir) throws IOException {
        Path legacyFile = dir.resolve("minecraft__overworld.json");
        Path nbFile = dir.resolve("nb-1").resolve("minecraft__overworld.json");
        Path bak = dir.resolve("minecraft__overworld.json" + MemoStore.BACKUP_SUFFIX);
        Memo a = memo("a");
        Memo b = memo("b");
        Memo n = memo("n");
        write(legacyFile, a, b);
        write(nbFile, n);
        byte[] legacyBefore = bytes(legacyFile);
        byte[] nbBefore = bytes(nbFile);
        MemoStore store = new MemoStore(5000);
        store.open(nbFile, legacyFile);

        AtomicReference<MoveResult> result = new AtomicReference<>();
        result.set(moveSync(store, List.of(a.id(), b.id()), MoveDirection.LEGACY_TO_PRIMARY));
        assertTrue(result.get().ok());
        assertEquals(2, result.get().moved());
        assertFalse(store.isBusy());
        assertEquals(List.of(n, a, b), MemoFile.read(nbFile).memos());
        assertTrue(MemoFile.read(legacyFile).memos().isEmpty());
        assertEquals(new String(legacyBefore, StandardCharsets.UTF_8), Files.readString(bak), ".bak は元の内容");
        assertEquals(List.of(n, a, b), store.memos());
        assertFalse(store.isLegacy(a.id()));

        // 戻す。.bak は残り、上書きされない
        byte[] bakBytes = bytes(bak);
        result.set(moveSync(store, List.of(a.id(), b.id()), MoveDirection.PRIMARY_TO_LEGACY));
        assertTrue(result.get().ok());
        assertEquals(new String(legacyBefore, StandardCharsets.UTF_8), Files.readString(legacyFile));
        assertEquals(new String(nbBefore, StandardCharsets.UTF_8), Files.readString(nbFile));
        assertEquals(new String(bakBytes, StandardCharsets.UTF_8), Files.readString(bak));

        // もう一度移しても .bak は上書きされない（内容が同じでも、先に作ったものを残す）
        Files.writeString(bak, "既存の控え");
        moveSync(store, List.of(a.id()), MoveDirection.LEGACY_TO_PRIMARY);
        assertEquals("既存の控え", Files.readString(bak));
        store.shutdown();
    }

    @Test
    void 移す途中で元のファイルへの書き込みが失敗すると主ファイルは戻り元は変わらない(@TempDir Path dir) throws IOException {
        Path legacyFile = dir.resolve("minecraft__overworld.json");
        Path nbFile = dir.resolve("nb-1").resolve("minecraft__overworld.json");
        Memo a = memo("a");
        Memo n = memo("n");
        write(legacyFile, a);
        write(nbFile, n);
        byte[] legacyBefore = bytes(legacyFile);
        byte[] nbBefore = bytes(nbFile);
        List<String> problems = new ArrayList<>();
        MemoStore store = new MemoStore(5000);
        store.setProblemHandler(problems::add);
        store.setMoveSink((f, snapshot) -> {
            if (f.equals(legacyFile)) {
                throw new IOException("試験用の失敗");
            }
            MemoFile.writeAtomic(f, MemoFile.toJson(snapshot));
        });
        store.open(nbFile, legacyFile);

        AtomicReference<MoveResult> result = new AtomicReference<>();
        result.set(moveSync(store, List.of(a.id()), MoveDirection.LEGACY_TO_PRIMARY));
        assertFalse(result.get().ok());
        assertEquals(MoveResult.KEY_FAILED, result.get().messageKey());
        assertFalse(store.isBusy());
        assertEquals(new String(legacyBefore, StandardCharsets.UTF_8), Files.readString(legacyFile));
        assertEquals(MemoFile.read(nbFile).memos(), MemoFile.parse(new String(nbBefore, StandardCharsets.UTF_8)).memos());
        assertEquals(List.of(a, n), store.memos(), "メモリは移す前のまま");
        assertEquals(1, problems.size());
        store.shutdown();
    }

    @Test
    void 移している間は追加も変更も削除も受け付けない(@TempDir Path dir) throws IOException {
        Path legacyFile = dir.resolve("minecraft__overworld.json");
        Path nbFile = dir.resolve("nb-1").resolve("minecraft__overworld.json");
        Memo a = memo("a");
        write(legacyFile, a);
        MemoStore store = new MemoStore(5000);
        List<Runnable> queued = new ArrayList<>();
        store.setMainThreadExecutor(queued::add);
        store.open(nbFile, legacyFile);
        store.move(List.of(a.id()), MoveDirection.LEGACY_TO_PRIMARY, r -> { });
        // 結果が主スレッドへ戻るまで busy のまま
        long deadline = System.currentTimeMillis() + 5000;
        while (queued.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.onSpinWait();
        }
        assertTrue(store.isBusy());
        store.add(memo("x"));
        store.remove(a.id());
        assertEquals(1, store.memos().size());
        queued.get(0).run();
        assertFalse(store.isBusy());
        store.add(memo("x"));
        assertEquals(2, store.memos().size());
        store.shutdown();
    }

    @Test
    void メモ帳を開いていない時や片方が読めない時の移す操作は失敗として返る(@TempDir Path dir) {
        MemoStore store = new MemoStore(5000);
        store.open(dir.resolve("a.json"));
        AtomicReference<MoveResult> result = new AtomicReference<>();
        store.move(List.of(UUID.randomUUID()), MoveDirection.LEGACY_TO_PRIMARY, result::set);
        assertFalse(result.get().ok());
        assertEquals(MoveResult.KEY_UNAVAILABLE, result.get().messageKey());
        store.shutdown();
    }

    @Test
    void SessionKeyのファイルはメモ帳があればその中_無ければ直下(@TempDir Path dir) {
        SessionKey direct = new SessionKey(dir, "w", "minecraft:overworld");
        SessionKey nb = new SessionKey(dir, "w", "minecraft:overworld", "nb-1");
        assertEquals(dir.resolve("minecraft__overworld.json"), direct.file());
        assertNull(direct.legacyFile());
        assertEquals(dir.resolve("nb-1").resolve("minecraft__overworld.json"), nb.file());
        assertEquals(dir.resolve("minecraft__overworld.json"), nb.legacyFile());
        assertTrue(SessionKey.needsSwitch(direct, nb), "メモ帳が変わると鍵が変わる");
    }

    @Test
    void SessionControllerはメモ帳の鍵で2ファイルを開く(@TempDir Path dir) throws IOException {
        Memo a = memo("a");
        write(dir.resolve("minecraft__overworld.json"), a);
        MemoStore store = new MemoStore(5000);
        SessionController controller = new SessionController(store);
        controller.update(new SessionKey(dir, "w", "minecraft:overworld", "nb-1"), null);
        assertEquals(List.of(a), store.memos());
        assertTrue(store.isLegacy(a.id()));
        store.shutdown();
    }
}
