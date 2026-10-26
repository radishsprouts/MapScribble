package io.github.radishsprouts.mapscribble.map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class MidDragHandlerTest {
    @BeforeEach
    void setUp() {
        MidDragHandler.reset();
    }

    @Test
    void testMiddleButtonDragCycle() {
        assertFalse(MidDragHandler.isDragging());

        // Button 0 (left) should not trigger mid drag
        assertFalse(MidDragHandler.onMouseDown(0, 100, 100));
        assertFalse(MidDragHandler.isDragging());

        // Button 2 (middle) triggers mid drag
        assertTrue(MidDragHandler.onMouseDown(2, 100.0, 150.0));
        assertTrue(MidDragHandler.isDragging());

        // Move mouse
        AtomicReference<double[]> deltas = new AtomicReference<>();
        MidDragHandler.onMouseMove(115.0, 140.0, (dx, dy) -> deltas.set(new double[]{dx, dy}));

        assertNotNull(deltas.get());
        assertEquals(15.0, deltas.get()[0], 0.001);
        assertEquals(-10.0, deltas.get()[1], 0.001);

        // Subsequent move calculates delta from new position
        MidDragHandler.onMouseMove(120.0, 140.0, (dx, dy) -> deltas.set(new double[]{dx, dy}));
        assertEquals(5.0, deltas.get()[0], 0.001);
        assertEquals(0.0, deltas.get()[1], 0.001);

        // Release middle button
        assertTrue(MidDragHandler.onMouseUp(2));
        assertFalse(MidDragHandler.isDragging());

        // Move after release does not call scroller
        deltas.set(null);
        MidDragHandler.onMouseMove(200.0, 200.0, (dx, dy) -> deltas.set(new double[]{dx, dy}));
        assertNull(deltas.get());
    }

    @Test
    void testReset() {
        MidDragHandler.onMouseDown(2, 50, 50);
        assertTrue(MidDragHandler.isDragging());
        MidDragHandler.reset();
        assertFalse(MidDragHandler.isDragging());
    }
}
