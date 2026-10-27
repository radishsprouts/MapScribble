package io.github.radishsprouts.mapscribble.map.ftb;

import io.github.radishsprouts.mapscribble.MapScribble;
import io.github.radishsprouts.mapscribble.geom.Projection;
import io.github.radishsprouts.mapscribble.map.MapAdapter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * FTB Chunks (LargeMapScreen) との連携アダプター。
 * FTB Library の ScreenWrapper から LargeMapScreen / RegionMapPanel を取得し、
 * リージョン座標系から Projection への変換、中ドラッグスクロール、grabbed フラグによるドラッグ抑止を行う。
 */
public final class FtbChunksAdapter implements MapAdapter {
    // FTB Library 26.1 以降は ScreenWrapper が client.gui.widget パッケージへ移っている（旧版は ui パッケージ）。
    // メソッド名（getGui 等）は新旧で同じなので、クラス名だけ両方を受け付ける
    private static final String WRAPPER_CLASS_NEW = "dev.ftb.mods.ftblibrary.client.gui.widget.ScreenWrapper";
    private static final String WRAPPER_CLASS_OLD = "dev.ftb.mods.ftblibrary.ui.ScreenWrapper";
    private static final String LARGE_MAP_NEW = "dev.ftb.mods.ftbchunks.client.gui.map.LargeMapScreen";
    private static final String LARGE_MAP_OLD = "dev.ftb.mods.ftbchunks.client.gui.LargeMapScreen";

    private Method getGuiMethod;
    private Field regionPanelField;
    private Field grabbedField;
    private Method getRegionTileSizeMethod;
    private Method currentDimensionMethod;

    private Method getScrollXMethod;
    private Method getScrollYMethod;
    private Method getXMethod;
    private Method getYMethod;
    private Method scrollToMethod;
    private Field regionMinXField;
    private Field regionMinZField;

    private boolean reflectionInitialized;
    private boolean available;
    /** 取得の失敗を記録したか。currentProjection は 1 フレームに複数回呼ばれるので、毎回出さない。 */
    private boolean projectionWarned;
    private boolean scrollWarned;

    public FtbChunksAdapter() {
        // 遅延初期化のため、コンストラクタではクラスロードを行わない
    }

