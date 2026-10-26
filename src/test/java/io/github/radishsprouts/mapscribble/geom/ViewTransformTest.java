package io.github.radishsprouts.mapscribble.geom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * 回転を含む画面変換の検証。
 * 式の出典は {@code docs/design/xaero-minimap-overlay.md} の第2.2節・第3節。
 */
class ViewTransformTest {

    @Test
    void 回転なしの変換は全画面マップの投影と一致する() {
        Projection p = new Projection(123.4, -56.7, 4.0, 1920, 1080);
        ViewTransform v = ViewTransform.of(p);
        Random random = new Random(7);
        for (int i = 0; i < 200; i++) {
            double wx = random.nextDouble() * 2000 - 1000;
            double wz = random.nextDouble() * 2000 - 1000;
            assertEquals(p.toScreenX(wx), v.screenX(wx, wz), 1e-9);
            assertEquals(p.toScreenY(wz), v.screenY(wx, wz), 1e-9);
        }
        assertTrue(v.isAxisAligned());
        assertTrue(v.isUsable());
    }

    @Test
    void 北固定では東が右で南が下になる() {
        // renderAngle = 90（Xaero の北固定）→ ps = 1, pc = 0 で回転なしに退化する
        ViewTransform v = ViewTransform.rotated(100.0, 200.0, 2.0, 50.0, 60.0, 90.0);
        assertEquals(1.0, v.ps(), 1e-12);
        assertEquals(0.0, v.pc(), 1e-12);

        // 中心は原点に写る
        assertEquals(50.0, v.screenX(100.0, 200.0), 1e-9);
        assertEquals(60.0, v.screenY(100.0, 200.0), 1e-9);
        // 東（+X）は右（+x）、西は左
        assertEquals(50.0 + 20.0, v.screenX(110.0, 200.0), 1e-9);
        assertEquals(50.0 - 20.0, v.screenX(90.0, 200.0), 1e-9);
        // 南（+Z）は下（+y）、北は上
        assertEquals(60.0 + 20.0, v.screenY(100.0, 210.0), 1e-9);
        assertEquals(60.0 - 20.0, v.screenY(100.0, 190.0), 1e-9);
        // 東西の移動は y を動かさない（回転していない）
        assertEquals(60.0, v.screenY(110.0, 200.0), 1e-9);
        assertTrue(v.isAxisAligned());
    }

    @Test
    void 回転時はプレイヤーの向きが画面の上になる() {
        // yaw = 0 は南（+Z）向き。renderAngle = -90 - 0 = -90 → ps = -1, pc = 0
        ViewTransform south = ViewTransform.rotated(0.0, 0.0, 4.0, 100.0, 100.0, -90.0);
        assertEquals(-1.0, south.ps(), 1e-12);
        assertEquals(0.0, south.pc(), 1e-12);
        // 向いている方向（+Z）の 10 ブロック先が画面の上（y が小さい側）に出る
        assertEquals(100.0 - 40.0, south.screenY(0.0, 10.0), 1e-9);
        assertEquals(100.0, south.screenX(0.0, 10.0), 1e-9);
        // 背後（-Z）は下
        assertEquals(100.0 + 40.0, south.screenY(0.0, -10.0), 1e-9);
        assertFalse(south.isAxisAligned());

        // yaw = 180 は北（-Z）向き。renderAngle = -270 → ps = 1, pc = 0（北固定と同じ向き）
        ViewTransform north = ViewTransform.rotated(0.0, 0.0, 4.0, 100.0, 100.0, -270.0);
        assertEquals(1.0, north.ps(), 1e-12);
        assertEquals(0.0, north.pc(), 1e-12);
        assertEquals(100.0 - 40.0, north.screenY(0.0, -10.0), 1e-9);

        // yaw = -90 は東（+X）向き。renderAngle = -90 - (-90) = 0 → ps = 0, pc = -1
        ViewTransform east = ViewTransform.rotated(0.0, 0.0, 4.0, 100.0, 100.0, 0.0);
        assertEquals(0.0, east.ps(), 1e-12);
        assertEquals(-1.0, east.pc(), 1e-12);
        // 向いている方向（+X）の 10 ブロック先が画面の上
        assertEquals(100.0 - 40.0, east.screenY(10.0, 0.0), 1e-9);
        assertEquals(100.0, east.screenX(10.0, 0.0), 1e-9);
        // 右手側（南・+Z）が画面の右
        assertEquals(100.0 + 40.0, east.screenX(0.0, 10.0), 1e-9);
    }

    @Test
    void 回転しても中心からの距離は変わらない() {
        Random random = new Random(11);
        for (int i = 0; i < 50; i++) {
            double angle = random.nextDouble() * 720 - 360;
            ViewTransform v = ViewTransform.rotated(10.0, -20.0, 3.0, 0.0, 0.0, angle);
            for (int j = 0; j < 20; j++) {
                double wx = 10.0 + random.nextDouble() * 100 - 50;
                double wz = -20.0 + random.nextDouble() * 100 - 50;
                double worldDistance = Math.hypot(wx - 10.0, wz + 20.0);
                double screenDistance = Math.hypot(v.screenX(wx, wz), v.screenY(wx, wz));
                assertEquals(worldDistance * 3.0, screenDistance, 1e-7);
            }
        }
    }

    @Test
    void ピクセルからワールドへの換算が逆数になる() {
        ViewTransform v = ViewTransform.rotated(0.0, 0.0, 8.0, 0.0, 0.0, 37.0);
        assertEquals(2.0, v.pixelsToWorld(16.0), 1e-12);
    }

    @Test
    void 使えない値を弾く() {
        assertFalse(ViewTransform.rotated(0, 0, 0.0, 0, 0, 90).isUsable());
        assertFalse(ViewTransform.rotated(0, 0, -1.0, 0, 0, 90).isUsable());
        assertFalse(ViewTransform.rotated(Double.NaN, 0, 4.0, 0, 0, 90).isUsable());
        assertTrue(ViewTransform.rotated(0, 0, 4.0, 0, 0, 90).isUsable());
    }
}
