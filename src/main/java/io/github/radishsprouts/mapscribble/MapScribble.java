package io.github.radishsprouts.mapscribble;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MapScribble implements ClientModInitializer {
    public static final String MOD_ID = "mapscribble";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("MapScribble initialized");
    }
}