    private synchronized void initReflection(Class<?> wrapperClass) {
        if (reflectionInitialized) {
            return;
        }
        reflectionInitialized = true;
        try {
            getGuiMethod = wrapperClass.getMethod("getGui");

            Class<?> mapClass = null;
            try {
                mapClass = Class.forName(LARGE_MAP_NEW, false, wrapperClass.getClassLoader());
            } catch (ClassNotFoundException e) {
                mapClass = Class.forName(LARGE_MAP_OLD, false, wrapperClass.getClassLoader());
            }

            regionPanelField = findField(mapClass, "regionPanel");
            grabbedField = findField(mapClass, "grabbed");
            getRegionTileSizeMethod = findMethod(mapClass, "getRegionTileSize");
            currentDimensionMethod = findMethod(mapClass, "currentDimension");

            if (regionPanelField != null) {
                Class<?> panelClass = regionPanelField.getType();
                getScrollXMethod = findMethod(panelClass, "getScrollX");
                getScrollYMethod = findMethod(panelClass, "getScrollY");
                getXMethod = findMethod(panelClass, "getX");
                getYMethod = findMethod(panelClass, "getY");
                scrollToMethod = findMethod(panelClass, "scrollTo", double.class, double.class);
                regionMinXField = findField(panelClass, "regionMinX");
                regionMinZField = findField(panelClass, "regionMinZ");
            }

            available = (getGuiMethod != null && regionPanelField != null && getRegionTileSizeMethod != null);
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

    private Object getLargeMapScreen(Screen screen) {
        if (screen == null) {
            return null;
        }
        String wrapperName = screen.getClass().getName();
        if (!wrapperName.equals(WRAPPER_CLASS_NEW) && !wrapperName.equals(WRAPPER_CLASS_OLD)) {
            return null;
        }
        initReflection(screen.getClass());
        if (!available) {
            return null;
        }
        try {
            Object gui = getGuiMethod.invoke(screen);
            if (gui != null) {
                String name = gui.getClass().getName();
                if (name.equals(LARGE_MAP_NEW) || name.equals(LARGE_MAP_OLD)) {
                    return gui;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Override
    public boolean handles(Screen screen) {
        return getLargeMapScreen(screen) != null;
    }

    @Override
    public Projection currentProjection(Screen screen) {
        Object largeMap = getLargeMapScreen(screen);
        if (largeMap == null) {
            return null;
        }
        try {
            Object panel = regionPanelField.get(largeMap);
            if (panel == null) {
                return null;
            }
            int tileSize = (int) getRegionTileSizeMethod.invoke(largeMap);
            if (tileSize <= 0) {
                return null;
            }

            Minecraft mc = Minecraft.getInstance();
            double guiScale = mc.getWindow().getGuiScale();
            // Projection は物理ピクセル前提なのでフレームバッファの大きさを、
            // FTB の画面上の座標は GUI 座標なので GUI 換算の大きさを、それぞれ取る
            int winW = mc.getWindow().getWidth();
            int winH = mc.getWindow().getHeight();
            double guiW = mc.getWindow().getGuiScaledWidth();
            double guiH = mc.getWindow().getGuiScaledHeight();

            double scrollX = ((Number) getScrollXMethod.invoke(panel)).doubleValue() - ((Number) getXMethod.invoke(panel)).doubleValue();
            double scrollY = ((Number) getScrollYMethod.invoke(panel)).doubleValue() - ((Number) getYMethod.invoke(panel)).doubleValue();

            int minX = regionMinXField != null ? regionMinXField.getInt(panel) : 0;
            int minZ = regionMinZField != null ? regionMinZField.getInt(panel) : 0;

            double pixelsPerBlock = (tileSize * guiScale) / 512.0;
            double centerWorldX = ((guiW / 2.0 + scrollX) / (double) tileSize + minX) * 512.0;
            double centerWorldZ = ((guiH / 2.0 + scrollY) / (double) tileSize + minZ) * 512.0;

            return new Projection(centerWorldX, centerWorldZ, pixelsPerBlock, winW, winH);
        } catch (Exception e) {
            if (!projectionWarned) {
                projectionWarned = true;
                MapScribble.LOGGER.warn("[MapScribble] FTB Chunks の表示範囲を取得できませんでした: {}",
                        e.getMessage());
            }
            return null;
        }
    }

    @Override
    public Object currentDimension(Screen screen) {
        Object largeMap = getLargeMapScreen(screen);
        if (largeMap == null || currentDimensionMethod == null) {
            return null;
        }
        try {
            return currentDimensionMethod.invoke(largeMap);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void scroll(Screen screen, double deltaPx, double deltaPy) {
        Object largeMap = getLargeMapScreen(screen);
        if (largeMap == null || scrollToMethod == null) {
            return;
        }
        try {
            Object panel = regionPanelField.get(largeMap);
            if (panel == null) {
                return;
            }
            double guiScale = Minecraft.getInstance().getWindow().getGuiScale();
            double deltaGx = deltaPx / guiScale;
            double deltaGy = deltaPy / guiScale;

            double curScrollX = ((Number) getScrollXMethod.invoke(panel)).doubleValue();
            double curScrollY = ((Number) getScrollYMethod.invoke(panel)).doubleValue();

            scrollToMethod.invoke(panel, curScrollX - deltaGx, curScrollY - deltaGy);
        } catch (Exception e) {
            if (!scrollWarned) {
                scrollWarned = true;
                MapScribble.LOGGER.warn("[MapScribble] FTB Chunks のスクロールに失敗しました: {}", e.getMessage());
            }
        }
    }

    @Override
    public void suppressMapDrag(Screen screen) {
        Object largeMap = getLargeMapScreen(screen);
        if (largeMap == null || grabbedField == null) {
            return;
        }
        try {
            grabbedField.setInt(largeMap, 0);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onScreenInit(Screen screen) {
    }

    @Override
    public void onScreenClose(Screen screen) {
    }
}
