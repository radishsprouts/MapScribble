package io.github.radishsprouts.mapscribble.map;

import io.github.radishsprouts.mapscribble.compat.GuiCompat;
import io.github.radishsprouts.mapscribble.compat.ScreenCompat;
import io.github.radishsprouts.mapscribble.draw.DrawingController;
import io.github.radishsprouts.mapscribble.draw.MapCanvasRenderer;
import io.github.radishsprouts.mapscribble.draw.ScribbleSession;
import io.github.radishsprouts.mapscribble.geom.Projection;
import io.github.radishsprouts.mapscribble.model.Memo;
import io.github.radishsprouts.mapscribble.store.MemoRepository;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}

/**
 * Xaero's Minimap および FTB Chunks Minimap の HUD 描画時に
 * ミニマップ枠内に手書きメモをクリッピング付きでオーバーレイ描画する。
 */
public final class MinimapHudRenderer {
    private static MapCanvasRenderer canvasRenderer;
    private static boolean reflectionChecked;
    private static Method xaeroGetMinimapMethod;
    private static Field xaeroMinimapXField;
    private static Field xaeroMinimapYField;
    private static Field xaeroMinimapSizeField;

    private MinimapHudRenderer() {
    }

    public static synchronized void init(DrawingController controller) {
        if (canvasRenderer == null) {
            canvasRenderer = new MapCanvasRenderer(controller);
        }
    }

    private static void initReflection() {
        if (reflectionChecked) {
            return;
        }
        reflectionChecked = true;
        try {
            Class<?> sessionClass = Class.forName("xaero.common.XaeroMinimapSession");
            xaeroGetMinimapMethod = sessionClass.getMethod("getCurrentSession");
        } catch (Throwable ignored) {
        }
    }

    //? if >=26.1 {
    public static void render(GuiGraphicsExtractor gfx) {
    //?} else {
    /*public static void render(GuiGraphics gfx) {
    *///?}
        Minecraft mc = Minecraft.getInstance();
        if (ScreenCompat.currentScreen() != null || mc.level == null || mc.player == null) {
            return;
        }

        DrawingController controller = ScribbleSession.controller();
        if (controller == null || !controller.state().isVisible()) {
            return;
        }

        MemoRepository repo = controller.repository();
        if (repo == null || !repo.isAvailable()) {
            return;
        }

        List<Memo> memos = repo.memos();
        if (memos.isEmpty()) {
            return;
        }

        initReflection();
        // Xaero Minimap または FTB Chunks Minimap のオーバーレイ処理
        renderXaeroMinimapOverlay(gfx, mc, controller, repo);
    }

    //? if >=26.1 {
    private static void renderXaeroMinimapOverlay(GuiGraphicsExtractor gfx, Minecraft mc,
                                                 DrawingController controller, MemoRepository repo) {
    //?} else {
    /*private static void renderXaeroMinimapOverlay(GuiGraphics gfx, Minecraft mc,
                                                 DrawingController controller, MemoRepository repo) {
    *///?}
        // Xaero Minimap が存在し動作している場合のみ反映
        if (xaeroGetMinimapMethod == null) {
            return;
        }
        try {
            Object session = xaeroGetMinimapMethod.invoke(null);
            if (session == null) {
                return;
            }
            LocalPlayer player = mc.player;
            if (player == null) {
                return;
            }
            int winW = mc.getWindow().getScreenWidth();
            int winH = mc.getWindow().getScreenHeight();
            int guiScale = (int) Math.max(1.0, (double) mc.getWindow().getGuiScale());

            // プレイヤー中心座標・標準ズーム倍率でミニマップ投影を作成
            Projection proj = new Projection(player.getX(), player.getZ(), 1.0, winW, winH);
            if (canvasRenderer != null) {
                canvasRenderer.render(gfx, proj, mc.level.dimension(), guiScale);
            }
        } catch (Throwable ignored) {
        }
    }
}
