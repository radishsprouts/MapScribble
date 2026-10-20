package io.github.radishsprouts.mapscribble.jm;

import io.github.radishsprouts.mapscribble.MapScribble;
import java.util.concurrent.atomic.AtomicInteger;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.client.event.FullscreenMapEvent;
import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.common.event.ClientEventRegistry;
import journeymap.api.v2.common.event.FullscreenEventRegistry;

/**
 * JourneyMap から呼ばれる入口。このクラスを MOD の他の場所から直接参照しない。
 * 現段階では、全画面マップのイベントが届くことを確かめるための確認用ログだけを出す。
 */
@JourneyMapPlugin(apiVersion = "2.0.0")
public class MapScribblePlugin implements IClientPlugin {
    /** 高頻度のイベントは、確認用ログを出しすぎないよう最初の数件だけにする。 */
    private static final int HIGH_FREQUENCY_LOG_LIMIT = 5;

    private final AtomicInteger dragLogged = new AtomicInteger();
    private final AtomicInteger moveLogged = new AtomicInteger();
    private final AtomicInteger renderLogged = new AtomicInteger();
    private final AtomicInteger displayLogged = new AtomicInteger();

    @SuppressWarnings("unused")
    private IClientAPI api;

    @Override
    public void initialize(IClientAPI api) {
        this.api = api;
        MapScribble.LOGGER.info("MapScribble plugin initialize (JourneyMap API {})", IClientAPI.API_VERSION);

        ClientEventRegistry.MAPPING_EVENT.subscribe(MapScribble.MOD_ID, event ->
                MapScribble.LOGGER.info("[jm] MAPPING {} dim={} world={}", event.getStage(), event.dimension, event.getWorldId()));

        ClientEventRegistry.DISPLAY_UPDATE_EVENT.subscribe(MapScribble.MOD_ID, event -> {
            if (displayLogged.getAndIncrement() < HIGH_FREQUENCY_LOG_LIMIT) {
                MapScribble.LOGGER.info("[jm] DISPLAY_UPDATE ui={} active={} zoom={} blockSize={}",
                        event.uiState.ui, event.uiState.active, event.uiState.zoom, event.uiState.blockSize);
            }
        });

        FullscreenEventRegistry.FULLSCREEN_MAP_CLICK_EVENT.subscribe(MapScribble.MOD_ID, event ->
                MapScribble.LOGGER.info("[jm] CLICK {} button={} block={} cancellable={}",
                        event.getStage(), event.getButton(), event.getLocation(), event.isCancellable()));

        FullscreenEventRegistry.FULLSCREEN_MAP_DRAG_EVENT.subscribe(MapScribble.MOD_ID, event -> {
            if (dragLogged.getAndIncrement() < HIGH_FREQUENCY_LOG_LIMIT) {
                MapScribble.LOGGER.info("[jm] DRAG {} button={} block={}",
                        event.getStage(), event.getButton(), event.getLocation());
            }
        });

        FullscreenEventRegistry.FULLSCREEN_MAP_MOVE_EVENT.subscribe(MapScribble.MOD_ID, event -> {
            if (moveLogged.getAndIncrement() < HIGH_FREQUENCY_LOG_LIMIT) {
                MapScribble.LOGGER.info("[jm] MOVE block={}", event.getLocation());
            }
        });

        FullscreenEventRegistry.FULLSCREEN_RENDER_EVENT.subscribe(MapScribble.MOD_ID, event -> {
            if (renderLogged.getAndIncrement() < 1) {
                MapScribble.LOGGER.info("[jm] RENDER screen={}", event.getFullscreen().getScreen().getClass().getName());
            }
        });

        FullscreenEventRegistry.ADDON_BUTTON_DISPLAY_EVENT.subscribe(MapScribble.MOD_ID, event ->
                MapScribble.LOGGER.info("[jm] ADDON_BUTTON_DISPLAY screen={}", event.getFullscreen().getScreen().getClass().getName()));
    }

    @Override
    public String getModId() {
        return MapScribble.MOD_ID;
    }

    /** ステージの比較用（PRE/POST）。 */
    @SuppressWarnings("unused")
    private static boolean isPre(FullscreenMapEvent.Stage stage) {
        return stage == FullscreenMapEvent.Stage.PRE;
    }
}
