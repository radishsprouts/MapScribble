package io.github.radishsprouts.mapscribble.map;

/**
 * 手書きモード中のマウス中ボタン（ホイールクリック）ドラッグによるマップスクロールを管理する。
 * 各地図MODの中ボタンスクロール処理（JourneyMap: centerOn, Xaero: cameraX/Z, FTB Chunks: scrollTo）に移動差分を渡す。
 */
public final class MidDragHandler {
    @FunctionalInterface
    public interface Scroller {
        /**
         * マウス移動差分（物理ピクセル）を受け取りマップをスクロールする。
         *
         * @param deltaPx X方向のマウス移動量
         * @param deltaPy Y方向のマウス移動量
         */
        void scroll(double deltaPx, double deltaPy);
    }

    private static boolean dragging;
    private static double lastX;
    private static double lastY;

    private MidDragHandler() {
    }

    public static boolean isDragging() {
        return dragging;
    }

    public static boolean onMouseDown(int button, double mouseX, double mouseY) {
        if (button == 2) { // 2 = マウス中ボタン（ミドルボタン）
            dragging = true;
            lastX = mouseX;
            lastY = mouseY;
            return true;
        }
        return false;
    }

    public static void onMouseMove(double mouseX, double mouseY, Scroller scroller) {
        if (dragging && scroller != null) {
            double dx = mouseX - lastX;
            double dy = mouseY - lastY;
            lastX = mouseX;
            lastY = mouseY;
            if (dx != 0.0 || dy != 0.0) {
                scroller.scroll(dx, dy);
            }
        }
    }

    public static boolean onMouseUp(int button) {
        if (button == 2) {
            dragging = false;
            return true;
        }
        return false;
    }

    public static void reset() {
        dragging = false;
    }
}
