package io.github.radishsprouts.mapscribble.compat;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.radishsprouts.mapscribble.MapScribble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * 画面の部品で版ごとに違う所（キーの押下状態の取り方、画像の描画）を吸収する。
 * 26.3 は {@code InputConstants.isKeyDown} が窓を取らず、キー定数が SDL のスキャンコードになっている。
 */
public final class GuiCompat {
    private GuiCompat() {
    }

    /** この MOD の画像（assets/mapscribble/ からの相対パス）。 */
    public static Identifier texture(String path) {
        return Identifier.fromNamespaceAndPath(MapScribble.MOD_ID, path);
    }

    /** 正方形の画像を (x, y) に size × size で描く。tint は ARGB で、白い線画に掛ける色。 */
    public static void blitIcon(GuiGraphicsExtractor g, Identifier texture, int x, int y, int size, int tint) {
        g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0f, 0f, size, size, size, size, tint);
    }

    /**
     * Ctrl が押されているか。イベントの修飾キーに加えて、窓のキー状態も見る
     * （JourneyMap の全画面マップ経由でも、修飾キーが載らない場合に備える）。
     */
    public static boolean isControlDown(KeyEvent event) {
        return event.hasControlDown() || isKeyDown(InputConstants.KEY_LCONTROL) || isKeyDown(InputConstants.KEY_RCONTROL);
    }

    private static boolean isKeyDown(int key) {
        //? if >=26.3 {
        /*return InputConstants.isKeyDown(key);
        *///?} else {
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), key);
        //?}
    }
}
