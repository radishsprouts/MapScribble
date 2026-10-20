package io.github.radishsprouts.mapscribble;

import io.github.radishsprouts.mapscribble.ui.ScribbleKeys;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MapScribble implements ClientModInitializer {
    public static final String MOD_ID = "mapscribble";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        // 操作設定に出るキー（手書きモードの切替・ツールの切替）。操作設定の読み込みより前に登録する
        ScribbleKeys.register();
        LOGGER.info("MapScribble initialized");
    }
}
