package io.github.radishsprouts.mapscribble.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.radishsprouts.mapscribble.model.MemoKind;
import org.junit.jupiter.api.Test;

class EditLayoutTest {
    @Test
    void 高い画面では5行_低い画面では3行() {
        assertEquals(5, new EditLayout(MemoKind.TEXT, false, 260).rows());
        assertEquals(3, new EditLayout(MemoKind.TEXT, false, 259).rows());
    }

    @Test
    void 囲みは塗りとミニマップの2段_線と文字は1段() {
        var region = new EditLayout(MemoKind.REGION, false, 300);
        var line = new EditLayout(MemoKind.LINE, false, 300);
        assertTrue(region.hasFill());
        assertFalse(line.hasFill());
        assertEquals(line.buttonsY() + 24, region.buttonsY());
        assertEquals(line.panelHeight() + 24, region.panelHeight());
    }

    @Test
    void 高さの数値は設計書の積み上げどおり() {
        var l = new EditLayout(MemoKind.REGION, true, 300);
        assertEquals(56, l.boxHeight());
        assertEquals(93, l.hintY());
        assertEquals(121, l.swatchY());
        assertEquals(145, l.cycleRow1Y());
        assertEquals(169, l.cycleRow2Y());
        assertEquals(199, l.buttonsY());
        assertEquals(228, l.panelHeight());
    }

    @Test
    void パネルは画面の中央に置き_上端は4以上() {
        var l = new EditLayout(MemoKind.REGION, false, 300);
        assertEquals(36, l.panelY());
        assertEquals(160, l.panelX(560));
        // 画面が低くてもはみ出さず上端は 4
        assertEquals(4, new EditLayout(MemoKind.REGION, false, 100).panelY());
    }

    @Test
    void 下のボタンはパネルの幅に収まる() {
        var l = new EditLayout(MemoKind.TEXT, true, 300);
        assertTrue(l.deleteX() + l.deleteWidth() <= l.saveX());
        assertTrue(l.saveX() + l.footerButtonWidth() <= l.cancelX());
        assertTrue(l.cancelX() + l.footerButtonWidth() <= EditLayout.PANEL_WIDTH);
    }
}
