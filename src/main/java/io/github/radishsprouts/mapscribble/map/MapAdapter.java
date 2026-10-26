package io.github.radishsprouts.mapscribble.map;

import io.github.radishsprouts.mapscribble.geom.Projection;
import net.minecraft.client.gui.screens.Screen;

/**
 * 地図MODごとの画面構造・座標変換・スクロール・ボタン配置を抽象化するインターフェース。
 */
public interface MapAdapter {
    /** このアダプターが対象の画面（全画面マップ）を処理できるか。 */
    boolean handles(Screen screen);

    /** 現在の画面のワールド投影情報（Projection）を取得する。 */
    Projection currentProjection(Screen screen);

    /** 現在表示中のディメンションを取得する。 */
    Object currentDimension(Screen screen);

    /** マップを中ドラッグでパン移動する。 */
    void scroll(Screen screen, double deltaPx, double deltaPy);

    /** 手書き中のマップ側のパンニング・慣性スクロール等を抑止する。 */
    default void suppressMapDrag(Screen screen) {
    }

    /** 切替ボタンの X 座標。既定は画面右上（screen.width - 24）。 */
    default int toggleButtonX(Screen screen) {
        return screen.width - 24;
    }

    /** 切替ボタンの Y 座標。既定は 6。 */
    default int toggleButtonY(Screen screen) {
        return 6;
    }

    /** 手書きモード切替ボタン等を画面に追加する。 */
    void onScreenInit(Screen screen);

    /** 画面が閉じられた時のクリーンアップ。 */
    void onScreenClose(Screen screen);
}
