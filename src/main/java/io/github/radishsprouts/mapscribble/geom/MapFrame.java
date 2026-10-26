package io.github.radishsprouts.mapscribble.geom;

/**
 * 1 フレームの描画条件。全画面マップとミニマップで同じ描画エンジンを共有するために、
 * 両者の違い（回転・切り抜き・絞り込み）をこの record に閉じ込める。
 *
 * @param projection  可視範囲の算出とキャッシュの比較に使う（回転を含まない）
 * @param view        画面座標への変換（回転を含む）
 * @param clip        切り抜き（物理ピクセル）
 * @param guiScale    GUI 倍率
 * @param dimension   今いる次元（比較にだけ使う）
 * @param minimapOnly true なら {@code onMinimap()} が false のメモを描かない
 */
public record MapFrame(Projection projection, ViewTransform view, ClipRegion clip,
                       int guiScale, Object dimension, boolean minimapOnly) {

    /** 全画面マップ用（回転なし・切り抜きは描画面いっぱい・すべてのメモ）。 */
    public static MapFrame fullscreen(Projection p, int guiScale, Object dimension) {
        return new MapFrame(p, ViewTransform.of(p),
                ClipRegion.rect(0, 0, p.width(), p.height()), guiScale, dimension, false);
    }

    /** ミニマップ用（回転あり・枠の内側で切り抜き・ミニマップに出すメモだけ）。 */
    public static MapFrame minimap(MinimapGeometry g, int guiScale, Object dimension) {
        return new MapFrame(g.toProjection(), g.toViewTransform(), ClipRegion.of(g),
                guiScale, dimension, true);
    }
}
