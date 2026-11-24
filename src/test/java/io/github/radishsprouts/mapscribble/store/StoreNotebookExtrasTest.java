package io.github.radishsprouts.mapscribble.store;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
    void 見えている分は移した後に減り_元に戻すと戻り_開いた後に増えたメモは数えない(@TempDir Path dir) throws Exception {
        Path overworld = dir.resolve("minecraft__overworld.json");
        Path nbFile = dir.resolve("nb-1").resolve("minecraft__overworld.json");
        write(overworld, memo("a"), memo("b"), memo("c"));
        MemoStore store = new MemoStore(5000);
        store.open(nbFile, overworld);
        try {
            StoreNotebookExtras extras = new StoreNotebookExtras(store);
            List<UUID> all = new ArrayList<>();
            for (Memo m : store.memos()) {
                all.add(m.id());
            }
            // 開いた時に見えていたのは先頭の2件
            extras.setOpenedVisible(all.subList(0, 2));
            assertEquals(2, extras.legacyVisible());

            CompletableFuture<MoveResult> moved = new CompletableFuture<>();
            store.move(new ArrayList<>(extras.legacyVisibleIds()), MoveDirection.LEGACY_TO_PRIMARY, moved::complete);
            assertEquals(2, moved.get(10, TimeUnit.SECONDS).moved());
            assertEquals(0, extras.legacyVisible(), "移した後は見えている分が 0 件");
            assertEquals(List.of(), List.copyOf(extras.legacyVisibleIds()));
            assertEquals(1, StoreNotebookExtras.countLegacyHere(store), "開いた時に見えていなかった1件は残る");

            CompletableFuture<MoveResult> back = new CompletableFuture<>();
            store.move(new ArrayList<>(all.subList(0, 2)), MoveDirection.PRIMARY_TO_LEGACY, back::complete);
            assertEquals(2, back.get(10, TimeUnit.SECONDS).moved());
            assertEquals(2, extras.legacyVisible(), "元に戻すと戻る（開いた時に見えていた分だけ）");
        } finally {
            store.shutdown();
        }
    }

    @Test
    void 移した後は移し先のメモ帳の件数が移した分だけ増え_元に戻すと減る_終わった時点で数え直す状態になっている(@TempDir Path dir)
            throws Exception {
        Path overworld = dir.resolve("minecraft__overworld.json");
        Path nbFile = dir.resolve("nb-1").resolve("minecraft__overworld.json");
        write(overworld, memo("a"), memo("b"), memo("c"));
        write(nbFile, memo("fresh"));
        MemoStore store = new MemoStore(5000);
        store.open(nbFile, overworld);
        try {
            StoreNotebookExtras extras = new StoreNotebookExtras(store);
            extras.refreshFrom(dir, overworld);
            assertEquals(1, extras.noteCount("nb-1"), "移す前の使用中のメモ帳は 1 件");
            assertEquals(3, extras.legacyTotal());
            assertFalse(extras.isDirty());

            List<UUID> target = new ArrayList<>();
            for (Memo m : store.memos()) {
                if (store.isLegacy(m.id())) {
                    target.add(m.id());
                }
            }
            // 移す操作が終わった時点（画面が結果を受けて一覧を出し直す時）で、書き込みは済み、数え直す状態になっている
            boolean[] atDone = new boolean[2];
            CompletableFuture<MoveResult> moved = new CompletableFuture<>();
            store.move(target, MoveDirection.LEGACY_TO_PRIMARY, r -> {
                atDone[0] = store.isBusy();
                atDone[1] = extras.isDirty();
                moved.complete(r);
            });
            assertEquals(3, moved.get(10, TimeUnit.SECONDS).moved());
            assertFalse(atDone[0], "終わった時点では、移している最中の印は外れている（数え直せる）");
            assertTrue(atDone[1], "終わった時点で、件数は数え直す状態になっている");
            extras.refreshFrom(dir, overworld);
            assertEquals(4, extras.noteCount("nb-1"), "移した 3 件が使用中のメモ帳の件数に足される");
            assertEquals(0, extras.legacyTotal());

            CompletableFuture<MoveResult> back = new CompletableFuture<>();
            store.move(target, MoveDirection.PRIMARY_TO_LEGACY, back::complete);
            assertEquals(3, back.get(10, TimeUnit.SECONDS).moved());
            assertTrue(extras.isDirty());
            extras.refreshFrom(dir, overworld);
            assertEquals(1, extras.noteCount("nb-1"), "元に戻すと件数も戻る");
            assertEquals(3, extras.legacyTotal());
        } finally {
            store.shutdown();
        }
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
