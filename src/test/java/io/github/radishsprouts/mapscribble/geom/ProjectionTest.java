package io.github.radishsprouts.mapscribble.geom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class ProjectionTest {
    @Test
    void 画面の中心がワールドの中心に対応する() {
        Projection p = new Projection(123.4, -56.7, 4.0, 1920, 1080);
        assertEquals(123.4, p.toWorldX(960), 1e-9);
        assertEquals(-56.7, p.toWorldZ(540), 1e-9);
        assertEquals(960, p.toScreenX(123.4), 1e-9);
        assertEquals(540, p.toScreenY(-56.7), 1e-9);
    }

    @Test
    void 画面_ワールド_画面の往復が一致する() {
        Random random = new Random(1);
        double[] scales = {0.0625, 0.25, 1, 4, 16, 64};
        for (double scale : scales) {
            Projection p = new Projection(random.nextDouble() * 2000 - 1000, random.nextDouble() * 2000 - 1000,
                    scale, 1920, 1080);
            for (int i = 0; i < 200; i++) {
                double px = random.nextDouble() * 1920;
                double py = random.nextDouble() * 1080;
                assertEquals(px, p.toScreenX(p.toWorldX(px)), 1e-6);
                assertEquals(py, p.toScreenY(p.toWorldZ(py)), 1e-6);
            }
        }
    }

    @Test
    void ワールド_画面_ワールドの往復が一致する() {
        Projection p = new Projection(10.5, -3.25, 8.0, 1280, 720);
        for (double wx = -50; wx <= 50; wx += 7.3) {
            assertEquals(wx, p.toWorldX(p.toScreenX(wx)), 1e-9);
            assertEquals(wx, p.toWorldZ(p.toScreenY(wx)), 1e-9);
        }
    }

    @Test
    void 画面に映っているワールドの範囲と距離の換算() {
        Projection p = new Projection(0, 0, 2.0, 200, 100);
        Rect view = p.visibleWorld();
        assertEquals(-50, view.minX(), 1e-9);
        assertEquals(50, view.maxX(), 1e-9);
        assertEquals(-25, view.minZ(), 1e-9);
        assertEquals(25, view.maxZ(), 1e-9);
        assertEquals(0.375, p.pixelsToWorld(0.75), 1e-9);
    }

    @Test
    void ブロックnは_nからn足す1の範囲_補正なしで床と一致する() {
        // ブロック 10 の中央（10.5）は画面上でブロックの描画範囲の中央に出る
        Projection p = new Projection(0, 0, 16.0, 800, 600);
        double left = p.toScreenX(10);
        double right = p.toScreenX(11);
        assertEquals(16.0, right - left, 1e-9);
        assertEquals((left + right) / 2, p.toScreenX(10.5), 1e-9);
        assertTrue(Math.floor(p.toWorldX((left + right) / 2)) == 10);
    }
}
