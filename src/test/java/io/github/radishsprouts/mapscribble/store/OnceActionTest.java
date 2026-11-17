package io.github.radishsprouts.mapscribble.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.radishsprouts.mapscribble.model.MapPoint;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OnceActionTest {
    @Test
    void 何度呼んでも1回だけ働く() {
        AtomicInteger count = new AtomicInteger();
        OnceAction once = new OnceAction(count::incrementAndGet);
        assertTrue(once.run());
        assertFalse(once.run());
        assertFalse(once.run());
        assertEquals(1, count.get());
    }

    @Test
    void 別のスレッドから同時に呼んでも1回だけ働く() throws Exception {
        AtomicInteger count = new AtomicInteger();
        OnceAction once = new OnceAction(count::incrementAndGet);
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            for (int i = 0; i < threads; i++) {
                pool.execute(() -> {
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    once.run();
                });
            }
            start.countDown();
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
        assertEquals(1, count.get());
    }

    @Test
    void 中身が例外でも2回目は走らない() {
        AtomicInteger count = new AtomicInteger();
        OnceAction once = new OnceAction(() -> {
            count.incrementAndGet();
            throw new IllegalStateException("x");
        });
        assertThrows(IllegalStateException.class, once::run);
        assertFalse(once.run());
        assertEquals(1, count.get());
    }

    @Test
    void 停止の書き切りが1回だけ働き_予約分がファイルに入る(@TempDir Path dir) throws Exception {
        // StoreSession.shutdown と同じ組み合わせ（OnceAction + 書き切り）を、MemoStore で確かめる
        Path file = dir.resolve("minecraft__overworld.json");
        MemoStore store = new MemoStore(60_000);
        store.open(file);
        store.add(MemoFileTest.memo("x", new MapPoint(1, 1), new MapPoint(5, 5), new MapPoint(1, 5)));
        AtomicInteger calls = new AtomicInteger();
        OnceAction shutdown = new OnceAction(() -> {
            calls.incrementAndGet();
            store.flush();
            store.shutdown();
        });
        shutdown.run();
        shutdown.run();
        assertEquals(1, calls.get());
        // 待ち時間（60 秒）を待たずに書かれている
        assertTrue(Files.exists(file));
        assertEquals(1, MemoFile.read(file).memos().size());
    }
}
