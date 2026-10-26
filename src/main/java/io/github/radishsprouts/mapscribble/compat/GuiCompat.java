package io.github.radishsprouts.mapscribble.compat;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.radishsprouts.mapscribble.MapScribble;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}

//? if >=1.21.11 {
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * 画面描画・入力系で版ごとに異なる API を一元的に吸収する互換レイヤー。
 * 1.20.1 〜 26.3 のすべての世代に対応する。
 */
public final class GuiCompat {
    private GuiCompat() {
    }

    /** ボタンクリック音を再生する。 */
    public static void playButtonClickSound() {
        Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    /**
     * この MOD のテクスチャ識別子を取得する。
     * ResourceLocation.fromNamespaceAndPath は素の Minecraft では 1.21 で入った（1.20.6 の公式マッピングには無い。
     * Forge 1.20.6 だけが自前で移植している）ので、1.20.6 は {@code >=1.21} の外側（public コンストラクタ）に置く。
     * Forge 1.20.6 ではこのコンストラクタが非推奨（警告のみ）だが、素の 1.20.6 でも通る。
     */
    //? if >=1.21.11 {
    public static Identifier texture(String path) {
        return Identifier.fromNamespaceAndPath(MapScribble.MOD_ID, path);
    }
    //?} elif >=1.21 {
    /*public static ResourceLocation texture(String path) {
        return ResourceLocation.fromNamespaceAndPath(MapScribble.MOD_ID, path);
    }
    *///?} else {
    /*public static ResourceLocation texture(String path) {
        return new ResourceLocation(MapScribble.MOD_ID, path);
    }
    *///?}

    //? if >=26.1 {
    public static void pushPose(GuiGraphicsExtractor g) {
        g.pose().pushMatrix();
    }

    public static void popPose(GuiGraphicsExtractor g) {
        g.pose().popMatrix();
    }

    public static void translate2D(GuiGraphicsExtractor g, float x, float y) {
        g.pose().translate(x, y);
    }

    public static void scale2D(GuiGraphicsExtractor g, float sx, float sy) {
        g.pose().scale(sx, sy);
    }

    public static void rotate2D(GuiGraphicsExtractor g, float degrees) {
        g.pose().rotate((float) Math.toRadians(degrees));
    }
    //?} elif >=1.21.11 {
    /*public static void pushPose(GuiGraphics g) {
        g.pose().pushMatrix();
    }

    public static void popPose(GuiGraphics g) {
        g.pose().popMatrix();
    }

    public static void translate2D(GuiGraphics g, float x, float y) {
        g.pose().translate(x, y);
    }

    public static void scale2D(GuiGraphics g, float sx, float sy) {
        g.pose().scale(sx, sy);
    }

    public static void rotate2D(GuiGraphics g, float degrees) {
        g.pose().rotate((float) Math.toRadians(degrees));
    }
    *///?} else {
    /*public static void pushPose(GuiGraphics g) {
        g.pose().pushPose();
    }

    public static void popPose(GuiGraphics g) {
        g.pose().popPose();
    }

    public static void translate2D(GuiGraphics g, float x, float y) {
        g.pose().translate(x, y, 0f);
    }

    public static void scale2D(GuiGraphics g, float sx, float sy) {
        g.pose().scale(sx, sy, 1f);
    }

    public static void rotate2D(GuiGraphics g, float degrees) {
        // 1.20.1〜1.21.4 の PoseStack.mulPose
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(degrees));
    }
    *///?}

    //? if >=26.1 {
    public static void nextLayer(GuiGraphicsExtractor g) {
    }
    //?} else {
    /*public static void nextLayer(GuiGraphics g) {
    }
    *///?}

    /**
     * 描画を矩形の内側へ切り抜く。<strong>引数は GUI 座標</strong>（Minecraft が内部で GUI 倍率を掛ける）。
     * メソッド名・引数は 1.20.1〜26.x で同じなので、吸収するのはクラス名の差だけ。
     * 必ず {@code try}/{@code finally} で {@link #disableScissor} と対にする
     * （切り抜きが残ると HUD 全体が切り抜かれたままになる）。
     */
    //? if >=26.1 {
    public static void enableScissor(GuiGraphicsExtractor g, int minX, int minY, int maxX, int maxY) {
        g.enableScissor(minX, minY, maxX, maxY);
    }

    public static void disableScissor(GuiGraphicsExtractor g) {
        g.disableScissor();
    }
    //?} else {
    /*public static void enableScissor(GuiGraphics g, int minX, int minY, int maxX, int maxY) {
        g.enableScissor(minX, minY, maxX, maxY);
    }

    public static void disableScissor(GuiGraphics g) {
        g.disableScissor();
    }
    *///?}

    /** 描画中のカメラの yaw（度）。ミニマップの回転角の計算に使う。 */
    public static float cameraYaw() {
        //? if >=26.2 {
        /*return Minecraft.getInstance().gameRenderer.mainCamera().yRot();
        *///?} elif >=1.21.11 {
        return Minecraft.getInstance().gameRenderer.getMainCamera().yRot();
        //?} else {
        /*return Minecraft.getInstance().gameRenderer.getMainCamera().getYRot();
        *///?}
    }

    /** このフレームの補間位置（0〜1）。エンティティ位置の補間に使う。 */
    public static float partialTicks() {
        //? if >=1.21.4 {
        return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        //?} elif >=1.21 {
        /*return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        *///?} else {
        /*return Minecraft.getInstance().getFrameTime();
        *///?}
    }

    /**
     * F3 のデバッグ画面が出ているか。
     * 1.20.5 で renderDebug が Options から DebugScreenOverlay（private）へ移り、
     * getDebugOverlay().showDebugScreen() が公開の入口になった（1.20.6 の実物で確認）。
     */
    public static boolean debugOverlayVisible() {
        //? if >=1.20.5 {
        return Minecraft.getInstance().getDebugOverlay().showDebugScreen();
        //?} else {
        /*return Minecraft.getInstance().options.renderDebug;
        *///?}
    }

    //? if >=26.1 {
    public static void fill(GuiGraphicsExtractor g, int minX, int minY, int maxX, int maxY, int color) {
        g.fill(minX, minY, maxX, maxY, color);
    }

    public static void fill(GuiGraphicsExtractor g, int minX, int minY, int maxX, int maxY, int z, int color) {
        g.fill(minX, minY, maxX, maxY, color);
    }
    //?} elif >=1.21.11 {
    /*public static void fill(GuiGraphics g, int minX, int minY, int maxX, int maxY, int color) {
        g.fill(minX, minY, maxX, maxY, color);
    }

    public static void fill(GuiGraphics g, int minX, int minY, int maxX, int maxY, int z, int color) {
        g.fill(minX, minY, maxX, maxY, color);
    }
    *///?} else {
    /*public static void fill(GuiGraphics g, int minX, int minY, int maxX, int maxY, int color) {
        g.fill(minX, minY, maxX, maxY, color);
    }

    public static void fill(GuiGraphics g, int minX, int minY, int maxX, int maxY, int z, int color) {
        g.fill(minX, minY, maxX, maxY, z, color);
    }
    *///?}

    //? if >=26.1 {
    public static void outline(GuiGraphicsExtractor g, int x, int y, int width, int height, int color) {
        g.outline(x, y, width, height, color);
    }
    //?} else {
    /*public static void outline(GuiGraphics g, int x, int y, int width, int height, int color) {
        // 1.20.1〜1.21.4 の renderOutline
        g.renderOutline(x, y, width, height, color);
    }
    *///?}

    //? if >=26.1 {
    public static void text(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int color, boolean shadow) {
        g.text(font, text, x, y, color, shadow);
    }
    //?} else {
    /*public static void text(GuiGraphics g, Font font, Component text, int x, int y, int color, boolean shadow) {
        g.drawString(font, text, x, y, color, shadow);
    }
    *///?}

    //? if >=26.1 {
    public static void centeredText(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int color, boolean shadow) {
        g.centeredText(font, text, x, y, color);
    }
    //?} else {
    /*public static void centeredText(GuiGraphics g, Font font, Component text, int x, int y, int color, boolean shadow) {
        g.drawCenteredString(font, text, x, y, color);
    }
    *///?}

    //? if >=26.1 {
    public static void blitIcon(GuiGraphicsExtractor g, Identifier texture, int x, int y, int size, int tint) {
        g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0f, 0f, size, size, size, size, tint);
    }

