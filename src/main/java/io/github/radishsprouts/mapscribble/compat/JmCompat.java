package io.github.radishsprouts.mapscribble.compat;

//? if >=26.2 {
/*import journeymap.api.v2.common.Context;
*///?} else {
import journeymap.api.v2.client.display.Context;
//?}
import journeymap.api.v2.client.display.Overlay;

/** JourneyMap API の版ごとの差（Context の置き場所）を吸収する。 */
public final class JmCompat {
    private JmCompat() {
    }

    /** オーバーレイの表示先をミニマップだけにする（全画面マップは自前で描くので二重に出さない。Webmap にも出さない）。 */
    public static void showOnMinimapOnly(Overlay overlay) {
        overlay.setActiveUIs(Context.UI.Minimap);
    }
}
