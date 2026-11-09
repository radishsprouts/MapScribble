package io.github.radishsprouts.mapscribble.geom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.radishsprouts.mapscribble.geom.ToggleButtonPlacement.Saved;
import org.junit.jupiter.api.Test;

class ToggleButtonPlacementTest {
    @Test
    void 右半分と上半分なら右端と上端からの距離で覚える() {
        // GUI 960×540 の右上、右端から 79・上端から 6（画面設計書 AC102）
        Saved s = ToggleButtonPlacement.fromPosition(861, 6, 960, 540);
        assertEquals(new Saved(true, false, 79, 6), s);
    }

    @Test
    void 画面の大きさが変わっても右端からの距離が同じ位置に出る() {
        Saved s = new Saved(true, false, 79, 6);
        assertEquals(861, ToggleButtonPlacement.resolveX(s, 960));
        assertEquals(381, ToggleButtonPlacement.resolveX(s, 480));
        assertEquals(221, ToggleButtonPlacement.resolveX(s, 320));
        assertEquals(6, ToggleButtonPlacement.resolveY(s, 540));
    }

    @Test
    void 左半分と下半分は左端と下端からの距離で覚える() {
        // 左下: x = 10, y = 540 − 20 − 30 = 490
        Saved s = ToggleButtonPlacement.fromPosition(10, 490, 960, 540);
        assertEquals(new Saved(false, true, 10, 30), s);
        assertEquals(10, ToggleButtonPlacement.resolveX(s, 480));
        assertEquals(270 - 20 - 30, ToggleButtonPlacement.resolveY(s, 270));
    }

    @Test
    void 中心がちょうど画面の中央なら右と下に倒す() {
        // 画面幅 100: 左上 x = 40 の中心は 50 = 幅の半分
        Saved s = ToggleButtonPlacement.fromPosition(40, 40, 100, 100);
        assertTrue(s.right());
        assertTrue(s.bottom());
        // 中心が 1 つ手前なら左と上
        Saved t = ToggleButtonPlacement.fromPosition(39, 39, 100, 100);
        assertFalse(t.right());
        assertFalse(t.bottom());
    }

    @Test
    void 画面の端ちょうどは距離0() {
        assertEquals(new Saved(false, false, 0, 0), ToggleButtonPlacement.fromPosition(0, 0, 320, 240));
        assertEquals(new Saved(true, true, 0, 0), ToggleButtonPlacement.fromPosition(300, 220, 320, 240));
    }

    @Test
    void 画面の外へ置いても画面内に丸めてから覚える() {
        assertEquals(new Saved(true, true, 0, 0), ToggleButtonPlacement.fromPosition(5000, 5000, 320, 240));
        assertEquals(new Saved(false, false, 0, 0), ToggleButtonPlacement.fromPosition(-50, -50, 320, 240));
    }

    @Test
    void 大きな保存値は表示だけ画面内に丸め_保存値は変わらない() {
        // AC103: 左端から 900 の保存値。GUI 幅 320 では x = 300、1920 に戻すと 900
        Saved s = new Saved(false, false, 900, 6);
        assertEquals(300, ToggleButtonPlacement.resolveX(s, 320));
        assertEquals(900, ToggleButtonPlacement.resolveX(s, 1920));
        assertEquals(900, s.dx());
        assertEquals(0, ToggleButtonPlacement.resolveX(new Saved(true, false, Integer.MAX_VALUE, 0), 320));
    }

    @Test
    void 画面がボタンより小さくても0に収まる() {
        assertEquals(0, ToggleButtonPlacement.clamp(10, 15));
        assertEquals(0, ToggleButtonPlacement.clamp(-3, 15));
    }

    @Test
    void 既定の位置に置いたら保存しない() {
        // Xaero / FTB の既定は (画面幅 − 24, 6)
        assertNull(ToggleButtonPlacement.toSaveOrNull(296, 6, 320, 240, 296, 6));
        // 1 ピクセルでもずれていれば保存する
        assertEquals(new Saved(true, false, 5, 6), ToggleButtonPlacement.toSaveOrNull(295, 6, 320, 240, 296, 6));
    }

    @Test
    void 保存してから表示すると置いた場所に戻る() {
        for (int x : new int[] {0, 7, 100, 159, 160, 161, 299, 300}) {
            for (int y : new int[] {0, 6, 119, 120, 121, 220}) {
                Saved s = ToggleButtonPlacement.fromPosition(x, y, 320, 240);
                assertEquals(x, ToggleButtonPlacement.resolveX(s, 320), "x=" + x);
                assertEquals(y, ToggleButtonPlacement.resolveY(s, 240), "y=" + y);
            }
        }
    }

    @Test
    void ドラッグの判定は画面ピクセルで_倍率1なら4_GUIピクセル() {
        // 画面設計書 AC90
        assertFalse(ToggleButtonPlacement.isDrag(100.5, 50.5, 103, 50, 1));
        assertTrue(ToggleButtonPlacement.isDrag(100.5, 50.5, 104, 50, 1));
    }

    @Test
    void 倍率2なら2_GUIピクセルでドラッグ() {
        assertFalse(ToggleButtonPlacement.isDrag(100.5, 50.5, 101, 50, 2));
        assertTrue(ToggleButtonPlacement.isDrag(100.5, 50.5, 102, 50, 2));
    }

    @Test
    void 倍率4なら1_GUIピクセルでドラッグ_同じ座標なら切替() {
        assertFalse(ToggleButtonPlacement.isDrag(100.7, 50.7, 100, 50, 4));
        assertTrue(ToggleButtonPlacement.isDrag(100.7, 50.7, 101, 50, 4));
    }

    @Test
    void 押した点の小数は切り捨てて比べる() {
        // 押した 100.9 と描画中の 100 は同じ GUI 座標。倍率が大きくても動かしていないのでドラッグにならない
        assertFalse(ToggleButtonPlacement.isDrag(100.9, 50.9, 100, 50, 4));
        assertFalse(ToggleButtonPlacement.isDrag(100.9, 50.9, 100, 50, 8));
    }

    @Test
    void 縦だけでも判定し_横と縦は別々に見る() {
        assertTrue(ToggleButtonPlacement.isDrag(10, 10, 10, 14, 1));
        assertTrue(ToggleButtonPlacement.isDrag(10, 10, 6, 10, 1));
        // 横 3・縦 3 は、合わせて 4 を超えてもドラッグにならない
        assertFalse(ToggleButtonPlacement.isDrag(10, 10, 13, 13, 1));
    }

    @Test
    void 掴んだ点を保ってカーソルに付いてくる() {
        // ボタン左上 (861, 6) の中の (870, 15) を掴んだ
        int off = ToggleButtonPlacement.grabOffset(870.4, 861);
        assertEquals(9, off);
        assertEquals(861, ToggleButtonPlacement.follow(870, off, 960));
        assertEquals(800, ToggleButtonPlacement.follow(809, off, 960));
    }

    @Test
    void カーソルが画面の外へ出てもボタンは画面内に残る() {
        int off = 9;
        assertEquals(940, ToggleButtonPlacement.follow(5000, off, 960));
        assertEquals(0, ToggleButtonPlacement.follow(-100, off, 960));
    }

    @Test
    void 負の距離は作れない() {
        assertThrows(IllegalArgumentException.class, () -> new Saved(true, true, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new Saved(true, true, 0, -1));
    }

    @Test
    void 地図MODのキーは3つ() {
        assertEquals(java.util.List.of("xaero", "ftb_chunks", "journeymap_legacy"), ToggleButtonPlacement.KEYS);
    }
}