    public static void blitIcon(GuiGraphicsExtractor g, Identifier texture, int x, int y, int size) {
        blitIcon(g, texture, x, y, size, 0xFFFFFFFF);
    }
    //?} elif >=1.21.11 {
    /*public static void blitIcon(GuiGraphics g, Identifier texture, int x, int y, int size, int tint) {
        g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0f, 0f, size, size, size, size, tint);
    }

    public static void blitIcon(GuiGraphics g, Identifier texture, int x, int y, int size) {
        blitIcon(g, texture, x, y, size, 0xFFFFFFFF);
    }
    *///?} elif >=1.21.4 {
    /*public static void blitIcon(GuiGraphics g, ResourceLocation texture, int x, int y, int size, int tint) {
        g.blit(net.minecraft.client.renderer.RenderType::guiTextured, texture, x, y, 0f, 0f, size, size, size, size, tint);
    }

    public static void blitIcon(GuiGraphics g, ResourceLocation texture, int x, int y, int size) {
        blitIcon(g, texture, x, y, size, 0xFFFFFFFF);
    }
    *///?} else {
    /*public static void blitIcon(GuiGraphics g, ResourceLocation texture, int x, int y, int size, int tint) {
        float a = ((tint >> 24) & 0xFF) / 255.0f;
        float r = ((tint >> 16) & 0xFF) / 255.0f;
        float gr = ((tint >> 8) & 0xFF) / 255.0f;
        float b = (tint & 0xFF) / 255.0f;
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(r, gr, b, a);
        g.blit(texture, x, y, 0f, 0f, size, size, size, size);
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    public static void blitIcon(GuiGraphics g, ResourceLocation texture, int x, int y, int size) {
        blitIcon(g, texture, x, y, size, 0xFFFFFFFF);
    }
    *///?}

