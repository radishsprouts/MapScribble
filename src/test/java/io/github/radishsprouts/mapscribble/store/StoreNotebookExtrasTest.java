package io.github.radishsprouts.mapscribble.store;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.radishsprouts.mapscribble.model.MapPoint;
import io.github.radishsprouts.mapscribble.model.Memo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 保存先の画面の「ここの分を移す（N件）…」の件数と、実際に移る件数の一致（設計書 v0.4 の 5章）。 */
class StoreNotebookExtrasTest {
    private static Memo memo(String text) {
        return MemoFileTest.memo(text, new MapPoint(1, 1), new MapPoint(5, 5), new MapPoint(1, 5));
    }

    private static void write(Path file, Memo... memos) throws IOException {
        MemoFile.writeAtomic(file, MemoFile.toJson(List.of(memos)));
    }

    @Test
    void ボタンの件数は今のディメンションの分ける前のメモだけで_移る件数と一致し_ほかのディメンションは動かない(@TempDir Path dir)
            throws Exception {
        Path overworld = dir.resolve("minecraft__overworld.json");
        Path nether = dir.resolve("minecraft__the_nether.json");
        Path nbFile = dir.resolve("nb-1").resolve("minecraft__overworld.json");
        write(overworld, memo("a"), memo("b"), memo("c"), memo("d"));
        write(nether, memo("x"), memo("y"), memo("z"));
        write(nbFile, memo("fresh"));
        byte[] netherBefore = Files.readAllBytes(nether);

        MemoStore store = new MemoStore(5000);
        store.open(nbFile, overworld);
        try {
            // ボタンの件数: 今のディメンションの分ける前のメモ（メモ帳のメモは数えない）
            assertEquals(4, StoreNotebookExtras.countLegacyHere(store));
            assertEquals(5, store.memos().size());

            // 移す操作が対象にするメモ（DrawingController#moveLegacyToNotebook と同じ条件）
            List<UUID> target = new ArrayList<>();
            for (Memo m : store.memos()) {
                if (store.isLegacy(m.id())) {
                    target.add(m.id());
                }
            }
            CompletableFuture<MoveResult> future = new CompletableFuture<>();
            store.move(target, MoveDirection.LEGACY_TO_PRIMARY, future::complete);
            MoveResult result = future.get(10, TimeUnit.SECONDS);
            assertTrue(result.ok());
            assertEquals(4, result.moved(), "移った件数は、移す前のボタンの件数（4）と同じ");
            assertEquals(0, StoreNotebookExtras.countLegacyHere(store));
            assertArrayEquals(netherBefore, Files.readAllBytes(nether), "ほかのディメンションのファイルには触らない");
        } finally {
            store.shutdown();
        }
    }
}
