package io.github.radishsprouts.mapscribble.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.radishsprouts.mapscribble.model.MapPoint;
import io.github.radishsprouts.mapscribble.model.Memo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MemoStoreTest {
    private static Memo memo(double x) {
        return MemoFileTest.memo("m" + x, new MapPoint(x, x));
    }

    @Test
    void 連続の変更は1回の書き込みにまとまる(@TempDir Path dir) throws Exception {
        AtomicInteger writes = new AtomicInteger();
        List<Integer> sizes = new CopyOnWriteArrayList<>();
        DebouncedWriter writer = new DebouncedWriter(200, (f, snapshot) -> {
            writes.incrementAndGet();
            sizes.add(snapshot.size());
            MemoFile.writeAtomic(f, MemoFile.toJson(snapshot));
        });
        MemoStore store = new MemoStore(writer);
        store.open(dir.resolve("minecraft__overworld.json"));
        for (int i = 0; i < 20; i++) {
            store.add(memo(i));
        }
        assertEquals(0, writes.get(), "変更の最中には書かない");
        Thread.sleep(700);
        assertEquals(1, writes.get());
        assertEquals(List.of(20), sizes);
        store.shutdown();
    }

    @Test
    void 追加変更削除でリスナーが呼ばれ保存される(@TempDir Path dir) {
        MemoStore store = new MemoStore(5000);
        List<Integer> seen = new ArrayList<>();
        store.addListener(() -> seen.add(store.memos().size()));
        Path file = dir.resolve("minecraft__overworld.json");
        store.open(file);
        Memo a = memo(1);
        store.add(a);
        store.update(new Memo(a.id(), a.kind(), a.points(), a.color(), a.width(), a.fillOpacity(), "changed",
                a.textScale(), a.onMinimap(), a.created(), a.updated()));
        assertEquals("changed", store.memos().get(0).text());
        store.remove(a.id());
        store.add(memo(2));
        assertEquals(List.of(0, 1, 1, 0, 1), seen);
        store.flush();
        assertEquals(1, MemoFile.read(file).memos().size());
        store.shutdown();
    }

    @Test
    void 保存先が決まるまで使えない(@TempDir Path dir) {
        MemoStore store = new MemoStore(5000);
        assertFalse(store.isAvailable());
        store.add(memo(1));
        assertTrue(store.memos().isEmpty());
        store.open(dir.resolve("a.json"));
        assertTrue(store.isAvailable());
        store.close();
        assertFalse(store.isAvailable());
        store.shutdown();
    }

    @Test
    void 知らないformatと壊れたJSONは読まず上書きもしない(@TempDir Path dir) throws IOException {
        String[] bodies = {"{\"format\":9,\"memos\":[]}", "{\"format\":1,\"memos\":["};
        for (String body : bodies) {
            Path file = dir.resolve("minecraft__overworld.json");
            Files.writeString(file, body);
            MemoStore store = new MemoStore(10);
            List<String> problems = new ArrayList<>();
            store.setProblemHandler(problems::add);
            store.open(file);
            assertEquals(1, problems.size());
            assertFalse(store.isAvailable());
            assertTrue(store.memos().isEmpty());
            store.add(memo(1));
            store.flush();
            store.close();
            assertEquals(body, Files.readString(file), "上書きされていない");
            store.shutdown();
        }
    }

    @Test
    void 切り替わりで書き切って捨てて読み直す順になる(@TempDir Path dir) throws Exception {
        Path worldA = dir.resolve("A");
        Path worldB = dir.resolve("B");
        // B には既にメモがある
        Files.createDirectories(worldB);
        MemoFile.writeAtomic(worldB.resolve("minecraft__overworld.json"), MemoFile.toJson(List.of(memo(100))));

        MemoStore store = new MemoStore(60_000); // 時間では書かれない。切り替わりの書き切りだけが頼り
        SessionController session = new SessionController(store);
        List<String> events = new ArrayList<>();
        store.addListener(() -> {
            Path aFile = worldA.resolve("minecraft__overworld.json");
            events.add("size=" + store.memos().size() + ",aWritten=" + Files.exists(aFile)
                    + ",available=" + store.isAvailable());
        });

        session.started(new SessionKey(worldA, "A", "minecraft:overworld"));
        store.add(memo(1));
        store.add(memo(2));
        events.clear();

        // JourneyMap が別サーバー(B)へ切り替わった（MAPPING_STOPPED → STARTED）
        session.stopped();
        session.started(new SessionKey(worldB, "B", "minecraft:overworld"));

        assertEquals(List.of(
                "size=0,aWritten=true,available=false", // 書き切り済み、手持ちは捨てられた
                "size=1,aWritten=true,available=true"), // B を読み直した
                events);
        assertEquals(2, MemoFile.read(worldA.resolve("minecraft__overworld.json")).memos().size());
        store.shutdown();
    }

    @Test
    void 比べ直しで鍵が変わった時だけ切り替える(@TempDir Path dir) throws Exception {
        MemoStore store = new MemoStore(60_000);
        SessionController session = new SessionController(store);
        AtomicInteger notified = new AtomicInteger();
        store.addListener(notified::incrementAndGet);

        SessionKey a = new SessionKey(dir.resolve("A"), "A", "minecraft:overworld");
        session.started(a);
        store.add(memo(1));
        int afterAdd = notified.get();

        session.recheck(new SessionKey(dir.resolve("A"), "A", "minecraft:overworld"));
        session.recheck(null);
        assertEquals(afterAdd, notified.get(), "同じ鍵・取れない鍵では何もしない");
        assertEquals(1, store.memos().size());

        session.recheck(new SessionKey(dir.resolve("B"), "B", "minecraft:overworld"));
        assertTrue(store.memos().isEmpty());
        assertEquals(1, MemoFile.read(dir.resolve("A").resolve("minecraft__overworld.json")).memos().size());
        store.shutdown();
    }

    @Test
    void 鍵の比較は純粋に判定できる() {
        SessionKey a = new SessionKey(Path.of("x"), "w", "minecraft:overworld");
        assertFalse(SessionKey.needsSwitch(a, a));
        assertFalse(SessionKey.needsSwitch(a, null));
        assertTrue(SessionKey.needsSwitch(null, a));
        assertTrue(SessionKey.needsSwitch(a, new SessionKey(Path.of("x"), "w", "minecraft:the_nether")));
        assertTrue(SessionKey.needsSwitch(a, new SessionKey(Path.of("y"), "w", "minecraft:overworld")));
        assertTrue(SessionKey.needsSwitch(a, new SessionKey(Path.of("x"), "v", "minecraft:overworld")));
    }
}