    /** ツールチップ描画。 */
    //? if >=26.1 {
    public static void drawTooltip(GuiGraphicsExtractor g, Font font, List<Component> lines, int mouseX, int mouseY) {
        g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
    }

    public static void drawTooltip(GuiGraphicsExtractor g, Font font, Component component, int mouseX, int mouseY) {
        drawTooltip(g, font, List.of(component), mouseX, mouseY);
    }
    //?} elif >=1.21.11 {
    /*public static void drawTooltip(GuiGraphics g, Font font, List<Component> lines, int mouseX, int mouseY) {
        g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
    }

    public static void drawTooltip(GuiGraphics g, Font font, Component component, int mouseX, int mouseY) {
        drawTooltip(g, font, List.of(component), mouseX, mouseY);
    }
    *///?} else {
    /*public static void drawTooltip(GuiGraphics g, Font font, List<Component> lines, int mouseX, int mouseY) {
        g.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    public static void drawTooltip(GuiGraphics g, Font font, Component component, int mouseX, int mouseY) {
        drawTooltip(g, font, List.of(component), mouseX, mouseY);
    }
    *///?}

    /** 1.20.1 に存在しない MutableComponent.withColor(int) を吸収する。 */
    public static MutableComponent withColor(MutableComponent component, int rgb) {
        //? if >=1.20.3 {
        return component.withColor(rgb);
        //?} else {
        /*return component.withStyle(s -> s.withColor(net.minecraft.network.chat.TextColor.fromRgb(rgb)));
        *///?}
    }

    /** キーが物理的に押されているか。 */
    public static boolean isKeyDown(int key) {
        //? if >=26.3 {
        /*return InputConstants.isKeyDown(key);
        *///?} elif >=1.21.11 {
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), key);
        //?} else {
        /*return InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), key);
        *///?}
    }

    //? if >=1.21.11 {
    public static boolean isControlDown(KeyEvent event) {
        return event.hasControlDown() || isKeyDown(InputConstants.KEY_LCONTROL) || isKeyDown(InputConstants.KEY_RCONTROL);
    }

    public static boolean isShiftDown(KeyEvent event) {
        return event.hasShiftDown() || isKeyDown(InputConstants.KEY_LSHIFT) || isKeyDown(InputConstants.KEY_RSHIFT);
    }
    //?} else {
    /*public static boolean isControlDown(int modifiers) {
        return (modifiers & 2) != 0 || isKeyDown(InputConstants.KEY_LCONTROL) || isKeyDown(InputConstants.KEY_RCONTROL);
    }

    public static boolean isShiftDown(int modifiers) {
        return (modifiers & 1) != 0 || isKeyDown(InputConstants.KEY_LSHIFT) || isKeyDown(InputConstants.KEY_RSHIFT);
    }
    *///?}

    public static boolean isControlDown() {
        return isKeyDown(InputConstants.KEY_LCONTROL) || isKeyDown(InputConstants.KEY_RCONTROL);
    }

    public static boolean isShiftDown() {
        return isKeyDown(InputConstants.KEY_LSHIFT) || isKeyDown(InputConstants.KEY_RSHIFT);
    }
}
