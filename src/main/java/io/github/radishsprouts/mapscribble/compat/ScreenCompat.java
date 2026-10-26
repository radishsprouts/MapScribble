package io.github.radishsprouts.mapscribble.compat;

import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class ScreenCompat {
    private ScreenCompat() {
    }

    public static void setScreen(Screen screen) {
        //? if >=26.2 {
        /*Minecraft.getInstance().gui.setScreen(screen);
        *///?} else {
        Minecraft.getInstance().setScreen(screen);
        //?}
    }

    /** 今開いている画面。何も開いていなければ null。 */
    public static Screen currentScreen() {
        //? if >=26.2 {
        /*return Minecraft.getInstance().gui.screen();
        *///?} else {
        return Minecraft.getInstance().screen;
        //?}
    }

    public static Path gameDirectory() {
        return Minecraft.getInstance().gameDirectory.toPath();
    }

    public static String dimensionId(ResourceKey<Level> key) {
        //? if >=1.21.11 {
        return key.identifier().toString();
        //?} else {
        /*return key.location().toString();
        *///?}
    }
}
