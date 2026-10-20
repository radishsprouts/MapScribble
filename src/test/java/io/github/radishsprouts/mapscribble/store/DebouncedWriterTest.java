package io.github.radishsprouts.mapscribble.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.radishsprouts.mapscribble.model.MapPoint;
import io.github.radishsprouts.mapscribble.model.Memo;
import io.github.radishsprouts.mapscribble.model.MemoKind;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class DebouncedWriterTest {
    private record Written(Path file, int count) {
    }

    private static Memo memo(String text) {
        Instant now = Instant.EPOCH;
        return new Memo(UUID.randomUUID(), MemoKind.TEXT, List.of(new MapPoint(0, 0)), 0, 1, 0f, text, 1f, true, now,
                now);
    }

    @Test
    void 予約は遅れて書かれ_連続の依頼は最後の1回だけ書く() throws Exception {
        List<Written> written = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch done = new CountDownLatch(1);
        try (DebouncedWriter writer = new DebouncedWriter(150, (file, snapshot) -> {
            written.add(new Written(file, snapshot.size()));
            done.countDown();
        })) {
            Path file = Path.of("a.json");
            writer.request(file, List.of(memo("1")));
            writer.request(file, List.of(memo("1"), memo("2")));
            writer.request(file, List.of(memo("1"), memo("2"), memo("3")));
            assertEquals(0, written.size(), "遅延の間はまだ書かれない");
            assertTrue(done.await(5, TimeUnit.SECONDS), "遅延の後に書かれる");
            Thread.sleep(300);
            assertEquals(1, written.size());
            assertEquals(3, written.get(0).count(), "最後のスナップショットが書かれる");
        }
    }

    @Test
    void flushは予約分を待たずに書き切る() throws Exception {
        List<Written> written = Collections.synchronizedList(new ArrayList<>());
        try (DebouncedWriter writer = new DebouncedWriter(60_000, (file, snapshot) -> written.add(new Written(file,
                snapshot.size())))) {
            writer.request(Path.of("a.json"), List.of(memo("x")));
            assertEquals(0, written.size());
            writer.flush();
            assertEquals(1, written.size());
        }
    }

    @Test
    void 保存先が違えばそれぞれ書く() {
        List<Written> written = Collections.synchronizedList(new ArrayList<>());
        try (DebouncedWriter writer = new DebouncedWriter(60_000, (file, snapshot) -> written.add(new Written(file,
                snapshot.size())))) {
            writer.request(Path.of("overworld.json"), List.of(memo("a")));
            writer.request(Path.of("nether.json"), List.of(memo("a"), memo("b")));
            writer.flush();
        }
        assertEquals(2, written.size());
        assertTrue(written.contains(new Written(Path.of("overworld.json"), 1)));
        assertTrue(written.contains(new Written(Path.of("nether.json"), 2)));
    }

    @Test
    void 書き込みが失敗しても次の予約は書ける() {
        List<Written> written = Collections.synchronizedList(new ArrayList<>());
        int[] calls = {0};
        try (DebouncedWriter writer = new DebouncedWriter(60_000, (file, snapshot) -> {
            calls[0]++;
            if (calls[0] == 1) {
                throw new IOException("disk full");
            }
            written.add(new Written(file, snapshot.size()));
        })) {
            writer.request(Path.of("a.json"), List.of(memo("a")));
            writer.flush();
            assertEquals(0, written.size());
            writer.request(Path.of("a.json"), List.of(memo("a"), memo("b")));
            writer.flush();
        }
        assertEquals(1, written.size());
        assertEquals(2, written.get(0).count());
    }

    @Test
    void 予約が無いflushは何も書かない() {
        List<Written> written = Collections.synchronizedList(new ArrayList<>());
        try (DebouncedWriter writer = new DebouncedWriter(100, (file, snapshot) -> written.add(new Written(file,
                snapshot.size())))) {
            writer.flush();
        }
        assertEquals(0, written.size());
    }

    @Test
    void 閉じた後の依頼もその場で書いて取りこぼさない() {
        List<Written> written = Collections.synchronizedList(new ArrayList<>());
        DebouncedWriter writer = new DebouncedWriter(60_000, (file, snapshot) -> written.add(new Written(file,
                snapshot.size())));
        writer.close();
        writer.request(Path.of("late.json"), List.of(memo("a")));
        assertEquals(1, written.size());
    }
}
