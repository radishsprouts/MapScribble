package io.github.radishsprouts.mapscribble.geom;

/**
 * ミニマップの枠の幾何を供給するもの。地図MODごとに実装を置く（今は Xaero's Minimap だけ）。
 *
 * <p>実装側は、対象の MOD が入っていない・取得経路が変わった・今フレームはミニマップを描いていない、
 * のいずれでも例外を投げ上げず {@code null} を返す。描画側は「取れなければ描かない」だけで済む。
 */
public interface MinimapSource {

    /** この環境で使えるか（対象の MOD が入っていて、取得経路が揃っているか）。 */
    boolean isAvailable();

    /**
     * 今このフレームの枠の幾何。
     *
     * @param partialTicks 描画の補間位置（0〜1）
     * @return 描くべきでない時（隠れている・無効・取得できない）は {@code null}
     */
    MinimapGeometry geometry(float partialTicks);
}
