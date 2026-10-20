package io.github.radishsprouts.mapscribble.jm;

import io.github.radishsprouts.mapscribble.MapScribble;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.common.event.ClientEventRegistry;

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

        ClientEventRegistry.MAPPING_EVENT.subscribe(MapScribble.MOD_ID, event ->
                MapScribble.LOGGER.info("[jm] MAPPING {} dim={} world={}", event.getStage(), event.dimension, event.getWorldId()));

        // メモの窓口（作業4の store/StoreSession.repository()）は main に来るまで「使えない」扱いでつないでおく
        new ScribbleBridge(api, () -> null).register();
    }

    @Override
    public String getModId() {
        return MapScribble.MOD_ID;
    }
}
