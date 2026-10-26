package io.github.radishsprouts.mapscribble.jm;

import com.mojang.blaze3d.platform.Window;
import io.github.radishsprouts.mapscribble.geom.Projection;
import java.lang.reflect.Method;
import journeymap.api.v2.client.fullscreen.IFullscreen;
import journeymap.api.v2.client.util.UIState;
import net.minecraft.client.Minecraft;

/**
 * JourneyMap の全画面マップから、画面座標とワールド座標の変換（{@link Projection}）を作る。
 * 変換の式そのものは geom/Projection にあり、ここは材料を JourneyMap から取るだけ。
 */
final class MapProjection {
    private static Method getCenterBlockXMethod;
    private static Method getCenterBlockZMethod;
    private static boolean reflectionTried;

    private MapProjection() {
    }

    private static synchronized void initReflection(Class<?> clazz) {
        if (reflectionTried) {
            return;
        }
        reflectionTried = true;
        try {
            getCenterBlockXMethod = clazz.getMethod("getCenterBlockX", boolean.class);
            getCenterBlockZMethod = clazz.getMethod("getCenterBlockZ", boolean.class);
        } catch (Throwable ignored) {
        }
    }

    static double getCenterBlockX(IFullscreen fullscreen) {
        if (fullscreen == null) {
            return 0.0;
        }
        initReflection(fullscreen.getClass());
        if (getCenterBlockXMethod != null) {
            try {
                return (double) getCenterBlockXMethod.invoke(fullscreen, true);
            } catch (Throwable ignored) {
            }
        }
        UIState state = fullscreen.getUiState();
        if (state != null) {
            if (state.blockBounds != null) {
                return (state.blockBounds.minX + state.blockBounds.maxX) / 2.0;
            }
            if (state.mapCenter != null) {
                return state.mapCenter.getX() + 0.5;
            }
        }
        return 0.0;
    }

    static double getCenterBlockZ(IFullscreen fullscreen) {
        if (fullscreen == null) {
            return 0.0;
        }
        initReflection(fullscreen.getClass());
        if (getCenterBlockZMethod != null) {
            try {
                return (double) getCenterBlockZMethod.invoke(fullscreen, true);
            } catch (Throwable ignored) {
            }
        }
        UIState state = fullscreen.getUiState();
        if (state != null) {
            if (state.blockBounds != null) {
                return (state.blockBounds.minZ + state.blockBounds.maxZ) / 2.0;
            }
            if (state.mapCenter != null) {
                return state.mapCenter.getZ() + 0.5;
            }
        }
        return 0.0;
    }

    static Projection of(IFullscreen fullscreen) {
        Window window = Minecraft.getInstance().getWindow();
        return new Projection(
                getCenterBlockX(fullscreen),
                getCenterBlockZ(fullscreen),
                fullscreen.getUiState().blockSize,
                window.getWidth(),
                window.getHeight());
    }

    /** GUI 倍率（物理ピクセル / GUI ピクセル）。 */
    static int guiScale() {
        return (int) Math.max(1.0, (double) Minecraft.getInstance().getWindow().getGuiScale());
    }
}
