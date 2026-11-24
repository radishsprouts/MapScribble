package io.github.radishsprouts.mapscribble.store;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.radishsprouts.mapscribble.compat.WorldIdentityCompat;
import org.junit.jupiter.api.Test;

/** 一覧の行を押した時の選び方（設計書 3.7 の状態の表: ワールドを見分けられない時は手で選べる）。 */
class NotebookServiceSelectTest {
    @Test
    void ワールドを見分けられない時は紐付けず手での選択になる() {
        assertTrue(NotebookService.selectsManually("nb-1", true, WorldIdentityCompat.UNKNOWN));
    }

    @Test
    void ハッシュが取れていて紐付ける時は手での選択にしない() {
        assertFalse(NotebookService.selectsManually("nb-1", true, 12345L));
    }

    @Test
    void 紐付けない指定と直下への切り替えは手での選択() {
        assertTrue(NotebookService.selectsManually("nb-1", false, 12345L));
        assertTrue(NotebookService.selectsManually(null, true, 12345L));
    }
}
