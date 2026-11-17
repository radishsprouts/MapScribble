package io.github.radishsprouts.mapscribble.store;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.radishsprouts.mapscribble.store.SessionGate.Change;
import org.junit.jupiter.api.Test;

class SessionGateTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";

    @Test
    void 初めての入力はLEVELで_同じ入力が続く間はNONE() {
        SessionGate gate = new SessionGate();
        Object level = new Object();
        Object conn = new Object();
        assertEquals(Change.LEVEL, gate.check(level, OVERWORLD, conn, false));
        // 開けた後の毎 tick
        assertEquals(Change.NONE, gate.check(level, OVERWORLD, conn, true));
        assertEquals(Change.NONE, gate.check(level, OVERWORLD, conn, true));
    }

    @Test
    void 開いていない間は同じ入力でも鍵を作り直す() {
        SessionGate gate = new SessionGate();
        Object level = new Object();
        Object conn = new Object();
        gate.check(level, OVERWORLD, conn, false);
        assertEquals(Change.KEY, gate.check(level, OVERWORLD, conn, false));
    }

    @Test
    void ディメンションか接続先が変わったらKEY_ハッシュは読み直さない() {
        SessionGate gate = new SessionGate();
        Object level = new Object();
        Object conn = new Object();
        gate.check(level, OVERWORLD, conn, true);
        assertEquals(Change.KEY, gate.check(level, NETHER, conn, true));
        assertEquals(Change.NONE, gate.check(level, NETHER, conn, true));
        assertEquals(Change.KEY, gate.check(level, NETHER, new Object(), true));
    }

    @Test
    void ClientLevelの実体が変わったらLEVEL_ディメンションが同じでも() {
        // プロキシの先でサーバーが変わると、ディメンション（overworld→overworld）も接続の実体も同じまま
        // ClientLevel だけが作り直される。これを拾えることが判定の要
        SessionGate gate = new SessionGate();
        Object conn = new Object();
        gate.check(new Object(), OVERWORLD, conn, true);
        assertEquals(Change.LEVEL, gate.check(new Object(), OVERWORLD, conn, true));
    }

    @Test
    void 切断で全部忘れる() {
        SessionGate gate = new SessionGate();
        Object level = new Object();
        Object conn = new Object();
        gate.check(level, OVERWORLD, conn, true);
        gate.reset();
        // 同じ実体でも、切断後の最初の入力は ClientLevel が変わった扱い
        assertEquals(Change.LEVEL, gate.check(level, OVERWORLD, conn, false));
    }
}
