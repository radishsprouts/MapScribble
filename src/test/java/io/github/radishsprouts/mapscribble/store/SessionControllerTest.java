package io.github.radishsprouts.mapscribble.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionControllerTest {
    private static SessionKey key(Path dir, String dimension) {
        return new SessionKey(dir, "saves/test", dimension);
    }

    @Test
    void 鍵が同じ間は読み直さない(@TempDir Path dir) {
        MemoStore store = new MemoStore(5000);
        List<Integer> notices = new ArrayList<>();
        store.addListener(() -> notices.add(store.memos().size()));
        SessionController controller = new SessionController(store);
        SessionKey overworld = key(dir, "minecraft:overworld");

        assertTrue(controller.update(overworld, null), "初回は開く");
        assertEquals(1, notices.size());

        // クライアントの毎 tick から呼ばれても、ここで弾かれる（20 回/秒で読み直さない）
        for (int i = 0; i < 100; i++) {
            assertFalse(controller.update(overworld, null), "鍵が同じなら何もしない");
        }
        assertEquals(1, notices.size(), "リスナーへの通知は開いた1回だけ");
        assertSame(overworld, controller.current());
        assertTrue(store.isAvailable());
        store.shutdown();
    }

    @Test
    void 毎tickで作り直した別の実体でも中身が同じなら読み直さない(@TempDir Path dir) {
        MemoStore store = new MemoStore(5000);
        List<Integer> notices = new ArrayList<>();
        store.addListener(() -> notices.add(store.memos().size()));
        SessionController controller = new SessionController(store);

        controller.update(key(dir, "minecraft:overworld"), null);
        // 毎 tick で作り直した別の実体でも、中身が同じなら読み直さない
        assertFalse(controller.update(key(dir, "minecraft:overworld"), null));
        assertEquals(1, notices.size());
        store.shutdown();
    }

    @Test
    void 鍵が変わった時だけ開く直前の処理が走る(@TempDir Path dir) {
        MemoStore store = new MemoStore(5000);
        SessionController controller = new SessionController(store);
        List<String> migrated = new ArrayList<>();
        SessionKey overworld = key(dir, "minecraft:overworld");
        SessionKey nether = key(dir, "minecraft:the_nether");

        assertTrue(controller.update(overworld, k -> migrated.add(k.dimension())));
        assertFalse(controller.update(overworld, k -> migrated.add(k.dimension())));
        assertTrue(controller.update(nether, k -> migrated.add(k.dimension())));
        assertFalse(controller.update(nether, k -> migrated.add(k.dimension())));

        assertEquals(List.of("minecraft:overworld", "minecraft:the_nether"), migrated,
                "古いデータの移行の確認は、切り替わりの時だけ");
        assertEquals(nether, controller.current());
        store.shutdown();
    }

    @Test
    void 停止は開いている時だけ効く(@TempDir Path dir) {
        MemoStore store = new MemoStore(5000);
        SessionController controller = new SessionController(store);
        controller.update(key(dir, "minecraft:overworld"), null);

        List<Integer> notices = new ArrayList<>();
        store.addListener(() -> notices.add(store.memos().size()));
        assertTrue(controller.update(null, null), "開いていれば閉じる");
        assertNull(controller.current());
        assertFalse(store.isAvailable());
        assertEquals(1, notices.size());

        assertFalse(controller.update(null, null), "すでに閉じていれば何もしない");
        assertEquals(1, notices.size());
        store.shutdown();
    }

    @Test
    void 保存先が取れない鍵は停止として扱う(@TempDir Path dir) {
        MemoStore store = new MemoStore(5000);
        SessionController controller = new SessionController(store);
        controller.update(key(dir, "minecraft:overworld"), null);

        controller.started(null);
        assertNull(controller.current());
        assertFalse(store.isAvailable());
        store.shutdown();
    }

    @Test
    void 切り替えの判定だけを問い合わせられる(@TempDir Path dir) {
        MemoStore store = new MemoStore(5000);
        SessionController controller = new SessionController(store);
        SessionKey overworld = key(dir, "minecraft:overworld");

        assertTrue(controller.needsSwitch(overworld), "まだ何も開いていない");
        controller.update(overworld, null);
        assertFalse(controller.needsSwitch(overworld));
        assertTrue(controller.needsSwitch(key(dir, "minecraft:the_end")));
        assertFalse(controller.needsSwitch(null), "鍵が取れない間は何もしない");
        store.shutdown();
    }
}
