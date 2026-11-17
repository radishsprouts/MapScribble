package io.github.radishsprouts.mapscribble.draw;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.radishsprouts.mapscribble.model.MapPoint;
import io.github.radishsprouts.mapscribble.model.Memo;
import io.github.radishsprouts.mapscribble.model.MemoKind;
import io.github.radishsprouts.mapscribble.store.MemoFile;
import io.github.radishsprouts.mapscribble.store.MemoStore;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 実物の MemoStore を使った、元に戻すの履歴と、移す操作・世代の試験。 */
class DrawingControllerNotebookTest {
    private MemoStore store;
    private DrawingController controller;
    private final List<String> notices = new ArrayList<>();

    @BeforeEach
    void setUp() {
        store = new MemoStore(5000);
        DrawingState state = new DrawingState();
        state.setNoticeHandler(notices::add);
        state.setModeOn(true);
        controller = new DrawingController(state, () -> store);
    }

    private static Memo memo(String text) {
        Instant now = Instant.now();
        return new Memo(UUID.randomUUID(), MemoKind.REGION,
                List.of(new MapPoint(0, 0), new MapPoint(10, 0), new MapPoint(10, 10)), 0xFF6E61, 2, 0.3f, text, 1.5f,
                true, now, now);
    }

    private void awaitIdle() {
        long deadline = System.currentTimeMillis() + 10_000;
        while (store.isBusy() && System.currentTimeMillis() < deadline) {
            Thread.onSpinWait();
        }
        assertFalse(store.isBusy());
    }

    private static void write(Path file, Memo... memos) throws IOException {
        MemoFile.writeAtomic(file, MemoFile.toJson(List.of(memos)));
    }

    @Test
    void 分ける前のメモを消して元に戻すと直下へ戻りメモ帳へ移らない(@TempDir Path dir) throws IOException {
        Path legacy = dir.resolve("minecraft__overworld.json");
        Path nb = dir.resolve("nb-1").resolve("minecraft__overworld.json");
        Memo a = memo("a");
        write(legacy, a);
        store.open(nb, legacy);
        controller.remove(a);
        assertTrue(controller.undo());
        store.flush();
        assertEquals(List.of(a), MemoFile.read(legacy).memos());
        assertFalse(java.nio.file.Files.exists(nb));
    }

    @Test
    void 同じディメンションのままメモ帳が変わると履歴を捨てる(@TempDir Path dir) {
        Path nb1 = dir.resolve("nb-1").resolve("minecraft__overworld.json");
        Path nb2 = dir.resolve("nb-2").resolve("minecraft__overworld.json");
        Path legacy = dir.resolve("minecraft__overworld.json");
        store.open(nb1, legacy);
        controller.add(memo("a"));
        assertTrue(controller.canUndo());
        store.open(nb2, legacy);
        assertFalse(controller.canUndo(), "別のメモ帳へ元に戻すが書かないよう、履歴は空");
        assertFalse(controller.undo());
        store.flush();
        assertTrue(MemoFile.read(nb2).memos().isEmpty());
        store.shutdown();
    }

    @Test
    void 移す操作は履歴に積まれ_元に戻すと戻りやり直すと移る(@TempDir Path dir) throws Exception {
        Path legacy = dir.resolve("minecraft__overworld.json");
        Path nb = dir.resolve("nb-1").resolve("minecraft__overworld.json");
        Memo a = memo("a");
        Memo b = memo("b");
        write(legacy, a, b);
        store.open(nb, legacy);
        java.util.concurrent.CompletableFuture<Boolean> done = new java.util.concurrent.CompletableFuture<>();
        controller.moveLegacyToNotebook(null, r -> done.complete(r.ok()));
        assertTrue(done.get(10, java.util.concurrent.TimeUnit.SECONDS));
        assertEquals(List.of(a, b), MemoFile.read(nb).memos());
        assertTrue(MemoFile.read(legacy).memos().isEmpty());
        assertFalse(store.isLegacy(a.id()));

        assertTrue(controller.undo());
        awaitIdle();
        assertEquals(List.of(a, b), MemoFile.read(legacy).memos());
        assertTrue(MemoFile.read(nb).memos().isEmpty());
        assertTrue(store.isLegacy(a.id()));

        assertTrue(controller.redo());
        awaitIdle();
        assertEquals(List.of(a, b), MemoFile.read(nb).memos());
        store.shutdown();
    }

    @Test
    void 世代が変わるまで履歴は残る(@TempDir Path dir) {
        store.open(dir.resolve("a.json"));
        controller.add(memo("a"));
        assertTrue(controller.canUndo());
        assertTrue(controller.undo());
        assertTrue(controller.canRedo());
        store.shutdown();
    }
}
