package io.github.radishsprouts.mapscribble.geom;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.radishsprouts.mapscribble.geom.PointerUnits.Unit;
import org.junit.jupiter.api.Test;

class PointerUnitsTest {
    @Test
    void 通知の座標が記録かける倍率なら物理ピクセルと判定する() {
        // GUI 倍率 3、押した位置が GUI 座標 (200.25, 100.5)。6.0.7 以降は物理ピクセル (600.75, 301.5) が入る
        assertEquals(Unit.PHYSICAL, PointerUnits.detect(600.75, 301.5, 200.25, 100.5, 3));
    }

    @Test
    void 通知の座標が記録と同じならGUI座標と判定する() {
        // 6.0.0 系は GUI 座標 (200.25, 100.5) のまま入る
        assertEquals(Unit.GUI, PointerUnits.detect(200.25, 100.5, 200.25, 100.5, 3));
    }

    @Test
    void 誤差0_5未満は同じ位置とみなし0_5以上はみなさない() {
        assertEquals(Unit.PHYSICAL, PointerUnits.detect(600.75 + 0.49, 301.5 - 0.49, 200.25, 100.5, 3));
        assertEquals(Unit.GUI, PointerUnits.detect(200.25 - 0.49, 100.5 + 0.49, 200.25, 100.5, 3));
        assertEquals(Unit.UNKNOWN, PointerUnits.detect(600.75 + 0.5, 301.5, 200.25, 100.5, 3));
        assertEquals(Unit.UNKNOWN, PointerUnits.detect(200.25, 100.5 + 0.5, 200.25, 100.5, 3));
    }

    @Test
    void 片方の軸だけ合っても判定しない() {
        assertEquals(Unit.UNKNOWN, PointerUnits.detect(600.75, 100.5, 200.25, 100.5, 3));
        assertEquals(Unit.UNKNOWN, PointerUnits.detect(200.25, 301.5, 200.25, 100.5, 3));
    }

    @Test
    void 倍率1では両方に合うので判定しない() {
        assertEquals(Unit.UNKNOWN, PointerUnits.detect(640, 360, 640, 360, 1));
    }

    @Test
    void 原点付近で両方に合う時は判定しない() {
        // 倍率 2 で GUI 座標 (0.2, 0.1)。物理 (0.4, 0.2) と GUI (0.2, 0.1) は誤差 0.5 未満でどちらにも合う
        assertEquals(Unit.UNKNOWN, PointerUnits.detect(0.4, 0.2, 0.2, 0.1, 2));
        assertEquals(Unit.UNKNOWN, PointerUnits.detect(0.2, 0.1, 0.2, 0.1, 2));
        assertEquals(Unit.UNKNOWN, PointerUnits.detect(0, 0, 0, 0, 4));
    }

    @Test
    void どちらにも合わない時は判定しない() {
        assertEquals(Unit.UNKNOWN, PointerUnits.detect(777, 555, 200.25, 100.5, 3));
    }

    @Test
    void 記録が無い時は判定しない() {
        assertEquals(Unit.UNKNOWN, PointerUnits.detect(600.75, 301.5, Double.NaN, Double.NaN, 3));
    }

    @Test
    void GUI座標の単位なら倍率を掛け物理や不明ならそのまま返す() {
        assertEquals(600.0, PointerUnits.toPhysical(200.0, Unit.GUI, 3), 1e-9);
        assertEquals(600.0, PointerUnits.toPhysical(600.0, Unit.PHYSICAL, 3), 1e-9);
        assertEquals(600.0, PointerUnits.toPhysical(600.0, Unit.UNKNOWN, 3), 1e-9);
    }

    @Test
    void クリックの座標は記録があれば記録かける倍率を使う() {
        // 通知の座標が GUI 座標でも物理ピクセルでも、記録から求めた値になる
        assertEquals(600.75, PointerUnits.clickToPhysical(200.25, 200.25, Unit.GUI, 3), 1e-9);
        assertEquals(600.75, PointerUnits.clickToPhysical(600.75, 200.25, Unit.PHYSICAL, 3), 1e-9);
        assertEquals(600.75, PointerUnits.clickToPhysical(999.0, 200.25, Unit.UNKNOWN, 3), 1e-9);
    }

    @Test
    void クリックの座標は記録が無ければ判定済みの単位で直す() {
        assertEquals(600.75, PointerUnits.clickToPhysical(200.25, Double.NaN, Unit.GUI, 3), 1e-9);
        assertEquals(600.75, PointerUnits.clickToPhysical(600.75, Double.NaN, Unit.PHYSICAL, 3), 1e-9);
        // 単位がまだ分からなければ、これまでどおり物理ピクセルとして扱う
        assertEquals(600.75, PointerUnits.clickToPhysical(600.75, Double.NaN, Unit.UNKNOWN, 3), 1e-9);
    }
}
