package io.github.radishsprouts.mapscribble.geom;

/**
 * ミニマップの枠の幾何。長さの単位はすべて<strong>物理ピクセル</strong>（GUI 座標 × GUI 倍率）。
 *
 * <p>地図MOD から取った値をここへ正規化し、描画側（{@link ViewTransform} / {@link ClipRegion}）は
 * この record だけを見る。どの地図MOD から取ったかは描画側に出てこない。
 *
 * @param centerPhysX   内側領域（地図が出ている部分）の中心 x
 * @param centerPhysY   同じく y
 * @param innerHalfPhys 内側領域の半辺
 * @param blockPxPhys   1 ブロックあたりの物理ピクセル
 * @param renderAngle   回転角（度）。北固定なら 90
 * @param circular      円形のミニマップか
 * @param worldX        地図の中心のワールド X（ブロック）
 * @param worldZ        同じく Z
 * @param dimension     地図が描いている次元（比較にだけ使う。取れなければ null）
 */
public record MinimapGeometry(double centerPhysX, double centerPhysY, double innerHalfPhys,
                              double blockPxPhys, double renderAngle, boolean circular,
                              double worldX, double worldZ, Object dimension) {

    /**
     * 値が描画に使える状態か。
     *
     * <p>0 除算・巨大な座標でのクラッシュを防ぐため、設計書の第8.4節のとおり
     * ズーム 0・枠が無いほど小さい・NaN を弾く。
     */
    public boolean isUsable() {
        return blockPxPhys > 0 && innerHalfPhys >= 1
                && isFinite(centerPhysX) && isFinite(centerPhysY) && isFinite(innerHalfPhys)
                && isFinite(blockPxPhys) && isFinite(renderAngle) && isFinite(worldX) && isFinite(worldZ);
    }

    /** この枠に対する画面変換（回転を含む）。 */
    public ViewTransform toViewTransform() {
        return ViewTransform.rotated(worldX, worldZ, blockPxPhys, centerPhysX, centerPhysY, renderAngle);
    }

    /**
     * 可視範囲とキャッシュの比較に使う投影（回転を含まない）。
     * 描画の向きは {@link #toViewTransform()} が持つ。
     */
    public Projection toProjection() {
        double side = innerHalfPhys * 2.0;
        return new Projection(worldX, worldZ, blockPxPhys, side, side);
    }

    private static boolean isFinite(double v) {
        return !Double.isNaN(v) && !Double.isInfinite(v);
    }
}
