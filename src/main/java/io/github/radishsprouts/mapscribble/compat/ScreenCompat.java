package io.github.radishsprouts.mapscribble.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

public final class ScreenCompat {
    private ScreenCompat() {
    }

    public static void setScreen(@Nullable Screen screen) {
        //? if >=26.2 {
        /*Minecraft.getInstance().gui.setScreen(screen);
        *///?} else {
        Minecraft.getInstance().setScreen(screen);
        //?}
    }

    /** 今開いている画面。何も開いていなければ null。 */
    public static @Nullable Screen currentScreen() {
        //? if >=26.2 {
        /*return Minecraft.getInstance().gui.screen();
        *///?} else {
        return Minecraft.getInstance().screen;
        //?}
    }
}
