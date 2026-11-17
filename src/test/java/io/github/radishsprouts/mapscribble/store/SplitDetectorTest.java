package io.github.radishsprouts.mapscribble.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.radishsprouts.mapscribble.store.SplitDetector.Result;
import org.junit.jupiter.api.Test;

class SplitDetectorTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";

    @Test
    void 同じ接続で同じディメンションIDのハッシュが変わったらsplit() {
        SplitDetector d = new SplitDetector();
        Object conn = new Object();
        assertFalse(d.observe(conn, OVERWORLD, 11L).split());
        assertFalse(d.observe(conn, OVERWORLD, 11L).split());
        Result r = d.observe(conn, OVERWORLD, 22L);
        assertTrue(r.split());
        assertEquals(11L, r.previous());
        assertEquals(22L, r.current());
    }

    @Test
    void 別のディメンションIDなら比べない_Multiverse型() {
        SplitDetector d = new SplitDetector();
        Object conn = new Object();
        assertFalse(d.observe(conn, OVERWORLD, 11L).split());
        assertFalse(d.observe(conn, "multiverse:world2", 22L).split());
        assertFalse(d.observe(conn, OVERWORLD, 11L).split());
    }

    @Test
    void ハッシュ0は記録せず_挟んでも0でない値同士で比べる() {
        SplitDetector d = new SplitDetector();
        Object conn = new Object();
        assertFalse(d.observe(conn, OVERWORLD, 0L).split());
        assertFalse(d.observe(conn, OVERWORLD, 11L).split());
        assertFalse(d.observe(conn, OVERWORLD, 0L).split());
        assertTrue(d.observe(conn, OVERWORLD, 22L).split());
        assertFalse(d.observe(conn, OVERWORLD, 0L).split());
    }

    @Test
    void 接続が変わったら表が空になる() {
        SplitDetector d = new SplitDetector();
        Object a = new Object();
        Object b = new Object();
        assertFalse(d.observe(a, OVERWORLD, 11L).split());
        assertFalse(d.observe(b, OVERWORLD, 22L).split(), "別の接続の最初の値");
        assertFalse(d.observe(b, OVERWORLD, 22L).split());
        assertTrue(d.observe(b, OVERWORLD, 33L).split());
    }

    @Test
    void 接続がnullなら何もしない() {
        SplitDetector d = new SplitDetector();
        assertFalse(d.observe(null, OVERWORLD, 11L).split());
        assertFalse(d.observe(null, OVERWORLD, 22L).split());
    }

    @Test
    void lobbyのoverworldからsurvivalのnetherを経てsurvivalのoverworldでsplit() {
        SplitDetector d = new SplitDetector();
        Object conn = new Object();
        assertFalse(d.observe(conn, OVERWORLD, 100L).split());
        assertFalse(d.observe(conn, NETHER, 200L).split());
        Result r = d.observe(conn, OVERWORLD, 200L);
        assertTrue(r.split());
        assertEquals(100L, r.previous(), "最初に見た値と比べる");
        assertEquals(200L, r.current());
    }
}
