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

    /**
     * マウスカーソルの横位置を、MapScribble の座標（GUI 座標 × guiScale の物理ピクセル）で返す。
     * 画面表示中はイベントを介さずに、ゲームが保持しているカーソル位置をそのまま読む。
     */
    public static double mousePhysicalX() {
        Minecraft mc = Minecraft.getInstance();
        com.mojang.blaze3d.platform.Window window = mc.getWindow();
        double guiX = mc.mouseHandler.xpos() * window.getGuiScaledWidth() / Math.max(1, window.getScreenWidth());
        return guiX * Math.max(1.0, (double) window.getGuiScale());
    }

    /** 縦位置。{@link #mousePhysicalX()} と同じ座標。 */
    public static double mousePhysicalY() {
        Minecraft mc = Minecraft.getInstance();
        com.mojang.blaze3d.platform.Window window = mc.getWindow();
        double guiY = mc.mouseHandler.ypos() * window.getGuiScaledHeight() / Math.max(1, window.getScreenHeight());
        return guiY * Math.max(1.0, (double) window.getGuiScale());
    }

    /**
     * 左ボタンが物理的に押されているか。画面表示中は MouseHandler の押下状態が更新されないので、OS のウィンドウから直接読む。
     * 26.3 は GLFW ではなく読み口が無いので、分からない時は null を返す。
     */
    public static Boolean isLeftMouseDown() {
        //? if >=26.3 {
        /*return null;
        *///?} elif >=1.21.11 {
        return org.lwjgl.glfw.GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().handle(),
                org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        //?} else {
        /*return org.lwjgl.glfw.GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().getWindow(),
                org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        *///?}
    }

    /**
     * ゲームのフォルダ。NeoForge 26.1 以降は Minecraft の生成より前に MOD を生成するため、
     * Minecraft.getInstance() ではなくローダーから取る。
     */
    public static Path gameDirectory() {
        //? if fabric {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir();
        //?} elif neoforge {
        /*return net.neoforged.fml.loading.FMLPaths.GAMEDIR.get();
        *///?} else {
        /*return net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get();
        *///?}
    }

    public static String dimensionId(ResourceKey<Level> key) {
        //? if >=1.21.11 {
        return key.identifier().toString();
        //?} else {
        /*return key.location().toString();
        *///?}
    }
}
