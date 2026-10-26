package io.github.radishsprouts.mapscribble.geom;

/**
 * ワールド座標から画面座標（物理ピクセル）への変換。<strong>回転を含む</strong>。
 *
 * <p>{@link Projection} は {@code toScreenX(double wx)} が x だけを受ける形なので、x と z を混ぜる回転を
 * 表現できない。ミニマップ（Xaero の既定は北固定 OFF で回転する）のために、回転を持てる変換をここに置く。
 * 回転が無い場合（{@code ps = 1, pc = 0}）は {@link Projection} と同じ式に退化するので、
 * 全画面マップとミニマップで同じ描画コードを共有できる。
 *
 * <p>回転の式は Xaero の {@code MinimapRenderer#getRenderAngle(boolean)} に合わせてある
 * （{@code docs/design/xaero-minimap-overlay.md} の第2.2節・第3節）。
 *
 * @param centerX 地図の中心のワールド X（ブロック）
 * @param centerZ 地図の中心のワールド Z（ブロック）
 * @param blockPx 1 ブロックあたりの画面上の大きさ（物理ピクセル）
 * @param originX 中心が描かれる画面の x（物理ピクセル。描画面の左上が原点）
 * @param originY 同じく y
 * @param ps      回転行列の sin 成分
 * @param pc      回転行列の cos 成分
 */
public record ViewTransform(double centerX, double centerZ, double blockPx,
                            double originX, double originY, double ps, double pc) {

    /** 全画面マップ用（回転なし・中心は描画面の中央）。 */
    public static ViewTransform of(Projection p) {
        return new ViewTransform(p.centerX(), p.centerZ(), p.scale(),
                p.width() / 2.0, p.height() / 2.0, 1.0, 0.0);
    }

    /**
     * Xaero の回転角（度）から作る。
     *
     * <p>{@code theta = PI - toRadians(renderAngle)} とし、{@code ps = sin(theta)}、{@code pc = cos(theta)}。
     * 北固定（{@code renderAngle = 90}）なら {@code ps = 1, pc = 0} で回転なしに退化する。
     *
     * @param renderAngleDegrees {@code northLocked ? 90.0 : (-90.0 - camera.yaw)}
     */
    public static ViewTransform rotated(double centerX, double centerZ, double blockPx,
                                        double originX, double originY, double renderAngleDegrees) {
        double theta = Math.PI - Math.toRadians(renderAngleDegrees);
        return new ViewTransform(centerX, centerZ, blockPx, originX, originY,
                Math.sin(theta), Math.cos(theta));
    }

    /** ワールド座標 → 画面の x（物理ピクセル）。 */
    public double screenX(double wx, double wz) {
        return originX + (ps * (wx - centerX) - pc * (wz - centerZ)) * blockPx;
    }

    /** ワールド座標 → 画面の y（物理ピクセル）。 */
    public double screenY(double wx, double wz) {
        return originY + (pc * (wx - centerX) + ps * (wz - centerZ)) * blockPx;
    }

    /** 画面上の距離（物理ピクセル）→ ワールドの距離（ブロック）。 */
    public double pixelsToWorld(double pixels) {
        return pixels / blockPx;
    }

    /**
     * 回転していないか（北固定・全画面マップ）。
     *
     * <p>北固定は {@code renderAngle = 90} から三角関数で作るので {@code pc} に 1e-16 程度の誤差が残る。
     * 厳密な比較では false になってしまうため、許容誤差で判定する。
     */
    public boolean isAxisAligned() {
        return Math.abs(ps - 1.0) < 1e-9 && Math.abs(pc) < 1e-9;
    }

    /** 値が計算に使える状態か（0 除算・NaN を弾く）。 */
    public boolean isUsable() {
        return blockPx > 0 && isFinite(blockPx) && isFinite(centerX) && isFinite(centerZ)
                && isFinite(originX) && isFinite(originY) && isFinite(ps) && isFinite(pc);
    }

    private static boolean isFinite(double v) {
        return !Double.isNaN(v) && !Double.isInfinite(v);
    }
}
