package io.github.radishsprouts.mapscribble.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
//? if >=1.21.11 {
import net.minecraft.client.input.KeyEvent;
//?}

/**
 * 一覧にフォーカスがある時の ↑↓ と Enter（設計書 3.7 の「キーボード」、確かめる項目 196〜199）。
 * 実機で「Tab で一覧へ移し、↓ を2回押して Enter を押しても切り替わらない」と報告された原因は、
 * 旧実装がカーソルを -1（無し）から始めたため ↓ 2回で使用中の行に乗り、使用中の行を選んでも画面が何もしないことだった。
 * 今はフォーカスが入った時にカーソルを使用中の行へ置く。
 *
 * <p>{@link NotebookList} は Minecraft のクラスをコンパイルの時点で参照するので、Fabric の構成でだけ回す。
 * クラス名を NeedsMinecraftTest で終えると、Forge・NeoForge の構成ではコンパイルの対象から外れる（build.*.gradle.kts の
 * sourceSets.test の exclude）。今後同じ種類の試験もこの名前にする。
 */
@Tag("needs-minecraft")
class NotebookListKeyNeedsMinecraftTest {
    private final List<String> picked = new ArrayList<>();

    /** 4 行の一覧。inUseIndex が -1 なら使用中の行なし。フォーカス済み。 */
    private NotebookList listWithInUse(int inUseIndex) {
        NotebookList list = new NotebookList(null, 0, 0, 280, 6, picked::add);
        List<NotebookList.Row> rows = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            rows.add(new NotebookList.Row("id" + i, "メモ帳" + i, "0件", "2026/11/18", i == inUseIndex, false));
        }
        list.setRows(rows);
        list.setFocused(true);
        return list;
    }

    private static boolean press(NotebookList list, int key) {
        //? if >=1.21.11 {
        return list.keyPressed(new KeyEvent(key, 0, 0));
        //?} else {
        /*return list.keyPressed(key, 0, 0);
        *///?}
    }

    private static void down(NotebookList list) {
        assertTrue(press(list, InputConstants.KEY_DOWN));
    }

    @Test
    void フォーカスが入るとカーソルは使用中の行にあり_下1回とEnterで次の行へ切り替わる() {
        NotebookList list = listWithInUse(1);
        down(list);
        assertTrue(press(list, InputConstants.KEY_RETURN));
        assertEquals(List.of("id2"), picked);
    }

    @Test
    void 使用中の行でEnterを押しても何も起きない() {
        NotebookList list = listWithInUse(1);
        press(list, InputConstants.KEY_RETURN);
        assertEquals(List.of(), picked);
    }

    @Test
    void 使用中の行が無ければカーソルは先頭の行() {
        NotebookList list = listWithInUse(-1);
        press(list, InputConstants.KEY_RETURN);
        assertEquals(List.of("id0"), picked);
    }

    @Test
    void 端では折り返さない() {
        NotebookList list = listWithInUse(0);
        press(list, InputConstants.KEY_UP);
        press(list, InputConstants.KEY_RETURN);
        assertEquals(List.of(), picked);
        for (int i = 0; i < 10; i++) {
            down(list);
        }
        press(list, InputConstants.KEY_RETURN);
        assertEquals(List.of("id3"), picked);
    }

    @Test
    void 押せない時はEnterで何も選ばない() {
        NotebookList list = listWithInUse(0);
        list.setPickable(false);
        press(list, InputConstants.KEY_DOWN);
        press(list, InputConstants.KEY_RETURN);
        assertEquals(List.of(), picked);

        NotebookList dimmed = listWithInUse(0);
        dimmed.setDimmed(true);
        press(dimmed, InputConstants.KEY_DOWN);
        press(dimmed, InputConstants.KEY_RETURN);
        assertEquals(List.of(), picked);
    }

    @Test
    void 一覧の中身が変わってもカーソルはidで追う() {
        NotebookList list = listWithInUse(0);
        down(list);
        down(list);
        // 先頭に新しい行が増えても、カーソルは id2 の行のまま
        List<NotebookList.Row> rows = new ArrayList<>();
        rows.add(new NotebookList.Row("new", "新", "0件", "2026/11/18", false, false));
        for (int i = 0; i < 4; i++) {
            rows.add(new NotebookList.Row("id" + i, "メモ帳" + i, "0件", "2026/11/18", i == 0, false));
        }
        list.setRows(rows);
        press(list, InputConstants.KEY_RETURN);
        assertEquals(List.of("id2"), picked);
    }

    private static List<NotebookList.Row> rowsOf(int count, int inUseIndex) {
        List<NotebookList.Row> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            rows.add(new NotebookList.Row("id" + i, "メモ帳" + i, "0件", "2026/11/18", i == inUseIndex, false));
        }
        return rows;
    }

    private NotebookList emptyFocused(int visible) {
        NotebookList list = new NotebookList(null, 0, 0, 280, visible, picked::add);
        list.setFocused(true);
        return list;
    }

    @Test
    void 押せない一覧でも上下はいつも一覧が受け取る() {
        for (int count : new int[] {0, 2, 8}) {
            NotebookList list = emptyFocused(6);
            list.setRows(rowsOf(count, 0));
            list.setPickable(false);
            for (int i = 0; i < 5; i++) {
                assertTrue(press(list, InputConstants.KEY_DOWN), "↓ " + count + " 件");
                assertTrue(press(list, InputConstants.KEY_UP), "↑ " + count + " 件");
            }
            assertFalse(press(list, InputConstants.KEY_RETURN));
            assertEquals(List.of(), picked);
        }
        NotebookList dimmed = emptyFocused(3);
        dimmed.setRows(rowsOf(8, 0));
        dimmed.setDimmed(true);
        assertTrue(press(dimmed, InputConstants.KEY_DOWN));
        assertEquals(1, dimmed.scrollOffset());
        assertEquals(null, dimmed.cursorRowId());
    }

    @Test
    void 行が表示の数を超える押せない一覧では上下でスクロールする() {
        NotebookList list = emptyFocused(3);
        list.setRows(rowsOf(8, 0));
        list.setPickable(false);
        for (int i = 0; i < 10; i++) {
            assertTrue(press(list, InputConstants.KEY_DOWN));
        }
        assertEquals(5, list.scrollOffset());
        for (int i = 0; i < 10; i++) {
            assertTrue(press(list, InputConstants.KEY_UP));
        }
        assertEquals(0, list.scrollOffset());
    }

    @Test
    void 押せるようになった時にフォーカス中ならカーソルが使用中の行へ置かれる() {
        NotebookList list = emptyFocused(6);
        list.setRows(rowsOf(4, 1));
        list.setPickable(false);
        assertEquals(null, list.cursorRowId());
        list.setPickable(true);
        assertEquals("id1", list.cursorRowId());
        down(list);
        assertEquals("id2", list.cursorRowId());

        NotebookList dimmed = emptyFocused(6);
        dimmed.setRows(rowsOf(4, 2));
        dimmed.setDimmed(true);
        assertEquals(null, dimmed.cursorRowId());
        dimmed.setDimmed(false);
        assertEquals("id2", dimmed.cursorRowId());
    }

    @Test
    void 押せない間に行を押してもカーソルは動かず_押せる時は押した行に置かれる() {
        NotebookList list = emptyFocused(6);
        list.setRows(rowsOf(4, 1));
        list.setPickable(false);
        list.click(2);
        assertEquals(null, list.cursorRowId());
        assertEquals(List.of(), picked);
        // 押せるようになった時に置く決まり（使用中の行）はそのまま
        list.setPickable(true);
        assertEquals("id1", list.cursorRowId());

        NotebookList dimmed = emptyFocused(6);
        dimmed.setRows(rowsOf(4, 1));
        dimmed.setDimmed(true);
        dimmed.click(3);
        assertEquals(null, dimmed.cursorRowId());

        picked.clear();
        NotebookList active = emptyFocused(6);
        active.setRows(rowsOf(4, 1));
        active.click(3);
        assertEquals("id3", active.cursorRowId());
        assertEquals(List.of("id3"), picked);
    }

    @Test
    void 押せなくなったらカーソルを消す() {
        NotebookList list = emptyFocused(6);
        list.setRows(rowsOf(4, 1));
        assertEquals("id1", list.cursorRowId());
        list.setPickable(false);
        assertEquals(null, list.cursorRowId());
        assertEquals(null, list.snapshot().cursorId());
    }

    @Test
    void 行が届いた時にフォーカス中ならカーソルが置かれ最初の下で使用中の次の行へ動く() {
        NotebookList list = emptyFocused(6);
        assertEquals(null, list.cursorRowId());
        list.setRows(rowsOf(4, 1));
        assertEquals("id1", list.cursorRowId());
        down(list);
        press(list, InputConstants.KEY_RETURN);
        assertEquals(List.of("id2"), picked);
    }

    @Test
    void フォーカスが無ければ行が届いてもカーソルを置かない() {
        NotebookList list = new NotebookList(null, 0, 0, 280, 6, picked::add);
        list.setRows(rowsOf(4, 1));
        assertEquals(null, list.cursorRowId());
    }

    @Test
    void 作り直しの記録を戻すとカーソルとスクロールがidで戻る() {
        NotebookList old = emptyFocused(3);
        old.setRows(rowsOf(8, 0));
        for (int i = 0; i < 5; i++) {
            down(old);
        }
        NotebookList.Snapshot snap = old.snapshot();
        assertEquals("id5", snap.cursorId());

        // 作り直し: 新しい部品へ行を入れ、記録を戻してからフォーカスを入れる（バニラの setInitialFocus の後）
        NotebookList fresh = new NotebookList(null, 0, 0, 280, 6, picked::add);
        fresh.setRows(rowsOf(8, 0));
        fresh.restore(snap);
        fresh.setFocused(true);
        assertEquals("id5", fresh.cursorRowId());
        assertTrue(fresh.scrollOffset() <= 5 && 5 < fresh.scrollOffset() + fresh.visibleRowCount());
        assertTrue(fresh.scrollOffset() + fresh.visibleRowCount() <= fresh.rowCount());
        down(fresh);
        assertEquals("id6", fresh.cursorRowId());
    }

    @Test
    void 記録のカーソルの行が無くなっていたら位置を丸める() {
        NotebookList old = emptyFocused(6);
        old.setRows(rowsOf(8, 0));
        for (int i = 0; i < 7; i++) {
            down(old);
        }
        NotebookList.Snapshot snap = old.snapshot();
        NotebookList fresh = new NotebookList(null, 0, 0, 280, 6, picked::add);
        fresh.setRows(rowsOf(3, 0));
        fresh.restore(snap);
        fresh.setFocused(true);
        assertEquals("id2", fresh.cursorRowId());
    }

    @Test
    void フォーカスが無ければ上下を取らない_行があっても() {
        NotebookList list = emptyFocused(6);
        list.setRows(rowsOf(4, 0));
        list.setFocused(false);
        assertFalse(press(list, InputConstants.KEY_UP));
    }

    @Test
    void フォーカスが無ければキーを取らない() {
        NotebookList list = listWithInUse(3);
        list.setFocused(false);
        assertFalse(press(list, InputConstants.KEY_DOWN));
    }
}
