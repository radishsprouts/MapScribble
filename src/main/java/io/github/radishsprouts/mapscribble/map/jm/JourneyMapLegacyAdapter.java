package io.github.radishsprouts.mapscribble.map.jm;

import com.mojang.blaze3d.platform.Window;
import io.github.radishsprouts.mapscribble.MapScribble;
import io.github.radishsprouts.mapscribble.geom.Projection;
import io.github.radishsprouts.mapscribble.map.MapAdapter;
import io.github.radishsprouts.mapscribble.map.ScreenMapHost;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * JourneyMap 5.10.x（v1 API 世代）向けの全画面マップ連携アダプター。
 * JourneyMap 6.0.0+（v2 API 世代）の ScribbleBridge が動いていない環境でフォールバックとして動作する。
 */
public final class JourneyMapLegacyAdapter implements MapAdapter {
    private static final String FULLSCREEN_CLASS = "journeymap.client.ui.fullscreen.Fullscreen";

    private boolean reflectionInitialized;
    private boolean available;
    private Field gridRendererField;
    private Method getCenterBlockXMethod;
    private Method getCenterBlockZMethod;
    private Method getUiStateMethod;
    private Field blockSizeField;
    private Field dimensionField;
    private Method centerOnMethod;
    private Field isScrollingField;

    public JourneyMapLegacyAdapter() {
        // 遅延初期化のため、コンストラクタではクラスロードを行わない
    }

    private synchronized void initReflection(Class<?> fsClass) {
        if (reflectionInitialized) {
            return;
        }
        reflectionInitialized = true;
        try {
            gridRendererField = findField(fsClass, "gridRenderer");
            isScrollingField = findField(fsClass, "isScrolling");
            centerOnMethod = findMethod(fsClass, "centerOn", double.class, double.class);

            if (gridRendererField != null) {
                Class<?> gridClass = gridRendererField.getType();
                getCenterBlockXMethod = findMethod(gridClass, "getCenterBlockX");
                getCenterBlockZMethod = findMethod(gridClass, "getCenterBlockZ");
                getUiStateMethod = findMethod(gridClass, "getUIState");
            }

            available = (centerOnMethod != null && gridRendererField != null);
        } catch (Throwable ignored) {
            available = false;
        }
    }

    private static Field findField(Class<?> clazz, String name) {
        Class<?> c = clazz;
        while (c != null && c != Object.class) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        return null;
    }

    private static Method findMethod(Class<?> clazz, String name, Class<?>... parameterTypes) {
        Class<?> c = clazz;
        while (c != null && c != Object.class) {
            try {
                Method m = c.getDeclaredMethod(name, parameterTypes);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException e) {
                c = c.getSuperclass();
            }
        }
        return null;
    }

    @Override
    public boolean handles(Screen screen) {
        if (screen == null) {
            return false;
        }
        // v2 プラグイン（ScribbleBridge）が初期化されている場合は v2 側に任せる
        if (ScreenMapHost.hasPrimaryBridge()) {
            return false;
        }
        if (!screen.getClass().getName().equals(FULLSCREEN_CLASS)) {
            return false;
        }
        initReflection(screen.getClass());
        return available;
    }

    private Object getUiState(Screen screen) {
        if (gridRendererField == null || getUiStateMethod == null) {
            return null;
        }
        try {
            Object grid = gridRendererField.get(screen);
            return grid != null ? getUiStateMethod.invoke(grid) : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Projection currentProjection(Screen screen) {
        if (!handles(screen)) {
            return null;
        }
        try {
            Object grid = gridRendererField.get(screen);
            if (grid == null) {
                return null;
            }
            double cx = (double) getCenterBlockXMethod.invoke(grid);
            double cz = (double) getCenterBlockZMethod.invoke(grid);

            Object uiState = getUiState(screen);
            if (uiState == null) {
                return null;
            }
            if (blockSizeField == null) {
                blockSizeField = findField(uiState.getClass(), "blockSize");
            }
            if (blockSizeField == null) {
                return null;
            }
            double blockSize = blockSizeField.getDouble(uiState);

            Window window = Minecraft.getInstance().getWindow();
            return new Projection(cx, cz, blockSize, window.getWidth(), window.getHeight());
        } catch (Exception e) {
            MapScribble.LOGGER.warn("Failed to get JourneyMap legacy projection: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public Object currentDimension(Screen screen) {
        Object uiState = getUiState(screen);
        if (uiState == null) {
            return null;
        }
        try {
            if (dimensionField == null) {
                dimensionField = findField(uiState.getClass(), "dimension");
            }
            return dimensionField != null ? dimensionField.get(uiState) : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void scroll(Screen screen, double deltaPx, double deltaPy) {
        if (!handles(screen) || centerOnMethod == null) {
            return;
        }
        try {
            Projection proj = currentProjection(screen);
            if (proj == null) {
                return;
            }
            double worldDx = proj.pixelsToWorld(deltaPx);
            double worldDz = proj.pixelsToWorld(deltaPy);
            centerOnMethod.invoke(screen, proj.centerX() - worldDx, proj.centerZ() - worldDz);
        } catch (Exception e) {
            MapScribble.LOGGER.warn("Failed to scroll JourneyMap legacy: {}", e.getMessage());
        }
    }

    @Override
    public void suppressMapDrag(Screen screen) {
        if (!handles(screen) || isScrollingField == null) {
            return;
        }
        try {
            isScrollingField.set(screen, Boolean.FALSE);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onScreenInit(Screen screen) {
    }

    @Override
    public void onScreenClose(Screen screen) {
    }
    @Override
    public int toggleButtonX(Screen screen) {
        // JourneyMap Legacy は右上に Close ボタン（screen.width - 28）があるため、左隣に配置する
        return screen.width - 52;
    }

    @Override
    public int toggleButtonY(Screen screen) {
        return 6;
    }
}
