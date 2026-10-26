package io.github.radishsprouts.mapscribble.jm;

import io.github.radishsprouts.mapscribble.MapScribble;
import io.github.radishsprouts.mapscribble.store.StoreSession;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
//? if >=26.2 || <=1.20.1 {
/*import journeymap.api.v2.common.JourneyMapPlugin;
*///?} else {
import journeymap.api.v2.client.JourneyMapPlugin;
//?}

/**
 * JourneyMap から呼ばれる入口。このクラスを MOD の他の場所から直接参照しない。
 * 入力・描画・ミニマップの配線は {@link ScribbleBridge} が持つ。
 */
@JourneyMapPlugin(apiVersion = "2.0.0")
public class MapScribblePlugin implements IClientPlugin {
    @SuppressWarnings("unused")
    private IClientAPI api;

    @Override
    public void initialize(IClientAPI api) {
        this.api = api;
        MapScribble.LOGGER.info("MapScribble plugin initialize (JourneyMap API {})", IClientAPI.API_VERSION);

        new ScribbleBridge(api, StoreSession::repository).register();
    }

    @Override
    public String getModId() {
        return MapScribble.MOD_ID;
    }
}
