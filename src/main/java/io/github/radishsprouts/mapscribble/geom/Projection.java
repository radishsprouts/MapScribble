package io.github.radishsprouts.mapscribble.geom;

/**
 * 画面座標とワールド座標の変換（純粋な計算）。
 *
 * <p>画面座標は JourneyMap の内部と同じ<strong>物理ピクセル</strong>（GUI 座標 × GUI 倍率）。
 * 描画に使う GUI 座標とは混ぜない。ブロック n は [n, n+1) の範囲なので、0.5 ブロックの補正は要らない。
 *
 * @param centerX 地図の中心のワールド X（小数）
 * @param centerZ 地図の中心のワールド Z（小数）
 * @param scale   1 ブロックの画面上の大きさ（物理ピクセル。UIState.blockSize）
 * @param width   地図を描く領域の幅（物理ピクセル。ウィンドウの幅）
 * @param height  同じく高さ
 */
public record Projection(double centerX, double centerZ, double scale, double width, double height) {

    /** 画面の x（物理ピクセル）→ ワールド X。 */
    public double toWorldX(double px) {
        return centerX + (px - width / 2.0) / scale;
    }

    /** 画面の y（物理ピクセル）→ ワールド Z。 */
    public double toWorldZ(double py) {
        return centerZ + (py - height / 2.0) / scale;
    }

    /** ワールド X → 画面の x（物理ピクセル）。 */
    public double toScreenX(double wx) {
        return width / 2.0 + (wx - centerX) * scale;
    }

    /** ワールド Z → 画面の y（物理ピクセル）。 */
    public double toScreenY(double wz) {
        return height / 2.0 + (wz - centerZ) * scale;
    }

    /** 画面に映っているワールドの範囲。 */
    public Rect visibleWorld() {
        return new Rect(toWorldX(0), toWorldZ(0), toWorldX(width), toWorldZ(height));
    }

    /** 画面上の距離（物理ピクセル）をワールドの距離（ブロック）に直す。 */
    public double pixelsToWorld(double pixels) {
        return pixels / scale;
    }
}
