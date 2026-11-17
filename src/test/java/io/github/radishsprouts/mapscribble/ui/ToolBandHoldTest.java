package io.github.radishsprouts.mapscribble.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.radishsprouts.mapscribble.draw.DrawingController;
import io.github.radishsprouts.mapscribble.draw.DrawingState;
import io.github.radishsprouts.mapscribble.geom.Projection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 帯の上で押したドラッグは、帯が動いてカーソルが帯の外へ出ても帯のもので、地図の一筆にならない。
 * 帯が画面の下端寄り（bandY=186）の時、帯の余白から始めたドラッグで「線のメモ」の画面が開いた不具合の再現。
 */
class ToolBandHoldTest {
    private static final Projection ONE = new Projection(0, 0, 1.0, 1000, 1000);

    private DrawingState state;
    private DrawingController controller;
    private ToolBand band;

    @BeforeEach
    void setUp() {
        state = new DrawingState();
        state.setModeOn(true);
        controller = new DrawingController(state, () -> null);
        band = new ToolBand(state, controller);
        state.setBandPosition(28, 186);
        state.setBandHitTest((x, y) -> state.isModeOn() && BandLayout.contains(x, y, state.bandX(), state.bandY()));
    }

    @Test
    void 帯の余白で押すとつかみ_離すと外れる() {
        assertFalse(band.isHeld());
        assertTrue(band.click(150, 188, true));
        assertTrue(band.isHeld());
        assertTrue(band.isDragging());
        band.release();
        assertFalse(band.isHeld());
        assertFalse(band.isDragging());
    }

    @Test
    void ボタンの上で押したドラッグも帯のもの() {
        // 帯の原点 (28,186) で、(150,198) は「元に戻す」ボタン（x 146〜166、y 189〜209）の中
        assertEquals(BandLayout.Kind.UNDO, BandLayout.cellAt(150, 198, 28, 186).kind());
        assertTrue(band.click(150, 198, true));
        assertTrue(band.isHeld());
        assertFalse(band.isDragging());
    }

    @Test
    void 帯の外で押してもつかまない() {
        assertFalse(band.click(150, 100, true));
        assertFalse(band.isHeld());
    }

    @Test
    void 右ボタンはつかまない() {
        assertTrue(band.click(150, 188, false));
        assertFalse(band.isHeld());
    }

    @Test
    void 帯が遅れて追従しカーソルが帯の外へ出ても_つかみは続く() {
        assertTrue(band.click(31, 187, true));
        // 帯はまだ動いていない（描画で追従する）のに、カーソルは帯の上端より上へ出た
        assertFalse(state.isOverBand(31, 180));
        assertTrue(band.isHeld());
    }

    /** つかみの判定が無い時の原因: 帯の外のカーソルで extendStroke を呼ぶと、一筆が無くても始まってしまう。 */
    @Test
    void 一筆が無い時の_extendStroke_は一筆を始める() {
        assertNull(controller.activeStroke());
        controller.extendStroke(31, 180, ONE);
        assertNotNull(controller.activeStroke());
        assertEquals(1, controller.activeStroke().points().size());
    }
}
