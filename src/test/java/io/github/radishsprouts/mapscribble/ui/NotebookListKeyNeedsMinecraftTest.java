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

    @Test
    void フォーカスが無ければキーを取らない() {
        NotebookList list = listWithInUse(3);
        list.setFocused(false);
        assertFalse(press(list, InputConstants.KEY_DOWN));
    }
}
